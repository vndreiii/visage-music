# Xvfb capture harness for the Penpot mirror

Never run the app on the live Wayland session. These scripts start the AppImage on Xvfb `:77` with isolated XDG dirs
(a copy of `~/.config/visage`, dynamic theme off) and take cropped screenshots.

* `sess.sh PREFIX WxH "route1 route2 ..." [ENV=VAL]`: one session, writes each route to `VISAGE_DEV_NAVFILE`, shoots after `RW` seconds.
  `WAIT` = start delay, `CLICKS="x,y"` before the routes, `POSTCLICKS="x,y"` after the last route (`PW` s between),
  `LIGHT=1` / `NOBLUR=1` flip theme / blur prefs. Set `SC=<dir>` (needs `xdg/config`, `xdg/data`, `xdg/cache`, `shots/`).
* `live.sh start|shot|click|nav|stop`: one long-lived session driven step by step (search, play a song, open Now Playing).
* `edges.py img row|col FIXED LO HI [THRESH]`: pixel edge finder for measuring rects.

Rules learned: the app is slow on software GL: wait >=70 s after start, >=45 s after each route/click (110 s at 2560/3440);
a route written to the navfile only triggers when it changes; `VISAGE_DEV_ROUTE=search/...` renders a blank window, use the UI instead;
never run two sessions on `:77`; kill by PID / process group, never `pkill -f` with a pattern that appears in your own command.

Capture gotcha (found 2026-11-01): the app only navigates when the navfile content CHANGES, and `sess.sh` pre-writes the first route before launch, so a session whose first route is e.g. `settings` shows Home. Always lead with `home`: `"home settings"`, `"home charts_screen"`. Parallel sessions need distinct displays: `D=:78 SC=<own dir> ./sess.sh ...`.
Route names: charts_screen, new_release, mood_and_genres, search_input, library, settings/<appearance|appearance/theme|player|account|song_swap|downloads|local_offline|diagnostics|keybinds|sidebars|lyrics|fullscreen_player|now_playing>.
