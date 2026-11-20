# Survey 05 - Account, Settings, Extension, Cast, Diagnostics, Feedback + Backend overview

Root: `D:/Coding_Project/MinTube/repo`; source root `app/src/main/java/com/hhst/youtubelite/` (abbrev. `yl/`). Read-only survey, verified from source. "Not found" = searched and absent.

## 0. Top findings (read first)

1. **Sign-in is OAuth device-code, NOT WebView/cookies.** `SignInActivity` shows a code + "open youtube.com/activate in browser" and polls (`account/presentation/SignInViewModel.kt:54-99`, `account/data/SignInService.kt:119-173`). A separate WebView activity (`ChatWebSignInActivity`) exists only to authorise live-chat posting / community-post votes (cookies isolated).
2. **Account switcher / sign-out / account menu live inside `library/YouActivity.kt`** (not in `account/`); `account/` holds only the sheet composables, the sign-in activity and models. Settings and About are reachable ONLY via that account menu (`YouActivity.kt:130-132`); no other entry point found.
3. **Settings are two disjoint UIs over one store** (`SettingsActivity` simple pages + `AdvancedActivity` full list) plus `ui/extension/SettingsDataScreen` (backup) and `ui/about/AboutActivity` (update/diagnostics). `Extension.catalog()` (the old data-driven catalog) is used by unit tests only, not production UI (`ExtensionCatalogTest.kt:14,26,65,70,73`). `ENABLE_IN_APP_MINI_PLAYER` has a default and is consumed by the player but is rendered in NO settings screen.
4. **Diagnostics (MUST KEEP) is fully backend + exposed only through `ui/about/AboutActivity`** ("Export logs" share, "Save logs" SAF zip). Plus in-context copyable failure reports (playback, cast, account action). Also github.com/HydeYYHH/litube is contacted at exactly 3 places, all in `AboutActivity.kt` (section 7).
5. **No Koin ViewModels except `DownloadViewModel`.** Almost every other ViewModel is created in its Activity via `ViewModelProvider.Factory` from `ContentRepositories` (a Koin `single`). Identity/revision plumbing is hand-written per Activity (`observeAccountChanges(::syncSession)` -> `vm.updateSession(signedIn, generation)`). A UI rewrite that moves VM creation (e.g. to `koinViewModel()`) must preserve this wiring.

---

## 1. Package map

### `account/` (697 lines)
| File | Lines | Purpose |
|---|---|---|
| `account/SignInActivity.kt` | 198 | Compose host of device-code flow; fixtures branch (debug) injects fake `SignInService`; exposes `currentUrl` for instrumentation; testTags `signin-screen/-loading/-ready/-code/-open/-copy/-completing/-success/-error/-retry` |
| `account/SignInFlow.kt` | 14 | `SignInFlow.open(context)` = start `SignInActivity`; `SIGN_IN_URL` const (unused for the device flow, points at m.youtube.com/signin) |
| `account/ChatWebSignInActivity.kt` | 169 | WebView (plain Views, hard-coded colors 0xff0f0f0f/0xfff1f1f1, `LinearLayout/Button/TextView`) used to authorise chat/post; closes on verify; extras `EXTRA_POST_AUTHORIZATION`; launched from `post/PostDetailActivity.kt:74`, `post/PostVotes.kt:39`, `watch/WatchActivity.kt:219` |
| `account/data/SignInService.kt` | 74 | `interface SignInService {loadCode(); poll(code); complete(token): List<Account>}` + `MediaSignInService` (MediaClient reads, `MediaSession.completeSignIn`, `updateAccounts`) |
| `account/domain/AccountModels.kt` | 23 | `AccountMenuAction` enum (ViewChannel, SwitchAccount, Settings, About, SignOut; English `label` + test `tag`), `AccountItem(id,name,email,photo,selected)`, `SignInUiState` (Loading/Ready(userCode,verificationUrl)/Completing/Success/Error(message)), `SignInAction.Retry` |
| `account/presentation/SignInViewModel.kt` | 62 | single job; `start()` cancels previous job; rethrows `CancellationException`; maps `MediaException.failure` codes to English messages |
| `account/presentation/AccountMenuSheet.kt` | 157 | `AccountMenuHeader(account: AccountProfile, onOpenChannel)`, `AccountSwitchSheet(accounts, onSelect, onAddAccount, onDismiss, deviceSelected, onUseDevice)`, top-level `accountMenuActions`/`accountMenuItems` |

### `settings/` (670), `extension/` (574), `ui/extension/` (274), `feedback/` (33)
| File | Lines | Purpose |
|---|---|---|
| `settings/SettingsActivity.kt` | 107 | 4 pages via `rememberSaveable` page arg (`SettingsPage`: Home/General/Playback/Notifications); owns `preferences` snapshot map refreshed by `ExtensionManager.addOnChangedListener`; theme -> `extensionManager.setThemeMode` + `AppAppearance.update`; language -> `AppLocale.apply` (recreate on API<33); forces light system bars (`:42`) |
| `settings/AdvancedActivity.kt` | 63 | Hosts `AdvancedScreen` or `SettingsDataScreen` (toggle `data` bool); `HapticsSettings` state holder |
| `settings/presentation/SettingsScreen.kt` | 219 | Pure UI; `RadioDialog` (shared with Advanced) |
| `settings/presentation/AdvancedScreen.kt` | 193 | Pure UI, long page; `HapticsSettings` class; gesture rows pair windowed+fullscreen keys into a 4-way radio |
| `settings/presentation/SettingsRows.kt` | 79 | `SettingsGroup/SettingsToggle/SettingsRow/SettingsLabels` (tags `settings-toggle-<key>`, `settings-toggle-state-<key>`) |
| `settings/domain/SettingsModels.kt` | 9 | `SettingsPage` enum |
| `extension/PreferenceKeys.kt` | 116 | keys + `DEFAULTS: Map<String,Boolean>` (backend - keep) |
| `extension/ExtensionManager.kt` | 173 | boolean toggle store, prefix `preferences:`, version counter, change listeners (listeners may be called off main), `importPreferences`, `resetToDefault`, haptic/theme/language accessors, gesture-legacy migration (backend - keep) |
| `extension/Extension.kt` | 164 | settings catalog tree (GROUP/TOGGLE/NAV/SLIDER) - test-only in prod |
| `extension/SettingsBackup.kt` | 110 | JSON export/import, schema `litube-settings` v1, strict parser, 512 KB cap (backend - keep) |
| `extension/PrefStore.kt` | 11 | KV interface implemented by `core/PreferenceStore` |
| `ui/extension/SettingsDataScreen.kt` | 274 | Backup/Restore screen (uses `koinInject()` for `ExtensionManager`/`DownloadPrefs` inside the composable; uses raw `MaterialTheme`+`SettingsTokens`, not `DesignTokens`) |
| `feedback/presentation/FeedbackSheet.kt` | 33 | **Not a bug-report feature**: a thin wrapper of `MenuBottomSheet` for "Not interested / Don't recommend channel" reason lists (used by `home/presentation/HomeScreen.kt:249`, `watch/presentation/WatchScreen.kt:204`). `FeedbackReason(label, tag, labelResource?)` |

