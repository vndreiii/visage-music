//! Artist imagery + bio lookup from public, key-less sources.
//!
//! Deezer (portrait), TheAudioDB (thumb / fanart banner / biography; the public test key `2`) and
//! Wikipedia (summary extract + lead image) are queried concurrently for the *primary* credited
//! artist ("Basshunter, Alien Cut" resolves "Basshunter"; the caller keeps the full string for
//! display). Only exact (normalised) name matches are accepted, so a wrong artist is never shown.
//! Results are cached in memory and on disk (hits 7 days, confirmed misses 6 hours; network
//! failures are never cached) and calls to one host are spaced out politely.

use regex::Regex;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha1::{Digest, Sha1};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

const UA: &str = concat!(
    "VIsage/",
    env!("CARGO_PKG_VERSION"),
    " (desktop music player; https://github.com/vndreiii/visage-music)"
);
const HIT_TTL_MS: u64 = 7 * 24 * 60 * 60 * 1000;
const MISS_TTL_MS: u64 = 6 * 60 * 60 * 1000;
const HOST_GAP: Duration = Duration::from_millis(180);
const MAX_BIO_CHARS: usize = 1400;

/// Same JSON shape as Kotlin's `ArtistInfo`.
#[derive(Serialize, Deserialize, Clone, Default, Debug, PartialEq)]
pub struct Info {
    pub name: String,
    #[serde(rename = "imageUrl")]
    pub image_url: Option<String>,
    #[serde(rename = "bannerUrl")]
    pub banner_url: Option<String>,
    pub bio: Option<String>,
    pub sources: Vec<String>,
}

impl Info {
    fn is_empty(&self) -> bool {
        self.image_url.is_none() && self.banner_url.is_none() && self.bio.is_none()
    }
}

#[derive(Deserialize)]
pub struct Request {
    pub name: String,
    #[serde(default)]
    pub cache_dir: Option<String>,
}

// ── name handling ─────────────────────────────────────────────────────────────────────────

fn delims() -> &'static Regex {
    static R: OnceLock<Regex> = OnceLock::new();
    R.get_or_init(|| {
        Regex::new(r"(?i)\s*[,;]\s*|\s*&\s*|\s+×\s+|\s+x\s+|\s+(?:feat|ft)\.?\s+|\s+featuring\s+|\s*\(\s*(?:feat|ft)\.?\s+").unwrap()
    })
}

/// Credited artists in order, original casing, deduplicated ("A, B & C" -> [A, B, C]).
pub fn split_credits(s: &str) -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    for part in delims().split(s) {
        let p = part.trim().trim_matches(|c| c == '(' || c == ')').trim();
        if !p.is_empty() && !out.iter().any(|o| normalize(o) == normalize(p)) {
            out.push(p.to_string());
        }
    }
    out
}

/// Lower-case ASCII alphanumerics separated by single spaces (diacritics stripped).
pub fn normalize(s: &str) -> String {
    use unicode_normalization::UnicodeNormalization;
    let stripped: String = s.nfd().filter(|c| !('\u{0300}'..='\u{036f}').contains(c)).collect();
    let mapped: String = stripped
        .to_lowercase()
        .chars()
        .map(|c| if c.is_alphanumeric() || c.is_whitespace() { c } else { ' ' })
        .collect();
    mapped.split_whitespace().collect::<Vec<_>>().join(" ")
}

/// Names to try, best first: the full credit string (it may really be one act, "Earth, Wind & Fire"),
/// then the primary artist.
pub fn candidates(name: &str) -> Vec<String> {
    let mut v = vec![name.trim().to_string()];
    if let Some(first) = split_credits(name).into_iter().next() {
        if normalize(&first) != normalize(name) {
            v.push(first);
        }
    }
    v.retain(|c| !normalize(c).is_empty());
    v
}

fn name_eq(a: &str, b: &str) -> bool {
    let (a, b) = (normalize(a), normalize(b));
    !a.is_empty() && a == b
}

// ── http ──────────────────────────────────────────────────────────────────────────────────

fn agent() -> &'static ureq::Agent {
    static A: OnceLock<ureq::Agent> = OnceLock::new();
    A.get_or_init(|| {
        ureq::AgentBuilder::new()
            .user_agent(UA)
            .timeout_connect(Duration::from_secs(4))
            .timeout(Duration::from_secs(8))
            .build()
    })
}

