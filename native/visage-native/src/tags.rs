//! Tag reading and *safe* writing with lofty (MP3 ID3v2.3/2.4, FLAC Vorbis+PICTURE, Ogg/Opus,
//! MP4/M4A, WAV/AIFF ID3v2, ...).
//!
//! Write safety: the original is never edited in place. We copy it next to itself, edit and
//! re-read the copy (verifying what we meant to write is really there), then atomically `rename`
//! it over the original. A read-only file is reported, not touched. An optional backup of the
//! untouched original is copied first (used for Undo).

use lofty::config::WriteOptions;
use lofty::file::{AudioFile, TaggedFile, TaggedFileExt};
use lofty::picture::{MimeType, Picture, PictureType};
use lofty::prelude::*;
use lofty::tag::{ItemKey, Tag, TagType};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::UNIX_EPOCH;

#[derive(Deserialize)]
pub struct ReadRequest {
    pub path: String,
    /// When set, the embedded front cover is extracted here (SHA-1 naming shared with the scanner).
    #[serde(default)]
    pub covers_dir: Option<String>,
}

#[derive(Serialize, Default, Debug)]
pub struct TagInfo {
    pub path: String,
    pub format: String,
    pub readonly: bool,
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    #[serde(rename = "albumArtist")]
    pub album_artist: Option<String>,
    pub year: Option<u32>,
    pub genre: Option<String>,
    #[serde(rename = "trackNo")]
    pub track_no: Option<u32>,
    #[serde(rename = "trackTotal")]
    pub track_total: Option<u32>,
    #[serde(rename = "discNo")]
    pub disc_no: Option<u32>,
    #[serde(rename = "discTotal")]
    pub disc_total: Option<u32>,
    pub composer: Option<String>,
    pub comment: Option<String>,
    pub lyrics: Option<String>,
    #[serde(rename = "hasCover")]
    pub has_cover: bool,
    #[serde(rename = "coverPath")]
    pub cover_path: Option<String>,
    #[serde(rename = "durationSec")]
    pub duration_sec: Option<u64>,
    /// Whether this container can carry embedded pictures / lyrics through us.
    #[serde(rename = "supportsCover")]
    pub supports_cover: bool,
}

#[derive(Deserialize)]
pub struct WriteRequest {
    pub path: String,
    /// Field name -> new value; an empty string clears the field. Absent = untouched.
    /// Names: title artist album albumArtist year genre trackNo trackTotal discNo discTotal composer comment lyrics.
    #[serde(default)]
    pub fields: HashMap<String, String>,
    /// Image file to embed as the front cover (replaces existing pictures).
    #[serde(default)]
    pub cover_path: Option<String>,
    #[serde(default)]
    pub remove_cover: bool,
    /// Write ID3v2.3 instead of 2.4 (MP3/WAV/AIFF only).
    #[serde(default)]
    pub id3v23: bool,
    /// Copy the untouched original here before modifying it.
    #[serde(default)]
    pub backup_path: Option<String>,
}

#[derive(Serialize, Debug)]
pub struct WriteResult {
    pub ok: bool,
    pub error: Option<String>,
    pub size: u64,
    #[serde(rename = "mtimeMs")]
    pub mtime_ms: i64,
}

fn fail(msg: impl Into<String>) -> WriteResult {
    WriteResult { ok: false, error: Some(msg.into()), size: 0, mtime_ms: 0 }
}

fn clean(s: Option<&str>) -> Option<String> {
    s.map(str::trim).filter(|s| !s.is_empty()).map(str::to_owned)
}

fn item(tag: &Tag, key: &ItemKey) -> Option<String> {
    clean(tag.get_string(key))
}

fn is_readonly(path: &Path) -> bool {
    fs::metadata(path).map(|m| m.permissions().readonly()).unwrap_or(true)
}

/// Tag type we edit: the file's primary tag, except WAV/AIFF where ID3v2 is used so pictures work.
fn target_type(f: &TaggedFile) -> TagType {
    use lofty::file::FileType::*;
    match f.file_type() {
        Wav | Aiff => TagType::Id3v2,
        _ => f.primary_tag_type(),
    }
}

