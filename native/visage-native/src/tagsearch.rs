//! Metadata candidates for the Tag Manager from public, key-less services: MusicBrainz (1 req/s,
//! descriptive User-Agent), Deezer, iTunes Search, TheAudioDB (public test key) and, only when the
//! caller supplies an AcoustID key + a chromaprint fingerprint, AcoustID. Cover Art Archive URLs
//! are derived from MusicBrainz release ids. Sources run concurrently; any one failing only adds an
//! entry to `errors`. Scoring/ranking happens on the Kotlin side (unit-tested there).

use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};

const UA: &str = concat!(
    "VIsage/",
    env!("CARGO_PKG_VERSION"),
    " (desktop music player; https://github.com/vndreiii/visage-music)"
);

#[derive(Deserialize, Default)]
pub struct Request {
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub artist: String,
    #[serde(default)]
    pub album: String,
    /// Source ids to skip, e.g. ["itunes"].
    #[serde(default)]
    pub skip: Vec<String>,
    #[serde(default)]
    pub duration_sec: Option<u64>,
    #[serde(default)]
    pub fingerprint: Option<String>,
    #[serde(default)]
    pub acoustid_key: Option<String>,
}

#[derive(Serialize, Deserialize, Default, Clone, Debug, PartialEq)]
pub struct Candidate {
    pub source: String,
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
    #[serde(rename = "coverUrl")]
    pub cover_url: Option<String>,
    #[serde(rename = "durationSec")]
    pub duration_sec: Option<u64>,
    /// Source-provided match strength 0..1 when there is one (MusicBrainz / AcoustID).
    #[serde(rename = "sourceScore")]
    pub source_score: Option<f32>,
}

#[derive(Serialize, Default)]
pub struct Response {
    pub candidates: Vec<Candidate>,
    pub errors: Vec<String>,
}

fn agent() -> &'static ureq::Agent {
    static A: OnceLock<ureq::Agent> = OnceLock::new();
    A.get_or_init(|| {
        ureq::AgentBuilder::new()
            .user_agent(UA)
            .timeout_connect(Duration::from_secs(5))
            .timeout(Duration::from_secs(10))
            .build()
    })
}

fn polite(host: &str, gap: Duration) {
    static LAST: OnceLock<Mutex<HashMap<String, Instant>>> = OnceLock::new();
    let wait = {
        let mut m = LAST.get_or_init(Default::default).lock().unwrap_or_else(|e| e.into_inner());
        let now = Instant::now();
        let at = m.get(host).map(|t| (*t + gap).max(now)).unwrap_or(now);
        m.insert(host.to_string(), at);
        at.saturating_duration_since(now)
    };
    if !wait.is_zero() {
        std::thread::sleep(wait);
    }
}

fn get(host: &str, gap_ms: u64, url: &str, q: &[(&str, &str)]) -> Result<Value, String> {
    polite(host, Duration::from_millis(gap_ms));
    let mut r = agent().get(url);
    for (k, v) in q {
        r = r.query(k, v);
    }
    let body = r.call().map_err(|e| format!("{host}: {e}"))?.into_string().map_err(|e| e.to_string())?;
    serde_json::from_str(&body).map_err(|e| format!("{host}: {e}"))
}

fn s(v: &Value, k: &str) -> Option<String> {
    v.get(k).and_then(Value::as_str).map(str::trim).filter(|x| !x.is_empty()).map(str::to_owned)
}

fn year_of(d: &str) -> Option<u32> {
    d.get(..4)?.parse().ok().filter(|y| (1000..3000).contains(y))
}

fn query_string(req: &Request) -> String {
    format!("{} {}", req.artist, req.title).trim().to_string()
}

// ── parsers (pure, unit-tested) ───────────────────────────────────────────────────────────

