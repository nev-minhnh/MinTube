# 04 - Collections survey (library, playlist, saves, downloader, gallery, share)

Root: `app/src/main/java/com/hhst/youtubelite/` (abbreviated `…/`). All paths repo-relative from `app/src/main/java/com/hhst/youtubelite/` unless they start with `app/`. Read-only survey; nothing was run or built.

## 1. Package map

### library/ (2.4k lines)
| File | Lines | Purpose |
|---|---|---|
| `library/domain/LibraryModels.kt` | 184 | All models: `AccountProfile`, `HistoryEntry`, `YouPage`, `HistoryPage`, `PlaylistCollectionPage`, `LibraryContent/HistoryContent/CollectionContent` (Loading/SignedOut/Ready/Empty/Failed), `LibraryRow` enum, `LibraryOverlay`, 3 UiStates, `HistoryMenuAction`, `LibraryNavigationRequest`, `LibraryAction` |
| `library/data/LibraryRepository.kt` | 27 | Interface (see s2) |
| `library/data/FixtureLibraryRepository.kt` | 81 | Debug/test fixtures + `LibraryScenario` + `SignedOutPrompts` (hardcoded English copy) |
| `library/presentation/LibraryViewModels.kt` | 897 | `YouViewModel` (l.53), `HistoryViewModel` (l.353), `CollectionViewModel` (l.721), `verificationCompleted` extensions |
| `library/presentation/YouScreen.kt` | 368 | `YouScreen`, `HistoryScreen`(l.196), `CollectionScreen`(l.272), `HistoryUndoBanner`, `LibrarySubPageBar`, `LibraryAppendRow` |
| `library/presentation/LibraryComponents.kt` | 195 | `LibraryRowItem`, `HistoryCard`, `PlaylistShelf`, `HistoryShelf`, skeleton/error/message cards |
| `library/YouActivity.kt` | 346 | Host of "You" tab: builds VM, collects nav requests, account/switch-account sheets, sign-out dialog, queue sheet |
| `library/HistoryActivity.kt` | 249 | Host of history sub-page: overflow menu sheet, manage-history sheet, clear-confirm dialog, save sheet, search overlay |
| `library/PlaylistsActivity.kt` | 146 | Host of the playlists list sub-page |

Local (no-account) store lives in backend: `data/media/LocalLibraryStore.kt` (211), `MediaLibraryRepositories.kt` (`MediaLibraryRepository`, 360), `MediaSaveRepository.kt` (202), `ContentRepositories.kt` (58, Koin-held bundle `sources.library/.playlist/.saves/.suggestions`).

### playlist/ (1.4k lines)
- `playlist/domain/PlaylistModels.kt` (141): `PlaylistSummary`, `PlaylistEntry`, `PlaylistPage`, `PlaylistRepository` (read-only: `playlist(id)`, `playlistNext(id,cursor)`), `PlaylistContent`, `PlaylistOverlay`, `PlaylistUiState`, `PlaylistAction`, `PlaylistVideoAction`, `PlaylistNavigationRequest`, `PlaylistLayout` (width breakpoints 600/840/1024 in logic file).
- `playlist/data/FixturePlaylistRepository.kt` (57), real impl is `MediaPlaylistRepository` in `data/media` (`ContentRepositories.kt:23`).
- `playlist/presentation/PlaylistViewModel.kt` (344), `PlaylistScreen.kt` (417), `PlaylistCards.kt` (150), `PlaylistPreviews.kt` (25).
- `playlist/PlaylistActivity.kt` (223): host; contains non-trivial logic (see s5).

### saves/ (0.55k lines)
- `saves/domain/SaveModels.kt` (29): `SaveRepository`, `SaveChoice`, `SaveDestination`, `NewPlaylistDraft`, `SavePlaylist`.
- `saves/presentation/SaveSheetController.kt` (255): plain class (not a ViewModel) owned by each host VM; exposes `state: StateFlow<SaveSheetState?>`, `writes`.
- `saves/presentation/SaveSheets.kt` (269): `SaveToPlaylistSheet`, `NewPlaylistSheet`, `SheetField`, `SheetAction`.

### share/ (143 lines), gallery/ (298 lines)
- `share/ShareGateway.kt` (86, takes an `Activity`), `VideoShare.kt` (50), `PlaylistShare.kt` (7).
- `gallery/GalleryActivity.kt` (208, Compose pager for post images: save/share image), `GalleryImages.kt` (90). Only caller: `post/` (`PostDetailActivity.kt:187`, `PostVotes.kt:52`). Not a collections feature; low priority.

### downloader/ (10.7k lines total) - ENGINE vs UI split
| Sub-package | Lines | Classification |
|---|---|---|
| `downloader/core/` | 1853 | ENGINE (do not touch): `DownloadCoordinator` (957), models/state machine/ports/snapshot guard/share parser/prefs |
| `downloader/data/` | 1249 | ENGINE: Room entities/DAO/DB, `RoomDownloadRepository`, mappers |
| `downloader/engine/` | 1623 | ENGINE: WorkManager/UIDT job, foreground service types, **notifications** (`AndroidDownloadNotifications.kt`, `DownloadNotificationController.kt`), startup reconciler, network restore |
| `downloader/io/` | 919 | ENGINE: file naming, MediaStore publisher, finalizer, integrity |
| `downloader/net/` | 1037 | ENGINE: transport, connection limiter, request factory |
| `downloader/resolve/` | 945 | ENGINE: catalog (formats), selector, PoToken, resolver |
| `downloader/ui/` | 3062 | UI/presentation, partly redesignable - see below |

