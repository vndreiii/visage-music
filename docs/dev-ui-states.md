# Dev UI states (`VISAGE_UI_STATE`)

`VISAGE_UI_STATE` forces a UI state for a dev run, even when the real condition is false (for example the
Home login state while already signed in). It is read once at startup by
`composeApp/src/desktopMain/kotlin/com/music/vivi/compose/dev/DevUiState.kt`. Unset means nothing changes.
Forced states never write to prefs (no done flags, no "what's new seen" marker).

## Usage

```bash
VISAGE_UI_STATE=home-login ./run-dev.sh
VISAGE_UI_STATE=onboarding-tour ./run-dev.sh
VISAGE_UI_STATE=home-error,whats-new ./run-dev.sh      # several states at once, comma-separated
```

`run-dev.sh` ends with `exec ./gradlew ... :composeApp:run`, so the variable is inherited by Gradle and the
app process. Names are case-insensitive and whitespace around commas is ignored.

## States

| Name | What it shows | Real trigger | How to run |
|---|---|---|---|
| `home-login` | Home's signed-out hero: "Sign in to see your music", Log in with YouTube Music, Continue without an account | Home feed fails and there is no stored session, or the request was refused with an auth error (`HomeViewModel.needsLogin`) | `VISAGE_UI_STATE=home-login ./run-dev.sh` |
| `home-error` | Home's generic failure card: "Home didn't load" with Retry (no raw error text) | Home feed fails for any reason other than sign-in | `VISAGE_UI_STATE=home-error ./run-dev.sh` |
| `onboarding` | First-run flow from step 1 (sign-in step with "Skip for now"), ignoring the done flag | Fresh install: settings file did not exist at launch, so `onboarding.done` starts as `false` | `VISAGE_UI_STATE=onboarding ./run-dev.sh` |
| `onboarding-tour` | Jumps straight to the tour's first step (Home) | Same as above, after sign-in is done or skipped | `VISAGE_UI_STATE=onboarding-tour ./run-dev.sh` |
| `whats-new` | "What's new" popup for the newest release in `WhatsNewCatalog` | App updated to a version newer than `whatsnew.lastSeenVersion` | `VISAGE_UI_STATE=whats-new ./run-dev.sh` |
| `crash-dialog` | Crash dialog with a made-up "dev preview" report | An uncaught UI-thread exception (`CrashReporter.record`) | `VISAGE_UI_STATE=crash-dialog ./run-dev.sh` |

Not forced: the update prompt (`update/UpdatePrompt.kt`). It needs a real update offer from `UpdateService`,
so there is no cheap way to fake it yet.

Older variable still works: `VISAGE_WHATSNEW_PREVIEW=<version>` shows the popup for that version (it writes
nothing either, for dev runs without `visage.version`).

## Notes

- Settings → About → **Replay tour** re-opens the onboarding tour (a real action, not a forced state).
- Forced onboarding does not write `onboarding.done`, so a normal run afterwards behaves as before.
