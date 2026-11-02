# Loading wireframes (for Penpot)

Code: `composeApp/.../ui/component/PageSkeletons.kt` (`PageLoadingState(title, kind, header)`). One shared pulse
(`surfaceContainerHighest`, alpha 0.35-0.75). Every wireframe uses the real page's insets and sizes, so nothing jumps
when content arrives. Name Penpot frames `<Page> / <Breakpoint> / loading` and the layers after the shape names below.

| `PageSkeleton` | Used by | Shapes (dp) |
|---|---|---|
| `Home` | HomeScreen | avatar circle 48 + title bar 220x28; chip pills h40; Quick picks card r24 (20 inset; 1/2/3 columns by width <600/<1000/else; rows 48 at 58.5 pitch, cover 48 r12, bars 14/10); shelf of 168 cover tiles r16 |
| `List` | AutoPlaylist, Search, Library, Tag Manager, Charts body | optional DetailPageHeader (back circle 44, 14 gap, title bar 180x28; insets 20/8/16); rows 64 high, cover 48 r12, bars 16/12 |
| `Grid` | NewReleases, ArtistItems, Browse, Moods body | optional DetailPageHeader; adaptive cells min 180 (150 compact), gap 10, inset 20; cell = square cover r12 (6/4 inset) + bars 16/12 |
| `MediaSquare` | Album, Playlist | back circle at 20/8; glass card r28 (surfaceContainer 0.55, margin 12); banner h = max(min(w/3,420), cover) fading to transparent; cover = clamp(0.6w,180,340) r24 centred, half over the banner; title bar 220x36; subtitle 140x20; 3 chips h32; Play pill h48 (weight 1.5) + 2 circles 48; then rows with 12 dp padding |
| `MediaRound` | Artist | same card, portrait circle 140, Play pill + 1 circle |
| `Charts` | Charts | back circle + status text; podium (>=600 wide only): 3 cards r24 weights 1.15/1/1, gap 14, heights 260/232, bottom aligned; ranked rows (24 dp side padding, 12 when narrow) |

`header = false` is used where the page draws its own header above the loading body (Browse, Moods, Tag Manager, Library, Search).
Podium card heights and Home shelf tile size are approximations, not measured from a capture.
Preview PNGs: `SKELETON_PNG_DIR=<dir> ./gradlew :composeApp:desktopTest --tests '*PageSkeletonRenderTest'`.
