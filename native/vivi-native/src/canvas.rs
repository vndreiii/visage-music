//! Canvas (motion artwork) resolution: Apple Music, Tidal and the Vivi manifest.
//!
//! A faithful port of upstream vivi-music's providers (`AppleMusicCanvasProvider`,
//! `TidalCanvasProvider`, `ViviMusicCanvasProvider`), with two changes: the three sources run
//! concurrently (result chosen by priority Apple > Tidal > Vivi, so the slow path is no longer
//! sequential), and results are cached on disk (hits 24 h, confirmed misses 30 min).

use base64::Engine;
use regex::Regex;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha1::{Digest, Sha1};
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use unicode_normalization::UnicodeNormalization;

const AMP_BASE: &str = "https://amp-api.music.apple.com";
const UA: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
const TIDAL_BASE: &str = "https://api.tidal.com/v1/";
const TIDAL_TOKEN: &str = "vNVdglQOjFJJGG2U";
const VIVI_MANIFEST: &str = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json";
const HIT_TTL_MS: u64 = 24 * 60 * 60 * 1000;
const MISS_TTL_MS: u64 = 30 * 60 * 1000;
// Public read-only web-player token; only a fallback (it expires), the live one is scraped.
const FALLBACK_TOKEN: &str = concat!(
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ",
    ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzgxMDMyODU1LCJleHAiOjE3ODQw",
    "NTY4NTUsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ",
    ".fiMFcJWkfSlxKP9NVA0UW9CbItD1Rge0SISuepz203XcpU762OqdCpU9M-YkmtKkjRmaIWtjsfGgqZPrlMonpA"
);

/// Same JSON shape as Kotlin's `CanvasArtwork`.
#[derive(Serialize, Deserialize, Clone, Default, Debug, PartialEq)]
pub struct Artwork {
    pub name: Option<String>,
    pub artist: Option<String>,
    #[serde(rename = "albumId")]
    pub album_id: Option<String>,
    #[serde(rename = "albumName")]
    pub album_name: Option<String>,
    #[serde(rename = "static")]
    pub static_url: Option<String>,
    pub animated: Option<String>,
    #[serde(rename = "videoUrl")]
    pub video_url: Option<String>,
    #[serde(rename = "animatedTall")]
    pub animated_tall: Option<String>,
}

impl Artwork {
    fn has_video(&self) -> bool {
        [&self.animated_tall, &self.animated, &self.video_url]
            .iter()
            .any(|u| u.as_deref().map(|s| !s.is_empty()).unwrap_or(false))
    }
}

#[derive(Deserialize)]
pub struct Request {
    pub title: String,
    pub artist: String,
    #[serde(default)]
    pub album: Option<String>,
    /// "auto" | "apple" | "tidal" | "vivi"
    #[serde(default = "auto")]
    pub source: String,
    #[serde(default = "us")]
    pub storefront: String,
    #[serde(default)]
    pub cache_dir: Option<String>,
}
fn auto() -> String {
    "auto".into()
}
fn us() -> String {
    "us".into()
}

/// A source failing (network/HTTP) is `Err` and is never cached; "nothing found" is `Ok(None)`.
type Found = Result<Option<Artwork>, String>;

// ── helpers ───────────────────────────────────────────────────────────────────────────────

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

pub fn normalize(s: &str) -> String {
    let stripped: String = s.nfd().filter(|c| !('\u{0300}'..='\u{036f}').contains(c)).collect();
    let mapped: String = stripped
        .to_lowercase()
        .chars()
        .map(|c| if c.is_ascii_lowercase() || c.is_ascii_digit() || c.is_whitespace() { c } else { ' ' })
        .collect();
    mapped.split_whitespace().collect::<Vec<_>>().join(" ")
}

fn artist_delims() -> &'static Regex {
    static R: OnceLock<Regex> = OnceLock::new();
    R.get_or_init(|| {
        Regex::new(r"(?i)(?:\s*,\s*|\s*&\s*|\s+×\s+|\s+x\s+|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)").unwrap()
    })
}

fn split_artists(s: &str) -> Vec<String> {
    artist_delims().split(s).map(normalize).filter(|x| !x.is_empty()).collect()
}

/// Every requested artist must appear among the returned ones (exact, after normalisation).
pub fn artists_match(requested: &str, returned: &str) -> bool {
    let req = split_artists(requested);
    let ret = split_artists(returned);
    !req.is_empty() && !ret.is_empty() && req.iter().all(|r| ret.iter().any(|x| x == r))
}