### `cast/` (4,802 lines, backend + one small VM-state surface; NO Compose UI in the package)
- Controller layer: `CastController.kt` (392; Chromecast, `CastUiState`, `CastDevice`), `DlnaController.kt` (435; `DlnaUiState`, `DlnaEngine`), `CastRemotePlayer.kt` (152, Media3 `SimpleBasePlayer` over Cast V2), `DlnaRemotePlayer.kt` (222), `CastFailureReport.kt` (34).
- Protocol: `cast/protocol/` `CastV2Session` (461), `CastV2Channel` (399), `CastMessageCodec`, `CastV2Discovery` (mDNS/NSD), `CastDeviceAuth`, `MediaStatus`, `CastTrustData`.
- Media serving: `LocalStreamProxy.kt` (790; local HTTP proxy that serves manifest/segments to the receiver), `CastManifest.kt`, `SidxParser.kt`, `CastIndexLoader.kt`, `BoundedProxyRunner.kt`, `LocalCastNetwork.kt`, `Dlna{Discovery,Transport,Description}.kt`.
- **Cast UI is in `player/`**: `player/surface/PlayerCastDialog.kt` (device picker `CastDialog`), `player/surface/CastFailureDialog.kt`, `player/surface/PlayerControls.kt:483` (`CastStatusBanners`), wired through `player/PlayerViewModel.kt` (see section 4). The player agent owns those files.

### `diagnostics/` (1,649, all backend, keep untouched)
`AppLog.kt` (245; facade), `DiagnosticRecorder`, `DiagnosticFiles` (rotation), `DiagnosticArchive` (zip writer), `DiagnosticNetwork` (OkHttp interceptor + transfer progress watchdog), `AndroidDiagnosticState` (lifecycle, connectivity, main-thread heartbeat), `PlaybackTelemetry` (Media3 `AnalyticsListener`), `PlaybackFailureReport`, `ActionFailureReport`, `CastDiagnosticTrace`, `DiagnosticEvent/Json/Tasks/ProgressWatchdog`, `Documents.save`.

### Backend overview (architecture only)
- **`core/` (1,703)** - cross-cutting infra, no feature logic: `PreferenceStore` (SharedPreferences KV, implements `PrefStore`, `PreferenceStore.app(ctx)`/`.of(ctx,name)`), `PreferenceJsonCache`/`JsonCache`, `ContentSnapshots` (disk page snapshots), `AppNotice` (sealed: `SignIn`, `Message`, `LocalizedMessage`, `Failure(text, ActionFailureReport)`), `AppLocale` (+`AppSettings`), `CredentialCipher` (AndroidKeyStore AES-GCM for auth prefs), `UrlPolicy`, `PageKind`, `YoutubeThumbnail` (image pipeline), `WriteFeedback`, `HapticsController`, `PipAutoEnter/PipSupport`, `WebViewMainGate`/`WebViewTimerOccupancy`, `TrafficVerificationActivity` (WebView challenge, manifest-registered), `Constants` (genuine UA holder).
- **`data/media/` (4,118; only sub-package)** - repository implementations. Domain *interfaces* live in the feature packages (`home/domain`, `watch/domain`, `library/domain`...); `data/media` implements them: `MediaHomeRepository`, `MediaSearchRepository`, `MediaWatchRepository` (+`CachedWatchRepository`), `MediaPlaylistRepository`, `MediaChannelRepository`, `MediaLibraryRepository`, `MediaSubscriptionsRepository`, `MediaExploreRepository`, `MediaShortsRepository`, `MediaSaveRepository`, `MediaPostRepository`, `MediaCommentsRepository`, `MediaVoteRepository`, `MediaVideoActions`, `MediaAvatarDirectory`, `MediaSearchSuggestions`, `LocalLibraryStore` (on-device history/playlists/watch-later). Aggregated by `ContentRepositories` (lazy vals: home, search, watch, playlist, posts, channel, votes, videoActions, saves, library, subscriptions, explore, avatars, suggestions, shortsEntry, `shorts(startId,channelId)`, `comments()`, `localLibrary`).
- **`extractor/` (4,238)** - playback extraction on the MediaServiceCore (`com.liskovsoft.youtubeapi.*`, pinned release): `Extractor` (returns immediately, work on a scope; epoch-guarded cache), `PlaybackHost` interface (`extractor/Extractor.kt:16`), `YoutubeExtractionHost`, `YoutubeSessionProvider` (WebView session capture), `PoTokenProvider`, `AndroidChallengeSolver`/`RemoteEjsRuntime`/`JavascriptRuntime` (signature solver, separate process `EjsRuntimeProcess`), `MediaRequestPlan` (identity + byte-window constraints), `MemCache/LayeredCache/DiskCache`. UI should only ever touch it through `PlaybackApi`/`PlayerViewModel`.
- **`di/AppModule.kt` (355)** - one Koin module `appModule`, started in `App.onCreate` (`App.kt:50-54`). Singles: `OkHttpClient` (DiagnosticNetwork-instrumented; connect 20 s/read 45 s/write 30 s), `MediaClient(get(), MediaSession::freshContext)`, `ContentRepositories`, `PlaybackWatchReporting`, `PreferenceStore`, caches, `ExtensionManager`, `DownloadPrefs`, `HapticsController`, extractor graph, download graph (Room `downloader.db`, coordinator, engine, scheduler, notifications), player (`PlayerDataSource`, `PlaybackApi` = `PlaybackEngine`, `SponsorBlockManager`, `DeArrowService`, `QueueRepository`, `MiniPlayerStore`), `CastController`, `DlnaController`, and **process-scoped `PlayerViewModel`** (a plain `single`, not `viewModel`). The only `viewModel { }` is `DownloadViewModel` (`AppModule.kt:350-`).