`downloader/ui/` files (lines): `DownloadScreens.kt` 676 (manager: list/batch/settings/history panes, delete dialog), `DownloadSheets.kt` 648 (`SingleVideoConfirmSheet`, `BatchConfirmSheet`), `DownloadComponents.kt` 368 (`DownloadItemRow`, chips, capsule, thumbnail), `DownloadPresentation.kt` 260 (pure logic: `ManagerFilter`, `PhaseCopy`, `DownloadRowAction`, filters, size/bytes formatting - logic, unit-tested), `DownloadViewModel.kt` 200 (facade, KEEP), `DownloadUiState.kt` 121 (`DownloadItemUiState`, `DownloadUiMapper`, KEEP), `DownloadSheetActivity.kt` 154, `DownloadShareActivity.kt` 96 (system share-intent entry), `DownloadActions.kt` 106 (notification action receiver + `DownloadActionActivity`; engine-adjacent, KEEP), `DownloadSelection.kt` 91 (multi-select), `DownloadUi.kt` 87 (call-site API object, KEEP), `DownloadEntries.kt` 71, `DownloadFileShare.kt` 35, `DownloadUiThrottle.kt` 58, `DownloadTokens.kt` 22, `DownloadActivity.kt` 69.

## 2. Screens, ViewModels and contracts

### Library repository contract (`library/data/LibraryRepository.kt`)
`supportsLocal`, `hasSavedAccounts`; `you(): YouPage`, `history(): HistoryPage`, `playlists(): PlaylistCollectionPage`, `historyNext(cursor)`, `playlistsNext(cursor)`, `removeFromHistory(feedbackToken): RemovedHistoryEntry?`, `restoreHistoryEntry(removed)`, `setHistoryPaused(Boolean)`, `clearHistory()`.

### Screens
| UI | Host | State holder | UiState | Action/Event types | Repos called |
|---|---|---|---|---|---|
| `YouScreen` | `YouActivity` | `YouViewModel(repository, SavedStateHandle, scenario, signedIn, saveRepository?)` | `StateFlow<LibraryUiState>` `uiState`; `navigationRequests: Flow<LibraryNavigationRequest>`; `actionNotices: Flow<AppNotice>`; `saveSheet: SaveSheetController?`; `writes: WriteFeedback`; `updateSession(Boolean, Long?)` | `onAction(LibraryAction)` | `LibraryRepository.you()`, shelf retries, `removeFromHistory/restoreHistoryEntry`, `SaveRepository` via controller |
| `HistoryScreen` | `HistoryActivity` | `HistoryViewModel` (same ctor shape; `signedIn` default true) | `StateFlow<HistoryUiState>` (+ same flows, `saveSheet`) | `LibraryAction` incl. `LoadHistoryNext`, `OpenManageHistory`, `PauseHistory`, `ResumeHistory`, `RequestClearHistory`, `ConfirmClearHistory`, `UndoHistoryRemove`, `MenuSelected` | `history()/historyNext()/removeFromHistory/restoreHistoryEntry/setHistoryPaused/clearHistory` |
| `CollectionScreen` (playlists list) | `PlaylistsActivity` | `CollectionViewModel(repository, savedState, scenario, signedIn)` (no saveSheet) | `StateFlow<CollectionUiState>` | `LibraryAction.LoadCollectionNext`, `OpenPlaylist`, ... | `playlists()/playlistsNext()` |
| `PlaylistScreen` | `PlaylistActivity` | `PlaylistViewModel(PlaylistRepository, SavedStateHandle, playlistId, scenario, saveRepository?)` | `StateFlow<PlaylistUiState>` `uiState` (`PlaylistContent`, `AppendState`, `PlaylistEnumeration`, `PlaylistOverlay`, `signedIn`); `navigationRequests`, `actionNotices`, `saveSheet`; `updateSession` | `onAction(PlaylistAction)`: Retry, LoadNext, Cancel/RetryEnumeration, OpenSearch, QueryChanged, Search, OpenMenu, CloseOverlay, PlayAll, Shuffle, Save, Share, OpenOwner, ToggleDescription, OpenVideo(entryId), OpenVideoMenu, VideoMenuAction, Destination, VoiceSearch, Download, Dismiss/OpenVerification | `PlaylistRepository.playlist/playlistNext`, `SaveSheetController.savePlaylistToLibrary` |
| `SaveToPlaylistSheet` / `NewPlaylistSheet` | rendered by the hosts (`PlaylistActivity.kt:~170`, `HistoryActivity.kt:~145`, `YouActivity`, and Search/Shorts/Subscriptions hosts; Watch and Home keep their own overlay copies) | `SaveSheetController(SaveRepository, CoroutineScope, notice)` | `StateFlow<SaveSheetState?>` {videoId, newPlaylist, title, description, choices?, choicesFailed, pending} | `open(videoId)`, `dismiss()`, `toggle(SaveChoice)`, `retry()`, `openNewPlaylist()`, `draftChanged()`, `submitNewPlaylist()`, `saveWatchLater(videoId)`, `savePlaylistToLibrary(id)`, `resetSession()` | `SaveRepository`: `choices`, `setContaining`, `saveWatchLater`, `create`, `savePlaylistToLibrary` |
| `DownloadManagerScreen` | `DownloadActivity` (setContent in own AppTheme) | `DownloadViewModel` (Koin `koinViewModel()`, `di/AppModule.kt:346`) | flows (no single UiState): `observeDownloads(DownloadFilter | ManagerFilter): Flow<List<DownloadItemUiState>>`, `observeBatch(id): Flow<BatchUiState?>`, `observeVideo(id): Flow<VideoDownloadUiState>`; screen-local `DownloadDest` (List/Batch/Settings/History) saved via rememberSaveable | `pause/resume/cancel/retryFailed/redownload(DownloadTarget)`, `remove(target, RemoveMode)`, `clearHistory(alsoDeleteFiles)`, `setWifiOnly/setDefaultQuality/setMaxConnections`, `prefs` | `DownloadCoordinator` |
| `SingleVideoConfirmSheet`, `BatchConfirmSheet` | `DownloadSheetActivity` (translucent, theme `Theme.App.DownloadSheet`, manifest l.175) | `DownloadViewModel` | local `rememberSaveable` fields (audioOnly, quality, videoItag, audioTrack, audioBitrate, includeSubtitle, subtitleLanguage, includeCover, fileName, attachmentsOnly) + `CatalogState` + `PreviewState` | `loadCatalog(videoId, fresh)`, `preview(...)`, `confirmSingle(DownloadRequest)`, `confirmBatch(snapshot, selection)`, `validateSnapshot` | `DownloadCatalogSource`, `DownloadCoordinator` |
| `DownloadShareActivity` | manifest l.142 | none | parses system SEND intent via `core/DownloadShareParser` | -> opens confirm sheet | - |
| `GalleryActivity` | `post/` | none | local | - | - |