/// Sleeps so two requests to one host start at least [`HOST_GAP`] apart.
fn polite(host: &str) {
    static LAST: OnceLock<Mutex<HashMap<String, Instant>>> = OnceLock::new();
    let wait = {
        let mut m = LAST.get_or_init(Default::default).lock().unwrap_or_else(|e| e.into_inner());
        let now = Instant::now();
        let at = m.get(host).map(|t| (*t + HOST_GAP).max(now)).unwrap_or(now);
        m.insert(host.to_string(), at);
        at.saturating_duration_since(now)
    };
    if !wait.is_zero() {
        std::thread::sleep(wait);
    }
}

/// `Ok(None)` for 404 (a clean "not there"), `Err` for anything transient.
fn get_json(host: &str, url: &str, query: &[(&str, &str)]) -> Result<Option<Value>, String> {
    polite(host);
    let mut r = agent().get(url);
    for (k, v) in query {
        r = r.query(k, v);
    }
    match r.call() {
        Ok(resp) => {
            let body = resp.into_string().map_err(|e| e.to_string())?;
            serde_json::from_str::<Value>(&body).map(Some).map_err(|e| e.to_string())
        }
        Err(ureq::Error::Status(404, _)) => Ok(None),
        Err(ureq::Error::Status(code, _)) => Err(format!("HTTP {code}")),
        Err(e) => Err(e.to_string()),
    }
}

fn s<'a>(v: &'a Value, key: &str) -> Option<&'a str> {
    v.get(key)?.as_str().map(str::trim).filter(|x| !x.is_empty())
}

// ── sources (pure parsers are separate so tests run on fixtures) ──────────────────────────

#[derive(Default, Debug, Clone, PartialEq)]
struct Part {
    source: &'static str,
    image: Option<String>,
    banner: Option<String>,
    bio: Option<String>,
}

/// Deezer's "no picture" placeholder has an empty hash segment (`/images/artist//250x250...`).
fn real_deezer_image(u: &str) -> bool {
    !u.is_empty() && !u.contains("/artist//")
}

fn parse_deezer(v: &Value, name: &str) -> Option<Part> {
    let hit = v.get("data")?.as_array()?.iter().find(|a| s(a, "name").map(|n| name_eq(n, name)).unwrap_or(false))?;
    let image = ["picture_xl", "picture_big", "picture_medium"]
        .iter()
        .filter_map(|k| s(hit, k))
        .find(|u| real_deezer_image(u))?
        .to_string();
    Some(Part { source: "deezer", image: Some(image), ..Default::default() })
}

fn parse_audiodb(v: &Value, name: &str) -> Option<Part> {
    let hit = v.get("artists")?.as_array()?.iter().find(|a| s(a, "strArtist").map(|n| name_eq(n, name)).unwrap_or(false))?;
    let part = Part {
        source: "theaudiodb",
        image: s(hit, "strArtistThumb").map(String::from),
        banner: s(hit, "strArtistFanart").or_else(|| s(hit, "strArtistWideThumb")).or_else(|| s(hit, "strArtistBanner")).map(String::from),
        bio: s(hit, "strBiographyEN").map(|b| shorten(b, MAX_BIO_CHARS)),
    };
    (part.image.is_some() || part.banner.is_some() || part.bio.is_some()).then_some(part)
}

fn music_words() -> &'static Regex {
    static R: OnceLock<Regex> = OnceLock::new();
    R.get_or_init(|| {
        Regex::new(r"(?i)\b(singer|musician|band|rapper|dj|disc jockey|producer|composer|group|duo|trio|songwriter|vocalist|guitarist|pianist|rock|pop|hip hop|electronic|dance|music)\b").unwrap()
    })
}

fn parse_wikipedia(v: &Value, name: &str) -> Option<Part> {
    if s(v, "type") == Some("disambiguation") {
        return None;
    }
    let title = s(v, "title")?;
    // "Name (band)" / "Name (musician)" are fine; compare without the parenthetical.
    let bare = title.split(" (").next().unwrap_or(title);
    if !name_eq(bare, name) {
        return None;
    }
    let desc = s(v, "description").unwrap_or("");
    let extract = s(v, "extract").unwrap_or("");
    if !music_words().is_match(desc) && !music_words().is_match(&extract.chars().take(300).collect::<String>()) {
        return None;
    }
    let image = v
        .get("originalimage")
        .and_then(|o| s(o, "source"))
        .or_else(|| v.get("thumbnail").and_then(|o| s(o, "source")))
        .map(String::from);
    let bio = (!extract.is_empty()).then(|| shorten(extract, MAX_BIO_CHARS));
    (image.is_some() || bio.is_some()).then_some(Part { source: "wikipedia", image, banner: None, bio })
}