fn supports_pictures(t: TagType) -> bool {
    !matches!(t, TagType::RiffInfo | TagType::AiffText)
}

fn mime_of(data: &[u8]) -> Option<MimeType> {
    if data.starts_with(&[0xFF, 0xD8, 0xFF]) {
        Some(MimeType::Jpeg)
    } else if data.starts_with(&[0x89, b'P', b'N', b'G']) {
        Some(MimeType::Png)
    } else if data.starts_with(b"GIF8") {
        Some(MimeType::Gif)
    } else if data.starts_with(b"BM") {
        Some(MimeType::Bmp)
    } else if data.len() > 12 && &data[0..4] == b"RIFF" && &data[8..12] == b"WEBP" {
        None
    } else {
        None
    }
}

pub fn read(req: &ReadRequest) -> Result<TagInfo, String> {
    let path = Path::new(&req.path);
    let f = lofty::read_from_path(path).map_err(|e| e.to_string())?;
    let mut info = TagInfo {
        path: req.path.clone(),
        format: format!("{:?}", f.file_type()),
        readonly: is_readonly(path),
        duration_sec: Some(f.properties().duration().as_secs()).filter(|d| *d > 0),
        supports_cover: supports_pictures(target_type(&f)),
        ..Default::default()
    };
    if let Some(tag) = f.primary_tag().or_else(|| f.first_tag()) {
        info.title = clean(tag.title().as_deref());
        info.artist = clean(tag.artist().as_deref());
        info.album = clean(tag.album().as_deref());
        info.album_artist = item(tag, &ItemKey::AlbumArtist);
        info.genre = clean(tag.genre().as_deref());
        info.composer = item(tag, &ItemKey::Composer);
        info.comment = clean(tag.comment().as_deref());
        info.lyrics = item(tag, &ItemKey::Lyrics);
        info.year = tag.year().or_else(|| {
            [ItemKey::Year, ItemKey::RecordingDate, ItemKey::OriginalReleaseDate]
                .iter()
                .find_map(|k| item(tag, k))
                .and_then(|d| d.get(..4)?.parse().ok())
        });
        info.track_no = tag.track();
        info.track_total = tag.track_total();
        info.disc_no = tag.disk();
        info.disc_total = tag.disk_total();
        let pic = tag
            .pictures()
            .iter()
            .find(|p| p.pic_type() == PictureType::CoverFront)
            .or_else(|| tag.pictures().first());
        if let Some(p) = pic {
            info.has_cover = true;
            if let Some(dir) = &req.covers_dir {
                let dir = PathBuf::from(dir);
                let _ = fs::create_dir_all(&dir);
                info.cover_path = crate::scan::save_cover(&dir, p.data());
            }
        }
    }
    Ok(info)
}

fn parse_num(s: &str) -> Option<u32> {
    // "3/12" style input keeps the leading number.
    s.split('/').next()?.trim().parse().ok()
}

fn apply_fields(tag: &mut Tag, fields: &HashMap<String, String>) {
    for (k, raw) in fields {
        let v = raw.trim();
        let empty = v.is_empty();
        match k.as_str() {
            "title" => if empty { tag.remove_title() } else { tag.set_title(v.to_owned()) },
            "artist" => if empty { tag.remove_artist() } else { tag.set_artist(v.to_owned()) },
            "album" => if empty { tag.remove_album() } else { tag.set_album(v.to_owned()) },
            "genre" => if empty { tag.remove_genre() } else { tag.set_genre(v.to_owned()) },
            "comment" => if empty { tag.remove_comment() } else { tag.set_comment(v.to_owned()) },
            "albumArtist" => text(tag, ItemKey::AlbumArtist, v),
            "composer" => text(tag, ItemKey::Composer, v),
            "lyrics" => text(tag, ItemKey::Lyrics, raw.trim_end()),
            "year" => match parse_num(v) {
                Some(n) if !empty => {
                    // Year alone is dropped by some containers; the recording date is universal.
                    tag.insert_text(ItemKey::Year, n.to_string());
                    tag.insert_text(ItemKey::RecordingDate, n.to_string());
                }
                _ => {
                    tag.remove_key(&ItemKey::Year);
                    tag.remove_key(&ItemKey::RecordingDate);
                }
            },
            "trackNo" => match parse_num(v) {
                Some(n) if !empty => tag.set_track(n),
                _ => tag.remove_track(),
            },
            "trackTotal" => match parse_num(v) {
                Some(n) if !empty => tag.set_track_total(n),
                _ => tag.remove_track_total(),
            },
            "discNo" => match parse_num(v) {
                Some(n) if !empty => tag.set_disk(n),
                _ => tag.remove_disk(),
            },
            "discTotal" => match parse_num(v) {
                Some(n) if !empty => tag.set_disk_total(n),
                _ => tag.remove_disk_total(),
            },
            _ => {}
        }
    }
}

