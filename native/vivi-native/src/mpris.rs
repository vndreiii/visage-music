//! MPRIS2 service (org.mpris.MediaPlayer2 + .Player) on the session bus, built on zbus.
//!
//! The JVM pushes state with `vivi_mpris_update` (partial JSON) and receives remote commands
//! through one callback. dbus-java never obtained its well-known name here, so playerctl and
//! shell widgets could not see the player; zbus does this reliably.

use serde::Deserialize;
use std::collections::HashMap;
use std::ffi::{c_char, CString};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use zbus::blocking::{connection::Builder, Connection};
use zbus::interface;
use zbus::zvariant::{ObjectPath, OwnedValue, Value};

const PATH: &str = "/org/mpris/MediaPlayer2";
const IFACE_PLAYER: &str = "org.mpris.MediaPlayer2.Player";

/// Commands delivered to the JVM: `(cmd, number, text)`.
pub type Callback = extern "C" fn(i32, f64, *const c_char);
pub const CMD_PLAY: i32 = 1;
pub const CMD_PAUSE: i32 = 2;
pub const CMD_PLAY_PAUSE: i32 = 3;
pub const CMD_STOP: i32 = 4;
pub const CMD_NEXT: i32 = 5;
pub const CMD_PREVIOUS: i32 = 6;
pub const CMD_SEEK_REL_US: i32 = 7;
pub const CMD_SET_POSITION_US: i32 = 8;
pub const CMD_VOLUME: i32 = 9;
pub const CMD_SHUFFLE: i32 = 10;
pub const CMD_LOOP: i32 = 11;
pub const CMD_RAISE: i32 = 12;
pub const CMD_OPEN_URI: i32 = 13;

#[derive(Deserialize, Clone, Default)]
struct Meta {
    track_id: String,
    title: Option<String>,
    artist: Option<String>,
    album: Option<String>,
    length_us: Option<i64>,
    art_url: Option<String>,
    rating: Option<f64>,
}

#[derive(Deserialize, Default)]
struct Update {
    status: Option<String>,
    position_us: Option<i64>,
    volume: Option<f64>,
    shuffle: Option<bool>,
    loop_status: Option<String>,
    metadata: Option<Meta>,
}

struct State {
    status: String,
    position_us: i64,
    volume: f64,
    shuffle: bool,
    loop_status: String,
    meta: Meta,
}

type Shared = Arc<Mutex<State>>;

fn object_path_for(id: &str) -> String {
    let clean: String = id.chars().map(|c| if c.is_ascii_alphanumeric() { c } else { '_' }).collect();
    format!("/org/vivi/track/{}", if clean.is_empty() { "none".into() } else { clean })
}

fn metadata_map(m: &Meta) -> HashMap<String, OwnedValue> {
    let mut out: HashMap<String, OwnedValue> = HashMap::new();
    let mut put = |k: &str, v: Value<'_>| {
        if let Ok(o) = v.try_to_owned() {
            out.insert(k.to_owned(), o);
        }
    };
    if let Ok(p) = ObjectPath::try_from(object_path_for(&m.track_id)) {
        put("mpris:trackid", Value::ObjectPath(p));
    }
    if let Some(t) = &m.title {
        put("xesam:title", Value::from(t.clone()));
    }
    if let Some(a) = &m.artist {
        put("xesam:artist", Value::from(vec![a.clone()]));
    }
    if let Some(a) = &m.album {
        put("xesam:album", Value::from(a.clone()));
    }
    if let Some(l) = m.length_us {
        put("mpris:length", Value::from(l));
    }
    if let Some(u) = &m.art_url {
        put("mpris:artUrl", Value::from(u.clone()));
    }
    if let Some(r) = m.rating {
        put("xesam:userRating", Value::from(r));
    }
    out
}

fn send(cb: Callback, cmd: i32, num: f64, text: &str) {
    let c = CString::new(text.replace('\0', "")).unwrap_or_default();
    cb(cmd, num, c.as_ptr());
}

struct Root {
    cb: Callback,
}