/// Cut at a sentence end when possible, collapse whitespace, strip simple HTML.
pub fn shorten(text: &str, max_chars: usize) -> String {
    static TAGS: OnceLock<Regex> = OnceLock::new();
    let tags = TAGS.get_or_init(|| Regex::new(r"<[^>]+>").unwrap());
    let plain = tags.replace_all(text, " ");
    let t = plain.split_whitespace().collect::<Vec<_>>().join(" ");
    if t.chars().count() <= max_chars {
        return t;
    }
    let cut: String = t.chars().take(max_chars).collect();
    match cut.rfind(|c| c == '.' || c == '!' || c == '?') {
        Some(i) if i > max_chars / 2 => cut[..=i].to_string(),
        _ => format!("{}…", cut.trim_end()),
    }
}

fn pct(s: &str) -> String {
    let mut o = String::new();
    for b in s.bytes() {
        if b.is_ascii_alphanumeric() || b"-_.~".contains(&b) {
            o.push(b as char);
        } else {
            o.push_str(&format!("%{b:02X}"));
        }
    }
    o
}

type Fetch = Result<Option<Part>, String>;

fn fetch_deezer(names: &[String]) -> Fetch {
    for n in names {
        let v = get_json("api.deezer.com", "https://api.deezer.com/search/artist", &[("q", n), ("limit", "8")])?;
        if let Some(p) = v.and_then(|v| parse_deezer(&v, n)) {
            return Ok(Some(p));
        }
    }
    Ok(None)
}

fn fetch_audiodb(names: &[String]) -> Fetch {
    for n in names {
        let v = get_json("www.theaudiodb.com", "https://www.theaudiodb.com/api/v1/json/2/search.php", &[("s", n)])?;
        if let Some(p) = v.and_then(|v| parse_audiodb(&v, n)) {
            return Ok(Some(p));
        }
    }
    Ok(None)
}

fn fetch_wikipedia(names: &[String]) -> Fetch {
    for n in names {
        let title = n.replace(' ', "_");
        for suffix in ["", "_(band)", "_(musician)", "_(singer)", "_(rapper)", "_(DJ)"] {
            let url = format!("https://en.wikipedia.org/api/rest_v1/page/summary/{}{}", pct(&title), suffix.replace('(', "%28").replace(')', "%29"));
            if let Some(p) = get_json("en.wikipedia.org", &url, &[])?.and_then(|v| parse_wikipedia(&v, n)) {
                return Ok(Some(p));
            }
        }
    }
    Ok(None)
}

/// Image: Deezer > TheAudioDB > Wikipedia. Banner: TheAudioDB only. Bio: Wikipedia > TheAudioDB.
fn merge(display: &str, deezer: Option<Part>, audiodb: Option<Part>, wiki: Option<Part>) -> Info {
    let parts: Vec<&Part> = [&deezer, &audiodb, &wiki].into_iter().flatten().collect();
    let image = [&deezer, &audiodb, &wiki].into_iter().flatten().find_map(|p| p.image.clone());
    let banner = parts.iter().find_map(|p| p.banner.clone());
    let bio = [&wiki, &audiodb].into_iter().flatten().find_map(|p| p.bio.clone());
    let sources = parts.iter().map(|p| p.source.to_string()).collect();
    Info { name: display.to_string(), image_url: image, banner_url: banner, bio, sources }
}

// ── cache + orchestration ─────────────────────────────────────────────────────────────────

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

#[derive(Serialize, Deserialize, Clone)]
struct Entry {
    expires_ms: u64,
    value: Option<Info>,
}

fn memory() -> &'static Mutex<HashMap<String, Entry>> {
    static M: OnceLock<Mutex<HashMap<String, Entry>>> = OnceLock::new();
    M.get_or_init(Default::default)
}

fn cache_file(dir: &Path, key: &str) -> PathBuf {
    let h: String = Sha1::digest(key.as_bytes()).iter().map(|b| format!("{b:02x}")).collect();
    dir.join(format!("artist-{}.json", &h[..20]))
}