### Key backend interfaces and propagation of identity
- **`MediaClient`** (`data/media/MediaClient.kt`; 243 lines): `suspend read{}`, `readIdempotent{}` (one bounded retry, transport-only), `readBackground{}` (2-permit semaphore), `readTogether`, `write{}` (single attempt, `forWrites()` disables `retryOnConnectionFailure`), `desiredStateWrites` (idempotent desired-state recovery). Fixed pool of 4 "media-read" threads, 20 pending permits. Every attempt re-checks `session.generation` vs `context().generation` and throws `MediaException(AUTH,"session_changed")`; cancellation cancels in-flight OkHttp calls. Responses > 8 MB rejected (`response_too_large`).
- **`MediaSession`** (`data/media/MediaSession.kt`): global object. `snapshot(): RequestContext` (has `generation`, `signedIn`, `accountId`, tokens); `changes: StateFlow<MediaSessionIdentity(generation, signedIn)>`; `accounts()`, `selectedAccount()`, `selectAccount(id)`, `signOut()`, `completeSignIn(...)`, `updateAccounts(accounts, expectedGeneration)`, `anonymousMode()/setAnonymousMode(Boolean)` ("On this device"), `privateBrowsing()` (lambda injected at `App.kt:48-50` reading `ExtensionManager`). Credentials persisted in `PreferencesAuthStore` ("media-auth" SharedPreferences, each value AES-GCM sealed). **Generation bumps on every identity change** (switch, sign out, anonymous toggle via `identityEpoch`); in-flight reads from the previous identity abort.
- **How VMs learn about identity change**: `ui/AccountChanges.kt` `ComponentActivity.observeAccountChanges{}` collects `MediaSession.changes` under `repeatOnLifecycle(STARTED)`; Activities call `vm.updateSession(signedIn, generation)`. Used by Channel, Home, History, Playlists, You, Playlist, PostDetail, PostVotes, Search, Subscriptions, Watch activities (grep list in `ui/AccountChanges.kt` consumers). VMs bump `sessionEpoch/scopeEpoch`, cancel jobs, `writes.reset()` (`home/presentation/HomeViewModel.kt:129-140`).
- **Private browsing propagation**: persisted bool `PRIVATE_BROWSING` in `ExtensionManager`; `MediaSession.privateBrowsing()` is polled (not observed) by: `WatchTimeReporter.kt:95,155`, `PlaybackWatchReporting.kt:160,198,214,227` (+ `onPrivacyChanged()` listener registered in `AppModule.kt:105-108`), `MediaSearchSuggestions.kt:71` (no search-history save), `MediaResumePosition.kt:9-23`, `ContentRepositories.kt:40` (shorts entry disabled), `PlaybackEngine.kt:710,1833,1842,2186,2245`, `ShortsMediaPreloader.kt:112+`. **No UI indicator of private mode exists** (searched "incognito", `privateBrowsing` in home/search/watch/ui: none). Only the Settings toggle (`SettingsScreen.kt:144`) and strings `settings_private_browsing[_summary]`.
- **Fixtures**: debug-only offline mode (`ContentSource.fixtures`, `ContentRepositories.fixtures(intent)` = `BuildConfig.DEBUG && (...)`) swaps repositories in each Activity factory; androidTest goldens rely on it. A rewrite must keep the fixtures branches intact.

---

## 2. Screens, ViewModels and contracts

| Screen / host | Driver | State / events | Calls |
|---|---|---|---|
| `SignInActivity` (full screen) | `SignInViewModel(service: SignInService)` created in-Activity | `uiState: StateFlow<SignInUiState>`; `onAction(SignInAction.Retry)`; activity `finish()`es on `Success` | `SignInService.loadCode/poll/complete` -> `MediaClient.read`, `MediaSession.completeSignIn/updateAccounts` |
| Account menu sheet (`MenuBottomSheet` with `AccountMenuHeader`) | `YouViewModel` (`library/presentation/LibraryViewModels.kt:93-160`) | `LibraryOverlay.Account`; `LibraryAction.OpenAccountMenu` / `AccountSelected(AccountMenuAction)`; VM emits `LibraryNavigationRequest.{Channel,SwitchAccount,SignIn,SignOut,Settings,About}` | rendered in `library/YouActivity.kt:215-246` (signed-out variant: only Switch/Settings/About, `:99` in `YouScreen.kt` shows a "More" button when signed out) |
| `AccountSwitchSheet` | **No VM** - Activity-local `showSwitchSheet` (`YouActivity.kt:84,257-285`) | builds `AccountItem` list from `MediaSession.accounts().filterNot{disabled}`; "On this device" row = `setAnonymousMode(true)`; select = `MediaSession.selectAccount(id)` then `youViewModel.updateSession(...)` | direct `MediaSession` calls in composition scope |
| Sign-out confirm `AppAlertDialog` | Activity-local `confirmSignOut` | `MediaSession.signOut()` + `updateSession` (`YouActivity.kt:288-310`), tag `account-signout-confirm` | same |
| `SettingsActivity` / `SettingsScreen` | no VM; `ExtensionManager` read/written directly from Activity lambdas | `preferences: Map<String,Boolean>` (re-read on any change), `notificationsEnabled`, `page` (saveable); callbacks `onToggle(key,bool)`, `onSelectTheme(ThemeMode)`, `onSelectLanguage(tag)`, `onNavigate`, `onAbout`, `onAdvanced`, `onOpenNotificationSettings` | `ExtensionManager.setEnabled/setThemeMode/allPreferences`, `AppAppearance.update`, `AppLocale.apply` |
| `AdvancedActivity` / `AdvancedScreen` | no VM; `HapticsSettings` holder | `onToggle`, `onGestureChange(Map)` -> `importPreferences(map,null)`, `onOpenData`, `onOpenDownloads` (`DownloadUi.openManager`) | `ExtensionManager.setEnabled/setHapticStrength/importPreferences` |
| `SettingsDataScreen` | none; local `remember` + `koinInject` | export: `CreateDocument("application/json")` file name `litube-settings.json`; import: `OpenDocument`, parse (<=512 KB) -> confirm dialog -> `SettingsBackup.apply` -> "reload pages" dialog (`onReloadPages`) | `SettingsBackup.export/parse/apply`, `AppLog.operation(STORAGE,...)` |
| `AboutActivity` / `AboutScreen` (`ui/about/`) | none; `action{}` helper in Activity (busy flag, `AppLog.operation`) | rows: Source, Check update, Releases, Clear cache, Export logs, Save logs; status text | OkHttp GET (update), `AppLog.export`, cache owners clear |
| `ChatWebSignInActivity` | none | `EXTRA_POST_AUTHORIZATION` | `MediaSession.snapshot/selectedAccount`, `WebViewTimerOccupancy` |
| `FeedbackSheet` | caller VM overlay (`HomeOverlay.Feedback`, `WatchOverlay.Feedback`) | `onSelect(FeedbackReason)` | n/a |
| Cast picker `CastDialog` | `PlayerViewModel` (process singleton) `uiState` fields `casting, castingDeviceName, castDevices: List<CastDevice>, castLinkUrl, castLinkConnected, castFailure: CastFailureReport?` (`player/PlayerViewModel.kt:134-139`) | callbacks (`player/surface/PlayerActions.kt:57-70`): `onCastStop`, `onCastDevice(id)`, `onCastFailureDismiss`, `onShareCastLink`, `onCloseCastLink`, `onCastDiscovery(enabled)`, `refreshCastState` | `CastController` (`state: StateFlow<CastUiState>`, `selectDevice/startCasting/stopCasting/endSession/setDiscoveryEnabled`), `DlnaController` (same shape, device ids prefixed `DlnaController.ID_PREFIX`) |