pub fn parse_musicbrainz(v: &Value) -> Vec<Candidate> {
    let mut out = Vec::new();
    for rec in v.get("recordings").and_then(Value::as_array).into_iter().flatten() {
        let artist = rec.get("artist-credit").and_then(Value::as_array).map(|a| {
            a.iter()
                .map(|c| format!("{}{}", s(c, "name").unwrap_or_default(), c.get("joinphrase").and_then(Value::as_str).unwrap_or("")))
                .collect::<String>()
        });
        let score = rec.get("score").and_then(Value::as_f64).map(|x| (x / 100.0) as f32);
        let dur = rec.get("length").and_then(Value::as_u64).map(|ms| ms / 1000);
        let releases = rec.get("releases").and_then(Value::as_array).cloned().unwrap_or_default();
        if releases.is_empty() {
            out.push(Candidate { source: "musicbrainz".into(), title: s(rec, "title"), artist, duration_sec: dur, source_score: score, ..Default::default() });
            continue;
        }
        for rel in releases.iter().take(3) {
            let media = rel.get("media").and_then(Value::as_array).and_then(|m| m.first());
            let track = media.and_then(|m| m.get("track")).and_then(Value::as_array).and_then(|t| t.first());
            out.push(Candidate {
                source: "musicbrainz".into(),
                title: s(rec, "title"),
                artist: artist.clone(),
                album: s(rel, "title"),
                album_artist: rel
                    .get("artist-credit")
                    .and_then(Value::as_array)
                    .map(|a| a.iter().filter_map(|c| s(c, "name")).collect::<Vec<_>>().join(", "))
                    .filter(|x| !x.is_empty()),
                year: s(rel, "date").and_then(|d| year_of(&d)),
                track_no: track.and_then(|t| s(t, "number")).and_then(|n| n.parse().ok()),
                track_total: media.and_then(|m| m.get("track-count")).and_then(Value::as_u64).map(|n| n as u32),
                disc_no: media.and_then(|m| m.get("position")).and_then(Value::as_u64).map(|n| n as u32),
                cover_url: s(rel, "id").map(|id| format!("https://coverartarchive.org/release/{id}/front-500")),
                duration_sec: dur,
                source_score: score,
                ..Default::default()
            });
        }
    }
    out
}

pub fn parse_deezer(v: &Value) -> Vec<Candidate> {
    v.get("data")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .map(|t| {
            let album = t.get("album");
            Candidate {
                source: "deezer".into(),
                title: s(t, "title"),
                artist: t.get("artist").and_then(|a| s(a, "name")),
                album: album.and_then(|a| s(a, "title")),
                cover_url: album.and_then(|a| s(a, "cover_xl").or_else(|| s(a, "cover_big"))),
                duration_sec: t.get("duration").and_then(Value::as_u64),
                ..Default::default()
            }
        })
        .collect()
}

pub fn parse_itunes(v: &Value) -> Vec<Candidate> {
    v.get("results")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter(|r| r.get("wrapperType").and_then(Value::as_str).map_or(true, |w| w == "track"))
        .map(|r| Candidate {
            source: "itunes".into(),
            title: s(r, "trackName"),
            artist: s(r, "artistName"),
            album: s(r, "collectionName"),
            album_artist: None,
            year: s(r, "releaseDate").and_then(|d| year_of(&d)),
            genre: s(r, "primaryGenreName"),
            track_no: r.get("trackNumber").and_then(Value::as_u64).map(|n| n as u32),
            track_total: r.get("trackCount").and_then(Value::as_u64).map(|n| n as u32),
            disc_no: r.get("discNumber").and_then(Value::as_u64).map(|n| n as u32),
            cover_url: s(r, "artworkUrl100").map(|u| u.replace("100x100bb", "1000x1000bb")),
            duration_sec: r.get("trackTimeMillis").and_then(Value::as_u64).map(|ms| ms / 1000),
            source_score: None,
        })
        .collect()
}

pub fn parse_audiodb(v: &Value) -> Vec<Candidate> {
    v.get("track")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .map(|t| Candidate {
            source: "theaudiodb".into(),
            title: s(t, "strTrack"),
            artist: s(t, "strArtist"),
            album: s(t, "strAlbum"),
            genre: s(t, "strGenre"),
            track_no: s(t, "intTrackNumber").and_then(|n| n.parse().ok()),
            cover_url: s(t, "strTrackThumb"),
            duration_sec: s(t, "intDuration").and_then(|n| n.parse::<u64>().ok()).map(|ms| ms / 1000),
            ..Default::default()
        })
        .collect()
}