fn agent() -> &'static ureq::Agent {
    static A: OnceLock<ureq::Agent> = OnceLock::new();
    A.get_or_init(|| {
        ureq::AgentBuilder::new()
            .timeout_connect(Duration::from_secs(12))
            .timeout(Duration::from_secs(25))
            .build()
    })
}

fn get_json(url: &str, headers: &[(&str, &str)], query: &[(&str, &str)]) -> Result<Value, String> {
    let mut r = agent().get(url);
    for (k, v) in headers {
        r = r.set(k, v);
    }
    for (k, v) in query {
        r = r.query(k, v);
    }
    match r.call() {
        Ok(resp) => serde_json::from_str::<Value>(&resp.into_string().map_err(|e| e.to_string())?).map_err(|e| e.to_string()),
        Err(ureq::Error::Status(code, _)) => Err(format!("HTTP {code}")),
        Err(e) => Err(e.to_string()),
    }
}

fn get_text(url: &str) -> Result<String, String> {
    agent().get(url).set("User-Agent", UA).call().map_err(|e| e.to_string())?.into_string().map_err(|e| e.to_string())
}

fn st<'a>(v: &'a Value, path: &[&str]) -> Option<&'a str> {
    let mut cur = v;
    for p in path {
        cur = cur.get(*p)?;
    }
    cur.as_str()
}

// ── Apple Music ───────────────────────────────────────────────────────────────────────────

fn token_cell() -> &'static Mutex<Option<(String, u64)>> {
    static T: OnceLock<Mutex<Option<(String, u64)>>> = OnceLock::new();
    T.get_or_init(Default::default)
}

fn jwt_expiry_ms(token: &str) -> Option<u64> {
    let payload = token.split('.').nth(1)?;
    let bytes = base64::engine::general_purpose::URL_SAFE_NO_PAD.decode(payload.trim_end_matches('=')).ok()?;
    let text = String::from_utf8(bytes).ok()?;
    if !(text.contains("iss") && text.contains("exp")) {
        return None;
    }
    let digits: String = text.split("\"exp\":").nth(1)?.chars().take_while(|c| c.is_ascii_digit()).collect();
    digits.parse::<u64>().ok().map(|s| s * 1000)
}

/// Scrape a live developer token from the Apple Music web player bundle.
fn scrape_token() -> Option<(String, u64)> {
    let html = get_text("https://music.apple.com/us/browse").ok()?;
    let script_re = Regex::new(r"/assets/index(?:-legacy)?[~-][a-zA-Z0-9_-]+\.js").ok()?;
    let token_re = Regex::new(r"ey[a-zA-Z0-9_-]+\.ey[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+").ok()?;
    let mut scripts: Vec<&str> = script_re.find_iter(&html).map(|m| m.as_str()).collect();
    scripts.dedup();
    for path in scripts {
        let Ok(js) = get_text(&format!("https://music.apple.com{path}")) else { continue };
        for m in token_re.find_iter(&js) {
            if let Some(exp) = jwt_expiry_ms(m.as_str()) {
                if exp > now_ms() {
                    return Some((m.as_str().to_owned(), exp));
                }
            }
        }
    }
    None
}

fn apple_token(cache_dir: Option<&Path>) -> String {
    let mut cell = token_cell().lock().unwrap();
    if let Some((t, exp)) = cell.as_ref() {
        if now_ms() + 60_000 < *exp {
            return t.clone();
        }
    }
    let file = cache_dir.map(|d| d.join("apple-token.json"));
    if let Some(f) = &file {
        if let Some((t, exp)) = std::fs::read_to_string(f).ok().and_then(|s| serde_json::from_str::<(String, u64)>(&s).ok()) {
            if now_ms() + 60_000 < exp {
                *cell = Some((t.clone(), exp));
                return t;
            }
        }
    }
    match scrape_token() {
        Some((t, exp)) => {
            if let Some(f) = &file {
                let _ = std::fs::write(f, serde_json::to_string(&(&t, exp)).unwrap_or_default());
            }
            *cell = Some((t.clone(), exp));
            t
        }
        None => FALLBACK_TOKEN.to_owned(),
    }
}