fn cache_read(dir: Option<&Path>, key: &str) -> Option<Option<Info>> {
    let now = now_ms();
    if let Some(e) = memory().lock().unwrap_or_else(|e| e.into_inner()).get(key) {
        if e.expires_ms > now {
            return Some(e.value.clone());
        }
    }
    let e: Entry = serde_json::from_slice(&std::fs::read(cache_file(dir?, key)).ok()?).ok()?;
    if e.expires_ms <= now {
        return None;
    }
    memory().lock().unwrap_or_else(|e| e.into_inner()).insert(key.to_string(), e.clone());
    Some(e.value)
}

fn cache_write(dir: Option<&Path>, key: &str, value: &Option<Info>) {
    let ttl = if value.is_some() { HIT_TTL_MS } else { MISS_TTL_MS };
    let entry = Entry { expires_ms: now_ms() + ttl, value: value.clone() };
    memory().lock().unwrap_or_else(|e| e.into_inner()).insert(key.to_string(), entry.clone());
    if let (Some(dir), Ok(bytes)) = (dir, serde_json::to_vec(&entry)) {
        let _ = std::fs::create_dir_all(dir);
        let f = cache_file(dir, key);
        let tmp = f.with_extension("tmp");
        if std::fs::write(&tmp, bytes).is_ok() {
            let _ = std::fs::rename(&tmp, &f);
        }
    }
}

