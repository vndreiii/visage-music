use jwalk::WalkDir;
use lofty::file::{AudioFile, FileType, TaggedFileExt};
use lofty::prelude::*;
use lofty::tag::{ItemKey, Tag};
use rayon::prelude::*;
use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{Instant, UNIX_EPOCH};

const AUDIO_EXTS: &[&str] = &[
    "mp3", "mp2", "aac", "m4a", "m4b", "ogg", "oga", "opus", "wma", "mp4", "flac", "wav", "aiff", "aif",
    "alac", "ape", "wv", "tak", "dsf", "dff", "caf",
];
const LOSSLESS_EXTS: &[&str] = &["flac", "wav", "aiff", "aif", "alac", "ape", "wv", "tak", "dsf", "dff", "caf"];
const SKIP_DIRS: &[&str] = &[
    "node_modules", "__macosx", "$recycle.bin", "system volume information", "venv", "env", "__pycache__",
    "site-packages", "dist-packages", "test-data", "testdata", "fixtures",
];
const COVER_NAMES: &[&str] = &["cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png"];

#[derive(Deserialize)]
struct Request {
    roots: Vec<String>,
    covers_dir: String,
    /// Files the caller already has indexed; unchanged ones are not re-read.
    #[serde(default)]
    known: Vec<Known>,
    #[serde(default = "default_min_bytes")]
    min_bytes: u64,
}

#[derive(Deserialize)]
struct Known {
    path: String,
    size: u64,
    mtime_ms: i64,
    #[serde(default)]
    cover_path: Option<String>,
}

fn default_min_bytes() -> u64 {
    8_192
}

#[derive(Serialize, Deserialize, Clone, Default)]
struct Track {
    path: String,
    size: u64,
    mtime_ms: i64,
    title: Option<String>,
    artist: Option<String>,
    /// Every artist credit on the file (multi-value tags), `artist` is the joined display form.
    #[serde(default)]
    artists: Vec<String>,
    album: Option<String>,
    album_artist: Option<String>,
    genre: Option<String>,
    composer: Option<String>,
    year: Option<u32>,
    track_no: Option<u32>,
    disc_no: Option<u32>,
    duration_sec: Option<u64>,
    bitrate: Option<u32>,
    sample_rate: Option<u32>,
    bit_depth: Option<u8>,
    lossless: bool,
    lyrics: Option<String>,
    lrc_path: Option<String>,
    cover_path: Option<String>,
    /// Tags could not be read; the caller should use its own reader for this file.
    #[serde(default)]
    unreadable: bool,
    /// Same size/mtime as the caller's `known` entry: nothing was read, keep the stored row.
    #[serde(default)]
    unchanged: bool,
}

#[derive(Serialize)]
struct Stats {
    files: usize,
    cached: usize,
    read: usize,
    unreadable: usize,
    walk_ms: u128,
    total_ms: u128,
}

fn is_hidden_or_junk(name: &str) -> bool {
    name.starts_with('.') || SKIP_DIRS.iter().any(|s| s.eq_ignore_ascii_case(name))
}

fn walk(root: &Path, min_bytes: u64) -> Vec<(PathBuf, u64, i64)> {
    WalkDir::new(root)
        .follow_links(false)
        .skip_hidden(false)
        .process_read_dir(|_, _, _, children| {
            children.retain(|c| match c {
                Ok(e) => !(e.file_type().is_dir() && is_hidden_or_junk(&e.file_name().to_string_lossy())),
                Err(_) => false,
            });
        })
        .into_iter()
        .filter_map(Result::ok)
        .filter(|e| e.file_type().is_file())
        .filter(|e| {
            e.path()
                .extension()
                .and_then(|x| x.to_str())
                .map(|x| AUDIO_EXTS.contains(&x.to_ascii_lowercase().as_str()))
                .unwrap_or(false)
        })
        .filter_map(|e| {
            let md = e.metadata().ok()?;
            if md.len() < min_bytes {
                return None;
            }
            let mtime = md.modified().ok()?.duration_since(UNIX_EPOCH).ok()?.as_millis() as i64;
            Some((e.path(), md.len(), mtime))
        })
        .collect()
}

fn text(tag: &Tag, key: &ItemKey) -> Option<String> {
    tag.get_string(key).map(str::trim).filter(|s| !s.is_empty()).map(str::to_owned)
}

fn read_track(path: &Path, size: u64, mtime_ms: i64, covers_dir: &Path) -> Track {
    let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("").to_ascii_lowercase();
    let dir = path.parent().unwrap_or(Path::new("."));
    let stem = path.file_stem().and_then(|s| s.to_str()).unwrap_or("");
    let mut t = Track {
        path: path.to_string_lossy().into_owned(),
        size,
        mtime_ms,
        lossless: LOSSLESS_EXTS.contains(&ext.as_str()),
        lrc_path: Some(dir.join(format!("{stem}.lrc"))).filter(|p| p.is_file()).map(|p| p.to_string_lossy().into_owned()),
        ..Default::default()
    };
    let tagged = match lofty::read_from_path(path) {
        Ok(f) => f,
        Err(_) => {
            t.unreadable = true;
            t.cover_path = folder_cover(dir, &ext, path);
            return t;
        }
    };
    let props = tagged.properties();
    t.duration_sec = Some(props.duration().as_secs()).filter(|d| *d > 0);
    t.bitrate = props.audio_bitrate().filter(|b| *b > 0);
    t.sample_rate = props.sample_rate().filter(|b| *b > 0);
    t.bit_depth = props.bit_depth().filter(|b| *b > 0);
    if matches!(tagged.file_type(), FileType::Flac | FileType::Wav | FileType::Aiff | FileType::Ape | FileType::WavPack) {
        t.lossless = true;
    }
    if let Some(tag) = tagged.primary_tag().or_else(|| tagged.first_tag()) {
        t.title = text(tag, &ItemKey::TrackTitle);
        t.artists = tag
            .get_strings(&ItemKey::TrackArtist)
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .map(str::to_owned)
            .collect();
        t.artist = if t.artists.is_empty() { None } else { Some(t.artists.join(", ")) };
        t.genre = tag.genre().map(|g| g.trim().to_owned()).filter(|g| !g.is_empty());
        t.composer = text(tag, &ItemKey::Composer);
        t.disc_no = tag.disk();
        t.album = text(tag, &ItemKey::AlbumTitle);
        t.album_artist = text(tag, &ItemKey::AlbumArtist);
        t.lyrics = text(tag, &ItemKey::Lyrics);
        t.year = tag.year().or_else(|| text(tag, &ItemKey::RecordingDate).and_then(|d| d.get(..4)?.parse().ok()));
        t.track_no = tag.track();
        if let Some(pic) = tag.pictures().first() {
            t.cover_path = save_cover(covers_dir, pic.data());
        }
    }
    if t.cover_path.is_none() {
        t.cover_path = folder_cover(dir, &ext, path);
    }
    t
}

