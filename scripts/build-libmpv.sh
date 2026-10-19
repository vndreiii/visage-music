#!/usr/bin/env bash
# Build a small, self-contained libmpv (audio and software-rendered video) for Linux.
# VIsage downloads this bundle when the system has no libmpv (see LibmpvProvisioner).
#
# Output: dist/visage-libmpv-linux-<arch>.tar.gz  (folder "libmpv/": libmpv.so.2, libav*, libsw*, VERSION)
# Needs: a C compiler, git, meson, ninja, pkg-config, patchelf, python3, nasm (x86 only),
#        and the dev packages for OpenSSL, PulseAudio and ALSA.
# Build it on an old distribution (CI uses Ubuntu 22.04): the bundle runs on any glibc at least as new.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCH="$(uname -m)"
FFMPEG_TAG="${FFMPEG_TAG:-n7.1}"
MPV_TAG="${MPV_TAG:-v0.39.0}"
WORK="${WORK:-$ROOT/build/libmpv}"
PREFIX="$WORK/prefix"
OUT="$WORK/libmpv"
JOBS="$(nproc)"
mkdir -p "$WORK" "$ROOT/dist"
export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig:$PREFIX/lib64/pkgconfig:${PKG_CONFIG_PATH:-}"

clone() { # url tag dir
  [[ -d "$3/.git" ]] || git clone --depth 1 --branch "$2" "$1" "$3"
}

ASM_FLAG=()
command -v nasm >/dev/null 2>&1 || [[ "$ARCH" != x86_64 ]] || ASM_FLAG=(--disable-x86asm)

echo "== ffmpeg $FFMPEG_TAG"
clone https://git.ffmpeg.org/ffmpeg.git "$FFMPEG_TAG" "$WORK/ffmpeg"
(
  cd "$WORK/ffmpeg"
  ./configure --prefix="$PREFIX" --enable-shared --disable-static --enable-pic \
    --disable-programs --disable-doc --disable-debug --disable-autodetect \
    --disable-avdevice --disable-encoders --disable-muxers --disable-hwaccels --disable-devices \
    --enable-openssl --enable-version3 "${ASM_FLAG[@]}"
  make -j"$JOBS"
  make install
)

echo "== mpv $MPV_TAG"
clone https://github.com/mpv-player/mpv.git "$MPV_TAG" "$WORK/mpv"
(
  cd "$WORK/mpv"
  rm -rf "$WORK/mpv-build"
  meson setup "$WORK/mpv-build" --prefix="$PREFIX" --libdir=lib --buildtype=release \
    -Dauto_features=disabled -Dlibmpv=true -Dcplayer=false -Dbuild-date=false \
    -Dgl=disabled -Dlua=disabled -Dpulse=enabled -Dalsa=enabled
  meson compile -C "$WORK/mpv-build" -j "$JOBS"
  meson install -C "$WORK/mpv-build"
)

echo "== bundle"
rm -rf "$OUT" && mkdir -p "$OUT"
cp -a "$PREFIX"/lib/libmpv.so* "$PREFIX"/lib/libav{codec,format,util,filter}.so* \
      "$PREFIX"/lib/libsw{resample,scale}.so* "$OUT/"
# libass and libplacebo are mandatory in mpv, so bundle their shared-library closure. The C library and the
# audio stack (PulseAudio, ALSA and everything they pull in) stay with the system.
deps() { ldd "$1" | awk '/=> \//{print $3}' | sort -u; }
SYSTEM=$( { for l in libpulse.so.0 libasound.so.2; do p=$(ldconfig -p | awk -v l="$l" '$1==l{print $NF; exit}'); echo "$p"; deps "$p"; done; } | sort -u)
for lib in $(deps "$OUT/libmpv.so.2"); do
  case "$(basename "$lib")" in
    libc.so*|libm.so*|libdl.so*|libpthread.so*|librt.so*|libmvec.so*|libgcc_s.so*|libstdc++.so*|ld-linux*) continue ;;
  esac
  grep -qxF "$lib" <<<"$SYSTEM" && continue
  [[ "$lib" == "$OUT"/* ]] && continue
  cp -L "$lib" "$OUT/$(basename "$lib")"
done
for f in "$OUT"/*.so*; do
  [[ -L "$f" ]] && continue
  strip --strip-unneeded "$f"
  patchelf --set-rpath '$ORIGIN' "$f"
done
printf 'mpv %s\nffmpeg %s\nbuilt %s\n' "$MPV_TAG" "$FFMPEG_TAG" "$(date -u +%FT%TZ)" > "$OUT/VERSION"
ARCHIVE="$ROOT/dist/visage-libmpv-linux-$ARCH.tar.gz"
tar -C "$WORK" -czf "$ARCHIVE" libmpv
ls -lh "$ARCHIVE"
du -sh "$OUT"
echo "still needed from the system:"
ldd "$OUT/libmpv.so.2" | grep -v "$OUT" | awk '{print $1}' | tr '\n' ' '; echo
