#!/bin/sh
# Launcher for the VIsage Flatpak. Installed as /app/bin/visage.
set -eu

APP_HOME=/app/lib/visage

# Same environment the AppImage AppRun sets.
export LC_NUMERIC=C
export DISABLE_LSFGVK=1

# libmpv and the FFmpeg libs live in /app/lib (built by the ffmpeg/mpv modules).
export LD_LIBRARY_PATH="/app/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

# Optional Rust helper: copied into the Compose app resources dir when it was built.
native="$APP_HOME/lib/app/resources/libvisage_native.so"
if [ -f "$native" ]; then
  export VISAGE_NATIVE_LIB="$native"
fi

exec "$APP_HOME/bin/visage" "$@"
