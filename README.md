# vivi-desktop

Linux desktop port of [VIVI Music](https://github.com/vivizzz007/vivi-music).

## Status

- **Slice A backend:** `:innertube` + `:lyrics` (KMP/JVM) with fixture tests
- **Login shell:** `:desktop` JavaFX window (reusable Stage for future UI)
- **Secrets:** YT session + account profile stored via Freedesktop Secret Service (GNOME Keyring / KWallet)

Upstream Android clone may live in `vivi-music/` locally (gitignored).

## Run YT Music login

```bash
./gradlew :desktop:run
```

Sign in with Google in the WebView. When YouTube Music loads, press **Confirm login**. Credentials are saved in your system keyring under service `vivi-desktop` (`yt-music-session`, `yt-music-account`).

## Tests

```bash
./gradlew :innertube:jvmTest :lyrics:jvmTest
./gradlew :innertube:jvmTest :lyrics:jvmTest -PincludeNetworkTests
```