Data flow for settings: `ExtensionManager` (PrefStore under key prefix `preferences:`) -> Activity `remember{allPreferences()}` snapshot -> composable pure function of the map. Writes go through the manager only; listener refreshes the map ("writes, never mirrors", `SettingsActivity.kt:68`). Account data flow: `MediaSession` (global) -> Activity (reads `accounts()` inline during composition, `YouActivity.kt:262-267`) -> sheet.

---

## 3. Feature matrix

### Account
| Feature | Status | Data side | UI side |
|---|---|---|---|
| Anonymous use | WORKS | `MediaSession` visitor identity (`KEY_VISITOR*`, `rememberVisitor`), `AnonymousHomeIdentity.kt` | all feeds work signed-out; sign-in prompts in You tab |
| Sign in | WORKS (device-code, not WebView) | `account/data/SignInService.kt`; client id/secret fetched via `loadSignInCredentials` and cached | `SignInActivity`; triggered by `AppNotice.SignIn` (`ui/Notices.kt:14`) and You-tab pill (`YouScreen.kt:64,223,295`) |
| Multiple accounts + switching | WORKS | `MediaSession.accounts()/selectAccount` (persisted `auth:accounts`), `Account.disabled` filtered | `AccountSwitchSheet`, reachable only via You tab -> account menu -> "Switch account" (`LibraryViewModels.kt:139-141`: if not signed in and no saved accounts it goes straight to SignIn) |
| "On this device" (anonymous while keeping account) | WORKS (extra) | `setAnonymousMode`, generation `identityEpoch` | first row of the switch sheet (tag `account-switch-device`) |
| Add account | WORKS | `completeSignIn` | "Add account" row -> `SignInFlow.open` |
| Private/incognito browsing | WORKS (no indicator) | `PRIVATE_BROWSING` pref, see section 1 | Settings > General > Privacy toggle only |
| Sign out | WORKS | `MediaSession.signOut()` wipes tokens + `ChatWebSessionStore.KEY` (keeps client id/secret/device id) | confirm dialog in `YouActivity.kt:288` |
| Account menu | WORKS (limited) | `AccountMenuAction` | header (avatar, name, handle -> channel) + Switch/Settings/About/Sign out; bottom bar shows avatar (`HomeScreen.kt:302-323`) |
| Manage accounts / Google account page / Your data | MISSING | none | not found (searched for account-management URLs, none) |
| Live-chat / post authorisation | WORKS (extra) | `ChatWebSessionStore` (watch/chat) | `ChatWebSignInActivity` |

### Settings
| Feature | Status | Notes / evidence |
|---|---|---|
| Theme light/dark/system | WORKS | `ThemeMode` (`ui/theme/AppStyle.kt:15`), persisted as long ordinal `preferences:theme_mode` (`ExtensionManager.themeMode`), applied at boot `App.kt:57-59`, radio dialog `SettingsScreen.kt:128-167`. Not part of backup export. |
| Language selection | WORKS | `AppLocale.OPTIONS` = 49 entries incl. `vi` (list in `core/AppLocale.kt:16-65`), API33+ `LocaleManager`, older: wrap + `recreate()`. Stored by `AppSettings` at `preferences:app_language`. `ExtensionManager.setAppLanguage` exists but SettingsActivity uses `AppLocale.apply`. Not in backup. |
| Player settings | WORKS | see key table (remember quality/speed/position/resize, autoplay, original title, PiP, background play) |
| Gesture settings | WORKS | 7 gestures x {windowed, fullscreen}; Advanced shows one row per gesture with 4-way radio; consumed by player (`GestureMath` zones). Legacy `enable_player_gestures` only seeds. |
| SponsorBlock | WORKS (categories only) | 9 category toggles + countdown (`AdvancedScreen.kt:77`); `SponsorBlockManager` in DI; server `https://sponsor.ajay.app`. No per-category action mode (skip/mute/ask), no segment colours, no server URL setting - MISSING |
| DeArrow | WORKS | titles + thumbnails toggles; `dearrow/` package; `dearrow-thumb.ajay.app` |
| Download settings | WORKS | `DownloadPrefs` (wifi only, max connections 1-16 default 4, default quality "1080p", last config) edited in `downloader/ui/DownloadScreens.kt:451-470` (opened from Advanced > Downloads); included in backup |
| Data import/export | WORKS | JSON file via SAF, prefs + haptic + download prefs; theme/language/queue/history/subscriptions NOT backed up |
| Notifications | WORKS (system link only) | shows on/off, opens system settings |
| Data saver | WORKS | `DATA_SAVER` consumed by `HomeActivity`, `PlaybackEngine`, `ShortsMediaPreloader` |
| Haptic strength | WORKS | slider 0-100 default 30, `HapticsController` |
| Captions/quality-on-mobile-data/default playback speed/default tab/clear history/auto-sign-in etc. | MISSING | no keys in `PreferenceKeys` |
| Clear cache / app version | WORKS | `AboutActivity` |

#### Complete settings list (group, key, type, default, where rendered)
All stored as `preferences:<key>` in `PreferenceStore.app()`; bool unless noted. "Basic" = `SettingsActivity` (`settings/presentation/SettingsScreen.kt`), "Adv" = `AdvancedScreen.kt`.
| Group | Key | Type | Default | Rendered |
|---|---|---|---|---|
| Appearance | `theme_mode` | long ordinal (0 System,1 Light,2 Dark) | 0 | Basic General (dialog) |
| Appearance | `app_language` | string BCP-47, "" = system | "" | Basic General (dialog) |
| Feed | `enable_hide_shorts` | bool | false | Basic General |
| Privacy | `private_browsing` | bool | false | Basic General |
| Privacy | `data_saver` | bool | false | Basic General |
| Playback | `enable_background_play` | bool | true | Basic Playback |
| Playback | `enable_pip` | bool | true | Basic Playback |
| Playback | `remember_last_position` | bool | true | Basic Playback |
| Interface (Adv) | `haptic_strength` | long 0-100 | 30 | Adv slider |
| Interface | `enable_display_dislikes` | bool | true | Adv |
| Interface | `enable_show_likes` | bool | true | Adv |
| Player (Adv) | `remember_quality` | bool | true | Adv |
| Player | `remember_playback_speed` | bool | false | Adv |
| Player | `enable_autoplay` | bool | true | Adv |
| Player | `remember_resize_mode` | bool | false | Adv |
| Player | `use_original_title` | bool | false | Adv |
| Player | **`enable_in_app_mini_player`** | bool | true | **NOT rendered anywhere** (read in `player/PlayerViewModel.kt`, `watch/WatchActivity.kt`) |
| Gestures | `gesture_{tap,double_tap,long_press,brightness,volume,seek,fullscreen}_{windowed,fullscreen}` (14 keys) | bool | all true | Adv (7 rows, each a 4-way radio over the pair) |
| Gestures | `enable_player_gestures` | bool legacy | n/a | never rendered, seeds 14 keys once (`ExtensionManager.migrateGestures`) |
| SponsorBlock | `skip_sponsors, skip_self_promo, skip_interaction, skip_intro, skip_outro, skip_preview, skip_music_offtopic, skip_filler, skip_poi_highlight` | bool | all true | Adv |
| SponsorBlock | `sponsor_countdown` | bool | true | Adv |
| DeArrow | `dearrow_titles`, `dearrow_thumbnails` | bool | false, false | Adv |
| Hidden | `preferences:version` | long | 0 | internal change counter |
| Download (`PreferenceDownloadPrefs`, different keys, no prefix) | `download.wifi_only` bool false; `download.max_connections` int 4 (1..16); `download.default_quality` string "1080p"; `download.last_config` JSON | | | `downloader/ui/DownloadScreens.kt:451+` |
| Auth (not settings) | `auth:*`, `visitor:*` in "media-auth" prefs | | | none |

