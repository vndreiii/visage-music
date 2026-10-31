# Now Playing popup (mini player window)

Opened from the Now Playing tool row ("Pop out mini player"), or `Ctrl+Shift+M` (rebindable: Settings -> Keyboard
shortcuts -> Player views). It is a second Compose `Window` in the same `application {}`, sharing the one
`PlaybackController`. Closing it only hides it; closing the main window exits the app and takes it down.

Layout is aspect driven (`PopupGeometry.modeFor`): portrait/square shows art, title/artist/album, seek bar, prev /
play-pause / next and a volume slider. A window at least 520 px wide and 1.3x wider than tall switches to landscape: a fixed-width player column on the left
(46% of the width, capped 264..330 dp: cover, title/artist/album, seek, transport, volume) and the lyrics (the same
`LyricsPanel` Now Playing uses) filling the entire right side, top to bottom. In the popup the lyrics toolbar and delay
pill fade out after ~1.2 s and show only while the pointer is over the lyrics. Below 430 px of height the album line
and volume row drop out so art keeps room (the cover shrinks first). The art + info block is passed through a
`dragArea` hook so the host window can make it the drag surface. Size is clamped to 280x340 .. 1100x900.

Code: `composeApp/.../ui/popup/` (`PopupGeometry` pure logic, `PopupPlayerLayout` slots, `PopupPlayerContent`,
`PopupPlayerWindow`, `WindowsPopupChrome`). Hooks elsewhere: one line in `alex.kt`, one enum entry + one branch in the
shortcut system, one button in `NowPlayingScreen`.

## Linux

### What the protocols give us

| Need | Wayland (native) | X11 / XWayland |
| --- | --- | --- |
| min / max size | `xdg_toplevel.set_min_size/set_max_size` (honoured by all three compositors) | `WM_NORMAL_HINTS` |
| keep above | **No protocol.** `xdg-toplevel` has no "always on top". Compositor-side only (rules / user action). | `_NET_WM_STATE_ABOVE` |
| popup / dialog hint | `xdg-dialog-v1` (modal-ness + "this is a dialog" for a parent); wrong for an independent mini player | `_NET_WM_WINDOW_TYPE_UTILITY`, `_NET_WM_STATE_SKIP_TASKBAR` |
| stable identity for rules | `app_id` + title (per process; `xdg-toplevel-tag-v1` adds a persistent per-window tag, new, not in the JDK) | `WM_CLASS` (per window) + title |
| layer-shell (`wlr-layer-shell`) | Wrong tool: it is for panels/wallpapers/OSDs, takes no normal window management (no move/resize by the user, no focus semantics), and the JDK cannot create layer surfaces | n/a |
| picture-in-picture | No generic protocol; browsers just spawn a small floating toplevel and rely on compositor rules | same |

Conclusion (and what the user asked for): on Wayland just spawn an ordinary toplevel with restricted size, a stable
title and class, and let compositor rules do float/pin/placement. Do not try layer-shell.

### What Compose Desktop / the JDK can do here

The app runs on Temurin/JBR 21 with the **X11 toolkit (XWayland under a Wayland session)**; JBR's native
`WLToolkit` is not used. So everything below describes XWayland unless said otherwise.

* `Window(undecorated = true, alwaysOnTop = ...)` maps to `_NET_WM_STATE_ABOVE` via `setAlwaysOnTop`. Tested on a nested
  Hyprland (0.56): **Hyprland ignores it** (`pinned: 0`). KWin honours keep-above for X11 clients; GNOME/mutter
  honours it for X11 clients too. On native Wayland `setAlwaysOnTop` is a no-op everywhere. So the in-window pin button
  is a hint, and the real guarantee on Hyprland is the `pin` window rule below.
* `window.minimumSize` reaches the WM (`WM_NORMAL_HINTS` min size). `maximumSize` does **not** (verified with `xprop`:
  only min is set), so the popup also clamps in a `ComponentListener` (works: a 1200x600 request ends at 1100x600).
* WM_CLASS: `awt.appClassName` is process-wide. The app already sets it to `visage` via reflection on
  `XToolkit.awtAppClassName`; the popup swaps that field to `visage-popup` just before its native window is created
  and restores it, so the popup gets its own class (`visage` main, `visage-popup` popup, verified in Hyprland and
  with `xprop`). Under a native Wayland toolkit there is one `app_id` per process, so rules must match the title
  (`VIsage Mini Player`) instead.
* Undecorated windows have no frame to resize with, so the popup draws its own bottom-right resize grip (clamped), plus a
  top drag handle (`WindowDraggableArea`). Compositor drags (Hyprland `Super+RMB`) still work.
