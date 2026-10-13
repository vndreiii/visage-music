//! C-ABI helpers loaded by the JVM through JNA. Every call takes and returns UTF-8 JSON
//! (`*mut c_char`, release with [`vivi_free_string`]) so the boundary stays tiny and the
//! Kotlin side can fall back to its pure-JVM implementation when this library is absent.

mod canvas;
mod mpris;
mod scan;
mod search;

use std::ffi::{c_char, CStr, CString};

pub(crate) fn into_c(s: String) -> *mut c_char {
    CString::new(s.replace('\0', "")).map(CString::into_raw).unwrap_or(std::ptr::null_mut())
}

pub(crate) unsafe fn from_c<'a>(p: *const c_char) -> Option<&'a str> {
    if p.is_null() {
        return None;
    }
    CStr::from_ptr(p).to_str().ok()
}

#[no_mangle]
pub extern "C" fn vivi_native_version() -> *mut c_char {
    into_c(env!("CARGO_PKG_VERSION").to_string())
}

/// # Safety
/// `p` must come from this library and not have been freed.
#[no_mangle]
pub unsafe extern "C" fn vivi_free_string(p: *mut c_char) {
    if !p.is_null() {
        drop(CString::from_raw(p));
    }
}

/// Scan request JSON: `{roots, covers_dir, cache_path, min_bytes}` → `{tracks, stats}` JSON.
///
/// # Safety
/// `request` must be a valid NUL-terminated string.
#[no_mangle]
pub unsafe extern "C" fn vivi_scan(request: *const c_char) -> *mut c_char {
    let out = match from_c(request).map(scan::run) {
        Some(Ok(json)) => json,
        Some(Err(e)) => format!(r#"{{"error":{}}}"#, serde_json::to_string(&e).unwrap_or_default()),
        None => r#"{"error":"bad request"}"#.to_string(),
    };
    into_c(out)
}

/// Build a fuzzy index from `[{"id": "...", "text": "..."}]`; returns an opaque handle (0 = failure).
///
/// # Safety
/// `items` must be a valid NUL-terminated string.
#[no_mangle]
pub unsafe extern "C" fn vivi_search_new(items: *const c_char) -> u64 {
    from_c(items).and_then(search::create).unwrap_or(0)
}

/// Query an index; returns a JSON array of ids ordered best-first.
///
/// # Safety
/// `query` must be a valid NUL-terminated string.
#[no_mangle]
pub unsafe extern "C" fn vivi_search_query(handle: u64, query: *const c_char, limit: u32) -> *mut c_char {
    let ids = from_c(query).map(|q| search::query(handle, q, limit as usize)).unwrap_or_default();
    into_c(serde_json::to_string(&ids).unwrap_or_else(|_| "[]".into()))
}

#[no_mangle]
pub extern "C" fn vivi_search_free(handle: u64) {
    search::free(handle);
}

/// Start the MPRIS service. `callback(cmd, number, text)` receives remote commands
/// (see `mpris::CMD_*`) on a D-Bus thread. Returns a handle, 0 on failure.
#[no_mangle]
pub extern "C" fn vivi_mpris_start(callback: mpris::Callback) -> u64 {
    mpris::start(callback)
}

/// Bus name the service ended up with (release with `vivi_free_string`).
#[no_mangle]
pub extern "C" fn vivi_mpris_name(handle: u64) -> *mut c_char {
    mpris::name(handle).map(into_c).unwrap_or(std::ptr::null_mut())
}

/// Push a partial state update: `{status, position_us, volume, shuffle, loop_status, metadata}`.
///
/// # Safety
/// `json` must be a valid NUL-terminated string.
#[no_mangle]
pub unsafe extern "C" fn vivi_mpris_update(handle: u64, json: *const c_char) {
    if let Some(j) = from_c(json) {
        mpris::update(handle, j);
    }
}

#[no_mangle]
pub extern "C" fn vivi_mpris_stop(handle: u64) {
    mpris::stop(handle);
}

/// Resolve motion artwork: `{title, artist, album, source, storefront, cache_dir}` →
/// CanvasArtwork JSON, or the string `null` when nothing was found. Blocks (network), call off the UI thread.
///
/// # Safety
/// `request` must be a valid NUL-terminated string.
#[no_mangle]
pub unsafe extern "C" fn vivi_canvas_resolve(request: *const c_char) -> *mut c_char {
    let out = from_c(request)
        .and_then(|j| serde_json::from_str::<canvas::Request>(j).ok())
        .map(|r| canvas::resolve(&r))
        .and_then(|a| serde_json::to_string(&a).ok())
        .unwrap_or_else(|| "null".to_string());
    into_c(out)
}
