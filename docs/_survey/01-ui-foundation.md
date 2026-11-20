# 01 - Shared UI foundation survey (Litube 4.0.0 -> MinTube)

Scope: `ui/`, `ui/design`, `ui/theme`, `ui/components`, `ui/about`, `ui/extension`, `navigation/`, `App.kt`, manifests, `app/src/main/res`.
All paths below are relative to `app/src/main/java/com/hhst/youtubelite/` (written `J/`) unless they start with `app/` or `res/` (= `app/src/main/res/`).
Usage counts are `grep` line counts OUTSIDE `ui/design/` (so they measure external consumers) across all of `J/`.

---------------------------------------------------------------------------------------------------
## 1. Package map

| Package | Files (lines) | Purpose |
|---|---|---|
| `J/App.kt` (98) | Application | Koin start, `AppLog`, `EjsRuntimeProcess`, publishes persisted theme to `AppAppearance.update(prefs.themeMode())` (`App.kt:61`), lazy `startMediaRuntime()`. Pure backend wiring - do not touch. |
| `J/ui/` root | `NativeActivity` (283), `ContentActivity` (205), `Notices` (33), `NoticeText` (73), `TransientMessage` (64), `YoutubeThumb` (110), `AccountChanges` (23), `ActivityMediaActionExecutor` (45) | Activity base classes, window/system-bar/transition handling, toast-like message, notice rendering, thumbnail composable. Mixed UI + logic. |
| `J/ui/design` (19 files) | `DesignTokens` (241), `AppPalette` (54), `AppMotion` (63), `MotionScale` (14), `AppIcons` (78, 63 enum icons w/ raw path strings), `MenuBottomSheet` (223), `PullRefresh` (207), `SearchPanel` (160), `SelectField` (104), `SubscribeButton` (78), `LocalizedLabels` (284), `CollapsingAppBar` (66), `BottomSheetDrag` (80), `BrowseNavigationBar` (42), `Artwork` (34), `GestureResistance`, `HapticAction`, `InteractionStates`, `ContentPreferences`, `SubscriptionConfirmation`, `VerificationCard`, `WriteProgress` | The in-house design system (primary). |
| `J/ui/theme` (6 files) | `Theme.kt` (116) `Color.kt` (29) `Type.kt` (38) `AppStyle.kt` (68, also holds `ThemeMode`/`AppAppearance`) `SettingsTokens.kt` (28) `AppDialogContext.kt` (14) | Material3 theme bridge. |
| `J/ui/components` (7 files) | `AppControls` (234), `MainNavigation` (91), `SheetGeometry` (42), `ConstrainedPage` (22), `PagingFooter` (22), `SearchRows` (38), `AudioTrackLabels` (26) | Material3 wrappers + tab bar + helpers. |
| `J/ui/about` | `AboutActivity` (176), `AboutScreen` (87), `StableVersion` (12) | About page + update check. |
| `J/ui/extension` | `SettingsDataScreen` (274) | Backup/restore settings screen (named "extension" because backend is `J/extension/`). |
| `J/navigation` | `ContentLink` (56), `ContentLinkActivity` (28), `ContentNavigation` (39), `WatchTransition` (174) | URL parsing, deep-link trampoline, Intent factory, watch/mini-player transition state. |

Total of the assigned Kotlin: 4,604 lines. There is NO `data/domain/presentation` split here; everything is presentation + glue.

Important structural facts
- **There is no Navigation-Compose / NavHost.** Every "screen" is an `Activity` (`J/ui/NativeActivity.kt:15`, grep `NavHost|NavController|navigation-compose` found nothing; build.gradle.kts has no navigation-compose dep). Each Activity calls `setAppContent {}` (30 uses in 16 files) or `setContent { AppTheme {...} }`.
- **Two functions named `AppTheme`**: `ui.design.AppTheme(content)` (`ui/design/DesignTokens.kt:81`, the "one theme entry": provides `LocalAppColors`, `LocalMotionScale`, `LocalHideShorts` then delegates) and `ui.theme.AppTheme(darkTheme, dynamicColor, content)` (`ui/theme/Theme.kt:121`, builds the M3 `MaterialTheme`). `ContentActivity` mini overlay calls the `ui.theme` one directly (`ui/ContentActivity.kt`, `com.hhst.youtubelite.ui.theme.AppTheme {`), so LocalAppColors is NOT provided there (defaults to `LightAppPalette` - see Risks).
- Two parallel colour systems: Material `colorScheme` (161 usages in 21 files) and `DesignTokens`/`AppPalette` (373 usages in 53 files). They hold mostly identical neutrals, but `AppPalette.red=#FF0033` vs `colorScheme.error=#FF0000`, `AppPalette.ghost`/`surface` differ from M3 containers.

---------------------------------------------------------------------------------------------------
## 2. Design system inventory

### 2.1 Colours

**A. `AppPalette` (primary, `ui/design/AppPalette.kt:6-54`)**, read through `DesignTokens.X` (`ui/design/DesignTokens.kt:49-62`), provided via `LocalAppColors` (default = Light).

| Token (DesignTokens.X) | Light | Dark |
|---|---|---|
| Background | `#FFFFFF` | `#000000` (pure black) |
| Panel (=`AppStyle.Panel` = `colorScheme.surface`) | white | black |
| Ink | `#0F0F0F` | `#F1F1F1` |
| Secondary | `#606060` | `#AAAAAA` |
| Surface (grey chip/field/card) | `#F2F2F2` | `#212121` |
| Ghost (selected row, skeleton, banner) | `#E5E5E5` | `#272727` |
| Border | black 10% | white 10% |
| NavigationDivider | black 20% | white 20% |
| Scrim | black 30% | black 50% |
| MenuScrim | black 60% | black 70% |
| StateLayer (press) | black 8% | white 8% |
| Red (brand accent) | `#FF0033` | `#FF0033` |
| Link | `#065FD4` | `#3EA6FF` |

**B. Material3 schemes (`ui/theme/Color.kt`, `Theme.kt:45-114`)**: fully custom neutral `lightColorScheme`/`darkColorScheme`; every container role is overridden to avoid M3 purple. Light: primary `#0F0F0F`, secondary `#606060`, background/surface `#FFFFFF`, surfaceVariant `#F2F2F2`, outline `#E5E5E5`. Dark: primary `#FFFFFF`, secondary `#AAAAAA`, background/surface `#000000`, surfaceVariant `#272727`, surfaceContainer(Low) `#212121`, outline `#3A3A3A`. `error=YtRed #FF0000` both; `surfaceTint=Transparent`. Brand red is NOT the primary - it is only `DesignTokens.Red`/`error`. File `Color.kt:5` defines `YtRed`.

**C. Dynamic colour / Material You**: `AppTheme(dynamicColor: Boolean = false)` (`Theme.kt:123`) supports `dynamicLight/DarkColorScheme` on API 31+, but the only call path (`design.AppTheme`, `DesignTokens.kt:92`) never passes `dynamicColor`, so it is **dead code, always off**. `DesignTokens`/`AppPalette` ignore it entirely anyway. No UI toggle exists.