#[interface(name = "org.mpris.MediaPlayer2")]
impl Root {
    fn raise(&self) {
        send(self.cb, CMD_RAISE, 0.0, "");
    }
    fn quit(&self) {}
    #[zbus(property)]
    fn can_quit(&self) -> bool {
        false
    }
    #[zbus(property)]
    fn can_raise(&self) -> bool {
        true
    }
    #[zbus(property)]
    fn can_set_fullscreen(&self) -> bool {
        false
    }
    #[zbus(property)]
    fn fullscreen(&self) -> bool {
        false
    }
    #[zbus(property)]
    fn has_track_list(&self) -> bool {
        false
    }
    #[zbus(property)]
    fn identity(&self) -> String {
        "VIVI Desktop".into()
    }
    #[zbus(property)]
    fn desktop_entry(&self) -> String {
        "vivi-desktop".into()
    }
    #[zbus(property)]
    fn supported_uri_schemes(&self) -> Vec<String> {
        vec!["https".into(), "http".into()]
    }
    #[zbus(property)]
    fn supported_mime_types(&self) -> Vec<String> {
        vec!["audio/mpeg".into(), "audio/mp4".into(), "audio/ogg".into()]
    }
}

struct Player {
    cb: Callback,
    state: Shared,
}

impl Player {
    fn with<R>(&self, f: impl FnOnce(&State) -> R) -> R {
        f(&self.state.lock().unwrap())
    }
}

#[interface(name = "org.mpris.MediaPlayer2.Player")]
impl Player {
    fn next(&self) {
        send(self.cb, CMD_NEXT, 0.0, "");
    }
    fn previous(&self) {
        send(self.cb, CMD_PREVIOUS, 0.0, "");
    }
    fn pause(&self) {
        send(self.cb, CMD_PAUSE, 0.0, "");
    }
    fn play_pause(&self) {
        send(self.cb, CMD_PLAY_PAUSE, 0.0, "");
    }
    fn stop(&self) {
        send(self.cb, CMD_STOP, 0.0, "");
    }
    fn play(&self) {
        send(self.cb, CMD_PLAY, 0.0, "");
    }
    fn seek(&self, offset: i64) {
        send(self.cb, CMD_SEEK_REL_US, offset as f64, "");
    }
    fn set_position(&self, _track_id: ObjectPath<'_>, position: i64) {
        send(self.cb, CMD_SET_POSITION_US, position as f64, "");
    }
    fn open_uri(&self, uri: String) {
        send(self.cb, CMD_OPEN_URI, 0.0, &uri);
    }

    #[zbus(property)]
    fn playback_status(&self) -> String {
        self.with(|s| s.status.clone())
    }
    #[zbus(property)]
    fn loop_status(&self) -> String {
        self.with(|s| s.loop_status.clone())
    }
    #[zbus(property)]
    fn set_loop_status(&mut self, value: String) {
        self.state.lock().unwrap().loop_status = value.clone();
        send(self.cb, CMD_LOOP, 0.0, &value);
    }
    #[zbus(property)]
    fn rate(&self) -> f64 {
        1.0
    }
    #[zbus(property)]
    fn set_rate(&mut self, _value: f64) {}
    #[zbus(property)]
    fn shuffle(&self) -> bool {
        self.with(|s| s.shuffle)
    }
    #[zbus(property)]
    fn set_shuffle(&mut self, value: bool) {
        self.state.lock().unwrap().shuffle = value;
        send(self.cb, CMD_SHUFFLE, if value { 1.0 } else { 0.0 }, "");
    }
    #[zbus(property)]
    fn metadata(&self) -> HashMap<String, OwnedValue> {
        self.with(|s| metadata_map(&s.meta))
    }
    #[zbus(property)]
    fn volume(&self) -> f64 {
        self.with(|s| s.volume)
    }
    #[zbus(property)]
    fn set_volume(&mut self, value: f64) {
        let v = value.clamp(0.0, 1.0);
        self.state.lock().unwrap().volume = v;
        send(self.cb, CMD_VOLUME, v, "");
    }
    #[zbus(property(emits_changed_signal = "false"))]
    fn position(&self) -> i64 {
        self.with(|s| s.position_us)
    }
    #[zbus(property)]
    fn minimum_rate(&self) -> f64 {
        1.0
    }
    #[zbus(property)]
    fn maximum_rate(&self) -> f64 {
        1.0
    }
    #[zbus(property)]
    fn can_go_next(&self) -> bool {
        true
    }
    #[zbus(property)]
    fn can_go_previous(&self) -> bool {
        true
    }
    #[zbus(property)]
    fn can_play(&self) -> bool {
        true
    }
    #[zbus(property)]
    fn can_pause(&self) -> bool {
        true
    }
    #[zbus(property)]
    fn can_seek(&self) -> bool {
        true
    }
    #[zbus(property)]
    fn can_control(&self) -> bool {
        true
    }
}