Key `LibraryNavigationRequest` types (host contract, exhaustive `when` without `else` in each host - a new request breaks compile in all 3 hosts): Watch, OpenVerification, Channel, Playlist, Search, OpenHistory, OpenPlaylists, OpenQueue, OpenDownloads, MenuAction, Destination(MainTab), Settings, About, SignIn, SwitchAccount, SignOut, VoiceSearch. Same exhaustive pattern in `PlaylistNavigationRequest` (`PlaylistActivity.kt:~147`).

## 3. Data flow
- Library: `MediaLibraryRepository` (`data/media/MediaLibraryRepositories.kt:15`) switches per call between device store (`useDevice()` = local store present and not signed in) and remote InnerTube reads. `you()` fans out 3 `async` reads (account profile primary; history and playlists optional, failure flags `historyFailed/playlistsFailed` on `YouPage`, retry via `RetryHistoryShelf/RetryPlaylistsShelf`). VM `init` decides SignedOut vs refresh (`!signedIn && !supportsLocal` -> SignedOut prompt).
- Pagination: History/Collection VMs use `ContinuationHistory` + `AppendState`; `LoadHistoryNext/LoadCollectionNext` triggered from composable near list end; `emptyAppends` loop guard. Device history returns an empty `historyNext`.
- Session handling: each VM has `updateSession(signedIn, key)`; a changed key increments `generation`/`sessionEpoch`, cancels jobs, resets `saveSheet`, resets overlays. Hosts call it from `observeAccountChanges`. Stale-write protection lives in VMs + repository (`LibraryOperation.check()`), not composables.
- Loading/empty/error: sealed `*Content` types; skeleton (`LibrarySkeleton`), message cards (`LibraryMessageCard`), `LibraryError` retry, `VerificationCard` for `/sorry` challenge (`VerificationRequired`), pull-to-refresh via `RefreshableContent` (You only).
- Overlay state persisted in `SavedStateHandle` (`libraryOverlay()`, `confirmClear`, `query`) so rotation/process death keep sheets.
- Playlist: `MediaPlaylistRepository` -> `PlaylistPage` (first page) + `playlistNext`; cap `PlaylistLimits.MAX_ENTRIES=1000`. Play all/Shuffle/Download run `runBatch(PendingBatch)` = bounded full enumeration (`PlaylistEnumeration` Running/Failed with Cancel/Retry UI).
- Saves: `SaveSheetController` loads `choices(videoId)` (account: server `loadPlaylistChoices`, which includes the server's `contains` flag; device: `LocalLibraryStore.choices`), toggles via `setContaining`, writes show `ActionProgress`/`WriteProgressOverlay`, failure via `AppNotice`.
- Downloads: Room (`downloader/data`) -> `DownloadCoordinator.observeDownloads/observeBatch/observeVideo` -> `DownloadUiMapper` -> `.uiThrottle(critical=…)` (`DownloadUiThrottle.kt`; drops non-critical progress ticks) -> `collectAsStateWithLifecycle`. Manager hoists one `observeDownloads()` flow (comment: avoid empty flash from cold Room subscriptions).

## 4. Feature matrix (YouTube Android parity)
Status: WORKS / DATA_ONLY / MISSING.

### Library ("You")
| Feature | Status | Data evidence | UI evidence / gap |
|---|---|---|---|
| Watch history view (account) | WORKS | `MediaLibraryRepositories.kt:53-64` (`LibrarySection.HISTORY`) | `HistoryScreen` `YouScreen.kt:196`, shelf on You `LibraryComponents.kt:162`; grouped by `section` string ("Today"/"Yesterday") |
| Watch history (device/local) | WORKS | `LocalLibraryStore.kt:79-123` (`history/record/remove/restore/clear/pause`) | same screens when signed out and `supportsLocal` |
| Remove history item | WORKS | account `removeWatchHistory(feedbackToken)` `MediaLibraryRepositories.kt:65`; device `LocalLibraryStore.remove` | menu item "Remove from watch history" `HistoryMenuAction.RemoveFromHistory` (`LibraryModels.kt:122`); undo banner `HistoryUndoBanner` (device lane only returns undo record) |
| Clear history | WORKS | `clearHistory()` `:96` | `HistoryActivity.kt:~188` manage sheet -> confirm `AppAlertDialog` (two-step, `confirmClear` in SavedState) |
| Pause / resume history | WORKS (partial UI) | `setHistoryPaused` `:74` (desired-state write w/ observation; device `pause`) | Manage sheet lists BOTH "Pause" and "Resume" always (`HistoryActivity.kt:186-187`); no current paused state read into `HistoryUiState` (`LocalLibraryStore.paused()` exists, unused by UI) -> DATA_ONLY for paused indicator |
| History search/filter inside history | MISSING | not found (grep `query` in history repo calls: only global search `SubmitSearch` -> `SearchResultsActivity`) | search icon opens global search overlay, not in-history filter |
| Watch Later (account) | WORKS | `saveWatchLater` -> `setMembership("WL")` `MediaSaveRepository.kt:52-58`; browse as playlist id `"WL"` (`LibraryViewModels.kt:~162`) | row `LibraryRow.WatchLater` -> `PlaylistActivity` |
| Watch Later (device) | WORKS | `LocalLibraryStore.WATCH_LATER="local:watch-later"` `:199`; adding requires video metadata: `localVideo()` looks in local history else fetches `loadWatch` (`MediaSaveRepository.kt:173`) | same row; `PlaylistSummary.local=true` renders compact local hero |
| Liked videos (account) | WORKS | opened as playlist id `"LL"` `LibraryViewModels.kt:~164` | row shown only `if (signedIn)` `YouScreen.kt:128` |
| Liked videos (local) | MISSING | explicit comment "Liked has no device lane" `LibraryViewModels.kt:~163` | row hidden when signed out |
| Playlists list (account + local) | WORKS | `playlists()/playlistsNext()` `:114-121`; device `LocalLibraryStore.playlists()` | `CollectionScreen`; shelf on You `PlaylistShelf` |
| Downloads entry | WORKS | `DownloadUi.openManager` | row `LibraryRow.Downloads` -> `YouActivity.kt:124-125` (guarded by test flag `launchDownloads`); also `settings/AdvancedActivity.kt:51` |
| Queue entry | WORKS | player `QueueRepository` (not in scope) | row `LibraryRow.Queue` opens queue sheet in `YouActivity` |
| Account switch / sign-out from You | WORKS | `AccountMenuAction`, `MediaSession` | `YouActivity` sheets + `account-signout-confirm` dialog |
| Your videos / Your clips / Purchases / "Your data" rows | MISSING | `LibraryRow` enum has 6 rows only (`LibraryModels.kt:73`) | n/a |
| Local<->account import/merge | MISSING | not found | n/a |

### Playlist
| Feature | Status | Evidence |
|---|---|---|
| View playlist (hero, metadata, description expand, owner link, paged list) | WORKS | `PlaylistRepository.playlist/playlistNext`; `PlaylistScreen.kt` (`playlist-hero`, `playlist-list`, `playlist-description`) |
| Play all | WORKS | hero cover tap + "Play all" button (`playlist-play-all`) -> `PlaylistAction.PlayAll` -> `runBatch(Queue(false))` -> `PlaylistNavigationRequest.Queue` -> `PlaylistActivity.kt:~112` `PlayerViewModel.startPlaylist` |
| Shuffle | WORKS | in "more" menu (`playlist-menu` item index 0 = Shuffle, 1 = Download), `PlaylistScreen.kt:148-152`. Not a primary button (differs from YouTube) |
| Download whole playlist | WORKS | `PlaylistAction.Download` -> `downloadPlaylist()` `PlaylistActivity.kt:54` -> `DownloadUi.showBatchConfirm(BatchSource.PLAYLIST)`; also `EXTRA_DOWNLOAD` deep entry |
| Open a video from playlist (queue context kept) | WORKS | `PlaylistActivity.kt:~104` builds `QueueItem` list + `startPlaylist(startId)` |
| Create playlist | WORKS (only via save sheet) | `SaveRepository.create(title, description, videoId)` `SaveModels.kt:28`; account + device; UI `NewPlaylistSheet` (title+description). No "create empty playlist" from the Playlists list screen; creation requires a video |
| Add / remove video | WORKS via save sheet toggle | `SaveRepository.setContaining` (add and remove, account + device); UI `SaveToPlaylistSheet` checkmarks |
| Remove from within the playlist screen (own list) | MISSING | `PlaylistVideoAction` only WatchLater, Save (`PlaylistModels.kt:117`); `LocalLibraryStore.removeFromPlaylist` exists but is reachable only via the save sheet |
| Rename playlist / delete playlist | MISSING | grep `rename`, `deletePlaylist` over repo: not found (only unrelated player-queue code) |
| Reorder videos in playlist | MISSING | not found for playlists (`player/surface/QueueDragState.kt` reorders the PLAYER queue only) |
| Privacy setting (public/unlisted/private) | MISSING | no field in `PlaylistSummary`/`NewPlaylistDraft`; grep `privacy` only matches watch-reporting privacy |
| Save someone else's playlist | WORKS | `SaveRepository.savePlaylistToLibrary` (`MediaSaveRepository.kt:108`); UI bookmark `AppIconButton` (`a11y_save_playlist`) hidden when `local`; no "saved/unsave" state shown, no unsave (DATA_ONLY for state) |
| Sort playlist / collaborative / description edit | MISSING | not found |
| Playlist search inside list | PARTIAL | toolbar search opens global search overlay and `Search` -> `SearchResultsActivity`, not in-list filter |

### Save-to-playlist sheet
| WORKS | `SaveToPlaylistSheet` `SaveSheets.kt:70` (live mode lists server choices incl. Watch Later if the server returns WL, check icon = `contains`, plus "New playlist" row) opened from: history menu (`LibraryViewModels.kt`), playlist entry menu (`PlaylistViewModel`), Search, Shorts, Subscriptions hosts, Watch, Home (own overlay copies). Tags: `save-to-playlist`, `save-menu-playlist-<id>`, `save-menu-new-playlist`, `save-menu-retry`, `save-menu-loading`, `new-playlist-sheet`. Failure -> retry row. Fixture mode (non-live) variant takes `SavePlaylist` list and `SaveDestination`. |

### Downloads (MUST KEEP)
| Feature | Status | Data evidence | UI evidence |
|---|---|---|---|
| Download options sheet: video vs audio-only | WORKS | `DownloadConfig.audioOnly` `DownloadModels.kt:80` | `MediaToggle` `DownloadSheets.kt:356` |
| Quality selection | WORKS | catalog formats `resolve/DownloadCatalog.kt`, `DownloadSelector` | `QualityChipRow` `:364`, default quality pref |
| Audio track / bitrate selection | WORKS | `audioTrack`, `audioBitrate` in config | "More options" (`MoreOptions` `:424`, `SelectionRow` + `ChoiceDialog`) |
| Subtitles | WORKS | `includeSubtitle`, `subtitleLanguage` | More options switch + language picker |
| Thumbnail/cover | WORKS | `includeCover` | More options switch |
| Attachments only (subtitle/cover without media) | WORKS | `attachmentsOnly` | More options |
| File name template | WORKS | `fileNameTemplate` | More options text field |
| Size preview (exact/estimate/unknown) | WORKS | `DownloadPresentation.sizeCopy` | `SizeRow` `:376` |
| Batch confirm (playlist, queue) with item selection | WORKS | `BatchSnapshot`, `BatchSelection`, `DownloadSnapshotGuard` (caps `DownloadLimits`) | `BatchConfirmSheet` `DownloadSheets.kt:481`, `DownloadSelection.kt` |
| Manager list with All / In progress / Completed filters | WORKS | `DownloadFilter`, `observeDownloads` | `DownloadManagerScreen` `DownloadScreens.kt:94`, `DownloadListPane :269` |
| Per-item progress (bytes/expected, phase copy: resolving, transferring, waiting network/process, merging, saving, paused, failed, cancelled, file not found) | WORKS | `DownloadItemUiState`, `PhaseCopy` | `DownloadItemRow` (`DownloadComponents.kt:145`) |
| Pause / resume / cancel / retry / redownload | WORKS | `DownloadViewModel.pause/resume/cancel/retryFailed/redownload` | row actions `DownloadRowAction` (PAUSE, RESUME, CANCEL, RETRY, REDOWNLOAD, OPEN, SHARE, COPY_ID, DELETE) via "more actions" menu |
| Batch detail view, batch pause/cancel | WORKS | `DownloadTarget.Batch`, `observeBatch` | `DownloadBatchPane :357` |
| Delete (record only / record + files) | WORKS | `RemoveMode` | `DeleteDialog :622`; `clearHistory(alsoDeleteFiles)` in History pane `:523` |
| Download settings: Wi-Fi only, default quality, max connections (slider) | WORKS | `DownloadPrefs`, `PreferenceDownloadPrefs` | `DownloadSettingsPane :447` (quality list hardcoded `144p..2160p` at l.452) |
| Play downloaded file offline INSIDE app | MISSING | no code resolves `publishedUris` into the player; `watchPageDownloaded` flag is produced (`DownloadUiState.kt`) but not consumed outside downloader (grep `watchPageDownloaded`/`observeVideo` outside `downloader/`: not found) | OPEN uses external `ACTION_VIEW` (`DownloadFileShare.viewIntent`, `DownloadScreens.kt:655`) with `*/*` mime. Offline test fixture exists only for player (`androidTest/.../OfflineWatchFixture.kt`) |
| "Downloaded" badge on watch page / rows | DATA_ONLY | `DownloadUiMapper.video()`, `VideoDownloadUiState.watchPageDownloaded`, `observeVideo` | no consumer in watch/home/search |
| Share downloaded file | WORKS | `DownloadFileShare.sendIntent` (FileProvider `${applicationId}.download.fileprovider`) | `DownloadRowAction.SHARE` |
| Copy video id | WORKS | - | `COPY_ID` |
| Storage location choice | MISSING (fixed) | `MediaStorePublishBackend` publishes to `Downloads/Litube` via MediaStore (`DownloadPublisherImpl.kt:301`, API 29+; fallback `getExternalFilesDir(DIRECTORY_DOWNLOADS)/Litube` `:375`); work dir under cacheDir (`DownloadDirectories.underCache`) | no folder picker; settings pane has no location row |
| Notifications (progress, pause/resume/cancel/view actions, channel `download_channel`) | WORKS (engine) | `engine/AndroidDownloadNotifications.kt`, `DownloadNotificationController.kt`, receiver/activity `DownloadActions.kt` (PAUSE/CANCEL/RESUME/VIEW) | strings `download_channel_name/desc`; VIEW opens `DownloadActivity` with `batchId` |
| Entry points | WORKS | `DownloadUi.showSingleConfirm/showBatchConfirm/openManager`, `DownloadEntries.*` | Watch overflow (`watch/WatchActivity.kt:285-296`), player sheets (`player/surface/PlayerSheets.kt:126,162,165`), queue sheet (`LibraryQueueSheet.kt:25-26`), feed items (`ui/ActivityMediaActionExecutor.kt:39`), playlist menu, system share (`DownloadShareActivity`), You tab, Settings/Advanced |
| Scheduled/Wi-Fi-only queue, auto resume on network | WORKS (engine) | `BackgroundDownloadScheduler`, `DownloadNetworkRestore` | `download_wifi_only` setting |

### Share
| Feature | Status | Evidence |
|---|---|---|
| Share video link | WORKS | `ShareGateway.share(videoId)` -> `https://youtu.be/<id>` (`VideoShare.kt:44`), uses `Intent.ACTION_SEND text/plain`; clipboard fallback if no handler |
| Share playlist link | WORKS (limited) | `sharePlaylist` accepts only ids matching `PL[A-Za-z0-9_-]{14,32}` (`PlaylistShare.kt:4`); WL, LL, `RD…`, `local:` ids silently return false (no user feedback) |
| Share channel link | WORKS | `shareChannel` (`@handle` or `channel/<id>`); callers `channel/ChannelActivity.kt:124` |
| Share with timestamp | MISSING | grep `t=`, `timestamp`, `startAt` in `share/` and `WatchActivity`: not found; `VideoShare` has no time parameter |
| Copy link | WORKS | `ShareGateway.copyLink` (toast `link_copied`); wired in feed action executor only (`ActivityMediaActionExecutor.kt:42`) |
| Open with... (external app) | WORKS | `ShareGateway.openWith` (chooser title hardcoded "Open with", "Share" in code, not string resources) |
| Share downloaded file | WORKS | see downloads |
| Share fixture-id mapping | note | `VideoShare.fixtureIds` maps local fixture ids to real YouTube ids; test/fixtures only |
| Image gallery save/share | WORKS | `GalleryActivity` (post images) |

### Item overflow menus on video rows
| Surface | Actions | Evidence |
|---|---|---|
| History row | Save to Watch later, Save to playlist, Share, Remove from watch history | `HistoryMenuAction` `LibraryModels.kt:122` (no Download, no Add to queue, no Not interested) |
| Playlist entry | Save to Watch later, Save to playlist | `PlaylistVideoAction` `PlaylistModels.kt:117` (no Download single, no Share, no Queue, no Remove) |
| Playlist header menu | Shuffle, Download | `PlaylistScreen.kt:148-152` |
| Home/feed video | Add to queue, Download, Save to Watch later, Save to playlist, Share, Not interested, Don't recommend channel, Send feedback, Show fewer Shorts, Report, Save to Library (kind-dependent via `FeedMenuKind`) | `home/domain/HomeModels.kt:150-158` (outside my scope; listed for parity) |
| Generic media actions | Play next, Add to queue, Download, Open with, Share, Copy link | `ui/ActivityMediaActionExecutor.kt:12` |
| Playlists list row | no menu at all (no rename/delete/share) | `YouScreen.kt:307-335` |

## 5. UI coupling and redesign notes

### Safe to rewrite (pure presentation)
- `library/presentation/YouScreen.kt`, `LibraryComponents.kt`; `playlist/presentation/PlaylistScreen.kt`, `PlaylistCards.kt`, `PlaylistPreviews.kt`; `saves/presentation/SaveSheets.kt`; `downloader/ui/DownloadScreens.kt`, `DownloadSheets.kt`, `DownloadComponents.kt`, `DownloadTokens.kt`, `DownloadSelection.kt` (UI only; keep `target()` mapping to `DownloadTarget` semantics); `gallery/GalleryActivity.kt` composables.
- Hosts (`YouActivity`, `HistoryActivity`, `PlaylistsActivity`, `PlaylistActivity`, `DownloadActivity`, `DownloadSheetActivity`) mix UI composition with wiring. Keep their wiring; restyle only content passed to `setAppContent`.

### Must keep (logic living in UI-adjacent files)
- `PlaylistActivity.kt`: `downloadPlaylist()` (VideoId.parse, distinctBy, `DownloadUi.showBatchConfirm`, rejection toast `playlist_too_large_to_download`, `EXTRA_DOWNLOAD` pending flag saved in instance state, line 49-73, 117-170); `PlayerViewModel.startPlaylist` calls for Watch/Queue requests (queue context handoff and `WatchProvisionalStore.put`); back-stack priority (save sheet > overlay > finish); edge-to-edge/status bar colors from `page.summary.accent`; `onResume` session sync.
- `YouActivity`: `confirmSignOut` flow, `showSwitchSheet`, `showQueue`, `launchDownloads` test flag.
- `HistoryActivity`: Manage-history sheet index mapping (0=pause,1=resume,2=clear) and confirm dialog gating on `state.confirmClear`; `rememberSearchRows(sources.suggestions, query)` search panel.
- Composable-held identity checks: `DownloadManagerScreen` `handle()` (itemId vs taskId targeting; `skipped` items only allow DELETE/COPY_ID; haptics on action) - keep this logic; `SingleVideoConfirmSheet` quality-cap logic on catalog load (`quality !in options` -> pick highest <= preference; `videoItag` from `qualityItag`) and `submitting` guard against double submit (confirm mints new submissionId in VM).
- `DownloadItemUiState`/`DownloadUiMapper`/`DownloadPresentation` define copy semantics (`PhaseCopy`, `fileMissing`, size kinds); treat as contract, unit-tested.
- `rememberSaveable` of download sheet fields (process-death restore) and `DownloadDest` Saver.
- `uiThrottle` on observed flows; do not replace with raw `collectAsState` (progress tick storm).

### Shared design system usage
- Library/playlist/saves use `ui/design` (`DesignTokens.Background/Surface/Ink/Secondary/Ghost/TopBarHeight`, `AppText`, `AppIcon/AppIconButton`, `MenuBottomSheet` + `MenuSheetItem`, `PillButton`, `VerificationCard`, `WriteProgressOverlay`, `ActionProgress`, `CollapsingAppBar`, `RefreshableContent`) and `ui/components` (`MainNavigationBar`, `SearchPanel`, `AppAlertDialog`, `MainTabs`).
- Downloader UI is on a DIFFERENT system: stock Material3 (`MaterialTheme.colorScheme`, `TopAppBar`, `Scaffold`, `Switch`, `Slider`, `ModalBottomSheet`, `Icon(painterResource(R.drawable.ic_*))`) wrapped in `AppTheme { NeutralControls { } }`, with `SettingsTokens` from settings. 51 `MaterialTheme/DesignTokens` references; it will pick up theme changes automatically but looks different from the DesignTokens-based screens; unify during redesign.
- Playlist hero has hardcoded colors: `PlaylistActivity.kt:~80` `SystemBarStyle.dark(0xFF2D4F72.toInt())`, `PlaylistSummary.accent` default `0xFF2D4F72` (`PlaylistModels.kt:13`), `Color.White.copy(alpha=0.1f/0.7f)` in hero buttons/dividers, blurred artwork (`blur(30.dp)`), `Color.Black.copy(0.6f)` duration chip.
- Hardcoded dimens throughout (160.dp thumb, 90.dp rows, 48.dp library rows, 175/160.dp shelf cards, 40.dp icon buttons - below the 48dp touch-target recommendation; `AppIconButton(diameter = 40.dp)` in playlist actions and per-row menu).
- Hardcoded English strings: `LibraryRow.label`, `HistoryMenuAction.label`, `PlaylistVideoAction.label`, `MediaActionKind.label`, `SignedOutPrompts` (all English in domain; UI maps to `labelResource` extension for display so strings are mostly in resources: `R.string.ui_*`). Remaining raw: `ShareGateway` chooser titles "Open with"/"Share", clipboard label "Video link", `FixtureLibraryRepository` text, `DownloadEntries.queueSnapshot(name = "Queue")`, `DownloadSettingsPane` quality list.
- Accessibility: back/search/more buttons have `contentDescription` via `R.string.a11y_*`; per-row menu uses `a11y_more_actions_for(title)`; skeletons use `clearAndSetSemantics {}`; overlays hide underlying content semantics (`YouScreen.kt:~46`); `role = Role.Button` on cover taps and rows; `appPaneTitle` on sheets.
- Responsive: playlist uses `BoxWithConstraints` width breakpoints from `PlaylistLayout` (>=600 large text, >=840 side-by-side hero, >=1024 medium text/cover 427x240); font-scale >1.3 switches action row to scroll and shrinks thumbnails. Library screens read `maxWidth` but mostly single column (goldens exist at 390 and 1280 widths). Downloader: `DownloadCompactLayoutAndroidTest` exists; row uses `stacked` layout.
- Scroll-state preservation: You uses `rememberLibraryListState()` (`YouScreen.kt:86`); downloader hoists `rememberLazyListState` so list scroll is restored after batch detail; playlist hero uses `rememberLazyListState` inside screen (not saveable across process death).

## 6. Tests touching these packages
Unit (`app/src/test/java/com/hhst/youtubelite/`):
- `library/`: 6 files (HistoryWriteContinuityTest 2, LibraryContinuationTest 3, LibraryEmptyPaginationTest 1, LibraryViewModelTest 33, LibraryWriteOrderingTest 5, YouShelfRetryTest 3) - VM/state tests; UI-independent, KEEP VM contract.
- `playlist/`: 4 files (PlaylistViewModelTest 16, PlaylistContinuationTest 6, PlaylistReadOwnershipTest 3, PlaylistEmptyPaginationTest 2).
- `saves/`: SaveSheetFeedbackTest 2, SaveTaskOwnershipTest 4. `share/VideoShareTest` 5.
- `data/media/`: LocalLibraryStoreTest, LibrarySourceIsolationTest, MediaHistoryRecoveryTest, MediaSaveRecoveryPublicApiTest (backend, unaffected).
- `watch/WatchSaveOwnershipTest` (touches save controller from Watch).
- `downloader/`: 39 files: core 10, engine 8, io 5, net 4, regression 1, resolve 4(+fixtures), ui 6 (DownloadEntriesTest 4, DownloadFileShareTest 2, DownloadPresentationTest 13, DownloadUiThrottleTest 1, DownloadViewModelConfirmTest 9, DownloadViewModelTest 5). All pure JVM; `ui/` ones test non-composable logic (presentation mapping, VM) and survive a redesign if `DownloadPresentation`, `DownloadUiMapper`, `DownloadViewModel`, `DownloadEntries` keep their signatures.

Instrumented (`app/src/androidTest/java/com/hhst/youtubelite/`):
- `library/` 8: `LibraryHostAndroidTest` (23 tag/text refs), `HistoryAppBarAndroidTest` (6), `MainTabsHostAndroidTest` (7), `LibraryIdentityAndroidTest` (3), `AnonymousLibraryAndroidTest`, `LibraryReferenceAndroidTest`, `LibraryScreenshotAndroidTest` (golden compare), `SignInHostAndroidTest`; plus `data/media/LibraryShelfRetryAndroidTest`.
- `playlist/` 3: `PlaylistScreenAndroidTest` (32 tag refs), `PlaylistHostAndroidTest` (11), `PlaylistReferenceAndroidTest`.
- `downloader/android/` 17: `DownloadManagerAndroidTest` (31 text/contentDescription lookups, resolves via `R.string.download_filter_*`, `download_more_actions`, `action_pause`), `DownloadOptionsAndroidTest` (30), `DownloadConfirmSheetAndroidTest` (17), `DownloadContinuityAndroidTest` (7), `DownloadSelectionAndroidTest` (8), `DownloadBatchRemovalAndroidTest` (5), `DownloadCompactLayoutAndroidTest` (5), `DownloadActivityRoutingAndroidTest`, `DownloadIntentAndroidTest`, PiP and engine/IO tests (not UI dependent).
- `menus/` 3 (`WatchMenuLayerAndroidTest` 6 tag refs, `ShareGatewayAndroidTest`, `MenuShareReferenceAndroidTest`), `gallery/GalleryActivityAndroidTest`, `ui/WriteFeedbackAndroidTest`, `ui/WriteContinuityAndroidTest`, `ui/CompactControlsAndroidTest`, `ui/ResponsiveExperienceAndroidTest`.
- Golden images (`app/src/androidTest/assets/`): `library/goldens` 13 PNG (you-390/1280, you-signed-out-390, history-390/1280/empty/error, playlists-390/1280, channels-390/1280, subscriptions-390/1280/signed-out); `playlist/goldens` 16 PNG (detail, channel, empty, error, loading, menu, search, unavailable, video-menu at 390 and 1280); `saves/goldens` 3 PNG (new-playlist-390, save-to-playlist-390/1280). Downloader has NO goldens (only `assets/downloader/media`).
- Tag naming that tests rely on (do not rename without updating tests): `you-screen`, `you-topbar`, `you-library`, `you-account`, `you-skeleton`, `you-error`, `you-signed-out`, `you-verification`, `you-refresh`, `library-row-<History|Playlists|WatchLater|LikedVideos|Queue|Downloads>`, `library-history-shelf`, `library-playlists-shelf`, `library-history-<id>`, `library-playlist-<id>`, `history-screen`, `history-list`, `history-item-<id>`, `history-manage`, `history-manage-sheet`, `history-undo`, `history-undo-dismiss`, `playlists-screen`, `playlists-list`, `playlists-item-<id>`, `library-subpage-bar`, `library-append-loading/failed`, `playlist-page/toolbar/hero/hero-cover/hero-local/heading/description/actions/play-all/list/empty/loading/loading-cover/error/unavailable/verification/verification-banner/enumeration/enumeration-failed/append-loading/append-failed/menu/video-menu/video-action-<Name>/entry-<id>/entry-cover-<id>/entry-title-<id>/entry-menu-<id>/shuffle/download`, `save-to-playlist`, `save-menu-*`, `new-playlist-sheet`, `account-signout-confirm`.
- Golden pixel tests (Library/Playlist/Saves) WILL fail after any visual change and must be regenerated; text/contentDescription-based downloader tests depend on string resource keys, not pixels.

## 7. Risks and surprises
1. `PlaylistRepository` is read-only and `SaveRepository` has no rename/delete/reorder/privacy; those YouTube features need NEW backend work (not UI only). Do not promise them in a UI-only redesign. Creating a playlist always requires a source videoId.
2. Downloader has no in-app offline playback and no consumer for `watchPageDownloaded`/`observeVideo`; OPEN goes to an external viewer with `*/*` mime. A "downloaded" badge or offline-play action would need a thin new UI call to `observeVideo` (allowed, VM already exposes it) plus player support for local `content://` URIs (backend).
3. Storage location is fixed to `Downloads/Litube` (MediaStore, API 29+); no setting. Adding a picker touches engine (`io/`), forbidden.
4. Three exhaustive `when` blocks (`YouActivity`, `HistoryActivity`, `PlaylistsActivity`) over the shared `LibraryNavigationRequest` and one for `PlaylistNavigationRequest`; adding/removing request types in `library/domain` (domain, off-limits) breaks compile in all hosts. Redesign should keep the 17-type surface and map new UI affordances onto existing actions.
5. Playlist Shuffle and Download are hidden in the header overflow menu only; Play all is the only primary button (`PlaylistButtons`). YouTube parity expects Shuffle primary. Easy UI change: `PlaylistAction.Shuffle` already exists.
6. Pause/Resume history sheet always shows both options with no state; wrongly implies the current paused status. Domain lacks a paused read for the account lane (`loadWatchHistoryManagement` exists in backend and could feed it, needs repository change).
7. Local library quirks: Liked row hidden signed-out; local Watch Later add needs `localVideo()` (history lookup then network watch read) and can fail with `UnconfirmedActionException`; `HistoryUndo` only for device lane (`removeFromHistory` returns null for account).
8. `PlaylistShare` silently drops non-`PL` ids (Watch Later, Liked, mixes, local) - share action produces no feedback.
9. Watch page and Home keep their own save-sheet overlay copies backed by SavedStateHandle (`SaveSheetController` docs) while other hosts use the shared controller: two code paths to restyle, `SaveToPlaylistSheet` serves both.
10. Downloader UI runs in its own activities (`DownloadActivity`, `DownloadSheetActivity` translucent theme `Theme.App.DownloadSheet`, `DownloadShareActivity`), each calling `App.startMediaRuntime()` and `PipAutoEnter.suppress()`; keep these hooks. `DownloadSheetActivity` receives `EXTRA_PLAYER_BOTTOM` fraction to avoid covering the player (`LocalSheetPlayerBottomFraction`).
11. Progress UI relies on `uiThrottle(critical=…)` heuristics; changing row composition should not re-collect raw Room flows per row (comment at `DownloadScreens.kt:~126` documents a past empty-flash bug).
12. Fixture/debug code paths are interleaved with production in hosts (`ContentRepositories.fixtures(intent)`, `LibraryScenario`, `PlaylistScenario`, `BuildConfig.DEBUG`); androidTests depend on them via intent extras (`EXTRA_SCENARIO`, `EXTRA_SIGNED_IN`) - preserve.
13. `GalleryActivity` is only used by Post detail; it is out of collections scope but shares FileProvider authority `${packageName}.download.fileprovider` with downloader (`GalleryActivity.kt:142`) - keep the authority and `xml/download_file_paths`.
