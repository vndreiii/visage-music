# visage-native

Optional Rust helpers for VIsage, loaded by the JVM through JNA
(`database/.../NativeHelpers.kt`). If the library is missing the app uses its pure-Kotlin paths.

| Call | What it does |
|------|--------------|
| `visage_scan` | Parallel directory walk (jwalk) + tag/cover extraction (lofty) Callers pass the files they already have (path/size/mtime from the SQLite library); unchanged files are not re-read, so re-scans of an unchanged library take ~0.2 s. There is no separate cache file: SQLite is the single source of truth. Files whose tags can't be read are flagged so Kotlin falls back to Jaudiotagger. |
| `visage_search_new/query/free` | In-memory fuzzy index (nucleo) over `(id, text)` pairs, used for "On this device" results in Search. |
| `visage_mpris_*` | MPRIS2 service (zbus) — see below. |
| `visage_canvas_resolve` | Motion-artwork lookup: Apple Music (live token scrape, scoring port of upstream), Tidal, Vivi manifest — queried concurrently, results cached on disk. |
| `visage_artist_resolve` | Artist portrait/banner/bio: Deezer, TheAudioDB (test key) and Wikipedia queried concurrently for the primary credited artist; exact-name matches only; disk + memory cache (hits 7 d, misses 6 h), polite per-host spacing. |
| `visage_tags_read/write/restore` | Tag Manager file access via lofty (MP3 ID3v2.3/2.4, FLAC, Ogg/Opus, M4A, WAV/AIFF). Writes go to a temp copy next to the file, are re-read and verified, then atomically renamed over the original; read-only files are refused; an optional backup of the untouched original is copied first (used for Undo). |
| `visage_tags_lookup` | Metadata candidates from MusicBrainz (1 req/s, descriptive User-Agent; Cover Art Archive URLs derived from release ids), Deezer, iTunes Search and TheAudioDB, queried concurrently; AcoustID only when the caller passes a key and a chromaprint fingerprint (`fpcalc`). Scoring happens in Kotlin. |

```bash
cargo build --release --manifest-path native/Cargo.toml   # run-dev.sh does this automatically
cargo test --manifest-path native/Cargo.toml
```

Library lookup order: `$VISAGE_NATIVE_LIB` (absolute path; run-dev.sh sets it), the packaged resources dir, then the system path. The working directory is never searched.
Set `VISAGE_NATIVE_DISABLE=1` to force the JVM fallback. Cover files use the same SHA-1 naming as the Kotlin scanner.