**D. XML colours** (`res/values/colors.xml`, `res/values-night/colors.xml`): `primary, on_primary, secondary, on_secondary, background, window_icon_color, window_icon_variant` (light `#0F0F0F/#FFF/#606060/#FFF/#FFF/#0F0F0F/#606060`; night `#FFF/#000/#AAA/#000/#000/#FFF/#AAA`). `res/color/window_icon_control.xml` is a selector for `colorControlNormal`. Hardcoded dialog colours also live in `res/values/themes.xml` (`Theme.App.Dialog.Light/Dark`: `#FFFFFF/#0F0F0F/#606060`, `#000000/#F1F1F1/#AAAAAA`).
Icons colours in launcher: foreground fill `#BB202D` (`res/drawable/ic_launcher_foreground.xml`, `ic_litube_mark.xml`), background `@android:color/white` (`res/mipmap-anydpi-v26/ic_launcher.xml`).
Hardcoded `Color(0x...)` literals outside `ui/design`: 64 lines in 23 files (e.g. fixture colours like `0xFF2D4F72` in `playlist/data/FixturePlaylistRepository.kt:34`, player overlay colours). `TransientMessage` hardcodes `Color.rgb(48,48,48)` bg / white text (`ui/TransientMessage.kt`).

### 2.2 Typography
- Single family: bundled **Roboto** Regular/Medium/Bold (Bold also mapped to Black) from `res/font/roboto_*.ttf`; `res/font/barlow_semicondensed.ttf` is bundled (use not found in `ui/` - grep `barlow_semicondensed` outside res not verified; license file at `app/src/main/assets/barlow_semicondensed-OFL.txt`).
- Two APIs: (1) `AppTypography` (`ui/theme/Type.kt:23-38`), sizes driven by `SettingsTokens` (Body 14/20, Detail 12/18, Title 20/26) plus display 24/30, 22/28, 20/26; letterSpacing 0, `includeFontPadding=false`. (2) `DesignTokens.text(size,line,weight,color)` / `AppText(...)` (`DesignTokens.kt:72,124`) built on `BasicText`, **sizes passed as raw Int sp at each call site** (317 `AppText(` lines in 43 files). Nav label = `AppText(size=11,line=14)`; brand wordmark 22/28 Black.
- `LitubeBrand` wraps content with `LocalDensity(fontScale=1f)` (`DesignTokens.kt:233-241`) so the wordmark ignores system font scale.
- No type scale beyond these; `MaterialTheme.typography` used only 8 lines/4 files.

### 2.3 Spacing / sizing
- `DesignTokens`: `TopBarHeight=48.dp`, `BottomBarHeight=56.dp`, `GridGap=16.dp` (`DesignTokens.kt:68-70`).
- `SettingsTokens` (`ui/theme/SettingsTokens.kt`): RowHeight 48, IconSize 24, IconSlot 40, LabelGap 8, PageInset 16, GroupGap 0, RowPadding 8, Indent 12 (57 uses in 14 files); `settingsAppBarHeight()` = max(48dp, 26sp->dp + 20dp).
- `MenuSheetLayout` (`MenuBottomSheet.kt:63-79`): WidthWideShort 400, BottomInset 24, Corner 12, RowHeight 48, RowStart 22, IconGap 18, handle 40x4; `sheetWidth()` = full width unless landscape-wide (>=416 and wider than tall and (ratio>=13:9 or >=931dp)) -> 400dp.
- `ConstrainedPage(maxWidth=960.dp default; About uses 720)`.
- No global spacing scale; raw `dp` literals everywhere (e.g. 16/12/8/24).

### 2.4 Shapes
`AppStyle.Shapes` (`ui/theme/AppStyle.kt:226-230`): extraSmall 4, small 8, medium 12, large 12, extraLarge 16 dp. `SheetShape` = top 16, `MenuShape` 12. Pills are ad-hoc `RoundedCornerShape(999.dp)` / `CircleShape`. Shadows are hand-drawn: `Modifier.headerShadow()` (1dp blur) and `messageCardShadow()` (8dp sigma) using bitmap blur (`DesignTokens.kt:105,135`; 4 external uses). No elevation/tonal elevation used (`surfaceTint` transparent).

### 2.5 Motion
`AppMotion` (`ui/design/AppMotion.kt`): WindowMs 240, ContentMs 140, ExpandMs 160, CollapseMs 100, ChromeInMs 100, ChromeOutMs 80; `Spatial` easing = custom emphasized path interpolator; `scrollResponse(scale)` spring (damping 1, stiffness 700). `PanelTransition(fromBottom, onDismiss, backEnabled, scrim, content)` owns enter/exit of panels (180ms in, <=120ms out, handles Back during entry; RTL aware). `LocalMotionScale` = system `ANIMATOR_DURATION_SCALE` re-read on every resume (`MotionScale.kt`, `DesignTokens.kt:85-88`); `motionDuration(base,scale)` returns 0 when scale<=0. Activity transitions are XML in `res/anim` (10 files: `navigation_detail_{enter,exit,return,close}`, `navigation_tab_{enter,exit,forward_enter,forward_exit,backward_enter,backward_exit}`; tab slide 140ms 4%p linear_out_slow_in; detail 220ms alpha + 8%p) with an `anim-ldrtl` variant dir. Press feedback: `Modifier.appStateLayer` (pressed 0ms, release 120ms colour fade; hover/focus layers) + `rememberAppInteractions()`. Haptics: `rememberHapticAction(event)` / `AppHapticFeedback` (wraps `LocalHapticFeedback`, routed to `HapticsController` from Koin). Gesture helpers: `GestureResistance`, `GestureThreshold`, `PullRefresh*`, `BottomSheetDrag`.