fn text(tag: &mut Tag, key: ItemKey, v: &str) {
    if v.trim().is_empty() {
        tag.remove_key(&key);
    } else {
        tag.insert_text(key, v.to_owned());
    }
}

fn tmp_sibling(path: &Path) -> PathBuf {
    let name = path.file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_default();
    // Keep the real extension last so format probing still works on the temp copy.
    let ext = path.extension().map(|e| e.to_string_lossy().into_owned()).unwrap_or_default();
    let stem = name.strip_suffix(&format!(".{ext}")).unwrap_or(&name).to_string();
    path.with_file_name(format!(".{stem}.visage-tmp.{ext}"))
}

pub fn write(req: &WriteRequest) -> WriteResult {
    // Resolve symlinks so the link itself is not replaced by a regular file.
    let path = match fs::canonicalize(&req.path) {
        Ok(p) => p,
        Err(e) => return fail(format!("cannot open file: {e}")),
    };
    if !path.is_file() {
        return fail("not a file");
    }
    if is_readonly(&path) {
        return fail("file is read-only");
    }
    let cover_bytes = match &req.cover_path {
        Some(p) => match fs::read(p) {
            Ok(b) if mime_of(&b).is_some() => Some(b),
            Ok(_) => return fail("cover must be a JPEG, PNG, GIF or BMP image"),
            Err(e) => return fail(format!("cannot read cover: {e}")),
        },
        None => None,
    };
    if let Some(b) = &req.backup_path {
        let b = Path::new(b);
        if let Some(parent) = b.parent() {
            if let Err(e) = fs::create_dir_all(parent) {
                return fail(format!("cannot create backup folder: {e}"));
            }
        }
        if let Err(e) = fs::copy(&path, b) {
            return fail(format!("backup failed: {e}"));
        }
    }
    let tmp = tmp_sibling(&path);
    let outcome = write_via_temp(&path, &tmp, req, cover_bytes);
    if outcome.is_err() {
        let _ = fs::remove_file(&tmp);
    }
    match outcome {
        Err(e) => fail(e),
        Ok(()) => match fs::metadata(&path) {
            Ok(md) => WriteResult {
                ok: true,
                error: None,
                size: md.len(),
                mtime_ms: md
                    .modified()
                    .ok()
                    .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                    .map(|d| d.as_millis() as i64)
                    .unwrap_or(0),
            },
            Err(e) => fail(e.to_string()),
        },
    }
}