pub fn parse_acoustid(v: &Value) -> Vec<Candidate> {
    let mut out = Vec::new();
    for res in v.get("results").and_then(Value::as_array).into_iter().flatten() {
        let score = res.get("score").and_then(Value::as_f64).map(|x| x as f32);
        for rec in res.get("recordings").and_then(Value::as_array).into_iter().flatten() {
            let artist = rec.get("artists").and_then(Value::as_array).map(|a| {
                a.iter().map(|c| format!("{}{}", s(c, "name").unwrap_or_default(), c.get("joinphrase").and_then(Value::as_str).unwrap_or(""))).collect::<String>()
            });
            let groups = rec.get("releasegroups").and_then(Value::as_array).cloned().unwrap_or_default();
            if groups.is_empty() {
                out.push(Candidate { source: "acoustid".into(), title: s(rec, "title"), artist: artist.clone(), source_score: score, ..Default::default() });
            }
            for g in groups.iter().take(2) {
                out.push(Candidate {
                    source: "acoustid".into(),
                    title: s(rec, "title"),
                    artist: artist.clone(),
                    album: s(g, "title"),
                    cover_url: g
                        .get("releases")
                        .and_then(Value::as_array)
                        .and_then(|r| r.first())
                        .and_then(|r| s(r, "id"))
                        .map(|id| format!("https://coverartarchive.org/release/{id}/front-500")),
                    source_score: score,
                    ..Default::default()
                });
            }
        }
    }
    out
}

// ── fetchers ──────────────────────────────────────────────────────────────────────────────

fn musicbrainz(req: &Request) -> Result<Vec<Candidate>, String> {
    let mut q = format!("recording:\"{}\"", req.title.replace('"', ""));
    if !req.artist.trim().is_empty() {
        q.push_str(&format!(" AND artist:\"{}\"", req.artist.replace('"', "")));
    }
    if !req.album.trim().is_empty() {
        q.push_str(&format!(" AND release:\"{}\"", req.album.replace('"', "")));
    }
    // MusicBrainz asks for at most one request per second.
    let v = get("musicbrainz.org", 1100, "https://musicbrainz.org/ws/2/recording", &[("query", &q), ("fmt", "json"), ("limit", "6")])?;
    Ok(parse_musicbrainz(&v))
}

fn deezer(req: &Request) -> Result<Vec<Candidate>, String> {
    let q = if req.artist.trim().is_empty() {
        req.title.clone()
    } else {
        format!("artist:\"{}\" track:\"{}\"", req.artist.replace('"', ""), req.title.replace('"', ""))
    };
    let v = get("api.deezer.com", 200, "https://api.deezer.com/search", &[("q", &q), ("limit", "6")])?;
    Ok(parse_deezer(&v))
}

fn itunes(req: &Request) -> Result<Vec<Candidate>, String> {
    let q = query_string(req);
    let v = get("itunes.apple.com", 300, "https://itunes.apple.com/search", &[("term", &q), ("entity", "song"), ("limit", "6")])?;
    Ok(parse_itunes(&v))
}

fn audiodb(req: &Request) -> Result<Vec<Candidate>, String> {
    if req.artist.trim().is_empty() {
        return Ok(vec![]);
    }
    let v = get("theaudiodb.com", 300, "https://www.theaudiodb.com/api/v1/json/2/searchtrack.php", &[("s", &req.artist), ("t", &req.title)])?;
    Ok(parse_audiodb(&v))
}