fn apple_headers(token: &str) -> Vec<(String, String)> {
    vec![
        ("Authorization".into(), format!("Bearer {token}")),
        ("Origin".into(), "https://music.apple.com".into()),
        ("Referer".into(), "https://music.apple.com/".into()),
        ("User-Agent".into(), UA.into()),
    ]
}

fn amp_get(path: &str, token: &str, query: &[(&str, &str)]) -> Result<Value, String> {
    let h = apple_headers(token);
    let hr: Vec<(&str, &str)> = h.iter().map(|(a, b)| (a.as_str(), b.as_str())).collect();
    get_json(&format!("{AMP_BASE}{path}"), &hr, query)
}

fn blacklisted(s: &str) -> bool {
    let l = s.to_lowercase();
    ["playlist", "set list", "essentials", "dj mix", "mixed", "apple music", "today's hits", "session"]
        .iter()
        .any(|w| l.contains(w))
}

/// Pick the first usable video URL from an `editorialVideo` object.
fn editorial_url(ev: &Value, prefer_tall: bool) -> Option<String> {
    let order: [&str; 4] = if prefer_tall {
        ["motionDetailTall", "motionDetailRaw", "motionDetailSquare", "motionDetailStatic"]
    } else {
        ["motionDetailSquare", "motionDetailRaw", "motionDetailTall", "motionDetailStatic"]
    };
    for key in order {
        let Some(asset) = ev.get(key) else { continue };
        for field in ["video", "videoUrl", "hlsUrl", "url"] {
            if let Some(u) = asset.get(field).and_then(Value::as_str) {
                if !u.is_empty() {
                    return Some(u.to_owned());
                }
            }
        }
    }
    None
}

fn artwork_from_ev(ev: &Value, name: Option<String>, artist: String, album_id: String, album_name: Option<String>) -> Option<Artwork> {
    let url = editorial_url(ev, false)?;
    Some(Artwork {
        name,
        artist: Some(artist),
        album_id: Some(album_id),
        album_name,
        animated: Some(url),
        animated_tall: editorial_url(ev, true),
        ..Default::default()
    })
}

fn apple_fetch_album(album_id: &str, storefront: &str, token: &str, fallback_artist: &str, title_override: Option<&str>, artist_override: Option<&str>) -> Found {
    if album_id.starts_with("pl.") {
        return Ok(None);
    }
    let root = amp_get(&format!("/v1/catalog/{storefront}/albums/{album_id}"), token, &[("extend", "editorialVideo"), ("include", "tracks")])?;
    let Some(album) = root.get("data").and_then(|d| d.get(0)) else { return Ok(None) };
    let attrs = album.get("attributes").cloned().unwrap_or(Value::Null);
    let album_name = st(&attrs, &["name"]).unwrap_or("").to_owned();
    if blacklisted(&album_name) {
        return Ok(None);
    }
    let artist = st(&attrs, &["artistName"]).unwrap_or(fallback_artist).to_owned();
    let title = title_override.map(str::to_owned).unwrap_or_else(|| album_name.clone());
    let artist = artist_override.map(str::to_owned).unwrap_or(artist);
    Ok(attrs.get("editorialVideo").and_then(|ev| artwork_from_ev(ev, Some(title), artist, album_id.to_owned(), Some(album_name))))
}

