import re,glob,os,sys
# Run from anywhere: always operate in the compose package dir.
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','composeApp','src','desktopMain','kotlin','com','music','vivi','compose'))
ALL=''
for root,_,fs in os.walk('.'):
    for f in fs:
        if f.endswith('.kt'): ALL+=open(os.path.join(root,f)).read()+'\n'
def enum_labels(name):
    out=[]
    for m in re.finditer(r'fun '+name+r'\.\w+\(\)[^=\n]*=\s*when\s*(?:\(\w*\))?\s*\{(.*?)\n\}',ALL,re.S):
        out+=re.findall(r'->\s*"([^"$]{1,40})"',m.group(1))
    m=re.search(r'enum class '+name+r'\b[^{]*\{(.*?)(?:;|\n\})',ALL,re.S)
    if m: out+=re.findall(r'\w+\("([^"$]{1,40})"',m.group(1))
    return out
def options_in(seg):
    seg=seg[:2500]
    # stop at the next Settings* call that is not part of this row
    out=re.findall(r'\bto "([^"$]{1,40})"',seg)
    for m in re.finditer(r'(\w+)\.entries',seg):
        out+=enum_labels(m.group(1))
    seen=[];[seen.append(x) for x in out if x not in seen]
    return seen
files={'ui/settings/SettingsScreens.kt':None}
for f in glob.glob('ui/settings/*.kt'): files[f]=None
fnroute={
'ThemeSettingsScreen':('SETTINGS_THEME','Theme'),
'AppearanceSettingsScreen':('SETTINGS_APPEARANCE','Interface'),
'NowPlayingSettingsScreen':('SETTINGS_NOW_PLAYING','Now Playing'),
'FullscreenPlayerSettingsScreen':('SETTINGS_FULLSCREEN_PLAYER','Fullscreen player'),
'PlayerSettingsScreen':('SETTINGS_PLAYER','Playback & audio'),
'DownloadsSettingsScreen':('SETTINGS_DOWNLOADS','Downloads'),
'AccountSettingsScreen':('SETTINGS_ACCOUNT','Account'),
'LyricsSettingsScreen':('SETTINGS_LYRICS','Lyrics'),
'TranslateLanguageRow':('SETTINGS_LYRICS','Lyrics'),
'LyricsAiProviderSettings':('SETTINGS_LYRICS','Lyrics'),
'ProviderFields':('SETTINGS_LYRICS','Lyrics'),
'SongSwapSettingsScreen':('SETTINGS_SONG_SWAP','Song swappings'),
'LocalOfflineSettingsScreen':('SETTINGS_LOCAL_OFFLINE','Local files'),
'DiagnosticsScreen':('SETTINGS_DIAGNOSTICS','Diagnostics'),
'KeybindsSettingsScreen':('SETTINGS_KEYBINDS','Keyboard shortcuts'),
'SidebarsSettingsScreen':('SETTINGS_SIDEBARS','Sidebars'),
'SettingsHubScreen':('HUB','Settings'),
'RingsControls':('SETTINGS_APPEARANCE','Interface'),
'InterfaceLinkItems':('HUB','Settings'),
}
POSITIONAL=re.compile(r'\b(?:SwitchItem|ChipChoiceRow|ChoiceRow|SettingsDropdownRow|LiveSlider|SettingsRowFrame|Item)\(\s*(?:(?:Icons\.\w+\.\w+|null|\w+)\s*,\s*)?"((?:[^"\\]|\\.)*)"(?:\s*,\s*"((?:[^"\\]|\\.)*)")?')
str_=r'"((?:[^"\\]|\\.)*)"'
# Every settings screen must be mapped, otherwise its settings silently go missing from search.
_missing=[]
for f in files:
    for m in re.finditer(r'^fun (\w+Screen)\(\s*navController',open(f).read(),re.M):
        if m.group(1) not in fnroute: _missing.append(m.group(1))
if _missing:
    sys.exit('gen-settings-index: add these screens to fnroute (route, page name): '+', '.join(_missing))

out=[];seen=set()
for f in files:
    s=open(f).read()
    # split by top-level functions
    parts=[(m.start(),m.group(1)) for m in re.finditer(r'^(?:private |internal )?fun (?:<T> )?(\w+)\(',s,re.M)]
    parts.append((len(s),None))
    for (a,name),(b,_) in zip(parts,parts[1:]):
        if name not in fnroute: continue
        route,page=fnroute[name]
        body=s[a:b]
        for m in re.finditer(r'SettingsSection\('+str_,body):
            if route!='HUB': out.append((m.group(1),'',page,route,True,[]))
        ms=sorted(list(re.finditer(r'\btitle = '+str_,body))+list(re.finditer(POSITIONAL,body)),key=lambda m:m.start())
        for i,m in enumerate(ms):
            end=ms[i+1].start() if i+1<len(ms) else len(body)
            seg=body[m.end():end]
            t=m.group(1)
            if '$' in t or t in('Settings','Every') : continue
            if route!='HUB' and t==page: continue
            opts=options_in(seg[:seg.find('SettingsRowFrame')] if False else seg) if re.match(r'.{0,400}?(options =)',seg,re.S) else []
            sub=re.search(r'subtitle = '+str_,seg)
            sub=sub.group(1) if sub and '$' not in sub.group(1) else ''
            if not sub and m.re is POSITIONAL and m.group(2) and '$' not in m.group(2): sub=m.group(2)
            r=route;pg=page
            if route=='HUB':
                n=re.search(r'Routes\.(\w+)',seg)
                if not n: continue
                r=n.group(1)
                pg='Settings'
            out.append((t,sub,pg,r,False,opts))
src=open('ui/settings/SettingsScreens.kt').read()
for m in re.finditer(r'InterfaceLink\(Icons\.Outlined\.\w+, '+str_+', '+str_+', Routes\.(\w+)\)',src):
    out.append((m.group(1),m.group(2),'Settings',m.group(3),False,[]))
res=[];ks=set()
for t,sub,pg,r,sec,opts in out:
    k=(t,r)
    if k in ks: continue
    ks.add(k);res.append((t,sub,pg,r,sec,opts))
def esc(x): return x.replace('$','\\$')
lines=['package com.music.vivi.compose.ui.settings','',
'import com.music.vivi.compose.navigation.Routes','',
'/** One searchable setting. Generated from the settings screens by `tools/gen-settings-index.py`; edit the screens, then re-run it. */',
'class SettingsEntry(val title: String, val subtitle: String, val page: String, val route: String, val isSection: Boolean = false, val options: String = "")','',
'internal val SettingsIndex: List<SettingsEntry> = listOf(']
for t,sub,pg,r,sec,opts in res:
    lines.append(f'    SettingsEntry("{esc(t)}", "{esc(sub)}", "{esc(pg)}", Routes.{r}, {"true" if sec else "false"}, "{esc(" · ".join(opts))}"),')
lines.append(')')
new='\n'.join(lines)+'\n'
if not os.path.exists('ui/settings/SettingsIndex.kt') or open('ui/settings/SettingsIndex.kt').read()!=new:
    open('ui/settings/SettingsIndex.kt','w').write(new)
print(len(res))