fn acoustid(req: &Request) -> Result<Vec<Candidate>, String> {
    let (Some(fp), Some(key), Some(dur)) = (&req.fingerprint, &req.acoustid_key, req.duration_sec) else {
        return Ok(vec![]);
    };
    let d = dur.to_string();
    let v = get(
        "api.acoustid.org",
        400,
        "https://api.acoustid.org/v2/lookup",
        &[("client", key), ("meta", "recordings releasegroups releases"), ("duration", &d), ("fingerprint", fp)],
    )?;
    Ok(parse_acoustid(&v))
}

pub fn search(req: &Request) -> Response {
    type Fetch = fn(&Request) -> Result<Vec<Candidate>, String>;
    let sources: [(&str, Fetch); 5] =
        [("musicbrainz", musicbrainz), ("deezer", deezer), ("itunes", itunes), ("theaudiodb", audiodb), ("acoustid", acoustid)];
    let mut resp = Response::default();
    if req.title.trim().is_empty() && req.fingerprint.is_none() {
        resp.errors.push("nothing to search for".into());
        return resp;
    }
    let results: Vec<(&str, Result<Vec<Candidate>, String>)> = std::thread::scope(|sc| {
        let hs: Vec<_> = sources
            .iter()
            .filter(|(id, _)| !req.skip.iter().any(|k| k == id))
            .map(|(id, f)| (*id, sc.spawn(move || f(req))))
            .collect();
        hs.into_iter().map(|(id, h)| (id, h.join().unwrap_or_else(|_| Err("panicked".into())))).collect()
    });
    for (id, r) in results {
        match r {
            Ok(mut c) => resp.candidates.append(&mut c),
            Err(e) => resp.errors.push(format!("{id}: {e}")),
        }
    }
    resp
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn musicbrainz_parse() {
        let v = json!({"recordings":[{"title":"Song","score":97,"length":201000,
            "artist-credit":[{"name":"A","joinphrase":" & "},{"name":"B"}],
            "releases":[{"id":"rid","title":"LP","date":"2001-05-01","media":[{"position":1,"track-count":12,"track":[{"number":"4"}]}]}]}]});
        let c = parse_musicbrainz(&v);
        assert_eq!(c.len(), 1);
        assert_eq!(c[0].artist.as_deref(), Some("A & B"));
        assert_eq!(c[0].year, Some(2001));
        assert_eq!(c[0].track_no, Some(4));
        assert_eq!(c[0].track_total, Some(12));
        assert_eq!(c[0].duration_sec, Some(201));
        assert!(c[0].cover_url.as_deref().unwrap().contains("release/rid"));
        assert!((c[0].source_score.unwrap() - 0.97).abs() < 1e-4);
    }

    #[test]
    fn itunes_deezer_audiodb_parse() {
        let it = parse_itunes(&json!({"results":[{"wrapperType":"track","trackName":"T","artistName":"A","collectionName":"C","releaseDate":"2010-01-01T08:00:00Z","primaryGenreName":"Pop","trackNumber":2,"trackCount":9,"artworkUrl100":"https://x/100x100bb.jpg"}]}));
        assert_eq!(it[0].year, Some(2010));
        assert!(it[0].cover_url.as_deref().unwrap().contains("1000x1000bb"));
        let dz = parse_deezer(&json!({"data":[{"title":"T","duration":180,"artist":{"name":"A"},"album":{"title":"C","cover_xl":"u"}}]}));
        assert_eq!(dz[0].album.as_deref(), Some("C"));
        assert_eq!(dz[0].duration_sec, Some(180));
        let db = parse_audiodb(&json!({"track":[{"strTrack":"T","strArtist":"A","strAlbum":"C","strGenre":"Rock","intTrackNumber":"3"}]}));
        assert_eq!(db[0].track_no, Some(3));
        assert!(parse_audiodb(&json!({"track":null})).is_empty());
    }

    #[test]
    fn acoustid_parse() {
        let c = parse_acoustid(&json!({"results":[{"score":0.93,"recordings":[{"title":"T","artists":[{"name":"A"}],"releasegroups":[{"title":"G","releases":[{"id":"r1"}]}]}]}]}));
        assert_eq!(c[0].album.as_deref(), Some("G"));
        assert_eq!(c[0].source_score, Some(0.93));
    }
}
