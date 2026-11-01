#!/usr/bin/env bash
# usage: sess.sh PREFIX WxH "route1 route2 ..." [ENV=VAL ...]   Xvfb only, never the live session.
PFX=$1; SIZE=$2; ROUTES=$3; shift 3
D=${D:-:77}; SC=${SC:-/tmp/visage-capture}
pgrep -a Xvfb | grep -q "$D" || { Xvfb $D -screen 0 3600x1500x24 >/dev/null 2>&1 & sleep 2; }
rm -rf $SC/xdg/config/visage && cp -r ~/.config/visage $SC/xdg/config/
sed -i 's/^dynamicTheme=.*/dynamicTheme=false/' $SC/xdg/config/visage/settings.properties
sed -i -e '/^bodyFont=/d' -e '/^headingFont=/d' -e '/^interfaceFont=/d' -e '/^paletteStyle=/d' -e '/^miniPlayerBackgroundStyle=/d' -e 's/^matugenTheme=.*/matugenTheme=false/' $SC/xdg/config/visage/settings.properties
grep -q '^dynamicTheme' $SC/xdg/config/visage/settings.properties || echo 'dynamicTheme=false' >> $SC/xdg/config/visage/settings.properties
[ -n "$LIGHT" ] && sed -i 's/^darkMode=.*/darkMode=OFF/' $SC/xdg/config/visage/settings.properties
[ -n "$NOBLUR" ] && sed -i 's/^glassBlur=.*/glassBlur=false/' $SC/xdg/config/visage/settings.properties
NAV=$SC/nav_$PFX.txt; echo "${ROUTES%% *}" > $NAV
setsid env -u WAYLAND_DISPLAY -u HYPRLAND_INSTANCE_SIGNATURE -u XDG_SESSION_TYPE DISPLAY=$D GDK_BACKEND=x11 XDG_CONFIG_HOME=$SC/xdg/config XDG_DATA_HOME=$SC/xdg/data XDG_CACHE_HOME=$SC/xdg/cache DISABLE_LSFGVK=1 LC_NUMERIC=C VISAGE_WINDOW_SIZE=$SIZE VISAGE_DEV_NAVFILE=$NAV "$@" /home/meh/Projects/vivi-desktop/dist/VIsage-x86_64.AppImage --appimage-extract-and-run > $SC/shots/$PFX.log 2>&1 &
PID=$!
sleep ${WAIT:-75}
for w in $(DISPLAY=$D xdotool search --onlyvisible --name . 2>/dev/null); do DISPLAY=$D xdotool windowmove $w 0 0 2>/dev/null; done
for c in $CLICKS; do DISPLAY=$D xdotool mousemove ${c%,*} ${c#*,} click 1; sleep 8; done
for r in $ROUTES; do
  echo "$r" > $NAV; sleep ${RW:-16}
  for w in $(DISPLAY=$D xdotool search --onlyvisible --name . 2>/dev/null); do DISPLAY=$D xdotool windowmove $w 0 0 2>/dev/null; done
  sleep 2
  if [ -n "$POSTCLICKS" ] && [ "$r" = "${ROUTES##* }" ]; then for c in $POSTCLICKS; do DISPLAY=$D xdotool mousemove ${c%,*} ${c#*,} click 1; sleep ${PW:-12}; done; fi
  import -display $D -window root -crop ${SIZE}+0+0 +repage "$SC/shots/${PFX}__$(echo $r | tr '/{}?=&' '_____').png" 2>>$SC/shots/$PFX.log
done
kill -- -$PID 2>/dev/null; sleep 3
echo done $PFX
