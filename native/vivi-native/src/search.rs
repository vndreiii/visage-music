use nucleo_matcher::pattern::{CaseMatching, Normalization, Pattern};
use nucleo_matcher::{Config, Matcher, Utf32Str};
use rayon::prelude::*;
use serde::Deserialize;
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

#[derive(Deserialize)]
struct Item {
    id: String,
    text: String,
}

struct Index {
    ids: Vec<String>,
    texts: Vec<String>,
}

static NEXT: AtomicU64 = AtomicU64::new(1);

fn registry() -> &'static Mutex<HashMap<u64, Arc<Index>>> {
    static R: OnceLock<Mutex<HashMap<u64, Arc<Index>>>> = OnceLock::new();
    R.get_or_init(Default::default)
}

pub fn create(json: &str) -> Option<u64> {
    let items: Vec<Item> = serde_json::from_str(json).ok()?;
    let index = Index {
        ids: items.iter().map(|i| i.id.clone()).collect(),
        texts: items.into_iter().map(|i| i.text).collect(),
    };
    let handle = NEXT.fetch_add(1, Ordering::Relaxed);
    registry().lock().ok()?.insert(handle, Arc::new(index));
    Some(handle)
}

pub fn query(handle: u64, q: &str, limit: usize) -> Vec<String> {
    let index = match registry().lock().ok().and_then(|r| r.get(&handle).cloned()) {
        Some(i) => i,
        None => return Vec::new(),
    };
    if q.trim().is_empty() {
        return Vec::new();
    }
    let pattern = Pattern::parse(q, CaseMatching::Ignore, Normalization::Smart);
    let mut scored: Vec<(u32, usize)> = index
        .texts
        .par_iter()
        .enumerate()
        .map_init(
            || (Matcher::new(Config::DEFAULT), Vec::<char>::new()),
            |(matcher, buf), (i, text)| {
                pattern.score(Utf32Str::new(text, buf), matcher).map(|s| (s, i))
            },
        )
        .flatten()
        .collect();
    scored.sort_unstable_by(|a, b| b.0.cmp(&a.0).then(a.1.cmp(&b.1)));
    scored.into_iter().take(limit).map(|(_, i)| index.ids[i].clone()).collect()
}

pub fn free(handle: u64) {
    if let Ok(mut r) = registry().lock() {
        r.remove(&handle);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ranks_fuzzy_matches() {
        let h = create(
            r#"[{"id":"a","text":"Magnetic ILLIT"},{"id":"b","text":"Capable of love PinkPantheress"},{"id":"c","text":"Where You Are Willow"}]"#,
        )
        .unwrap();
        assert_eq!(query(h, "magnt", 5).first().map(String::as_str), Some("a"));
        assert_eq!(query(h, "pink love", 5), vec!["b".to_string()]);
        free(h);
        assert!(query(h, "x", 5).is_empty());
    }
}