/// Same file naming as the Kotlin scanner (SHA-1 of the artwork, first 24 hex, `.jpg`),
/// so covers extracted by either side are shared.
fn save_cover(covers_dir: &Path, data: &[u8]) -> Option<String> {
    if data.is_empty() {
        return None;
    }
    let hash: String = Sha1::digest(data).iter().map(|b| format!("{b:02x}")).collect();
    let out = covers_dir.join(format!("{}.jpg", &hash[..24]));
    let up_to_date = fs::metadata(&out).map(|m| m.len() == data.len() as u64).unwrap_or(false);
    if !up_to_date {
        let tmp = out.with_extension("tmp");
        fs::write(&tmp, data).ok()?;
        fs::rename(&tmp, &out).ok()?;
    }
    Some(out.to_string_lossy().into_owned())
}

fn folder_cover(dir: &Path, ext: &str, file: &Path) -> Option<String> {
    COVER_NAMES
        .iter()
        .map(|n| dir.join(n))
        .find(|p| p.is_file())
        .map(|p| p.to_string_lossy().into_owned())
        .or_else(|| (ext == "opus").then(|| format!("opus-lazy://{}", file.display())))
}

pub fn run(request: &str) -> Result<String, String> {
    let started = Instant::now();
    let req: Request = serde_json::from_str(request).map_err(|e| e.to_string())?;
    let covers_dir = PathBuf::from(&req.covers_dir);
    fs::create_dir_all(&covers_dir).map_err(|e| e.to_string())?;

    let mut files: Vec<(PathBuf, u64, i64)> = req
        .roots
        .par_iter()
        .flat_map(|r| walk(Path::new(r), req.min_bytes))
        .collect();
    files.sort_by(|a, b| a.0.cmp(&b.0));
    files.dedup_by(|a, b| a.0 == b.0);
    let walk_ms = started.elapsed().as_millis();

    let known: HashMap<&str, &Known> = req.known.iter().map(|k| (k.path.as_str(), k)).collect();

    let tracks: Vec<Track> = files
        .par_iter()
        .map(|(path, size, mtime)| {
            let key = path.to_string_lossy();
            match known.get(key.as_ref()) {
                // An unchanged file whose stored cover still exists needs no re-read.
                Some(k) if k.size == *size && k.mtime_ms == *mtime
                    && k.cover_path.as_deref().map(|p| p.starts_with("opus-lazy://") || Path::new(p).exists()).unwrap_or(true) =>
                {
                    Track { path: key.into_owned(), size: *size, mtime_ms: *mtime, unchanged: true, ..Default::default() }
                }
                _ => read_track(path, *size, *mtime, &covers_dir),
            }
        })
        .collect();

    let cached = tracks.iter().filter(|t| t.unchanged).count();
    let unreadable = tracks.iter().filter(|t| t.unreadable).count();

    let stats = Stats {
        files: tracks.len(),
        cached,
        read: tracks.len() - cached,
        unreadable,
        walk_ms,
        total_ms: started.elapsed().as_millis(),
    };
    serde_json::to_string(&serde_json::json!({ "tracks": tracks, "stats": stats })).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn walk_skips_junk_and_small_files() {
        let dir = std::env::temp_dir().join(format!("visage-native-test-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(dir.join("Artist/Album")).unwrap();
        fs::create_dir_all(dir.join("venv/lib")).unwrap();
        fs::create_dir_all(dir.join(".hidden")).unwrap();
        fs::write(dir.join("Artist/Album/a.mp3"), vec![0u8; 9000]).unwrap();
        fs::write(dir.join("Artist/Album/tiny.mp3"), vec![0u8; 10]).unwrap();
        fs::write(dir.join("venv/lib/x.wav"), vec![0u8; 9000]).unwrap();
        fs::write(dir.join(".hidden/y.flac"), vec![0u8; 9000]).unwrap();
        fs::write(dir.join("Artist/Album/readme.txt"), vec![0u8; 9000]).unwrap();
        let found = walk(&dir, 8_192);
        assert_eq!(found.len(), 1, "{found:?}");
        let out = run(&format!(
            r#"{{"roots":["{0}"],"covers_dir":"{0}/covers"}}"#,
            dir.display()
        ))
        .unwrap();
        assert!(out.contains("unreadable")); // garbage mp3 -> caller falls back
        let _ = fs::remove_dir_all(&dir);
    }
}