Dead/undiscoverable: Advanced screen omits PiP/background/mini-player/remember-position (they are Basic), so the old catalog's "background_mini_player" group is split across screens.

### Cast
| Feature | Status | Evidence |
|---|---|---|
| Discover devices (Chromecast via mDNS) | WORKS | `cast/protocol/CastV2Discovery.kt`, `CastController.setDiscoveryEnabled/applyDiscovery` |
| Discover DLNA/UPnP renderers | WORKS | `cast/DlnaDiscovery.kt` (SSDP), `DlnaController` |
| Cast button locations | WORKS, single place | Player "More" sheet row `MoreRow(R.drawable.ic_cast, R.string.cast)` (`player/surface/PlayerSheets.kt:348`, opens `PlayerDialog.Cast` `:116`); status banner tap reopens dialog (`PlayerSurface.kt:500-507`). No dedicated cast icon in the player top bar, none in Shorts (`ShortsActivity.kt:277` stubs `shareCastLink`), none in Home/Watch app bar, no system MediaRouter button. |
| Picker | WORKS | `CastDialog` (`PlayerCastDialog.kt`), merged Chromecast+DLNA list with "DLNA" tag; polls `onRefresh` every 1.5 s; discovery only while open |
| Link cast (open stream URL on any receiver) | WORKS (extra) | `LocalStreamProxy`, `castLinkUrl/castLinkConnected`, share-link action |
| Remote control UI | DATA_ONLY-ish | no dedicated remote UI: `CastController.remotePlayer()` / `DlnaController.remotePlayer()` return a Media3 `Player` and the normal player controls drive it (play/pause/seek/stop); banner `CastStatusBanners` ("Casting to X") + "Stop casting" in dialog |
| Queue on cast | MISSING | no queue code in `cast/` (grep "queue" only hits `SynchronousQueue`); `PlaybackEngine.kt:604` comment "No skipping while casting, by design"; casting one video via manifest/proxy |
| Failure diagnostics | WORKS | `CastFailureReport` text (app, Android, protocol, device, redacted route, stage, redacted stack), `CastFailureDialog` with copy button (tags `cast-failure-dialog/-report/-copy`) |

### Diagnostics (keep)
Collected: structured events `DiagnosticEvent` schema v2 (time, elapsed, sequence, process, pid, session, category in {APP, CRASH, NETWORK, BROWSER, EXTRACTOR, DOWNLOADER, PLAYER, CAST, EXTENSION, GALLERY, STORAGE}, level DEBUG..FATAL, event, `DiagnosticContext` {operation/trace/parent ids, video_id, task_id, generation, tab_id, document_generation, cast_session_id, request_id}, redacted fields, `DiagnosticFailure` with <=8 causes and sanitized frames, incident id) (`diagnostics/DiagnosticEvent.kt`, `AppLog.kt`). Sources: uncaught-exception handler (`AppLog.kt:57-61`), historical process exit reasons API30+ (crash/ANR/native), app foreground/background, network capability changes (wifi/cellular/vpn/metered, no addresses), main-thread heartbeat stall detector (5 s), Media3 `PlaybackTelemetry` (load/format/decoder), OkHttp `DiagnosticNetwork` (slow first byte, no-progress), settings snapshot `controlled_settings`/`download_settings` providers, cast trace. Storage: `files/diagnostics`, 20 MB total/16 MB ordinary, 3-day retention, ordinary events never evict critical ones. Redaction: `DiagnosticRedaction` (tokens/cookies/URLs/identities; account-failure reports allowlisted per CONTRIBUTING).
Export zip (`AppLog.export`, `cache/diagnostic-exports/litube-diagnostics-*.zip`, 1-day cleanup): `summary.md`, `timeline.jsonl`, `incidents/<id>.jsonl`, `context.json` (app version, build id/type, SDK, manufacturer/model, WebView version, dependencies, free storage, snapshots/providers), `manifest.json` (coverage per process, truncation stats, deadline-stopped files). 5 s global deadline; partial archives are flagged.
UI:
- `AboutActivity` -> "Export logs" (`ACTION_SEND` chooser via FileProvider `$packageName.download.fileprovider`, `AboutActivity.kt:128-141`) and "Save logs" (`CreateDocument("application/zip")`, `Documents.save`, `:20-49,142-166`). Status strings `about_*`.
- Per-failure copyable reports: `PlaybackFailureDialog` (`player/surface/PlaybackFailureDialog.kt`, tags `playback-failure-dialog/-report`, retry), `CastFailureDialog`, and `ui/Notices.kt:20-30` `showFailureReport` for `AppNotice.Failure` (copy-all AlertDialog built from framework `AlertDialog.Builder`).
No UI in `diagnostics/` itself. A redesign must keep all three entry points and `AppLog.operation(...)` calls (they live in Activities/composables: `AboutActivity`, `SettingsDataScreen`).

### Feedback / update / upstream
| Item | Status | Evidence |
|---|---|---|
| In-app feedback / report-a-problem | MISSING | no screen; `FeedbackSheet` is recommendation feedback only |
| Update check | WORKS (pull, manual, upstream-bound) | `ui/about/AboutActivity.kt:85-110`, `StableVersion` compare (dev/alpha/beta/rc/snapshot builds report "development version") |
| Auto update / install | MISSING | no downloader of APK |

---