fn write_via_temp(path: &Path, tmp: &Path, req: &WriteRequest, cover: Option<Vec<u8>>) -> Result<(), String> {
    fs::copy(path, tmp).map_err(|e| format!("cannot create temporary copy (folder not writable?): {e}"))?;
    let mut f = lofty::read_from_path(tmp).map_err(|e| format!("unsupported or unreadable file: {e}"))?;
    let ty = target_type(&f);
    if f.tag(ty).is_none() {
        f.insert_tag(Tag::new(ty));
    }
    {
        let tag = f.tag_mut(ty).ok_or("no tag available for this format")?;
        apply_fields(tag, &req.fields);
        if req.remove_cover || cover.is_some() {
            while tag.picture_count() > 0 {
                tag.remove_picture(0);
            }
        }
        if let Some(data) = cover {
            if !supports_pictures(ty) {
                return Err("this format cannot hold cover art".into());
            }
            let mime = mime_of(&data);
            tag.push_picture(Picture::new_unchecked(PictureType::CoverFront, mime, Some("Cover".to_string()), data));
        }
    }
    let opts = WriteOptions::default().use_id3v23(req.id3v23);
    let v23_year = if req.id3v23 && ty == TagType::Id3v2 { req.fields.get("year").and_then(|y| parse_num(y.trim())) } else { None };
    if let Some(year) = v23_year {
        // lofty's v2.3 writer drops a plain-text TDRC, so give it an explicit TYER frame.
        use lofty::id3::v2::{Frame, FrameId, Id3v2Tag, TextInformationFrame};
        use lofty::TextEncoding;
        use lofty::tag::TagExt;
        let tag = f.tag(ty).cloned().ok_or("no tag available")?;
        let mut id3: Id3v2Tag = tag.into();
        id3.remove_year();
        id3.insert(Frame::Text(TextInformationFrame::new(FrameId::Valid("TYER".into()), TextEncoding::Latin1, year.to_string())));
        id3.save_to_path(tmp, opts).map_err(|e| format!("tag write failed: {e}"))?;
    } else {
        f.save_to_path(tmp, opts).map_err(|e| format!("tag write failed: {e}"))?;
    }

    // Verify the temp copy before it replaces anything.
    let v = lofty::read_from_path(tmp).map_err(|e| format!("verification failed: {e}"))?;
    let vt = v.tag(ty).ok_or("verification failed: tag missing")?;
    for (k, want) in &req.fields {
        let want = want.trim();
        let got = match k.as_str() {
            "title" => clean(vt.title().as_deref()),
            "artist" => clean(vt.artist().as_deref()),
            "album" => clean(vt.album().as_deref()),
            _ => continue,
        };
        if got.as_deref().unwrap_or("") != want {
            return Err(format!("verification failed for {k}"));
        }
    }
    if let Ok(perm) = fs::metadata(path).map(|m| m.permissions()) {
        let _ = fs::set_permissions(tmp, perm);
    }
    fs::rename(tmp, path).map_err(|e| format!("could not replace original: {e}"))
}