### 2.6 Icons - where they come from
1. **`AppIcon` enum** (`ui/design/AppIcons.kt`, 63 entries): raw SVG path strings rendered by `AppIcon(icon, modifier, color)` (`DesignTokens.kt:173`) which draws into a cached `Bitmap` via `android.graphics.Path` (24dp, supports 960-unit viewBox, stroke mode, RTL mirroring via `mirrorsInRtl`). Entries: Globe, Info, Audience, VideoOutline, Trending, Shuffle, Play, Playlist, HomeOutline, Dismiss, Report, Feedback, ShortsBody, ShortsPlay, Search, Back, Voice, Home, Shorts, You, Subscriptions, Explore, More, Close, Clock, Bookmark, Share, Block, Refresh, ArrowUpLeft, Like, Dislike, Comment, Mix, ShortsMark, Filter, ChevronDown, Download, QueueAdd, Queue, OpenWith, Add, Bell, ChannelGrid, Check, Send, Pause, Delete, Device (+ others). Looks like YouTube-style glyphs, 1-23 coordinates (YouTube's own icon set exported to paths; no licence file for them - check before reuse). Material "download/queue_add" paths are Apache-2 (`app/src/main/assets/material-icons-LICENSE.txt`).
2. **XML vector drawables** `res/drawable/ic_*.xml` (~57): player controls (`ic_player_*`, `ic_pip*`, `ic_resize*`, `ic_playback_end_*`, `ic_cast*`, `ic_subtitles_*`, ...), tinted via `res/color/window_icon_control.xml` / attrs `colorOnSurface`, `colorControlNormal` (`res/values/attrs.xml`). 115 `R.drawable.` lines in 30 files, 68 `painterResource` in 34 files.
3. **No `androidx.compose.material.icons`**: grep `Icons\.` found nothing.
Component: `AppIconButton(icon, description, modifier, diameter=48.dp, background, color, enabled, pending, onClick)` (61 uses/23 files) - 48dp touch target, state layer, haptic, `contentDescription = description`, `Role.Button`.
Brand marks: `LitubeMark` (`R.drawable.ic_litube_mark`, a11y string `a11y_litube`) and `LitubeBrand` (mark 28dp + `app_name` text); used in `home/presentation/HomeScreen.kt:294,374`, `library/presentation/YouScreen.kt:95`, `subscriptions/presentation/SubscriptionsScreen.kt:190`. `ShortsLogo` = ShortsBody (red) + ShortsPlay (white).

### 2.7 Shared components - signatures and usage (external lines / files)

`ui/design`
| Component | Signature (abridged) | Uses |
|---|---|---|
| `AppText` | `(text:String, modifier, size=14, line=20, weight, color=Ink, maxLines, align, exactLineHeight)` | 317 / 43 |
| `AppIcon` / `AppIconButton` | see 2.6 | 27 / 25 ; 61 / 23 |
| `DesignTokens.*` | colours, Font, TopBarHeight, BottomBarHeight, GridGap, `text()` | 373 / 53 |
| `AppTheme`, `setAppContent` | see 1 | 17 / 14 ; 30 / 16 |
| `Artwork(asset, modifier, contentScale=Crop, fallback)` | https/`//` url -> `YoutubeThumb`; blank -> grey box; else bundled sample drawable resolution | 75 / 27 |
| `MenuBottomSheet(items:List<MenuSheetItem>, onDismiss, onSelect:(Int)->Unit, modifier, tag="video-menu", paneTitle, sheetWidth, bottomPadding, rowStart, iconGap, header, dismissEnabled, actionsEnabled, fillWatchHeight)` ; `MenuSheetItem(icon:AppIcon?, label, tag?, contentStart?, iconResource?)` | 22 / 16 |
| `SearchPanel(query, suggestions, onQueryChanged, onSubmit, onClose, onVoiceSearch, history, onRemoveHistory)` (internal) | 8 / 9 |
| `SelectField<T>(tag, selected, options, labelOf, minWidth, chipHeight, onSelect)` | 3 / 3 |
| `SubscribeButton(subscribed, onClick, modifier, enabled, pending, onDarkSurface, testTag, channelId)` | 3 / 4 |
| `VerificationCard(verification, onVerify, onDismiss, modifier)` / `VerificationBanner` | 15 / 9 |
| `RefreshableContent<T>(value, refreshing, onRefresh, modifier, enabled, contentKey, tag="refresh", canPull, content)` ; `rememberPullRefreshState`, `Modifier.pullRefreshGesture`, `PullRefreshIndicator(state, tag, anchor)` | 9 / 8 |
| `CollapsingAppBar(state, content)` + `rememberAppBarCollapseState(key, enabled)` (NestedScroll hide-on-scroll-down, snap on fling) | 8 / 7 |
| `ActionProgress`, `WriteProgressOverlay(feedback: WriteFeedback)` (write-action progress) | 11 / 11 ; 12 / 9 |
| `PanelTransition`, `rememberBottomSheetDrag(enabled, onDismiss)` | 3/4 ; 3/3 |
| `BrowseNavigationBar(tag, signedIn, onSelect:(BrowseDestination)->Unit)` | 3 / 4 |
| `Modifier.appDescription(@StringRes id, args)` / `appPaneTitle` | 34 / 18 ; 9 / 8 |
| `LocalHideShorts` (+`rememberHideShorts`, reads `ExtensionManager` pref `ENABLE_HIDE_SHORTS`) | used by all nav bars |
| `LocalizedLabels.kt`: 27 `val <Enum>.labelResource: Int` extension props mapping domain enums (MediaActionKind, ChannelTab, HomeCatalog, MainTab, ...) to string ids | pervasive |

`ui/components`
| Component | Signature | Uses |
|---|---|---|
| `AppTopAppBar(title, modifier, navigationIcon, actions, expandedHeight=settingsAppBarHeight(), colors)` | M3 TopAppBar wrapper | 2 / 2 |
| `AppTextButton(onClick, modifier, enabled, contentPadding, colors, content)` | pill TextButton + haptic | 15 / 8 |
| `AppAlertDialog(onDismissRequest, ...)` | custom dialog | 6 / 6 |
| `AppSwitch` / `AppSlider` / `AppCheckbox` / `AppRadioButton` / `AppLinearProgressIndicator` (2 overloads) / `AppSheetHandle` | M3 wrappers tinted by `AppStyle.Action` | 2/2, 2/2, 3/3, 1/1, 2/1, 5/4 |
| `MainNavigationBar(tag, signedIn, avatarAsset, selected=MainTab.You, onSelect)` | bottom bar (see 3) | 3 / 3 |
| `MainTab` enum, `MainTabs.tabs()/intent()` | tab model + Intent factory | |
| `ConstrainedPage(modifier, maxWidth=960.dp, content)` (internal) | wide-window centred column | 3 / 3 |
| `PagingFooter(loading, failed, onLoad)` | | 2 / 2 |
| `rememberSearchRows(source, query)`, `audioTrackLabel(choice)`, `Context.watchSheetHost()`, `LocalSheetPlayerBottomFraction`, `Modifier.watchSheetHeight()` | helpers | |

`ui/theme` helpers: `AppStyle.Panel/Action/OnAction` (44 uses/19 files), `NeutralControls{}` (5/3), `Context.appDialogContext()` (themed framework dialogs, `AppDialogContext.kt`).

`ui/` root: `YoutubeThumb(url, modifier, contentDescription, contentScale, placeholderColor, fallbackUrl, placeholder)` - bitmap loader over `core.YoutubeThumbnail` (own cache, Crossfade 90ms, 3 retries) - NOT Coil; 5 direct uses + via `Artwork`. `Activity.showMessage(text)` (a framework `TextView` pinned over the decor, 3s or accessibility timeout; NOT a Compose Snackbar), `Activity.renderNotice(AppNotice)`, `Context.noticeText(String)` (maps ~60 English literal notice strings to string resources - **text-keyed**, see Risks), `ComponentActivity.observeAccountChanges(refresh)`, `ActivityMediaActionExecutor`.

### 2.8 Bottom sheets / dialogs / menus
Custom: `MenuBottomSheet` (all long-press/overflow menus), `PanelTransition` sheets, `AppAlertDialog`, framework `AlertDialog` with `appDialogContext()` (Notices failure report), `DownloadSheetActivity` (separate translucent Activity, `Theme.App.DownloadSheet`). No M3 `ModalBottomSheet` in the shared layer (not grepped beyond ui/).

---------------------------------------------------------------------------------------------------
## 3. Navigation

### 3.1 Model
- Pure multi-Activity navigation. Hierarchy: `NativeActivity : ComponentActivity` (`ui/NativeActivity.kt:15`) -> `ContentActivity : NativeActivity` (adds mini-player overlay host, `ui/ContentActivity.kt:19`) -> screen activities. `PlaybackActivity : NativeActivity` -> `WatchActivity`.
- Extending `ContentActivity`: HomeActivity, YouActivity, SubscriptionsActivity, HistoryActivity, PlaylistsActivity, PlaylistActivity, ChannelActivity, ExploreActivity, PostDetailActivity, SearchResultsActivity. Extending `NativeActivity`: SignInActivity, SettingsActivity, AdvancedActivity. Plain `ComponentActivity`: ShortsActivity (own black theme + own chrome, **no bottom bar**), AboutActivity, GalleryActivity, Download*Activity (Activity/ComponentActivity), TrafficVerificationActivity, ChatWebSignInActivity.
- Each screen Activity owns a ViewModel (per feature, other surveyors) and receives `*NavigationRequest` sealed events from the VM, translated into `startActivity` in the Activity (e.g. `HomeActivity.kt:209`, `ChannelActivity.kt:121`). The UI never calls an Intent directly; this request layer is the VM<->UI contract to preserve.
- `ContentLink` (`navigation/ContentLink.kt`): sealed `Video(id, seconds?)`, `Short(id?)`, `Playlist`, `Channel`, `Post`, `Search`, `Feed(name)`; `ContentLink.parse(url)` strictly validates https + host in {youtube.com, www., m., music.youtube.com, youtu.be}, ids by regex; `ContentLink.intent(context)` (`ContentNavigation.kt:12`) maps to `WatchActivity/ShortsActivity/PlaylistActivity/ChannelActivity/PostDetailActivity/SearchResultsActivity/Subscriptions|History|Playlists|You|HomeActivity`; `Context.openContentLink(url)` for in-app links.

### 3.2 Bottom navigation bar
- Model: `enum MainTab { Home, Shorts, Subscriptions, You }` (`ui/components/MainNavigation.kt:20`). `MainTabs.tabs(signedIn, hideShorts)` = all four when signed in, else {Home, Shorts, You}; Shorts removed if the "hide shorts" pref is on. Subscriptions tab only appears when signed in.
- **Four separate hand-copied implementations** of the same bar (same 56dp, 1dp `NavigationDivider`, icon 24 + 11sp label, `Role.Tab`, test tags `"$tag-nav-<Tab>"`):
  1. `MainNavigationBar` (`ui/components/MainNavigation.kt:67`) - used by `library/presentation/YouScreen.kt:69` (selected=You) and `subscriptions/presentation/SubscriptionsScreen.kt:178` (selected=Subscriptions). Only one with a selected state, avatar for You tab (`Artwork`), haptic TAB, semantics `selected`.
  2. `BrowseNavigationBar` (`ui/design/BrowseNavigationBar.kt:19`) - no selected indication at all; used by `channel/presentation/ChannelScreen.kt:317`, `playlist/presentation/PlaylistScreen.kt:364`, `post/presentation/PostDetailScreen.kt:76`.
  3. Inline bar in `home/presentation/HomeScreen.kt:~300-330` (uses `HomeDestination`, `selectable(Home)`, icon `AppIcon.Home`).
  4. `SearchNavigationBar` (`search/presentation/SearchResultsScreen.kt:319`) using `SearchDestination`, for the You tab shows `R.drawable.sample_bunny` as avatar (**placeholder bitmap in production code**, line ~333).
  Duplicate destination enums: `MainTab`, `BrowseDestination`, `HomeDestination` (`home/domain`), `SearchDestination`, all `{Home, Shorts, Subscriptions, You}` and all name-mapped via `valueOf(tab.name)`. Shorts and Explore/History/Playlists/Settings/Watch screens have no tab bar.
- Switching: `MainTabs.intent(context, tab, signedIn)` returns an Intent for the target tab Activity with `FLAG_ACTIVITY_REORDER_TO_FRONT | SINGLE_TOP` (Shorts only reorders) plus extras `navigation_tab`, `navigation_tab_backward` (computed from tab ordinal vs source Activity). Consequence: **each tab is its own Activity instance kept in the task and brought to front** - not a back-stack push - so scroll state/VM state of each tab survives while the Activity lives; the doc comment says "Each destination retains its saved scroll state" (`MainNavigation.kt` KDoc). Tab changes animate with `res/anim/navigation_tab_*` (direction-aware); detail pushes use `navigation_detail_*`; Watch, download sheet and "instant" intents use 0 animation (`NativeActivity.startActivity`, `ui/NativeActivity.kt:100-122`).
- Back: Home `BackHandler` closes overlay else `moveTaskToBack(true)` (`home/HomeActivity.kt:164-166`); You/Subscriptions `BackHandler(onBack=back)` navigate to Home (`YouActivity.kt:156-158`, `SubscriptionsActivity.kt:146`). `enableOnBackInvokedCallback=true` in manifest. On API 34+ `overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE)`; <34 `overridePendingTransition` (`NativeActivity.kt:78-90`).
- Scroll/state preservation: relies on Activities staying alive (REORDER_TO_FRONT) and `rememberLazyListState/GridState` in 11 files (HomeScreen, SearchResults, Playlist, PostDetail, Subscriptions, comments, download, chat, player sheets). `RefreshableContent` retains last content during pull-refresh. `rememberSaveable` used in `SearchPanel` (text field) and `AboutActivity`. No process-death scroll restoration beyond the default Compose saver. `ContentActivity.onSaveInstanceState` saves `navigation_animation` only.

### 3.3 Mini-player host
`ContentActivity.onPostCreate` (`ui/ContentActivity.kt`) adds a custom `FrameLayout` overlay + `ComposeView` onto `android.R.id.content` of **every** ContentActivity; it observes `PlaybackSession.player` (a global singleton) and when `uiState.visible && mini && !pip` draws `MiniPlayerWindow(...)` (`player/surface/MiniPlayerWindow.kt:133`) wrapping `WatchPlayer` inside `windowInsetsPadding(safeDrawing)`. It also gates first draw via `OnPreDrawListener` (<=500ms) until the mini surface is ready and paints a restore bitmap (`WatchTransition.MiniRestoreFrame`, `PixelCopy` of the SurfaceView) to avoid flicker during Watch -> browse. `WatchTransition` (`navigation/WatchTransition.kt`, 174, internal object with static mutable state: `backdrop` bitmap of the origin screen, artwork rects per root view (<=128), `miniBounds`, `videoFrame`) prepares a screenshot backdrop of the browse window and origin rect of the tapped thumbnail so WatchActivity animates from the card. `WatchTransition.restore()` re-fronts the Watch Activity (`FLAG_ACTIVITY_REORDER_TO_FRONT`). 
**This layer is player/engine-coupled and must stay as is.** The mini player draws over the activity's content, so bottom-bar height is NOT excluded - MiniPlayerWindow is passed a `56` (`MiniPlayerWindow(maxWidth.value.toInt(), 56, ...)`) = hardcoded equal to `DesignTokens.BottomBarHeight`; if the new bar height changes this literal must change.

### 3.4 Deep links / intent filters (`app/src/main/AndroidManifest.xml`)
Launcher: `.home.HomeActivity` (exported, `singleTop`, theme `Theme.App.Home`, `adjustResize`) - MAIN/LAUNCHER (`:60-70` region).
`.navigation.ContentLinkActivity` (exported, theme `Theme.App.Home`, no UI):
1. `VIEW`, DEFAULT+BROWSABLE, `autoVerify="false"`, scheme `https`, hosts `youtube.com`, `www.youtube.com`, `m.youtube.com`, `music.youtube.com`, `youtu.be` (no pathPrefix filter - all paths).
2. `SEND`, DEFAULT, mimeType `text/plain` (share-sheet).
`.downloader.ui.DownloadShareActivity` (exported, `singleTask`, `excludeFromRecents`, label `@string/download`): `SEND` text/plain (second share target, "Download").
`<queries>`: VIEW https BROWSABLE and `android.speech.RecognitionService`.
Behaviour: `ContentLinkActivity.onCreate` extracts a URL from `dataString` or `EXTRA_TEXT` via `DownloadShareParser.extractUrl`; Video/Short -> start `WatchActivity`/`ShortsActivity` (if task root: stack `HomeActivity(EXTRA_DEFER_HOME=true)` under it); every other link (channel, playlist, search, post, feed, or non-link share) is forwarded to `HomeActivity` with the original intent (`ContentLinkActivity.kt:20-24`). So `HomeActivity` must still handle VIEW/SEND intents for channel/playlist/etc. Tests: `test/.../navigation/ContentLinkTest.kt` (3 @Test), `androidTest/.../ContentLinkAndroidTest.kt` (2), `browser/ExternalRouteAndroidTest.kt`.
Other Activities in the manifest (all `exported=false`): Playlist, You, History, Playlists, Settings, Advanced, SignIn, ChatWebSignIn, TrafficVerification, Subscriptions, Shorts (`Theme.App.Shorts`), Explore, SearchResults(singleTop), Watch (`supportsPictureInPicture`, `configChanges=orientation|screenSize|smallestScreenSize|screenLayout`, `windowDisablePreview` theme), Channel(singleTop), PostDetail, About, Gallery, DownloadAction/Download/DownloadSheet; services `EjsRuntimeService` (`:youtube_ejs` process), `PlaybackService`, `DownloadUidtJobService`; receiver `DownloadActionReceiver`.
Debug manifest (`app/src/debug/AndroidManifest.xml`) adds `.debug.reference.PageReferenceActivity`, `LayoutReferenceActivity` (exported, own process `:layout_reference`), `PlayerTestActivity`, `ExtractionTestActivity`. `app/src/minifiedDebug/` contains only a `java` dir (no manifest). `r8AndroidTest` source set also exists.

### 3.5 Tablet / landscape adaptation
- No `WindowSizeClass`, no `isTablet`, no `values-sw600dp`, no layout resource variants (grep). Adaptation is hand-coded with `BoxWithConstraints` widths: Home feed `compact = width < 528f`, shorts shelf columns via `HomeLayout.shortsColumns(windowWidth)` with breakpoints 528/600 (`home/presentation/FeedCards.kt:40,152-157`), `ConstrainedPage` max 960/720dp, `MenuSheetLayout.sheetWidth` (400dp wide sheet in landscape), `downloader/ui/DownloadSheets.kt:196` uses `ORIENTATION_LANDSCAPE`, `player/surface/MiniPlayerLayout` compact vs large by `screenWidthDp`.
- Previews exist for 390x844, 600x960, 840x1000, 1280x800 (HomePreviews, CommentsPreviews, ChannelDiscoveryPreviews).
- Bottom bar is always bottom; no navigation rail. WatchActivity handles rotation itself (`configChanges`), others recreate on rotation (no `configChanges` in manifest for them).
- Orientation lock: none in manifest; `PlayerWindowHost` sets `requestedOrientation` for fullscreen.
- `enableEdgeToEdge()` called in 36 places; `NativeActivity.applySystemBarStyle` forces transparent status bar, solid black/white nav bar (`NativeActivity.kt:155-164`).

---------------------------------------------------------------------------------------------------
## 4. Theme switching, language

### 4.1 Theme (light / dark / system) - WORKS
- `enum ThemeMode { System, Light, Dark }` and `object AppAppearance { dark: Boolean?, mode }` with `update(mode)` + `onChange(listener)` in `ui/theme/AppStyle.kt:198-223` (Compose snapshot state + non-Compose listeners).
- Persist: `ExtensionManager.themeMode()/setThemeMode()` (`extension/ExtensionManager.kt:110-116`, pref `PreferenceKeys.THEME_MODE`, stored as Long ordinal). Loaded in `App.onCreate` (`App.kt:61`) before first Activity. Changed in `settings/SettingsActivity.kt:79-80` (`setThemeMode` + `AppAppearance.update`). UI: `settings/presentation/SettingsScreen.kt:123-158` (dialog with labels `settings_theme_system|light|dark`). Test: `androidTest/.../settings/AppearanceSettingsAndroidTest.kt` (2 tests), `ui/NativeWindowBackgroundAndroidTest`, `watch/WatchWindowAppearanceAndroidTest`.
- Application: `design.AppTheme` -> `AppAppearance.dark ?: isSystemInDarkTheme()` -> palette + M3 scheme. Window: `NativeActivity` re-applies window background (black/white `ColorDrawable`) and status/nav bar colours on create/resume and on `AppAppearance.onChange`; XML themes cover the starting window (`values/themes.xml` Light, `values-night/themes.xml` + `values-v27` for Dark; **framework themes `android:Theme.Material.*` - no AppCompat/MDC**). Framework dialogs use `appDialogContext()` -> `Theme.App.Dialog.Light/Dark`. Pure-black dark theme (OLED), no tonal dark greys at the page level.
- Edge: with in-app override != system, the XML starting window follows the SYSTEM mode (flash possible); the code corrects it in `onCreate`.

### 4.2 Language - WORKS
- 727 `<string>` keys in `res/values/strings.xml` (735 lines), 1 `<plurals>` in `res/values/plurals.xml`; prefix groups: `ui_` 239, `download_` 84, `settings_` 55, `a11y_` 49, `info_` 26, `player_` 21, `comments_` 17, `about_` 17, `chat_` 16, `cast_` 15, ... No `translatable="false"` strings.
- 93 `res/values-*` dirs, of which 91 are locale dirs with strings. **Every locale dir has exactly 727 keys (100%, 0 missing, 0 extra)** - enforced by `test/.../ui/TranslationResourcesTest.kt` (complete catalogue, plural fallbacks, format-arg parity).
- **`res/values-b+vi/` EXISTS**: 727/727 keys, only 11 values identical to English (`app_name, sponsorblock, dearrow, speed_hold_2x, info_itag, download_video, download_bitrate, a11y_litube, a11y_title_duration, ui_shorts, ui_playlist_owner`) - i.e. effectively complete Vietnamese (quality of translation not audited; machine-like completeness suggests generated translations).
- Packaged locales: `build.gradle.kts:44-49` `androidResources.localeFilters` = 47 tags (en, zh, zh-rTW, ar, ..., vi). Many `values-b+xx` dirs (af, am, as, az, be, ...) are NOT in localeFilters, so they are stripped from the APK (dead weight in repo). `res/xml/locales_config.xml` lists 48 locales (incl. `zh-Hans`, `zh-Hant`, `he`, `id`, `vi`) for Android 13 per-app language. Note: `values-zh` vs `values-zh-rHK/rMO/rTW` and `b+zh+Hans/Hant` coexist.
- Switching: `core/AppLocale.kt` - `OPTIONS` list of ~48 (tag, native name) (`:15-62`), `apply(context, tag)` (API 33+: `LocaleManager.applicationLocales`; below: pref `preferences:app_language` + `wrap()` in `NativeActivity.attachBaseContext` and `recreate()` on resume if changed, `NativeActivity.kt:33-34,137`). UI in `settings/presentation/SettingsScreen.kt:137-166`; handler `settings/SettingsActivity.kt:83`. Plain `ComponentActivity` screens (AboutActivity, Shorts, Gallery, Download*) do NOT call `attachBaseContext` wrap - **on API <33 these ignore the in-app language** (not verified at runtime; code read only).
- Hardcoded English in code that bypasses resources: `ThemeMode.label`, `MainTab.label`, `BrowseDestination.label` constants (not displayed - `labelResource` used), `AccountModels.kt:7` `"About Litube"`, `AppLocale.OPTIONS` "System default", `NoticeText` literal-matching, diagnostics reports. `ui_shorts`, `speed_hold_2x` etc. are not translated by design.

---------------------------------------------------------------------------------------------------
## 5. App identity touchpoints for the rename to "MinTube" (applicationId `com.nevminhnh.mintube`)

Kotlin package/namespace stays `com.hhst.youtubelite` (`build.gradle.kts:28`).

**Gradle** (`app/build.gradle.kts`): `applicationId = "com.hhst.litube"` (`:32`); debug adds `applicationIdSuffix=".debug"`, `versionNameSuffix="-debug"` (`:63-64`), minified `".minified"`/`"-minified"` (`:70-71`); versionCode 400, versionName 4.0.0 (`:35-36`).

**Manifest placeholders**: both providers use `${applicationId}` (auto-follows the rename): `FileProvider` authority `${applicationId}.download.fileprovider` and `InitializationProvider` `${applicationId}.androidx-startup`. Code builds the same authority from `packageName` (`AboutActivity.kt` `"$packageName.download.fileprovider"`; other call sites in downloader not in my scope - grep `download.fileprovider` there). No hardcoded `com.hhst.litube` in `app/src/main`: **0 hits** (grep `com\.hhst\.litube`).
Hardcoded `com.hhst.litube(.debug)` ONLY in tests/tools: `androidTest/.../downloader/android/DownloadAndroidHarness.kt:140,149,166` (content:// URIs with `com.hhst.litube.debug.download.fileprovider`), `androidTest/.../watch/WatchReferenceAndroidTest.kt:91` (`run-as com.hhst.litube.debug`), `androidTest/tools/casting/README.md:21,53,78,79` (`com.hhst.litube.debug.test/...ContentTestRunner`).

**Names**: `res/values/strings.xml`: `app_name="Litube"` (`:3`), `a11y_litube="Litube"` (`:412`), `a11y_litube_home="Litube Home"` (`:413`), `settings_about_title="About Litube"` (`:435`), `settings_open_system_settings_summary="Android notification settings for Litube"` (`:462`). **`app_name` is also hardcoded "Litube" in every one of the 91 locale files** (Vietnamese included), and the other four keys are translated per locale (contain the word Litube); a global search/replace over `res/values*/strings.xml` is required. Manifest label `@string/app_name` on `<application>`; the launcher label is therefore `app_name`; `DownloadShareActivity` label `@string/download`. `PlaybackService.kt:356` notification title uses `R.string.app_name`; `DesignTokens.kt:238` `LitubeBrand` renders `app_name`.

**Kotlin literals containing "Litube" (non-package)** (rename candidates; most are backend, flag for owners): `AccountModels.kt:7` (menu "About Litube"), `cast/CastFailureReport.kt:15`, `cast/DlnaTransport.kt:35` (UPnP User-Agent `Litube/1.0`), `diagnostics/{ActionFailureReport:39,DiagnosticArchive:104,PlaybackFailureReport:25}` (report headers), `diagnostics/AppLog.kt:183` (`litube-diagnostics-*.zip`), `diagnostics/PlaybackTelemetry.kt:20` + `player/datasource/MediaSourceResolver.kt:101` (bundle key `litube.diagnostic_context`, internal consistent pair), `downloader/io/DownloadFileNames.kt:3` (doc), **`downloader/io/DownloadPublisherImpl.kt:282,301,375` (MediaStore folder `Downloads/Litube`, LIKE query `%Litube%`, external files dir `Litube`)**, **`res/xml/download_file_paths.xml:7` (`Download/Litube/`)**, `extension/SettingsBackup.kt:30` (`SCHEMA="litube-settings"` - backup file format id, changing it breaks import of old backups), `extractor/ExtractionDiagnostics.kt:37`, `gallery/GalleryActivity.kt:126` (`LiTube-<ts>.<ext>` filename), fixtures `FixtureLibraryRepository.kt:39`, `FixturePlaylistRepository.kt:34-35`, `YouActivity.kt:257`, `ui/extension/SettingsDataScreen.kt:170` (`litube-settings.json` default export name). Classes/ids named `Litube*`: `LitubeMark`, `LitubeBrand`, `R.drawable.ic_litube_mark`, string ids `a11y_litube`, `a11y_litube_home` (Kotlin-visible names; renaming is optional, only values matter).
Tests asserting the old name: `androidTest/.../home/HomeScreenshotAndroidTest.kt:548` (`onNodeWithText("Litube")` + 28f size), `HomeRefreshEvidenceAndroidTest.kt:89` (`AboutScreen("Litube","4.0.0",...)`).
Docs/metadata: `README.md`, `fastlane/metadata/android/en-US/title.txt` = "Litube".

**Launcher icon files**
- Adaptive: `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` (identical: background `@android:color/white`, foreground `@drawable/ic_launcher_foreground`, monochrome `@drawable/ic_launcher_monochrome`).
- Layers: `res/drawable/ic_launcher_foreground.xml` (108dp vector, fill `#BB202D`, large traced path), `ic_launcher_monochrome.xml` (themed icon; also reused inside About screen), `ic_litube_mark.xml` (64dp in-app brand mark, same path).
- Legacy PNG: `res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` (1.1-4.8 KB), `res/values/ic_launcher.xml` aliases `ic_launcher_round` -> `ic_launcher` for those densities.
- Play Store: `app/ic_launcher-playstore.png`.
- Notification small icons: `res/drawable/ic_notification.xml`, `ic_stat_play.xml`.
- `res/drawable-nodpi/comment_avatar.jpg`, `drawable/sample_bunny|dream|sintel.jpg` are fixture/sample images shipped in main (also used for the production You-tab avatar placeholder in Search bar, see 3.2).

**About screen** (`ui/about/AboutActivity.kt`, `AboutScreen.kt`): header = monochrome launcher drawable 40dp + `applicationInfo.loadLabel` (so follows `app_name`) + `versionName`; rows: Source (`openUrl(SOURCE)`), Check update, Releases (`SOURCE/releases/latest`), divider, Clear cache, Export logs (share zip via FileProvider), Save logs (SAF). `SOURCE = "https://github.com/HydeYYHH/litube"` (`AboutActivity.kt:175`). No author/credits/licences list/privacy text in the screen. Strings `about_*` (17 keys).
**Update checker**: `AboutActivity.kt:87` `GET https://api.github.com/repos/HydeYYHH/litube/releases/latest` via injected `OkHttpClient`; rejects prerelease/draft, compares `tag_name` with `versionName` (`StableVersion.isNewer`, dev/alpha/beta/rc/snapshot = "development version"). It only displays a status string - no download/install. It is user-triggered only (no auto-check found). Repoint or disable for MinTube (also the "Source"/"Releases" rows).
Other upstream links: only these 2 in `app/src/main` kt/xml/json (grep `github.com|hydeyyhh`).

**Entry points to About/Settings**: Settings -> `onAbout` (`settings/SettingsActivity.kt:76`); You screen "About" row (`library/YouActivity.kt:132`, `LibraryNavigationRequest.About`); Settings reached from You via `LibraryNavigationRequest.Settings` (`YouActivity.kt:130`). No settings access from Home/Subscriptions/Shorts toolbar found in `home/` (grep SettingsActivity: only YouActivity).

---------------------------------------------------------------------------------------------------
## 6. Feature matrix (my checklist)

| Feature | Status | Data/engine side | UI side |
|---|---|---|---|
| Bottom bar Home/Shorts/Subscriptions/You | **WORKS** (with caveats) | tab Activities exist (`home/HomeActivity`, `shorts/ShortsActivity`, `subscriptions/SubscriptionsActivity`, `library/YouActivity`); `MainTabs.tabs(signedIn, hideShorts)` `ui/components/MainNavigation.kt:15` | 4 duplicated bar implementations (3.2); Shorts screen itself has no bar; Subscriptions only when signed in; selected state only in `MainNavigationBar` (You/Subscriptions) and Home inline bar; `BrowseNavigationBar` never highlights; "hide shorts" pref removes the tab |
| Light/dark theme (+System) | **WORKS** | `ExtensionManager.themeMode/setThemeMode` `extension/ExtensionManager.kt:110`; `AppAppearance` `ui/theme/AppStyle.kt:200` | Settings dialog `settings/presentation/SettingsScreen.kt:123`; no dynamic colour toggle (dynamic path exists but is never enabled) |
| Multi-language | **WORKS** | `core/AppLocale.kt`, 91 locale dirs (47 packaged by `localeFilters`), `locales_config.xml` 48 | Settings language dialog `SettingsScreen.kt:137-166`; vi = 727/727 keys; API<33 wrap missing in non-`NativeActivity` screens (About/Shorts/Gallery/Download) |
| Open YouTube links from other apps | **WORKS** | `ContentLink.parse` + `ContentLinkActivity` + manifest filters (3.4) | Video/Short open directly; channel/playlist/search/post/feed forwarded to HomeActivity (that path in `home/`, other surveyor); `SEND text/plain` registered on BOTH `ContentLinkActivity` and `DownloadShareActivity` (chooser shows two entries, by design) |
| Settings entry point | **WORKS** (indirect) | `settings/SettingsActivity` | Only via You screen (`YouActivity.kt:130`); not in Home toolbar |
| About screen | **WORKS** | `ui/about/AboutActivity` (cache clear, log export, update check hitting upstream GitHub) | `AboutScreen`; points to upstream repo (5) |
| Mini player over browse | WORKS | `PlaybackSession`, `MiniPlayerWindow` | `ContentActivity` overlay (3.3) |
| Predictive back / edge-to-edge | WORKS | manifest `enableOnBackInvokedCallback`; `enableEdgeToEdge` x36 | |
| Material You dynamic colour | **DATA_ONLY** | `AppTheme(dynamicColor=true)` code path `ui/theme/Theme.kt:127` | no setting, never enabled, `AppPalette` ignores it |
| Per-screen deep links for channels etc. | WORKS via Home forward | | |

---------------------------------------------------------------------------------------------------
## 7. UI coupling and redesign notes

**Pure presentation (safe to rewrite/restyle)**: `ui/design/*` visuals (`DesignTokens`, `AppPalette`, `AppIcons`, `AppMotion`, `MenuBottomSheet`, `SelectField`, `SubscribeButton`, `VerificationCard`, `Artwork`, `CollapsingAppBar`, `SearchPanel`), `ui/theme/*`, `ui/components/AppControls.kt`, `MainNavigation.kt`, `ConstrainedPage`, `PagingFooter`, `ui/about/AboutScreen`, `ui/extension/SettingsDataScreen` (UI only; read/validate logic lives inside it - `readBoundedText` `:97` - keep), XML themes, drawables, mipmaps, anims.

**Logic/engine-coupled - change with care**
- `ui/NativeActivity.kt`: language wrap (`AppLocale`), window background + system bars, close/open transitions, PiP suppression during navigation (`PipAutoEnter.suppressForNavigation`), voice search (`SpeechRecognizer`, `RECORD_AUDIO`), `WatchTransition.prepare` on watch launch. Treat as infrastructure.
- `ui/ContentActivity.kt`, `navigation/WatchTransition.kt`: mini-player/PiP/PixelCopy handoff (see 3.3). Redesign must keep: bottom bar height literal `56`, `WindowInsets.safeDrawing` padding, `WatchTransition.artwork(view,id,bounds)` registrations done by thumbnails in feeds (grep callers outside `ui/` before changing card layout; the handoff animates from the registered artwork rect).
- `ui/design/PullRefresh.kt`, `GestureResistance.kt`, `BottomSheetDrag.kt`, `CollapsingAppBar.kt`: gesture physics tuned by tests (`GestureResistanceTest`, `PullRefreshAndroidTest`, `BottomSheetDragAndroidTest`).
- `ui/design/HapticAction.kt`, `ContentPreferences.kt`: read Koin `GlobalContext` directly (`HapticsController`, `ExtensionManager` pref listener) - composables depend on Koin being started.
- `ui/NoticeText.kt`: maps **English literal text from VMs/domain** to resource ids (e.g. `"Saved to Watch later" -> R.string.ui_saved_to_watch_later`). Backend strings must not be changed; if a message string changes in a VM it silently falls back to English.
- `ui/Notices.kt`/`AppNotice`: `AppNotice.SignIn -> SignInFlow.open`, failure report dialog with Copy.
- `ui/AccountChanges.kt`: `MediaSession.changes` identity (generation, signedIn) refresh hook.
- `ui/about/AboutActivity.kt`: cache clearing touches many repositories (`ContentRepositories`, `DeArrowService`, `MemCache`, `PreferenceStore` keys `extractor:*`, `cacheDir/gallery|thumbnails`) and log export - business logic embedded in the Activity; keep when restyling (only `AboutScreen` is UI).
- `ContentLink.parse` is security-sensitive (strict host/port/userinfo/id regex) - has unit tests; leave.

**Hardcoded values to be aware of**
- Colours: 64 `Color(0x..)` literals in 23 files (outside `ui/design`), `Color.Black/White` in many player/shorts surfaces; `#FFFFFF`/`#000000` in `themes.xml`; `TransientMessage` grey `rgb(48,48,48)`; `Theme.App.Shorts` is always black.
- Sizes: `56` mini host literal, `11sp` nav labels, per-call `AppText(size,line)`; `SettingsTokens` vs `DesignTokens` vs literal dp - three sources.
- Strings: `ThemeMode.label`, enum `label` constants are English; `"System default"` in `AppLocale.OPTIONS`.
- Placeholder assets in production: `R.drawable.sample_bunny` as the You avatar in `SearchResultsScreen.kt` bar; sample images in `res/drawable` (fixture mode).
- Test hooks: `ContentRepositories.fixtures(intent)` / `EXTRA_FIXTURE_SCENE` branches in `NativeActivity.launchNavigation` and `ContentActivity` - fixture ("reference/golden") mode shares production code paths.

**Accessibility state**: `AppIconButton` has 48dp target + `contentDescription`; nav tabs have `Role.Tab` + text label (and `selected` semantics only in `MainNavigationBar`); `appDescription/appPaneTitle` helpers used 34/9 times; 49 `a11y_*` strings; transient message is an accessibility live region (polite) with `getRecommendedTimeoutMillis`; motion respects the animator-duration scale; hover/focus state layers exist. Gaps: `LitubeMark` description "Litube" (rename), nav `selected` missing in 3 of 4 bars, `Artwork` decorative thumbnails default `contentDescription=null`.

**Scroll-state preservation** summarised in 3.2; any switch to a single-Activity/NavHost redesign would lose the Activity-per-tab retention and the `navigation_tab_*` animations - recommend keeping the Activity-per-screen model for MinTube.

**RTL**: `res/anim-ldrtl`, `AppIcon.mirrorsInRtl`, `PanelTransition` RTL aware; `android:supportsRtl=true`.

---------------------------------------------------------------------------------------------------
## 8. Tests touching these packages

Totals in repo: 246 unit test `.kt` (`app/src/test`), 283 instrumented `.kt` (`app/src/androidTest`), 154 golden PNGs in 15 `androidTest/assets/<feature>/goldens` dirs (account 4, channel 10, comments 8, explore 12, feedback 2, home 28, library 13, livechat 3, playlist 18, post 2, saves 3, search 12, settings 8, shorts 9, watch 6).

Unit (JVM, UI-independent => survive a redesign): `navigation/ContentLinkTest` (3), `ui/about/StableVersionTest`, `ui/design/GestureResistanceTest`, `ui/design/MotionScaleTest`, `ui/BrowseContinuationTest`, `ui/RefreshFailureContinuityViewModelTest`, `ui/TranslationResourcesTest` (parses every `values*/strings.xml`: any new string key MUST be added to all 91 locale dirs - or the test fails; renaming a key needs the same). Also `library/LibraryViewModelTest`, `subscriptions/SubscriptionsViewModelTest` reference `MainTab`.

Instrumented under `ui/`+`navigation/` (25 files): `ContentLinkAndroidTest`, `WatchArtworkHandoffAndroidTest`, `AccountChangesAndroidTest`, `BrowseSessionAndroidTest`, `CompactControlsAndroidTest`, `LocalizationAndroidTest` (4), `NativeWindowBackgroundAndroidTest`, `PullRefreshAndroidTest`, `RefreshCoverageAndroidTest`, `ResponsiveExperienceAndroidTest`, `SearchRowsAndroidTest`, `SharedInteractionAndroidTest`, `SubscribeButtonAndroidTest`, `SubscriptionConfirmationAndroidTest`, `VoiceSearchFallbackAndroidTest`, `WriteContinuityAndroidTest`, `WriteFeedbackAndroidTest`, `YoutubeThumbContinuityAndroidTest`, `design/{BottomSheetDrag,MotionScale,PanelTransition,SearchEditing,SearchPanelMotion}AndroidTest`. Others touching shared UI: `library/MainTabsHostAndroidTest`, `settings/AppearanceSettingsAndroidTest` (2), `browser/ExternalRouteAndroidTest`, `watch/IconRenderingAndroidTest`, `watch/WatchWindowAppearanceAndroidTest`, `account/AccountSettingsScreenshotAndroidTest`, `player/engine/BackMiniHandoffAndroidTest`.
UI-structure dependence: 109 androidTest files use `testTag`/`onNodeWithTag`. Nav tags asserted: `main-navigation`, `main-nav-Home`, `home-navigation`, `search-navigation`, `search-nav-You/Home`, `channel-navigation`, `post-detail-navigation`, `test-nav-*`. Format: `"<tag>-navigation"` and `"<tag>-nav-<MainTab name>"` - **keep these tag formats** or update ~dozens of tests. Pixel dependence: the 154 goldens (home 28, playlist 18 are largest) compare screenshots of production composables - **all will fail after a visual redesign** and must be regenerated; `WatchReferenceAndroidTest`, `HomeScreenshotAndroidTest` write device evidence/ compare references. Tests asserting the brand text: `HomeScreenshotAndroidTest.kt:548` (`"Litube"`, 28sp) and `HomeRefreshEvidenceAndroidTest.kt:89`. Tests using the old package id: see section 5 (DownloadAndroidHarness, WatchReferenceAndroidTest, casting README) - they hardcode `com.hhst.litube.debug`; after the rename they need `com.nevminhnh.mintube.debug`.

---------------------------------------------------------------------------------------------------
## 9. Risks / surprises

1. **Four copies of the bottom bar + four destination enums** - redesign should centralise into one component, but test tags (`<tag>-nav-<Name>`) and the `selected` semantic assertions are consumed by tests; the Home bar sits inline in HomeScreen (`HomeScreen.kt:~300`) and the Search bar uses a bundled sample avatar.
2. **Two `AppTheme`s and two colour systems**; `ContentActivity`'s mini overlay uses the `ui.theme.AppTheme` (no `LocalAppColors`/`LocalMotionScale`) so `DesignTokens.*` inside the mini player resolve to the Light palette default and motion scale 1f. Likely harmless today (player draws its own colours) but a trap when re-theming.
3. **Activity-per-screen, no NavHost**: tab state survives only through Activity reuse; Activities are not `configChanges`-protected (except Watch) so rotation recreates them.
4. **Mini-player layout literal `56`** (bottom bar height) in `ContentActivity.kt` must follow any bar-height change; mini overlay is full-window.
5. **`app_name` is hardcoded `Litube` in 91 locale files** (+ 4 other keys with the word) and `Downloads/Litube` MediaStore folder + `download_file_paths.xml` path + `litube-settings` backup schema id are persistent identifiers - rename of the visible name is a global resource edit, but changing storage folder names changes where existing downloads are found (`DownloadPublisherImpl.kt:282` queries `%Litube%`); changing `SCHEMA` breaks restore of old backups. New applicationId = fresh install: no data migration from `com.hhst.litube` (separate app, FileProvider authority auto-follows).
6. **Upstream coupling**: update checker + Source/Releases rows point at `HydeYYHH/litube` (`AboutActivity.kt:87,175`); cast User-Agent `Litube/1.0`; README/fastlane title.
7. **Icon set provenance**: `AppIcon` paths look like YouTube's own glyphs (Home/Shorts/Subscriptions/You/Like/Share...) - flagged for licence review; Material ones have a licence file in assets. Launcher icon uses YouTube-red (`#BB202D`) play mark traced path - needs replacement for MinTube.
8. **`Theme.App.*` are framework (`android:Theme.Material`) themes**, no AppCompat/MDC; `Theme.App.Home` is hardcoded white (night variant black); Shorts theme always black; changing brand colours requires touching `colors.xml`, `values-night/colors.xml`, `themes.xml` (dialog colours hardcoded), `Color.kt`, `AppPalette.kt` (three places for the same neutrals).
9. **Dynamic colour is unused** (code present, flag false). A redesign adopting Material You must also bridge `DesignTokens`/`AppPalette`, which ignore the M3 scheme.
10. **`NoticeText` matches English literals** emitted by VMs/domain (~60) - any wording change in backend breaks translation silently; do not edit those backend strings.
11. API<33 language wrap exists only in `NativeActivity`; `AboutActivity` (ComponentActivity), Shorts, Gallery, Download* don't call it (verify before relying on in-app language on older devices).
12. Many `values-b+xx` locale dirs are excluded from release by `localeFilters` (47 kept) but still count in `TranslationResourcesTest` (all 91 must stay complete).
13. `TransientMessage` is a raw `TextView` added to the decor, not Compose - restyling toasts means editing View code (`ui/TransientMessage.kt`).
14. No `WindowSizeClass`/tablet layout infra; tablet behaviour = width breakpoints (528/600/960) scattered per screen.
15. `Barlow Semi Condensed` font + license bundled; usage not verified (grep for `barlow` outside `res`/assets was not run).