/// `None` = nothing found anywhere. Blocks on the network; call off the UI thread.
pub fn resolve(req: &Request) -> Option<Info> {
    let key = normalize(&req.name);
    if key.is_empty() {
        return None;
    }
    let dir = req.cache_dir.as_deref().map(PathBuf::from);
    if let Some(hit) = cache_read(dir.as_deref(), &key) {
        return hit;
    }
    let names = candidates(&req.name);
    let (deezer, audiodb, wiki) = std::thread::scope(|sc| {
        let a = sc.spawn(|| fetch_deezer(&names));
        let b = sc.spawn(|| fetch_audiodb(&names));
        let c = sc.spawn(|| fetch_wikipedia(&names));
        let j = |r: std::thread::ScopedJoinHandle<'_, Fetch>| r.join().unwrap_or(Err("panic".into()));
        (j(a), j(b), j(c))
    });
    let clean = deezer.is_ok() && audiodb.is_ok() && wiki.is_ok();
    let info = merge(&req.name, deezer.ok().flatten(), audiodb.ok().flatten(), wiki.ok().flatten());
    let found = (!info.is_empty()).then_some(info);
    // A partial result is still shown, but only fully clean lookups are remembered as misses.
    if found.is_some() || clean {
        cache_write(dir.as_deref(), &key, &found);
    }
    found
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn splits_credits() {
        assert_eq!(split_credits("Basshunter, Alien Cut"), vec!["Basshunter", "Alien Cut"]);
        assert_eq!(
            split_credits("DJ llz, XSKAVI1, DJ SMG, & Dj v2be"),
            vec!["DJ llz", "XSKAVI1", "DJ SMG", "Dj v2be"]
        );
        assert_eq!(split_credits("Drake feat. Future"), vec!["Drake", "Future"]);
        assert_eq!(split_credits("Song (feat. Zara Larsson)"), vec!["Song", "Zara Larsson"]);
        assert_eq!(split_credits("Calvin Harris x Rag'n'Bone Man"), vec!["Calvin Harris", "Rag'n'Bone Man"]);
        assert_eq!(split_credits("AC/DC"), vec!["AC/DC"]);
        assert_eq!(split_credits("A & a"), vec!["A"]);
    }

    #[test]
    fn candidates_try_full_then_primary() {
        assert_eq!(candidates("Basshunter, Alien Cut"), vec!["Basshunter, Alien Cut", "Basshunter"]);
        assert_eq!(candidates("  ILLIT "), vec!["ILLIT"]);
    }

    #[test]
    fn normalizes() {
        assert_eq!(normalize("Beyoncé!"), "beyonce");
        assert_eq!(normalize("  DJ   v2be "), "dj v2be");
        assert!(name_eq("Sigur Rós", "sigur ros"));
        assert!(!name_eq("", ""));
    }

    #[test]
    fn deezer_requires_exact_name_and_real_picture() {
        let v = json!({"data": [
            {"name": "Basshunter Fan Club", "picture_xl": "https://x/a.jpg"},
            {"name": "Basshunter", "picture_xl": "https://cdn/images/artist/abc/1000x1000.jpg"}
        ]});
        assert_eq!(parse_deezer(&v, "basshunter").unwrap().image.as_deref(), Some("https://cdn/images/artist/abc/1000x1000.jpg"));
        let placeholder = json!({"data": [{"name": "Nobody", "picture_xl": "https://cdn/images/artist//1000x1000.jpg"}]});
        assert!(parse_deezer(&placeholder, "Nobody").is_none());
        assert!(parse_deezer(&v, "Someone Else").is_none());
    }

    #[test]
    fn audiodb_parses_images_and_bio() {
        let v = json!({"artists": [{
            "strArtist": "Zara Larsson", "strArtistThumb": "t.jpg", "strArtistFanart": "f.jpg",
            "strBiographyEN": "Zara <b>Larsson</b> is a Swedish singer."
        }]});
        let p = parse_audiodb(&v, "Zara Larsson").unwrap();
        assert_eq!((p.image.as_deref(), p.banner.as_deref()), (Some("t.jpg"), Some("f.jpg")));
        assert_eq!(p.bio.as_deref(), Some("Zara Larsson is a Swedish singer."));
        assert!(parse_audiodb(&json!({"artists": null}), "Zara Larsson").is_none());
    }

    #[test]
    fn wikipedia_rejects_disambiguation_and_non_music() {
        let ok = json!({"type":"standard","title":"Basshunter","description":"Swedish musician and DJ","extract":"Jonas Altberg is a Swedish musician.","thumbnail":{"source":"w.jpg"}});
        assert_eq!(parse_wikipedia(&ok, "Basshunter").unwrap().image.as_deref(), Some("w.jpg"));
        let dis = json!({"type":"disambiguation","title":"Basshunter","description":"x","extract":"may refer to"});
        assert!(parse_wikipedia(&dis, "Basshunter").is_none());
        let town = json!({"type":"standard","title":"Cher","description":"Commune in France","extract":"Cher is a department.","thumbnail":{"source":"c.jpg"}});
        assert!(parse_wikipedia(&town, "Cher").is_none());
        let band = json!({"type":"standard","title":"Cher (singer)","description":"American singer","extract":"x"});
        assert!(parse_wikipedia(&band, "Cher").is_some());
    }

    #[test]
    fn merge_prioritises_sources() {
        let d = Part { source: "deezer", image: Some("d.jpg".into()), ..Default::default() };
        let a = Part { source: "theaudiodb", image: Some("a.jpg".into()), banner: Some("b.jpg".into()), bio: Some("adb bio".into()) };
        let w = Part { source: "wikipedia", bio: Some("wiki bio".into()), ..Default::default() };
        let m = merge("X, Y", Some(d), Some(a), Some(w));
        assert_eq!(m.name, "X, Y");
        assert_eq!(m.image_url.as_deref(), Some("d.jpg"));
        assert_eq!(m.banner_url.as_deref(), Some("b.jpg"));
        assert_eq!(m.bio.as_deref(), Some("wiki bio"));
        assert_eq!(m.sources, vec!["deezer", "theaudiodb", "wikipedia"]);
        assert!(merge("n", None, None, None).is_empty());
    }

    #[test]
    fn shortens_at_sentence_boundary() {
        let t = format!("{} Second sentence here. Tail without end that keeps going on", "First sentence is fairly long indeed.");
        let out = shorten(&t, 60);
        assert!(out.ends_with('.'), "{out}");
        assert!(out.chars().count() <= 60);
    }

    #[test]
    fn cache_round_trip_and_negative() {
        let dir = std::env::temp_dir().join(format!("visage-artist-test-{}", now_ms()));
        let info = Info { name: "T".into(), image_url: Some("i".into()), ..Default::default() };
        cache_write(Some(&dir), "zz-hit", &Some(info.clone()));
        cache_write(Some(&dir), "zz-miss", &None);
        assert_eq!(cache_read(Some(&dir), "zz-hit"), Some(Some(info)));
        assert_eq!(cache_read(Some(&dir), "zz-miss"), Some(None));
        assert_eq!(cache_read(Some(&dir), "zz-unknown"), None);
        let _ = std::fs::remove_dir_all(dir);
    }
}