/// Atomically put `backup` back over `dest` (Undo).
pub fn restore(backup: &str, dest: &str) -> Result<(), String> {
    let dest = fs::canonicalize(dest).unwrap_or_else(|_| PathBuf::from(dest));
    let tmp = tmp_sibling(&dest);
    fs::copy(backup, &tmp).map_err(|e| e.to_string())?;
    fs::rename(&tmp, &dest).map_err(|e| {
        let _ = fs::remove_file(&tmp);
        e.to_string()
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    pub fn tiny_wav(path: &Path) {
        let data = vec![0u8; 16_000];
        let mut b = Vec::new();
        b.extend(b"RIFF");
        b.extend(((36 + data.len()) as u32).to_le_bytes());
        b.extend(b"WAVEfmt ");
        b.extend(16u32.to_le_bytes());
        b.extend(1u16.to_le_bytes());
        b.extend(1u16.to_le_bytes());
        b.extend(8000u32.to_le_bytes());
        b.extend(16000u32.to_le_bytes());
        b.extend(2u16.to_le_bytes());
        b.extend(16u16.to_le_bytes());
        b.extend(b"data");
        b.extend((data.len() as u32).to_le_bytes());
        b.extend(data);
        fs::write(path, b).unwrap();
    }

    fn tiny_mp3(path: &Path) {
        // 8 silent MPEG1 Layer III 128 kbps 44.1 kHz frames (417 bytes each).
        let mut b = Vec::new();
        for _ in 0..8 {
            let mut frame = vec![0u8; 417];
            frame[..4].copy_from_slice(&[0xFF, 0xFB, 0x90, 0x00]);
            b.extend(frame);
        }
        fs::write(path, b).unwrap();
    }

    fn png() -> Vec<u8> {
        let mut v = vec![0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
        v.extend([0u8; 40]);
        v
    }

    fn dir(name: &str) -> PathBuf {
        let d = std::env::temp_dir().join(format!("visage-tags-{name}-{}", std::process::id()));
        let _ = fs::remove_dir_all(&d);
        fs::create_dir_all(&d).unwrap();
        d
    }

    fn fields(pairs: &[(&str, &str)]) -> HashMap<String, String> {
        pairs.iter().map(|(a, b)| (a.to_string(), b.to_string())).collect()
    }

    fn roundtrip(file: &Path, d: &Path) {
        let backup = d.join("bk/orig.bin");
        let cover = d.join("c.png");
        fs::write(&cover, png()).unwrap();
        let original = fs::read(file).unwrap();
        let r = write(&WriteRequest {
            path: file.to_string_lossy().into(),
            fields: fields(&[("title", "Song"), ("artist", "Band"), ("album", "LP"), ("year", "1999"), ("trackNo", "3/10"), ("albumArtist", "Various"), ("lyrics", "la la")]),
            cover_path: Some(cover.to_string_lossy().into()),
            remove_cover: false,
            id3v23: true,
            backup_path: Some(backup.to_string_lossy().into()),
        });
        assert!(r.ok, "{:?}", r.error);
        assert_eq!(fs::read(&backup).unwrap(), original);
        let info = read(&ReadRequest { path: file.to_string_lossy().into(), covers_dir: Some(d.join("covers").to_string_lossy().into()) }).unwrap();
        assert_eq!(info.title.as_deref(), Some("Song"));
        assert_eq!(info.artist.as_deref(), Some("Band"));
        assert_eq!(info.album_artist.as_deref(), Some("Various"));
        assert_eq!(info.year, Some(1999));
        assert_eq!(info.track_no, Some(3));
        assert!(info.has_cover && info.cover_path.is_some());
        // clear + remove cover
        let r = write(&WriteRequest {
            path: file.to_string_lossy().into(),
            fields: fields(&[("album", ""), ("title", "Song 2")]),
            cover_path: None,
            remove_cover: true,
            id3v23: false,
            backup_path: None,
        });
        assert!(r.ok, "{:?}", r.error);
        let info = read(&ReadRequest { path: file.to_string_lossy().into(), covers_dir: None }).unwrap();
        assert_eq!(info.title.as_deref(), Some("Song 2"));
        assert_eq!(info.album, None);
        assert!(!info.has_cover);
        // restore (undo)
        restore(&backup.to_string_lossy(), &file.to_string_lossy()).unwrap();
        assert_eq!(fs::read(file).unwrap(), original);
        assert!(!tmp_sibling(file).exists());
    }

    #[test]
    fn wav_roundtrip_backup_undo() {
        let d = dir("wav");
        let f = d.join("a.wav");
        tiny_wav(&f);
        roundtrip(&f, &d);
        let _ = fs::remove_dir_all(&d);
    }

    #[test]
    fn mp3_roundtrip_backup_undo() {
        let d = dir("mp3");
        let f = d.join("a.mp3");
        tiny_mp3(&f);
        roundtrip(&f, &d);
        let _ = fs::remove_dir_all(&d);
    }

    #[test]
    fn readonly_and_bad_cover_are_reported_not_written() {
        let d = dir("ro");
        let f = d.join("a.wav");
        tiny_wav(&f);
        let mut p = fs::metadata(&f).unwrap().permissions();
        p.set_readonly(true);
        fs::set_permissions(&f, p.clone()).unwrap();
        let r = write(&WriteRequest { path: f.to_string_lossy().into(), fields: fields(&[("title", "x")]), cover_path: None, remove_cover: false, id3v23: false, backup_path: None });
        assert!(!r.ok && r.error.unwrap().contains("read-only"));
        #[allow(clippy::permissions_set_readonly_false)]
        p.set_readonly(false);
        fs::set_permissions(&f, p).unwrap();
        let bad = d.join("bad.png");
        fs::write(&bad, b"not an image").unwrap();
        let r = write(&WriteRequest { path: f.to_string_lossy().into(), fields: HashMap::new(), cover_path: Some(bad.to_string_lossy().into()), remove_cover: false, id3v23: false, backup_path: None });
        assert!(!r.ok);
        let _ = fs::remove_dir_all(&d);
    }
}