struct Instance {
    conn: Connection,
    state: Shared,
    name: String,
}

static NEXT: AtomicU64 = AtomicU64::new(1);

fn instances() -> &'static Mutex<HashMap<u64, Arc<Instance>>> {
    static R: OnceLock<Mutex<HashMap<u64, Arc<Instance>>>> = OnceLock::new();
    R.get_or_init(Default::default)
}

fn build(name: &str, cb: Callback, state: &Shared) -> zbus::Result<Connection> {
    Builder::session()?
        .name(name.to_owned())?
        .serve_at(PATH, Root { cb })?
        .serve_at(PATH, Player { cb, state: state.clone() })?
        .build()
}

/// Returns a handle (0 on failure). Tries `org.mpris.MediaPlayer2.vivi`, then a per-pid name.
pub fn start(cb: Callback) -> u64 {
    let state: Shared = Arc::new(Mutex::new(State {
        status: "Stopped".into(),
        position_us: 0,
        volume: 1.0,
        shuffle: false,
        loop_status: "None".into(),
        meta: Meta { track_id: "none".into(), ..Default::default() },
    }));
    let primary = "org.mpris.MediaPlayer2.vivi".to_string();
    let fallback = format!("org.mpris.MediaPlayer2.vivi.instance{}", std::process::id());
    let (conn, name) = match build(&primary, cb, &state) {
        Ok(c) => (c, primary),
        Err(_) => match build(&fallback, cb, &state) {
            Ok(c) => (c, fallback),
            Err(e) => {
                eprintln!("vivi-native mpris: {e}");
                return 0;
            }
        },
    };
    let handle = NEXT.fetch_add(1, Ordering::Relaxed);
    instances().lock().unwrap().insert(handle, Arc::new(Instance { conn, state, name }));
    handle
}

pub fn name(handle: u64) -> Option<String> {
    instances().lock().ok()?.get(&handle).map(|i| i.name.clone())
}

pub fn update(handle: u64, json: &str) {
    let Some(inst) = instances().lock().ok().and_then(|m| m.get(&handle).cloned()) else { return };
    let Ok(u) = serde_json::from_str::<Update>(json) else { return };
    let mut changed: HashMap<&str, Value<'_>> = HashMap::new();
    {
        let mut s = inst.state.lock().unwrap();
        if let Some(v) = u.status {
            if v != s.status {
                changed.insert("PlaybackStatus", Value::from(v.clone()));
                s.status = v;
            }
        }
        if let Some(v) = u.position_us {
            s.position_us = v; // Position never emits PropertiesChanged per the spec
        }
        if let Some(v) = u.volume {
            if (v - s.volume).abs() > f64::EPSILON {
                s.volume = v;
                changed.insert("Volume", Value::from(v));
            }
        }
        if let Some(v) = u.shuffle {
            if v != s.shuffle {
                s.shuffle = v;
                changed.insert("Shuffle", Value::from(v));
            }
        }
        if let Some(v) = u.loop_status {
            if v != s.loop_status {
                changed.insert("LoopStatus", Value::from(v.clone()));
                s.loop_status = v;
            }
        }
        if let Some(m) = u.metadata {
            let map = metadata_map(&m);
            s.meta = m;
            changed.insert("Metadata", Value::from(map));
        }
    }
    if !changed.is_empty() {
        let _ = inst.conn.emit_signal(
            None::<&str>,
            PATH,
            "org.freedesktop.DBus.Properties",
            "PropertiesChanged",
            &(IFACE_PLAYER, changed, Vec::<&str>::new()),
        );
    }
}

pub fn stop(handle: u64) {
    if let Ok(mut m) = instances().lock() {
        m.remove(&handle);
    }
}
