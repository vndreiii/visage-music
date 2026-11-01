#!/usr/bin/env bash
# live.sh start SIZE [ENV=VAL...] | shot NAME SIZE | click X Y | nav ROUTE | stop
D=:77; SC=${SC:-/tmp/visage-capture}
cmd=$1; shift
case $cmd in
start) SIZE=$1; shift
 pgrep -a Xvfb | grep -q ":77" || { Xvfb $D -screen 0 3600x1500x24 >/dev/null 2>&1 & sleep 2; }
 rm -rf $SC/xdg/config/visage && cp -r ~/.config/visage $SC/xdg/config/
 sed -i 's/^dynamicTheme=.*/dynamicTheme=false/' $SC/xdg/config/visage/settings.properties
 sed -i -e '/^bodyFont=/d' -e '/^headingFont=/d' -e '/^interfaceFont=/d' -e '/^paletteStyle=/d' -e '/^miniPlayerBackgroundStyle=/d' -e 's/^matugenTheme=.*/matugenTheme=false/' $SC/xdg/config/visage/settings.properties
 grep -q '^dynamicTheme' $SC/xdg/config/visage/settings.properties || echo 'dynamicTheme=false' >> $SC/xdg/config/visage/settings.properties
 : > $SC/nav_live.txt
 setsid env -u WAYLAND_DISPLAY -u HYPRLAND_INSTANCE_SIGNATURE -u XDG_SESSION_TYPE DISPLAY=$D GDK_BACKEND=x11 XDG_CONFIG_HOME=$SC/xdg/config XDG_DATA_HOME=$SC/xdg/data XDG_CACHE_HOME=$SC/xdg/cache DISABLE_LSFGVK=1 LC_NUMERIC=C VISAGE_WINDOW_SIZE=$SIZE VISAGE_DEV_NAVFILE=$SC/nav_live.txt "$@" /home/meh/Projects/vivi-desktop/dist/VIsage-x86_64.AppImage --appimage-extract-and-run > $SC/shots/live.log 2>&1 &
 echo $! > $SC/live.pid; echo started $!;;
shot) NAME=$1; SIZE=$2; for w in $(DISPLAY=$D xdotool search --onlyvisible --name . 2>/dev/null); do DISPLAY=$D xdotool windowmove $w 0 0 2>/dev/null; done; sleep 1
 import -display $D -window root -crop ${SIZE}+0+0 +repage $SC/shots/$NAME.png; echo $SC/shots/$NAME.png;;
click) DISPLAY=$D xdotool mousemove $1 $2 click 1; echo clicked;;
nav) echo "$1" > $SC/nav_live.txt; echo nav $1;;
stop) P=$(cat $SC/live.pid); kill -- -$P 2>/dev/null; sleep 2; for p in $(pgrep java); do d=$(tr '\0' '\n' < /proc/$p/environ 2>/dev/null | grep -E '^DISPLAY='); case "$d" in *:77) kill $p;; esac; done; echo stopped;;
esac