fn apple_search(term: &str, artist: &str, album: Option<&str>, storefront: &str, typ: &str, cache_dir: Option<&Path>) -> Found {
    let token = apple_token(cache_dir);
    let mut query = if term.to_lowercase().contains(&artist.to_lowercase()) { term.to_owned() } else { format!("{artist} {term}") };
    if let Some(a) = album.filter(|a| !a.is_empty()) {
        if !query.to_lowercase().contains(&a.to_lowercase()) {
            query = format!("{query} {a}");
        }
    }
    let root = amp_get(
        &format!("/v1/catalog/{storefront}/search"),
        &token,
        &[("term", &query), ("types", typ), ("limit", "10"), ("extend", "editorialVideo"), ("include", "albums")],
    )?;
    let Some(results) = root.pointer(&format!("/results/{typ}/data")).and_then(Value::as_array) else { return Ok(None) };

    let norm_term = normalize(term);
    let mut scored: Vec<(i32, &Value)> = results
        .iter()
        .filter_map(|item| {
            let attrs = item.get("attributes")?;
            let r_artist = st(attrs, &["artistName"]).unwrap_or("");
            let r_name = st(attrs, &["name"]).unwrap_or("");
            let r_coll = st(attrs, &["albumName"]).or_else(|| st(attrs, &["collectionName"])).unwrap_or("");
            if blacklisted(r_name) || blacklisted(r_coll) {
                return None;
            }
            if !artists_match(artist, r_artist) {
                return None;
            }
            let mut score = 10;
            let n = normalize(r_name);
            if n == norm_term {
                score += 15;
            } else if n.contains(&norm_term) || norm_term.contains(&n) {
                score += 7;
            } else {
                score -= 10;
            }
            for w in ["deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus"] {
                let in_term = term.to_lowercase().contains(w);
                let in_res = r_name.to_lowercase().contains(w);
                if in_term && in_res {
                    score += 5;
                } else if in_term != in_res && in_res {
                    score -= 3;
                }
            }
            if let Some(a) = album.filter(|a| !a.is_empty()) {
                if !r_coll.is_empty() {
                    let (na, nc) = (normalize(a), normalize(r_coll));
                    if nc == na {
                        score += 20;
                    } else if nc.contains(&na) || na.contains(&nc) {
                        score += 10;
                    }
                }
            }
            Some((score, item))
        })
        .collect();
    scored.sort_by(|a, b| b.0.cmp(&a.0));

    for (score, item) in scored {
        if score < 12 {
            continue;
        }
        let attrs = &item["attributes"];
        let r_name = st(attrs, &["name"]).unwrap_or("").to_owned();
        let r_artist = st(attrs, &["artistName"]).unwrap_or("").to_owned();
        let kind = st(item, &["type"]).unwrap_or("");
        let album_id: Option<String> = if kind == "songs" {
            item.pointer("/relationships/albums/data/0/id")
                .and_then(Value::as_str)
                .map(str::to_owned)
                .or_else(|| st(attrs, &["collectionId"]).map(str::to_owned))
                .or_else(|| {
                    // https://music.apple.com/region/album/name/ID?i=songId
                    let url = st(attrs, &["url"])?;
                    let id = url.split("/album/").nth(1)?.split('?').next()?.rsplit('/').next()?.to_owned();
                    (!id.is_empty() && id.chars().all(|c| c.is_ascii_digit())).then_some(id)
                })
        } else if kind == "albums" {
            st(item, &["id"]).map(str::to_owned)
        } else {
            None
        };
        let Some(album_id) = album_id.filter(|a| !a.starts_with("pl.")) else { continue };

        if let Some(ev) = attrs.get("editorialVideo") {
            let coll = st(attrs, &["collectionName"]).map(str::to_owned);
            let resolved_album = if kind == "songs" { coll } else { Some(r_name.clone()) };
            if let Some(art) = artwork_from_ev(ev, Some(r_name.clone()), r_artist.clone(), album_id.clone(), resolved_album) {
                return Ok(Some(art));
            }
        }
        let (title_o, artist_o) = if kind == "songs" { (Some(r_name.as_str()), Some(r_artist.as_str())) } else { (None, None) };
        if let Some(art) = apple_fetch_album(&album_id, storefront, &token, &r_artist, title_o, artist_o)? {
            return Ok(Some(art));
        }
    }
    Ok(None)
}

// ── Tidal ─────────────────────────────────────────────────────────────────────────────────

pub fn tidal_video_url(id: &str) -> Option<String> {
    let p: Vec<&str> = id.split('-').collect();
    (p.len() == 5).then(|| format!("https://resources.tidal.com/videos/{}/{}/{}/{}/{}/1280x1280.mp4", p[0], p[1], p[2], p[3], p[4]))
}

