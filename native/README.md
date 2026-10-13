# vivi-native

Optional Rust helpers for VIVI Desktop, loaded by the JVM through JNA
(`database/.../NativeHelpers.kt`). If the library is missing the app uses its pure-Kotlin paths.

| Call | What it does |
|------|--------------|
| `vivi_scan` | Parallel directory walk (jwalk) + tag/cover extraction (lofty) with an incremental on-disk cache keyed by path/size/mtime. Re-scans of an unchanged library take ~0.2 s. Files whose tags can't be read are flagged so Kotlin falls back to Jaudiotagger. |
| `vivi_search_new/query/free` | In-memory fuzzy index (nucleo) over `(id, text)` pairs, used for "On this device" results in Search. |

```bash
cargo build --release --manifest-path native/Cargo.toml   # run-dev.sh does this automatically
cargo test --manifest-path native/Cargo.toml
```

Library lookup order: `$VIVI_NATIVE_LIB`, `native/target/release/libvivi_native.so`, system path.
Set `VIVI_NATIVE_DISABLE=1` to force the JVM fallback. Cover files use the same SHA-1 naming as the Kotlin scanner.
