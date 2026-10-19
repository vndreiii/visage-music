//! MPRIS is a D-Bus API, so it exists on Unix only. These stubs keep the C ABI the same on Windows:
//! `start` returns 0, which the JVM treats as "no media-session service".

use std::ffi::c_char;

pub type Callback = extern "C" fn(i32, f64, *const c_char);

pub fn start(_cb: Callback) -> u64 {
    0
}

pub fn name(_handle: u64) -> Option<String> {
    None
}

pub fn update(_handle: u64, _json: &str) {}

pub fn stop(_handle: u64) {}
