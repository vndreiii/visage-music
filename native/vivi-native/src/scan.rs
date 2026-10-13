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
const CACHE_VERSION: u32 = 1;

#[derive(Deserialize)]
struct Request {
    roots: Vec<String>,
    covers_dir: String,
    #[serde(default)]
    cache_path: Option<String>,
    #[serde(default = "default_min_bytes")]
    min_bytes: u64,
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
    album: Option<String>,
    album_artist: Option<String>,
    year: Option<u32>,
    track_no: Option<u32>,
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
}

#[derive(Serialize, Deserialize, Default)]
struct Cache {
    version: u32,
    covers_dir: String,
    entries: HashMap<String, Track>,
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
        t.artist = text(tag, &ItemKey::TrackArtist);
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

fn load_cache(path: &Path, covers_dir: &str) -> Cache {
    fs::read(path)
        .ok()
        .and_then(|b| serde_json::from_slice::<Cache>(&b).ok())
        .filter(|c| c.version == CACHE_VERSION && c.covers_dir == covers_dir)
        .unwrap_or_else(|| Cache { version: CACHE_VERSION, covers_dir: covers_dir.to_owned(), ..Default::default() })
}

fn save_cache(path: &Path, cache: &Cache) {
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    let tmp = path.with_extension("tmp");
    if let Ok(bytes) = serde_json::to_vec(cache) {
        if fs::write(&tmp, bytes).is_ok() {
            let _ = fs::rename(&tmp, path);
        }
    }
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

    let cache_path = req.cache_path.as_deref().map(PathBuf::from);
    let cache = cache_path.as_deref().map(|p| load_cache(p, &req.covers_dir)).unwrap_or_default();

    let tracks: Vec<(Track, bool)> = files
        .par_iter()
        .map(|(path, size, mtime)| {
            let key = path.to_string_lossy();
            match cache.entries.get(key.as_ref()) {
                // A cached cover must still exist, otherwise re-read to regenerate it.
                Some(c) if c.size == *size && c.mtime_ms == *mtime && !c.unreadable
                    && c.cover_path.as_deref().map(|p| p.starts_with("opus-lazy://") || Path::new(p).exists()).unwrap_or(true) =>
                {
                    (c.clone(), true)
                }
                _ => (read_track(path, *size, *mtime, &covers_dir), false),
            }
        })
        .collect();

    let cached = tracks.iter().filter(|(_, hit)| *hit).count();
    let unreadable = tracks.iter().filter(|(t, _)| t.unreadable).count();
    let tracks: Vec<Track> = tracks.into_iter().map(|(t, _)| t).collect();

    if let Some(p) = &cache_path {
        let fresh = Cache {
            version: CACHE_VERSION,
            covers_dir: req.covers_dir.clone(),
            entries: tracks.iter().map(|t| (t.path.clone(), t.clone())).collect(),
        };
        save_cache(p, &fresh);
    }

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
        let dir = std::env::temp_dir().join(format!("vivi-native-test-{}", std::process::id()));
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
            r#"{{"roots":["{0}"],"covers_dir":"{0}/covers","cache_path":"{0}/cache.json"}}"#,
            dir.display()
        ))
        .unwrap();
        assert!(out.contains("unreadable")); // garbage mp3 -> caller falls back
        let _ = fs::remove_dir_all(&dir);
    }
}