fn tidal_song(song: &str, artist: &str, album: Option<&str>, storefront: &str) -> Found {
    let query = match album.filter(|a| !a.is_empty()) {
        Some(a) => format!("{a} {artist} {song}"),
        None => format!("{artist} {song}"),
    };
    let country = storefront.to_uppercase();
    let root = get_json(
        &format!("{TIDAL_BASE}search"),
        &[("X-Tidal-Token", TIDAL_TOKEN)],
        &[("query", &query), ("limit", "10"), ("types", "TRACKS"), ("countryCode", &country)],
    )?;
    let Some(items) = root.pointer("/tracks/items").and_then(Value::as_array) else { return Ok(None) };
    let want = normalize(song);
    for item in items {
        let title = st(item, &["title"]);
        if let Some(t) = title {
            if normalize(t) != want {
                continue;
            }
        }
        let names: Vec<String> = item
            .get("artists")
            .and_then(Value::as_array)
            .map(|a| a.iter().filter_map(|x| st(x, &["name"]).map(str::to_owned)).collect())
            .unwrap_or_default();
        let combined = if names.is_empty() { st(item, &["artist", "name"]).unwrap_or("").to_owned() } else { names.join(", ") };
        if !combined.is_empty() {
            let req = split_artists(artist);
            let ret: Vec<String> = names.iter().map(|n| normalize(n)).collect();
            if !(!req.is_empty() && !ret.is_empty() && req.iter().all(|r| ret.contains(r))) {
                continue;
            }
        }
        let cover = item.pointer("/album/videoCover").and_then(Value::as_str).filter(|c| !c.is_empty());
        if let Some(url) = cover.and_then(tidal_video_url) {
            return Ok(Some(Artwork {
                name: Some(title.unwrap_or(song).to_owned()),
                artist: Some(if combined.is_empty() { artist.to_owned() } else { combined }),
                album_name: item.pointer("/album/title").and_then(Value::as_str).map(str::to_owned),
                video_url: Some(url),
                ..Default::default()
            }));
        }
    }
    Ok(None)
}

// ── Vivi manifest ─────────────────────────────────────────────────────────────────────────

fn vivi_song(song: &str, artist: &str, album: &str) -> Found {
    if song.is_empty() || artist.is_empty() {
        return Ok(None);
    }
    static M: OnceLock<Mutex<Option<(Value, u64)>>> = OnceLock::new();
    let cell = M.get_or_init(Default::default);
    let manifest = {
        let mut g = cell.lock().unwrap();
        match g.as_ref() {
            Some((m, exp)) if *exp > now_ms() => m.clone(),
            _ => {
                let m = get_json(VIVI_MANIFEST, &[], &[])?;
                *g = Some((m.clone(), now_ms() + 60_000));
                m
            }
        }
    };
    let (ns, na, nal) = (normalize(song), normalize(artist), normalize(album));
    let hit = manifest.get("items").and_then(Value::as_array).and_then(|items| {
        items.iter().find(|i| {
            let (is, ia, ial) = (normalize(st(i, &["song"]).unwrap_or("")), normalize(st(i, &["artist"]).unwrap_or("")), normalize(st(i, &["album"]).unwrap_or("")));
            (ns.contains(&is) || is.contains(&ns)) && (na.contains(&ia) || ia.contains(&na)) && nal == ial
        })
    });
    Ok(hit.map(|i| {
        let url = st(i, &["url"]).map(str::to_owned);
        Artwork {
            name: st(i, &["song"]).map(str::to_owned),
            artist: st(i, &["artist"]).map(str::to_owned),
            album_name: st(i, &["album"]).filter(|a| !a.is_empty()).map(str::to_owned),
            video_url: url.clone(),
            animated: url,
            ..Default::default()
        }
    }))
}

// ── Orchestration + cache ─────────────────────────────────────────────────────────────────

/// Title cleanup identical to the old Kotlin `CanvasResolver`: drop `(...)`/`[...]`, keep text before " - ".
pub fn clean_title(title: &str) -> String {
    static R: OnceLock<Regex> = OnceLock::new();
    let re = R.get_or_init(|| Regex::new(r"(?i)\s*[\[\(].*?[\]\)]").unwrap());
    let stripped = re.replace_all(title, "");
    stripped.split(" - ").next().unwrap_or("").trim().to_owned()
}

#[derive(Serialize, Deserialize)]
struct CacheEntry {
    expires_ms: u64,
    value: Option<Artwork>,
}

fn cache_file(dir: &Path, key: &str) -> PathBuf {
    let h: String = Sha1::digest(key.as_bytes()).iter().map(|b| format!("{b:02x}")).collect();
    dir.join(format!("canvas-{}.json", &h[..20]))
}

fn cache_read(dir: &Path, key: &str) -> Option<Option<Artwork>> {
    let e: CacheEntry = serde_json::from_slice(&std::fs::read(cache_file(dir, key)).ok()?).ok()?;
    (e.expires_ms > now_ms()).then_some(e.value)
}