## 4. Cast UI vs backend split (for redesign)
Backend (do not touch): everything in `cast/`, `PlayerViewModel` cast orchestration (`onCastDevice` `:1162`, `onCastStop` `:1139`, `onCastDiscovery` `:1181`, `startDlnaCast`, `showCastFailure`, `refreshCastState`, `currentCastLink`, `initializeCast` called from `watch/presentation/WatchPlayer.kt:105`), `PlaybackEngine` cast hooks (`onCastError`, `onCastManifestReload`, `pauseLocal`).
Presentation: `player/surface/PlayerCastDialog.kt` (`CastDialog`, `LearnAboutCastingLink` -> support.google.com/googlecast/answer/6102923), `CastFailureDialog.kt`, `CastStatusBanners` in `PlayerControls.kt:483-550`, `MoreRow` cast entry in `PlayerSheets.kt:348`, `PlayerUi.CAST_BANNER_CLEAR_DP` layout offset (`PlayerSurface.kt:481`). Hard-coded: `PlayerUi.YtRed`, `fontSize 11/12.sp`, `44.dp` row height, literal "DLNA" label, `delay(1_500)` polling loop in the composable (`PlayerCastDialog.kt` LaunchedEffect) - this polling and the discovery enable/disable `DisposableEffect` are behavior and must survive a restyle.

---

## 5. UI coupling and redesign notes

Pure presentation (safe to rewrite): `settings/presentation/*`, `account/presentation/AccountMenuSheet.kt`, `AboutScreen.kt`, `FeedbackSheet.kt` (keep signature), layout parts of `SignInActivity`/`SettingsActivity`/`AdvancedActivity`, `PlayerCastDialog.kt` visuals.

Must be preserved when rewriting:
- **Logic in Activities/composables**: `YouActivity` account sheets (identity switch + `updateSession`), `SettingsActivity` listener lifecycle and `AppAppearance.update`/`AppLocale.apply`+`recreate()`, `SettingsDataScreen` SAF launchers + `AppLog.operation` + `fileOperations` array tracking, `AboutActivity.action{}` busy gating and `clearCache` owner list (HTTP cache, `YoutubeThumbnail`, DeArrow, avatars, watch cache, snapshots, MemCache, extractor KV prefixes, gallery/thumbnail dirs - deliberately NOT account/downloads/queue/SimpleCache).
- **Gesture settings semantics**: the Advanced gesture row writes TWO keys atomically via `onGestureChange -> importPreferences`; windowed/full rows are zipped by position (`AdvancedScreen.kt:138-160`), so reorder = bug.
- **Test tags / semantics asserted by tests** (see section 6): `account-menu`, `account-menu-header`, `account-action-{view,switch,settings,about,signout}`, `account-switch`, `account-switch-device`, `account-switch-<id>`, `account-switch-add`, `account-signout-confirm`, `you-account`, `signin-*`, `settings-screen`, `settings-topbar`, `settings-page-<home|general|playback|notifications>`, `settings-row-{general,playback,notifications,advanced,about,theme,language}`, `settings-toggle-<key>` / `settings-toggle-state-<key>`, `settings-notifications-state`, `settings-open-notification-settings`, `theme-dialog[-option-N]`, `language-dialog[-option-N]`, `advanced-screen/-topbar/-page`, `advanced-row-{data,downloads}`, `gesture-<key>`, `gesture-scope-dialog`, `feedback-sheet`, `cast-failure-*`, `playback-failure-*`.
- **Accessibility present**: `appPaneTitle`, `appDescription(R.string.a11y_close_menu)` scrim, `Role.Button/Switch/RadioButton`, `toggleable`/`selectable` whole-row semantics, min heights 48 dp (settings rows), 44 dp radio rows, `AppIconButton(..., diameter = 48.dp)`. Gaps: `AboutScreen` rows lack Role.Button; cast device rows are `clickable{}` w/o Role and icon `contentDescription = null`; `SettingsDataScreen.ActionRow` has icon decorative but row ok; `ChatWebSignInActivity` plain Views.
- **Hard-coded strings (not in resources)**: `AccountMenuAction.label` English (`AccountModels.kt:3-8`; localized via `ui/design/LocalizedLabels.kt:50` `labelResource` in the real UI, the raw labels are only used for `accountMenuItems` and tests); `SignInViewModel.messageFor` English messages (not localized; rendered through `noticeText()`); `ThemeMode.label`; `AppLocale.OPTIONS` native names (intentional); `SettingsBackup` file name `litube-settings.json`, schema `litube-settings` (compat - keep or old backups fail); "DLNA"; cast report text "Litube cast report".
- **Hard-coded colors/dimens**: `SettingsActivity` system bars forced `SystemBarStyle.light(WHITE, WHITE)` (`:42`) even in dark mode (also `YouActivity.kt:93`); `SignInActivity` button text `Color.White`; `ChatWebSignInActivity` hex colors; settings use literal 16/20/48 dp rather than `SettingsTokens`; `SettingsDataScreen` and `AboutScreen` use Material3 `MaterialTheme.colorScheme` while Settings/Advanced/Account use `DesignTokens` (two dialects, so light/dark parity must be re-checked per screen).
- **Design-system components used**: `ui/design`: `AppText`, `AppIcon`, `AppIconButton`, `DesignTokens`, `MenuBottomSheet`/`MenuSheetItem`/`MenuSheetLayout`, `Artwork`, `appPaneTitle`, `appDescription`, `rememberHapticAction`, `setAppContent`; `ui/components`: `AppTopAppBar`, `AppTextButton`, `AppAlertDialog`, `AppSwitch`, `AppSlider`, `ConstrainedPage`; `ui/theme`: `AppStyle`, `AppAppearance`, `ThemeMode`, `SettingsTokens`, `AppDialogContext`.
- **Tablet/landscape**: Settings/Advanced/Data/About cap width at 720 dp (`widthIn(max=720.dp)` / `ConstrainedPage`); account sheets use `MenuSheetLayout.sheetWidth(maxWidth, maxHeight)`; goldens at 320/390/1280, font-scale 2 and landscape exist. SignInActivity: single scroll column, no width cap.
- **Scroll-state**: settings pages use `rememberScrollState()` that is not saveable across page switches (page arg is `rememberSaveable`, scroll is not); switching pages resets scroll. `SettingsScreen` creates one verticalScroll per page via `Column` keyed by composition (same state instance reused across pages since not keyed - a latent oddity).
- **Back handling**: `BackHandler(enabled = current != Home)` in SettingsActivity; Advanced uses `data` flag; `YouActivity` chains overlays in `back` lambda (`:149-157`).
- **Koin in composables**: `rememberHideShorts()` (`ui/design/ContentPreferences.kt`) uses `GlobalContext.getOrNull()` + listener, `SettingsDataScreen` uses `koinInject()`. `KoinGraphAndroidTest` verifies graph.
- **Settings consumption by UI**: `LocalHideShorts` composition local fed by `ENABLE_HIDE_SHORTS` listener (`ContentPreferences.kt`), `ContentPreferencesAndroidTest` asserts navigation/channel tabs/home respond without recreation - keep the listener-driven (no-recreate) behavior.