* The popup is `transparent = true` so the Compose rounded clip shows (otherwise white corners under XWayland).
* Wayland clients cannot position themselves, so the remembered x/y is stored and applied only on X11 / Windows; size is
  always remembered. A remembered position is re-validated against the current screens (`PopupPlacement.restore`).

### Hyprland rules (recommended)

```
windowrule = float on, match:class ^(visage-popup)$, match:title ^(VIsage Mini Player)$
windowrule = pin on,   match:class ^(visage-popup)$, match:title ^(VIsage Mini Player)$
windowrule = size 340 480, match:class ^(visage-popup)$, match:title ^(VIsage Mini Player)$
windowrule = move 100%-360 100%-500, match:class ^(visage-popup)$, match:title ^(VIsage Mini Player)$
windowrule = no_initial_focus on, match:class ^(visage-popup)$, match:title ^(VIsage Mini Player)$
```
(Hyprland >= 0.53 `windowrule` syntax; older: `windowrulev2 = float, class:^(visage-popup)$, title:^(VIsage Mini Player)$`.
Verified `float`, `pin`, `size`, `move` apply with this snippet. Lua config: `hl.window_rule({ match = { class = ... }, float = true, pin = true })`.)
Native-Wayland toolkit: replace the class match with `match:class ^(visage)$`.

### KDE / GNOME / X11

* KDE: right-click title -> More Actions -> Keep Above, or a Window Rule on title "VIsage Mini Player" (Force: Keep above,
  Skip taskbar/pager). X11 `_NET_WM_STATE_ABOVE` from the pin button works too.
* GNOME: no rules; the pin button works on X11 windows (XWayland). Native Wayland GNOME has no way for an app to stay
  on top; use the Extensions "Always on Top" shortcut.
* Plain X11: `_NET_WM_STATE_ABOVE` via `setAlwaysOnTop` is the proper mechanism and is honoured by essentially every WM.

## Windows (UNTESTED ON WINDOWS)

Implemented in `WindowsPopupChrome` (JNA, `HostOs.isWindows` guarded, every call wrapped in `runCatching`). Nothing in
this section has been run on Windows: the author had no Windows host. Unit tests cover only the pure parts
(`WinExStyle`, geometry).

* Window: undecorated, `transparent = true` + Compose rounded clip (same approach as the main window, which already
  ships this on Windows), `alwaysOnTop = pinned` -> AWT calls `SetWindowPos(HWND_TOPMOST)`.
* Rounded corners: `DwmSetWindowAttribute(DWMWA_WINDOW_CORNER_PREFERENCE=33, DWMWCP_ROUND=2)`, Windows 11 only (older
  builds return an error that is ignored). Redundant with the transparent clip but correct if transparency is ever off.
* Skip taskbar: `WS_EX_TOOLWINDOW` set, `WS_EX_APPWINDOW` cleared, then `SetWindowPos(... SWP_FRAMECHANGED)`. Preference
  key `popupPlayerSkipTaskbar` (default false: it keeps an Alt+Tab entry and taskbar button so it cannot get lost). No
  settings UI yet.
* Drag: the header calls `ReleaseCapture()` + `SendMessage(WM_NCLBUTTONDOWN, HTCAPTION)` so the OS runs the move loop
  (Aero Snap, edge tiling, per-monitor DPI handled by Windows). On failure the Compose-side drag is not used on Windows
  (the call just returns false); non-Windows uses `WindowDraggableArea`.
* Min/max size: AWT `minimumSize` maps to `WM_GETMINMAXINFO`; max is additionally clamped in the resize listener.
* Position/size are persisted (`popupPlayerBounds` = `x,y,w,h`), clamped to the current screens on restore (multi-monitor,
  changed scaling). AWT coordinates are in logical pixels, so it survives DPI changes.
* Known risk to check on a real machine: SendMessage-based drag with a transparent layered window; DWM corner call before
  the HWND is realised (it runs in a `DisposableEffect` on the first composition; if it returns an error nothing breaks).

## Testing done

Layout switch: `PopupLayoutUiTest` (`runComposeUiTest`) + `PopupGeometryTest`. Live: Xvfb (class, min hint, no WM) and a
nested Hyprland 0.56 run inside a headless `kwin_wayland --virtual` (no physical output is ever touched; Hyprland itself
cannot run headless without a DRM seat). Verified: popup spawns floating at 340x480 with the rules, min 280x340 enforced by
the compositor, max 1100 by the app, stack at 340x480 and 280x340, lyrics layout at 760x420, play/pause and close
buttons, `Ctrl+Shift+M` toggle, main window close exits with the popup open, per-window WM_CLASS.