fn cache_write(dir: &Path, key: &str, value: &Option<Artwork>) {
    let ttl = if value.is_some() { HIT_TTL_MS } else { MISS_TTL_MS };
    let entry = CacheEntry { expires_ms: now_ms() + ttl, value: value.clone() };
    if let Ok(bytes) = serde_json::to_vec(&entry) {
        let f = cache_file(dir, key);
        let tmp = f.with_extension("tmp");
        if std::fs::write(&tmp, bytes).is_ok() {
            let _ = std::fs::rename(&tmp, &f);
        }
    }
}

pub fn resolve(req: &Request) -> Option<Artwork> {
    let dir = req.cache_dir.as_deref().map(PathBuf::from);
    if let Some(d) = &dir {
        let _ = std::fs::create_dir_all(d);
    }
    let title = clean_title(&req.title);
    let album = req.album.clone().unwrap_or_default();
    let key = format!("{}|{}|{}|{}|{}", req.source, title.to_lowercase(), req.artist.to_lowercase(), album.to_lowercase(), req.storefront);
    if let Some(d) = &dir {
        if let Some(v) = cache_read(d, &key) {
            return v;
        }
    }

    let want = |s: &str| req.source == "auto" || req.source == s;
    let (t, a, al, sf) = (title.as_str(), req.artist.as_str(), album.as_str(), req.storefront.as_str());
    let dirp = dir.as_deref();
    let (apple, tidal, vivi) = std::thread::scope(|s| {
        let ha = want("apple").then(|| s.spawn(|| apple_search(t, a, Some(al), sf, "songs", dirp)));
        let ht = want("tidal").then(|| s.spawn(|| tidal_song(t, a, Some(al), sf)));
        let hv = want("vivi").then(|| s.spawn(|| vivi_song(t, a, al)));
        let join = |h: Option<std::thread::ScopedJoinHandle<'_, Found>>| h.map(|h| h.join().unwrap_or(Err("panic".into())));
        (join(ha), join(ht), join(hv))
    });

    // Priority Apple > Tidal > Vivi; only cache when no source failed outright.
    let all: Vec<&Found> = [&apple, &tidal, &vivi].into_iter().flatten().collect();
    let found = all.iter().filter_map(|r| r.as_ref().ok()).flatten().find(|art| art.has_video()).map(|a| (*a).clone());
    let clean_miss = all.iter().all(|r| r.is_ok());
    if let Some(d) = &dir {
        if found.is_some() || clean_miss {
            cache_write(d, &key, &found);
        }
    }
    found
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_like_kotlin() {
        assert_eq!(normalize("Beyoncé  — Halo (Live!)"), "beyonce halo live");
        assert_eq!(normalize("  A-B_c  "), "a b c");
    }

    #[test]
    fn artist_matching_splits_credits() {
        assert!(artists_match("Zara Larsson", "Zara Larsson"));
        assert!(artists_match("PinkPantheress, Ice Spice", "PinkPantheress & Ice Spice"));
        assert!(artists_match("ILLIT", "ILLIT"));
        assert!(!artists_match("ILLIT", "NewJeans"));
        assert!(artists_match("Drake", "Drake, 21 Savage x Future"));
        assert!(!artists_match("Drake feat. Future", "Drake"));
    }

    #[test]
    fn cleans_titles() {
        assert_eq!(clean_title("Magnetic (Official Video) - ILLIT"), "Magnetic");
        assert_eq!(clean_title("Stateside + Zara Larsson [Live]"), "Stateside + Zara Larsson");
    }

    #[test]
    fn tidal_url_requires_uuid_shape() {
        assert_eq!(
            tidal_video_url("a-b-c-d-e").as_deref(),
            Some("https://resources.tidal.com/videos/a/b/c/d/e/1280x1280.mp4")
        );
        assert!(tidal_video_url("nope").is_none());
    }

    #[test]
    fn editorial_prefers_requested_shape() {
        let ev: Value = serde_json::json!({
            "motionDetailSquare": {"video": "sq.m3u8"},
            "motionDetailTall": {"video": "tall.m3u8"}
        });
        assert_eq!(editorial_url(&ev, false).as_deref(), Some("sq.m3u8"));
        assert_eq!(editorial_url(&ev, true).as_deref(), Some("tall.m3u8"));
    }

    #[test]
    fn jwt_expiry_is_read_from_payload() {
        assert_eq!(jwt_expiry_ms(FALLBACK_TOKEN), Some(1_784_056_855_000));
    }
}