---

## 6. Tests touching these packages
Unit (`app/src/test/.../`), counts of files: account 3 (`SignInViewModelTest` 118 lines asserts state transitions/messages, `SignInServiceTest`, `MediaSignInServiceTest`), extension 4 (`ExtensionCatalogTest` - asserts catalog covers keys; `ExtensionManagerTest`; `SettingsBackupTest`; `MemoryPrefStore`), cast 13 (protocol codec, DLNA controller/transport, proxy range, SIDX, manifest - all backend), diagnostics 8, core 13, data/media ~35+ (identity/generation/retry/ownership: `MediaClientTest` 301, `MediaClientPreSendRecoveryTest`, `MediaDesiredStateTimeoutOwnershipTest`, `LibrarySourceIsolationTest`, ...), extractor 21, ui 6 (`StableVersionTest`, `TranslationResourcesTest` 87 lines checks string resources across locales). None of the unit tests depend on Compose structure; **`TranslationResourcesTest` and `ExtensionCatalogTest` depend on string resources / `Extension.catalog()` still compiling** (if `Extension.kt` is deleted, update the test).
Instrumented (`app/src/androidTest/.../`):
- `account/` 13 files: **UI-dependent** `AccountSettingsAndroidTest` (327; tags above, drives You tab -> account menu -> switch/sign-out/sign-in/settings/about via fixtures), `AccountSettingsScreenshotAndroidTest` (160; golden compare), `SignInLifecycleAndroidTest` (72; `SignInActivity`, `signin-*` tags, `currentUrl`), `MenuCollectionAndroidTest` (681). Others are live/probe tests (`LiveChat*`, `ContentIdentityProbe`, `MscLive*`) needing network (`network=1`) - backend.
- `settings/` 3: `AdvancedSettingsAndroidTest` (gesture radio pairs + recreation, toggle persistence), `AppearanceSettingsAndroidTest` (theme dialog persistence, language RTL), `ContentPreferencesAndroidTest` (hide-shorts effects) - all use test tags.
- `diagnostics/` 5: `DiagnosticSaveAndroidTest` (About "Save logs": picker cancel / success / denied provider), `DiagnosticExportAndroidTest` (218), network, perf probes. About rows are located by text/label -> will break if strings change.
- `cast/` 13: mostly software receiver/VM tests (backend). `CastVmAndroidTest`, `CastReceiverVmAndroidTest`, `DlnaRouteAndroidTest` touch `PlayerViewModel`, not the dialog visuals.
- `ui/` 20 (`AccountChangesAndroidTest`, `LocalizationAndroidTest`, `ResponsiveExperienceAndroidTest`, `WriteContinuityAndroidTest` ...), `di/KoinGraphAndroidTest`, `data/media` 23 (live MSC + Koin tests).
Golden images: `app/src/androidTest/assets/account/goldens/` 4 PNG (`account-menu-{390,390-font2,390-landscape,1280}`), `settings/goldens/` 8 PNG (`settings-home-{320,390,390-font2,390-landscape,1280}`, `settings-general-390`, `settings-notifications-390`, `settings-playback-390`), `feedback/goldens` (contents: 2 PNG), no goldens for Advanced, Data, About, Sign-in, cast dialog. **All 14+ goldens will fail after a visual redesign and must be regenerated deliberately.**

---

## 7. CONTRIBUTING.md - rules a UI/presentation rewriter could violate (100 lines read in full)

Rules and where the ViewModels already follow them:

1. **Identity + revision captured before async work, checked before publishing** ("ViewModels own feature state. Capture the request's identity and revision before asynchronous work, and check them before publishing results or notices. Propagate coroutine cancellation").
   - `search/presentation/SearchResultsViewModel.kt:242-262`: `val revision = ++menuRevision; requestGeneration = generation; requestSession = sessionEpoch; fun ownsMenu()`; after `repository.menu(...)` it calls `currentCoroutineContext().ensureActive()` then `if (!ownsMenu()) return`.
   - `home/presentation/HomeViewModel.kt:608-660` (`fillMissingAvatars`): captures `requestAccount`, `requestFilter`, `requestSession` and re-checks inside every `_state.update` and again after `delay(5_100)`.
   - `HomeViewModel.kt:129-140` `updateSession` bumps `sessionEpoch`/`scopeEpoch`, `writes.reset()`, cancels `loadJob`/`feedbackJob`.
   - `watch/WatchViewModel.kt:438-454` (`menuRevision` + `generation`), `channel/ChannelViewModel.kt:145-170`, `comments/CommentsViewModel.kt:423-444` (`stale()`).
   - `account/presentation/SignInViewModel.kt:81-82`: `catch (error: CancellationException) { throw error }` before generic `catch (Exception)`; same in `HomeViewModel.kt:295,331,453,476`.
   - **Rewriter risk**: moving "when to load / refresh / menu fetch" into composables (`LaunchedEffect`, `rememberCoroutineScope`) or collapsing VM `updateSession(signedIn, generation)` into a plain recomposition key loses `sessionEpoch/scopeEpoch/menuRevision`; wrapping VM calls in `runCatching {}` swallows `CancellationException` (flagged rule); `_state.update { }` lambdas must stay side-effect free (they read `accountKey()` etc. but do not launch).
2. **Account vs business-scope ownership are separate** ("Capture account and business-scope ownership separately. Returning to a previous filter creates a new scope"). Home: `requestAccount` (`accountKey()`) vs `requestFilter` (`selectedChip`) vs `requestSession` (`sessionGeneration`) checked separately; `scopeEpoch++` on session change. **Risk**: reusing a chip/tab list so selecting the previous chip re-uses an old `loadJob` result; UI `rememberSaveable` state restoring a filter without the VM creating a new scope.
3. **Reads vs writes ordering**: "capture the preceding job before launching; never look up a mutable job reference while waiting". Look at `home/presentation/HomeViewModel.kt` `feedbackJob`/`loadJob`; `ui/design/WriteProgress.kt` + `core/WriteFeedback.kt` (`writes.reset()`). **Risk**: a redesigned like/subscribe/save button that fires the repository call from the composable (or optimistic UI) bypasses `WriteFeedback`/`DesiredStateWriteRecovery`; **never add UI-level retry** to account mutations (single-attempt HTTP, server confirmation required, no replay of create/append/opaque-token commands; retry only via `MediaClient.desiredStateWrites` with identity+intent ownership). A "Retry" snackbar button on a failed write must route to the VM's explicit retry (e.g. `RetryPlaylistsShelf` pattern is for reads), not re-send blindly.
4. **Save sheet lifetimes**: "Opening or closing a save sheet owns its reads and form state. A background save belongs to the account until completion or account reset." (`saves/presentation/SaveSheetController.kt`, held by `YouViewModel.saveSheet`, with `WriteProgressOverlay(it.writes)` in `YouActivity.kt`). Risk: tying sheet state to composition (`remember`) instead of the controller; dismissing the sheet must not cancel an admitted background save.
5. **Rating requests carry desired + displayed previous state**; cached docs supply capabilities only. Risk: toggle buttons that compute the next state in the composable from stale UI state; the VM contract should keep passing "desired" rather than "toggle".
6. **Pagination**: preserve rows on append failure, consume cursors only after success, stop repeated cursors, keep per-query/account/section history, retain old pagination history with old rows on failed refresh. `ui/BrowseContinuationTest`, `ui/RefreshFailureContinuityViewModelTest` enforce it; `PagingFooter.kt` in `ui/components` is presentation. Risk: a pull-to-refresh/skeleton redesign that clears the list on refresh start (current code keeps rows) or triggers append on every composition.
7. **Private browsing**: "prevents new local search and viewing history; existing history remains readable and removable". Risk: a "history" screen or search suggestion UI that hides existing history when private mode is on, or a new search box that records suggestions itself (`MediaSearchSuggestions.kt:71` already blocks writes).
8. **Diagnostic reports contain only allowlisted summaries, never credentials/raw entity keys/response bodies.** Risk: showing or logging raw `MediaException` messages / URLs in new error UI; new `AppLog.event` fields must use `DiagnosticRedaction`.
9. **Optional playback metadata must not hold source submission open; Cast refreshes own chapter results** (player package; UI must not await chapters).
10. **Style**: immutable UI state, no base ViewModels/generic action runners/forwarding interfaces, explicit imports, keep unrelated cleanup separate; add regression tests for externally observable behavior. Validation commands: `./gradlew :app:testDebugUnitTest :app:lintDebug` and `:app:assembleDebug :app:assembleDebugAndroidTest`; Release/`minifiedDebug` gates for reflection/shrinking (relevant: Compose changes that add reflection). Live network tests need `network=1`; account mutations need a dedicated authorized test.
11. Download rules (generation/publish naming, FileProvider decode once) matter only if UI touches download paths (`DownloadViewModel`, `downloader/ui`).

Concrete "accidental violation" checklist for a presentation rewrite:
- Do not create VMs in composables via `viewModel()` where the Activity factory injects fixtures/`signedIn`/`SavedStateHandle` (e.g. `YouViewModel(repository, SavedStateHandle, scenario, signedIn, saves)` at `YouActivity.kt:90-97`).
- Keep `observeAccountChanges` + `updateSession(signedIn, generation)` for every screen that shows account data, including newly added screens (Settings/About do not observe account today because they show none).
- Do not read `MediaSession.accounts()` in recomposition for new screens without keying by `generation`.
- Do not catch `Exception` without rethrowing `CancellationException` in new Kotlin UI code.

---

## 8. Upstream (Litube) contact points in `app/src/main`
Searched `app/src/main` (java, res, assets, AndroidManifest) for `HydeYYHH`, `litube` + hosts, `github.com`, `jitpack`:
| URL | Where | Use |
|---|---|---|
| `https://api.github.com/repos/HydeYYHH/litube/releases/latest` | `ui/about/AboutActivity.kt:87` | manual update check (GET, `Accept: application/vnd.github+json`, parses `tag_name`, rejects prerelease/draft) |
| `https://github.com/HydeYYHH/litube` | `ui/about/AboutActivity.kt:175` (`SOURCE`) | "Source code" row (`onSource`) |
| `https://github.com/HydeYYHH/litube/releases/latest` | `ui/about/AboutActivity.kt:94`-ish via `"$SOURCE/releases/latest"` | "Releases" row |
Other github.com hits are license text only (`assets/licenses/*`, `assets/ejs/*`, `assets/cast/*.LICENSE.txt`, fonts). `HydeYYHH/MediaServiceCore` appears only in `CONTRIBUTING.md` and the Gradle JitPack dependency (build time, not runtime). For MinTube: re-point or remove these three and the strings `about_source/about_check_update/about_releases` and `AccountMenuAction.About` label "About Litube". Other runtime hosts (not upstream): youtube.com family, `i.ytimg.com`, `sponsor.ajay.app`, `dearrow-thumb.ajay.app`, `support.google.com/googlecast/...`, `accounts.google.com`, `www.google.com`, `www.blender.org` (license/attribution text), `example.com` (browser-probe intent in `SignInActivity.openBrowser`).

---

## 9. Risks / surprises
1. Branding strings in backend-adjacent code: `SettingsBackup.SCHEMA = "litube-settings"` and default file name; diagnostic zip prefix `litube-diagnostics-`; report headers "Litube cast report", "Litube playback report", "# Litube diagnostic report"; `"litube.diagnostic_context"` media-metadata extra key; FileProvider authority `$packageName.download.fileprovider`. Changing these is backend/compat - leave them or change deliberately with tests (`SettingsBackupTest`, `DiagnosticArchiveTest`).
2. Account state is global-singleton (`MediaSession`), so any Preview/Compose-UI test of a new screen that touches it must call `MediaSession.initialize(MemoryAuthStore())` first.
3. Settings reachable only from the You tab account menu; Settings back-stack: Home page -> General etc. via in-Activity page state, so process-death restoration relies on `rememberSaveable` of the page arg only.
4. `SignInActivity` extends `ui.NativeActivity` (tab animation + window plumbing); `SettingsData` flow is hosted by `AdvancedActivity`, not a separate Activity, so its `onReloadPages = { data = false }` is a no-op "reload" (pages are not WebViews anymore).
5. The "Settings" Activity forces white system bars regardless of theme; dark mode relies on `setAppContent` background only - verify status-bar icon contrast.
6. `ExtensionManager` change listeners fire on the caller thread; UI listeners write Compose state from non-main threads in places (`mutableStateOf` is thread-safe but heavy work should hop).
7. `AccountMenuAction.ViewChannel` is part of the enum but excluded from the sheet list (header click). Adding entries to the enum requires matching `when` in `LibraryViewModels.kt:139-152` (exhaustive) and `LocalizedLabels.kt:50`.
8. English-only strings reach UI through `SignInUiState.Error(message)` (not translatable); `TranslationResourcesTest` will not catch it.
9. Cast dialog keeps scanning only while shown (`onDiscovery(true/false)`); a redesign moving cast into a persistent button/route chooser must keep discovery gating (battery) and the 1.5 s refresh.
10. `ChatWebSignInActivity` returns immediately (`finish()`) when not signed in (`:23-24`), so any new "post/chat while signed out" UI must route to `SignInFlow` first.
