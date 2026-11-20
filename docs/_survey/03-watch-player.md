# 03 - Watch / Player / Comments / Shorts / DeArrow / Live chat survey

Root: `app/src/main/java/com/hhst/youtubelite/` (abbrev. `YL/`). All paths below are relative to `app/src/main/java/com/hhst/youtubelite/` unless prefixed `app/src/`. Line numbers verified against source.

## 0. Executive summary (read first)

1. **One process-scoped `PlayerViewModel` (Koin `single`, `di/AppModule.kt:268-282`) owns playback state; one `PlaybackApi`/`PlaybackEngine` (Koin `single`, `di/AppModule.kt:243-256`) owns ExoPlayer.** Watch, Shorts, ContentActivity mini and PiP are all views of that singleton. A redesign must keep that: UI = observers + `PlayerPlaybackActions` callers, never an owner.
2. **The player is split cleanly.** ENGINE/backend: `player/engine`, `player/datasource`, `player/service`, `player/sponsor`, `player/QueueRepository.kt`, `player/PlayerViewModel.kt`, `player/Playback*.kt`, `player/FullscreenState.kt`. SURFACE/presentation: only `player/surface/*` (24 composable files ~6.5k lines) plus `watch/presentation/WatchPlayer.kt` glue. The seam is `PlayerUiState` (state out) + `PlayerSurfaceCallbacks` = `PlayerPlaybackActions` + `PlayerWindowActions` (intents in).
3. **Gesture detection is pure presentation** (`player/surface/PlayerGestures.kt`, `GestureMath.kt`, `ZoomMath.kt`), but it emits into `PlayerViewModel` (speed hold, scrub, double-tap seek) and `PlayerWindowHost` (brightness/volume/orientation). Gesture pref gating lives in `PlayerViewModel.gestureEnabled` (`PlayerViewModel.kt:241`) - must be kept.
4. **The watch page is NOT a classic "screen hosting a player"**: `WatchActivity` hosts a stable overlay `MiniPlayerWindow` that interpolates between the page's measured player-slot bounds, fullscreen, and mini. `WatchScreen` only reports a slot rect (`WatchActivity.kt:524-526`). Any redesign of the page layout must keep a slot Box and keep reporting its `boundsInRoot` or the player will not follow.
5. **Biggest gaps vs YouTube parity**: no seek-bar thumbnail/storyboard preview, no sleep timer, no cast SDK button on the top bar (cast is in More sheet), no shuffle toggle on the queue UI (only `LoopMode.QUEUE_RANDOM` end-behavior), no comment posting for non-fixture "like/edit/report" paths (see feature matrix), no channel-level swipe gestures. Everything else on the checklist exists.

---

## 1. Package map

### 1.1 `watch/` (4,295 lines)
| File | Lines | Purpose |
|---|---|---|
| `watch/WatchActivity.kt` | 863 | Activity (extends `player.PlaybackActivity`, `ui/components/WatchSheetHost`). Builds `WatchViewModel`, `CommentsViewModel`, `LiveChatViewModel` via factories (`:170-209`); composes page + overlay player + search + comments/chat panels (`:392-667`); handles `WatchNavigationRequest` (`:273-322`); mini handoff (`:751-815`). MIXED UI+logic. |
| `watch/presentation/WatchScreen.kt` | 991 | Pure composable page: skeleton/error/provisional header, metadata, action bar, comments teaser, related feeds (Phone/Grid/List/Stacked/SidebarCompact/SidebarWide). |
| `watch/presentation/WatchViewModel.kt` | 791 | State holder (see 2.1). |
| `watch/presentation/WatchPlayer.kt` | 166 | Glue: `WatchPlayer(...)` composable = `PlayerSurface` + `PlayerWindowHost` + surface attach/detach lifecycle + cast init. Used by WatchActivity overlay, also `ContentActivity`/`YouActivity` for mini. |
| `watch/presentation/WatchDescription.kt` | 128 | Description parse (links, timestamps -> `parseDescription`, `timestampSeconds`) + composable. |
| `watch/presentation/WatchFullscreenPull.kt` | 105 | Modifier: pull-down/up on page content drives fullscreen handle (`FullscreenSwipeHandle`). |
| `watch/presentation/WatchPageMotion.kt` | 16 | Pure alpha/progress math for page vs mini reveal. |
| `watch/presentation/WatchPlayerState.kt` | 8 | `PlayerUiState.withWatchPresentation(handoff)` merge (keeps live truth, overrides visible/mini/fullscreen). |
| `watch/presentation/WatchPreviews.kt` | 57 | Previews. |
| `watch/domain/WatchModels.kt` | 271 | `WatchVideo`, `WatchRelated`, `WatchPage`, `WatchRepository`, `WatchUiState`, `WatchOverlay`, `WatchAction`, `WatchNavigationRequest`, `WatchLayout` (hard-coded CSS-px breakpoints/geometry). |
| `watch/domain/VideoActions.kt` | 19 | `VideoActions` (`rate`, `subscribe`), `VideoRating`, `VoteCountRepository`, domain exceptions. |
| `watch/domain/WatchProvisional.kt` | 79 | Card summary store painted while page loads. |
| `watch/data/FixtureWatchRepository.kt` | 154 | Test/fixture repo. Real impl: `data/media` (`ContentRepositories.watch`, `.votes`, `.videoActions`, `.saves`, `.avatars`). |
| `watch/chat/*` | 647 | Live chat: `LiveChatViewModel` (329, incl. `LiveChatGateway`, `MediaLiveChatGateway`), `LiveChatPanel` (222), `LiveChatModels`, `LiveChatRouting`, `ChatWebSessionStore`. |

### 1.2 `player/` (16,345 lines)
**ENGINE / BACKEND - DO NOT TOUCH**
- `player/PlayerViewModel.kt` 1,639 - process singleton, `PlayerUiState`, intent handlers, queue/cast/PiP/mini/shorts orchestration. (It is a ViewModel but is the controller; treat as backend. It implements `PlayerPlaybackActions` from the surface package, `:151-164`.)
- `player/engine/` PlaybackEngine 2,418 (`PlaybackApi` interface `:168-235`, `PlaybackSnapshot` `:118`, `LoopMode` `:100`, `SubtitleTrack` `:112`), PlaybackAttempt, PlaybackDiagnostics, PlaybackNetworkMessage, PlaybackNotificationController, PlaybackProgress, PlaybackRenderersFactory, PlaybackTransport.
- `player/datasource/` (21 files, ~3k lines): MediaSourceResolver 463, YoutubeHttpDataSource 531, PlayerDataSource 257, DashManifestFactory, StreamSelection, SubtitleSelection, AudioTrackIdentity, codec/startup/recovery/cache-key helpers.
- `player/service/` PlaybackService 476 (Media3 `MediaSession`, `PlaybackService.kt:28,215`), ServiceNotificationController, PlaybackCommandRouter, PlaybackQueueCommands, LatestArtworkRequest, NotificationPermission.
- `player/sponsor/` SponsorBlockManager 219 (fetch `sponsor.ajay.app`, categories from prefs), SponsorSkipController 164 (countdown/skip/chip state machine, pure).
- `player/QueueRepository.kt` 296, `PlaybackSession.kt`, `PlaybackLifecycle.kt` (background pause / video-surface hide at 700 ms), `FullscreenState.kt` (auto-fullscreen sensor history), `PlayerTtl.kt`, `PlaybackActivity.kt` 275 (base activity: PiP params/actions/receiver, auto-enter, audio-only bg).

**SURFACE / PRESENTATION - MAY BE REDESIGNED** (`player/surface/`, line counts)
| File | Lines | Role |
|---|---|---|
| `PlayerSurface.kt` | 742 | Root composable: SurfaceView host, controls, gestures, sheets/dialogs state, subtitle overlay, sponsor card, error, zoom (`VideoZoom` `:713`). |
| `PlayerControls.kt` | 822 | `TopBar`, `CenterControls`, `BottomBar`, `PlayerIconButton`, `ControlScrims`, `HintOverlay`, `CastStatusBanners`, `ErrorOverlay`, `LockAffordance`, `MiniPlayerChrome` (`:620`), `PlaybackLoadingIndicator`. |
| `PlayerTimeBar.kt` | 155 | Seek bar (chapter ticks `:144`, sponsor segments). |
| `PlayerGestures.kt` | 304 | `Modifier.playerGestures(...)`, `GestureCallbacks` interface. |
| `PlayerGestureOverlays.kt` | 323 | `GestureOverlays` (double-tap arrows, speed-hold pill, scrub bubble, edge slider), `SponsorCountdownCard` (`:285`). |
| `GestureMath.kt` | 126 | Pure: zones, thresholds, velocity tracker, `GestureZone` enum. |
| `ZoomMath.kt` / `FullscreenSwipeMotion.kt` / `AutoFullscreen.kt` | 25/70/58 | Pure math. |
| `MiniPlayerWindow.kt` | 980 | The morphing window: embedded <-> fullscreen <-> mini, drag/dock/collapse-to-edge/pinch-resize/dismiss. Exposes `FullscreenSwipeHandle` (`:101`), `MiniPlayerHandle` (`:118`). |
| `MiniPlayerLayout.kt` / `MiniPlayerStore.kt` | 158/32 | Pure geometry; prefs (`mini_player_width_dp`, `_translation_x_dp`, `_y_dp`). |
| `PlayerSheets.kt` | 468 | `PlayerSheetHost` (More, SubtitleStyle, Queue, Chapters sheets), `PlayerDialogHost` (EndBehavior, Quality, Speed, Resize, Audio, Captions, Info, Cast dialogs), `PlayerAnchorMenu.Subtitles`; `MoreSheet` (`:316`). |
| `AnchorMenus.kt` | 300 | Dropdown popups + `SegmentPreviewDialog` (chapter thumbnail). |
| `ChaptersSheet.kt` | 298 | `ChapterEntryChip`, `ChaptersSheet`. |
| `QueueSheet.kt` / `QueueDragState.kt` / `LibraryQueueSheet.kt` | 339/137/29 | Queue UI with drag reorder. |
| `PlayerCastDialog.kt`, `CastFailureDialog.kt`, `PlaybackFailureDialog.kt`, `InfoDialog.kt`, `PlayerModalDialog.kt`, `FullscreenPlayerSheet.kt` | 234/56/67/195/67/105 | Dialogs/sheets. |
| `SubtitleOverlay.kt` / `SubtitleStyle.kt` / `SubtitleStyleEditor.kt` | 71/117/504 | Caption rendering + style editor (color picker, position preview). |
| `PlayerUi.kt` | 411 | Pure helpers (`nextBackStep`, icons/labels, `ResizeMode` enum `:380`). |
| `PlayerActions.kt` | 113 | **The contract**: `PlayerPlaybackActions` (`:10`), `PlayerWindowActions` (`:79`), `PlayerSurfaceCallbacks`, `PlayerCallbackBridge`. |
| `PlayerWindowHost.kt` | 215 | Window side effects: brightness/volume/orientation/PiP/share (`PlayerWindowActions` impl) + `PlayerWindowEffects` (immersive bars, orientation lock). Presentation-adjacent logic: keep behavior. |

### 1.3 `comments/` (2,605)
`comments/domain/CommentModels.kt` 271 (`CommentsRepository`, `CommentThread/Reply`, `CommentsUiState`, `CommentsContent`, `CommentsOverlay`, `CommentAction`, `CommentNavigationRequest`, `CommentsLayout` constants); `domain/CommentActions.kt` (write interface); `domain/CommentText.kt`; `CommentLinks.kt`; `presentation/CommentsPanel.kt` 985; `CommentsViewModel.kt` 829; `CommentSheetMotion.kt` 119 (drag-to-dismiss); `CommentRichBody.kt`; `CommentEffects.kt` (`bindCommentNotices`); `data/FixtureCommentsRepository.kt`. Real impl: `data/media/MediaCommentsRepository.kt` (implements both `CommentsRepository` and `CommentActions`).

### 1.4 `shorts/` (2,232)
`ShortsActivity.kt` 663 (hosts everything; uses singleton `PlayerViewModel`), `presentation/ShortsScreen.kt` 249 (`VerticalPager`), `ShortPlaybackControls.kt` 142, `ShortsDescriptionSheet.kt` 52, `ShortsViewModel.kt` 420, `RecentCommentsViewModel.kt` 90 (per-item `CommentsViewModel` cache), `domain/ShortsModels.kt` 106, `data/ShortsRepository.kt`, `ShortsMediaPreloader.kt` 397 (backend), `ShortsEntryPreparation.kt`, `FixtureShortsRepository.kt`.

### 1.5 `dearrow/` (337)
`DeArrowService.kt` 267 (Koin `single`, caches `Branding(videoId,title,thumbTime)`, flows `titlesEnabled`/`thumbnailsEnabled`, prefs `DEARROW_TITLES`/`DEARROW_THUMBNAILS`, defaults false, `extension/PreferenceKeys.kt:34-35,89-90`); `DeArrowDisplay.kt` 70 (composables `rememberDeArrowTitle`, `rememberDeArrowArtwork`, `VideoArtwork`, fn `deArrowThumbnailUrl`). Used in: watch, shorts, home FeedCards, channel, explore, library, search, subscriptions screens (grep list: `channel/presentation/ChannelScreen.kt, ChannelSearchTab.kt, explore/.../ExploreScreen.kt, home/.../FeedCards.kt, library/.../LibraryComponents.kt, search/.../SearchResultsScreen.kt, ShortsResultsList.kt, shorts/.../ShortsScreen.kt, subscriptions/.../SubscriptionsScreen.kt, watch/.../WatchScreen.kt`). **Not applied inside the player surface title/notification** (grep: player/ has no DeArrow reference).

### 1.6 Live chat
Only in `watch/chat/` (above); gateway implementation `MediaLiveChatGateway` lives in `LiveChatViewModel.kt:33-110`; extractor side `com.liskovsoft.youtubeapi.videoinfo.V2.ChatPoller/ChatReplay` and `data/media/MediaRepositories.kt` (chat continuation discovery comment `:51`).

---

## 2. Screens & ViewModels (the VM <-> domain contract to preserve)

### 2.1 Watch page
- **Host**: `watch/WatchActivity` (one activity; page, search overlay, comments/chat panel, video menu, save sheet all inside `setAppContent`, `:392`).
- **VM**: `WatchViewModel(repository: WatchRepository, savedState, videoId, scenario, voteRepository: VoteCountRepository?, videoActions: VideoActions?, saveRepository: SaveRepository?, provisional, loadOnStart, avatars: MediaAvatarDirectory?)` (`WatchViewModel.kt:39-50`).
- **State**: `val uiState: StateFlow<WatchUiState>` (`:93`). Fields: `videoId, content (Loading | Ready(WatchPage) | Failed), descriptionExpanded, overlay (None | Search | ItemMenu | Save | Feedback), draftQuery, commentsOpen, actionPending, pendingVideoAction, relatedLoading/Failed, menuLoading/Failed, live, provisional, verification` (`WatchModels.kt:72-92`).
- **Side channels**: `navigationRequests` (`Flow<WatchNavigationRequest>`, `:95`), `actionNotices` (`Flow<AppNotice>`, `:97`), `writes: WriteFeedback` (`:98`, shown by `ui/design/WriteProgressOverlay`).
- **Public API**: `startLoading()`, `refreshSession()`, `updateCommentsPreview(videoId,status,total,comment)`, `selectVideo(id, provisional)`, `refreshVoteCounts(showLikes, showDislikes)`, `onAction(WatchAction)`, `verificationCompleted(url)`.
- **Events (WatchAction, `WatchModels.kt:~120-160`)**: `Retry, LoadRelated, VoiceSearch, ToggleDescription, SeekToTimestamp(s), OpenDescriptionLink(url), OpenSearch/CloseSearch/QueryChanged/SubmitSearch, OpenPageMenu, Like, Dislike, Subscribe, Download, Queue, OpenWith, Share, Save, SaveDestinationSelected, SaveChoiceToggled, SaveChoicesRetry, OpenNewPlaylist, SaveDraftChanged, SubmitNewPlaylist, FeedbackReason, Report, OpenChannel, OpenComments, CloseComments, OpenRelated, OpenItemMenu, RetryMenu, CloseOverlay, DismissVerification, OpenVerification, MenuAction(WatchVideoMenuAction)`.
- **Domain calls**: `WatchRepository.watch/watchFresh/next/menu/feedback` (`WatchModels.kt:57-64`); `VideoActions.rate(videoId, rating, previous)` and `.subscribe(channelId, subscribed)` (`VideoActions.kt:13-16`); `VoteCountRepository.counts(videoId)`; `SaveRepository.setContaining(...)`, save choices.
- **Navigation requests handled by the Activity** (`WatchActivity.kt:273-322`): Channel, Search, Watch(videoId,start), SeekTo -> `playerViewModel.onSeek`, OpenLink, OpenVerification, VoiceSearch, Share (`ShareGateway`), Download (`DownloadSingleSpec`, `:284`), Queue (`QueueItem`, `:298`), OpenWith, Comments -> `commentsViewModel.startLoading()`. Like/Dislike/Subscribe/Save*/Feedback/PageMenu are no-ops in the activity (VM does them when `videoActions != null`).
- **Composables** (`WatchScreen.kt`): `WatchScreen(state,onAction,onBack,modifier,fullscreen,playerSlot,menuContent,sidebarCovered,drawBackground,keepPlayerSlotInFullscreen)` `:66`; `WatchVideoMenu` `:161` (item menu / save sheet / feedback sheet host); `WatchMainBody`, `RelatedPaging`, `RelatedFooter`, `WatchProvisionalHeader`, `WatchSkeleton`, `WatchError`, `WatchMetadata` `:438`, `WatchTitle`, `WatchMetaRow` `:487`, `WatchActionBar` `:511`, `WatchSlotButton`, `WatchComments` `:588` (teaser card), `WatchSidebarPane` `:643`, `SidebarCell`, `PhoneFeed/StackedFeed/ListFeed/GridFeed`, `RelatedThumb/Text/Kebab`, `Artwork`; `WatchDescription` (separate file).
- **Dialogs/sheets**: item menu, save-to-playlist (with new-playlist form), feedback reasons, verification card (traffic challenge), search overlay `SearchPanel` (from `ui/components`), comments sheet, live chat sheet.

### 2.2 Player surface (shared by Watch, Shorts, mini)
- **State holder**: `PlayerViewModel.uiState: StateFlow<PlayerUiState>` (`PlayerViewModel.kt:167`). Fields (`:64-141`): `visible, fullscreen, fullscreenKeepsOrientation, fullscreenRestoresEntryOrientation, controlsVisible, loading, isPlaying, userWantsPlay, durationMs, speed, title, author, isLive, videoId, prepared, firstFrameRendered, hasRenderedVideoFrame, url, loopMode, qualityLabel, qualities, activeQuality, subtitleKey, subtitleTracks, subtitleEnabled, audioTracks, audioTrackKey, error, notice, failureReport, failureDialogVisible, scrubbing, hasNext, hasPrevious, locked, queueItems, queueEnabled, queueOwner, sponsorSegments, sponsorCountdownSec, sponsorChip, sponsorChipHighlight, resizeMode, pipAvailable, ended, mini, page* geometry, videoWidth/Height, chapters, itag/audio stats, casting*, cast*, castFailure, subtitleStyle, discontinuityEpoch`.
- **High-rate state kept OUTSIDE `PlayerUiState`** (do not merge them back; perf): `positionState`, `bufferedPositionState` (`State<Long>`), `gestureState: State<GestureUi?>` (sealed `GestureUi` `:55`: DoubleTapSeek, Scrub, EdgeSlider...), `hintState: State<String?>`, `subtitleCuesState: State<List<String>>` (`:180-187`).
- **Intents** (`PlayerPlaybackActions`, `PlayerActions.kt:10-77`): onPlayPause, onSeek, onToggleControls, onHideControls, onFullscreenToggle / SwipeExit / SwipeRestore, onSpeedHoldStart/End, onHint, onSpeed, onQuality(label?), onSubtitle(key?), onLoopMode, onNext, onPrevious, onDoubleTapSeek, onScrubPreview/Commit, onTimeBarPreview/Commit, onLockToggle, onAudioTrack, onAudioMenuOpened, onSubtitleStyle, onQueueItem(url), onResize, onQueueEnabled/Clear/Remove/Move(from,to,owner)/Add/AddNext, onRetry, onErrorDetails, onCastStop/Device/FailureDismiss/ShareCastLink/CloseCastLink/Discovery, onMiniClose, onMiniRestore, onSponsorSkipCancel/SkipNow/ChipSkip, onGestureEnd. `PlayerWindowActions` (`:79`): onBack, onBrightness(delta), onVolume(delta), onPip, onShare, shareCastLink, onGestureEnd. Extra non-interface members used by surface hosts: `gestureEnabled(zone)`, `attachSurface/detachSurface`, `hasRenderedFrame`, `setFullscreen/setFullscreenManual/setLocked`, `enterMiniPlayer`, `onMiniSwipeRequested`, `prepareWindowHandoff`, `refreshCastState/initializeCast`, `currentCastLink`, `onEdgeSlider`, `onPhysicalOrientation`, `dismissPlaybackFailure`, `onPipEntered/onPipDismissed`, `refreshPipAvailability`.
- **Composables** (all in `player/surface`): `PlayerSurface` (entry), `TopBar`, `CenterControls`, `BottomBar`, `PlayerTimeBar`, `ChapterEntryChip`, `GestureOverlays`, `SponsorCountdownCard`, `HintOverlay`, `CastStatusBanners`, `ErrorOverlay`, `LockAffordance`, `MiniPlayerChrome`, `MiniPlayerWindow`, `SubtitleOverlay`, `PlayerSheetHost` (More/SubtitleStyle/Queue/Chapters), `PlayerDialogHost` (EndBehavior/Quality/Speed/Resize/Audio/Captions/Info/Cast), `PlayerAnchorDropdown`, `SegmentPreviewDialog`, `CastFailureDialog`, `PlaybackFailureDialog`, `QueueSheet`, `LibraryQueueSheet`, `SubtitleStyleEditor`, `PlayerTransitionFrame`.
- **Mini player hosts**: (a) in `WatchActivity` (overlay, `:558`), (b) in `ui/ContentActivity.kt:141` (browse tabs; docks above 56 dp nav), (c) `library/YouActivity` references surface. Position persisted via `MiniPlayerStore`.

### 2.3 Comments
- **VM**: `CommentsViewModel(repository: CommentsRepository, savedState, initialTarget: CommentTarget, loadOnStart)` (`CommentsViewModel.kt:40`); write capability is `repository as? CommentActions` (`:104`), menu actions from `actions?.supportedMenuActions` (`:91,155`).
- **State**: `uiState: StateFlow<CommentsUiState>` (`:94`): `videoId, sort (Top|Newest), content (Loading|Ready(threads,nextCursor,loadingMore,loadMoreFailed)|Empty|Disabled|Failed), expanded:Set<String>, total, overlay (None|SortMenu|ThreadMenu|ReplyMenu|Composer(threadId,replyId,editing)|DeleteConfirm), draft, actionPending, refreshing, pendingAction, target, supportedMenuActions`. Also `actionNotices`.
- **Public**: `startLoading, selectVideo, refreshSession, refreshAuthorization, onAction, cancelTransient, ensureLoaded, dispose`.
- **Events (`CommentAction`)**: `Retry, LoadMore, SelectSort, OpenSortMenu, CloseMenu, MenuAction, ToggleReplies, LoadMoreReplies, Compose, DraftChanged, SubmitDraft, DismissComposer, RequestDelete, ConfirmDelete, CancelDelete, LikeThread, DislikeThread, ReportThread, OpenThreadMenu, Reply, OpenAuthor, OpenLink, LikeReply, DislikeReply, ReportReply, OpenReplyMenu`.
- **Domain**: `CommentsRepository.comments(target, sort, cursor)`, `.repliesPage(target, thread, cursor)`; `CommentActions.perform(CommentNavigationRequest): CommentMutation`.
- **Composables** (`CommentsPanel.kt`): `CommentsPanel(state, commentCount, onAction, onClose, listState)` `:86`, `CommentsSurface`, `CommentComposerSheet` `:324`, `DeleteCommentSheet`, `CommentsHeader`, `SortControl/SortMenu`, `CommentsBody`, `Simplebox` (input teaser), `ThreadList`, `ThreadItem`, `ThreadContent`, `ReplyContent`, `CommentAuthorRow`, `CommentActions`, `CountedAction`, `RepliesToggle`, `CommentAvatar`. Motion: `CommentSheetMotion.kt`.
- Used by: `WatchActivity` (panel host `testTag("comments-panel-host")`, saved list state per `(videoId, sort)`), `ShortsActivity` (`:472`, via `RecentCommentsViewModel.getOrCreate`).

### 2.4 Live chat
- **VM**: `LiveChatViewModel(gateway: LiveChatGateway?, ...)` created at `WatchActivity.kt:202-209`. `LiveChatUiState(content Loading|Ready(messages)|Failed, draft, sending, signedIn, canSend, replay, ended, postingRestriction)`; actions `DraftChanged, Send, Retry, SignIn`.
- **Gateway** `LiveChatGateway.listen/replay/send` (`LiveChatViewModel.kt:~29`). Routing: `liveChatTarget(video, playerLive, commentsOpen, searchOpen, fullscreen, pip)` (`LiveChatRouting.kt`) - chat replaces the comments panel only when the video is live and has `chatKey`; hidden in fullscreen/PiP.
- **UI**: `LiveChatPanel(state,onAction,onClose)` `:61` (rail on wide, sheet on narrow, drag header, `live-chat-*` test tags).

### 2.5 Shorts
- **VM**: `ShortsViewModel(repository: ShortsRepository, savedState, scenario, startItemId, videoActions: VideoActions?, preloader: ShortsPreloader?, saves: SaveRepository?)` (`ShortsViewModel.kt:27-35`). `uiState: StateFlow<ShortsUiState>` (`content Loading|Ready(page)|Failed|Empty, startItemId, overlay (None|Comments(id)|Menu(id)), currentItemId, appendLoading/Failed, live, ratings, subscriptions, subscriptionsPending, ratingsPending, details`). Flows: `navigationRequests`, `actionNotices`; `saveSheet: SaveSheetController?`.
- **Events (`ShortsAction`)**: `Retry, LoadMore, RetryMore, OpenChannel, OpenComments, CloseComments, CloseMenu, OpenMenu, MenuSelected, OpenSaveToPlaylist, Share, Like, Dislike, SelectItem, Subscribe`; VM hooks `onPlaybackReady/Ended`, `updateSession`.
- **Domain**: `ShortsRepository.shorts()/shortsNext(cursor)/hydrate(itemId)`; `VideoActions.rate/subscribe`; `SaveRepository`.
- **Composables**: `ShortsScreen(state,onAction,...,videoContent)` `:50`, `ShortsPager` (`VerticalPager`, `beyondViewportPageCount = 1`, `:137`), `ShortPage`, `ShortsSkeleton`, `RailAction`, `ShortPlaybackControls` (tap pause, speed-hold, buffering, access notice), `ShortsDescriptionSheet`; playback-order dialog (`ShortsActivity.kt:512`, `onLoopMode`). Player attach: `onShortsOpened/Closed/Stopped`, `openShort(id)`, `holdShortsForSwipe`, `resumeShortsAfterSwipe` (`PlayerViewModel.kt:532-620`).

---

## 3. Data flow
- **Watch**: `WatchActivity` creates VM -> `startLoading()` (when `loadOnStart=false` in prod, kicked later) -> `WatchRepository.watch(videoId)` -> `WatchContent` -> `WatchScreen`. Provisional card from `WatchProvisionalStore.consume(videoId)` paints title/artwork instantly. Related paging: `LoadRelated` -> `repository.next(videoId, cursor)` (`WatchViewModel.kt:647`); avatars backfilled (`:689`); `/sorry` challenge -> `verification` state + `OpenVerification` nav request (`:783`). Error/empty: `WatchContent.Failed` -> `WatchError` retry (`Retry -> load(fresh=true)`). Related failure shows `RelatedFooter`. Votes: `refreshVoteCounts` (return-dislike style counts, `:167`). Comments preview into the page via `updateCommentsPreview(...)` fed from `CommentsViewModel.uiState` collector (`WatchActivity.kt:~380`).
- **Playback**: `WatchActivity` -> `playerViewModel.openVideo(id, start)` (`PlayerViewModel.kt:508`) -> engine resolves -> `PlaybackSnapshot` (~4 Hz) -> `PlayerUiState` + `positionState`. WatchViewModel never touches the player; the Activity bridges (`SeekTo` -> `onSeek`, `Queue`, `Download`).
- **Comments**: paging via `cursor`; `LoadMore` footer (`ListFooter`), `loadMoreFailed` retry; replies per-thread cursor (`repliesNextCursor`, `repliesLoading/Failed`); states Loading/Ready/Empty/Disabled/Failed rendered by `CommentsBody` (`:585`) and `MessageBlock` with retry. Scroll position survives via `rememberSaveable(videoId, sort)` LazyListState in the Activity plus a restore effect (`WatchActivity.kt:~398-410`).
- **Shorts**: `repository.shorts()`/`shortsNext()` -> `ShortsContent`; `hydrate()` fills missing metadata/subscription/rating lazily (`hydrateSelected`, `:203`); pager selection -> `SelectItem` + `playerViewModel.openShort`; `ShortsMediaPreloader` preloads next.
- **DeArrow**: display-layer only; composable asks `DeArrowService.branding(videoId)` and swaps title / thumbnail URL when prefs on; original stays canonical.
- **Live chat**: `listen(key)` polling loop (page.waitMs) or `replay(key, positionMs)` for ended streams; `send` needs signed in + web session (`ChatWebSessionStore`) else `ChatAuthorizationRequiredException` -> `SignIn` action opens `chatAuthorizationLauncher`.

---

## 4. Feature matrix (YouTube Android parity)

### 4.1 Watch page
| Feature | Status | Data evidence | UI evidence |
|---|---|---|---|
| Title / views / age / handle / LIVE badge | WORKS | `WatchVideo` `WatchModels.kt:5-30` | `WatchScreen.kt:438-510` |
| Expandable description (links, timestamps -> seek) | WORKS | `WatchVideo.description`; `parseDescription` `WatchDescription.kt:47`; `SeekToTimestamp` blocked for live (`WatchViewModel.kt:~214`) | `WatchDescription`, `watch-more` toggle `WatchScreen.kt:~500` |
| Channel row (avatar, name -> channel) | WORKS | `WatchVideo.channelId/avatar` | `WatchActionBar` `:511-535` |
| Subscribe | WORKS (needs sign-in) | `VideoActions.subscribe` `VideoActions.kt:15`; `MediaVideoActions.kt:35` | `SubscribeButton` (`ui/design`) `WatchScreen.kt:~533`; no bell/notification-level menu (not found) |
| Like / Dislike with counts | WORKS | `VideoActions.rate`; counts via `VoteCountRepository` | `WatchSlotButton` idx 0/1 `WatchScreen.kt:538-550` |
| Share | WORKS | `ShareGateway` | action bar |
| Save to playlist / Watch later (+ create playlist) | WORKS | `SaveRepository` (saves pkg), `WatchOverlay.Save` | `WatchVideoMenu` `:161` |
| Download | WORKS | `DownloadSingleSpec` -> downloader (`WatchActivity.kt:284`) | action bar |
| Add to queue | WORKS | `QueueItem` -> `PlayerViewModel.onQueueAdd` | action bar |
| Open with (external) | WORKS | `ShareGateway.openWith` | action bar |
| Report | DATA_ONLY / hidden in prod | needs feedback token the page lacks (comment `WatchScreen.kt:~546`) | filtered when `live` |
| Related/recommended (phone feed, grid, list, sidebar) + paging | WORKS | `WatchRepository.next` | `WatchRelatedFeed` variants `:751-`; item menu (queue/download/watch-later/save/share/not-interested/don't-recommend, token-gated `WatchModels.kt:~109`) |
| Comments teaser card + open sheet | WORKS | `WatchComment`, `commentsStatus` | `WatchComments` `:588` |
| Comments read (sort Top/Newest, replies, pagination) | WORKS | `CommentsRepository` | `CommentsPanel` |
| Comment post / reply / delete | WORKS (signed in) | `MediaCommentsRepository.perform` handles Submit/Reply/Delete `:194-208`; `supportedMenuActions = {Reply, Delete}` `:46` | composer sheet `CommentsPanel.kt:324`, delete sheet `:422` |
| Comment like/dislike | WORKS | `LikeComment/DislikeComment` -> `rate()` `MediaCommentsRepository.kt:180-181` | `CountedAction` `CommentsPanel.kt:923` |
| Comment edit / report | DATA_ONLY (typed requests exist, not in `supportedMenuActions`, so menu hides them in prod) | `CommentNavigationRequest.EditComment/ReportComment` `CommentModels.kt` | menu filtered by `menuActions(state)` `CommentsPanel.kt:311` |
| Live chat (read, replay, send) | WORKS | `LiveChatGateway` | `LiveChatPanel` |
| Pull-to-refresh on watch page | MISSING (not found; only `Retry` button; searched `pullRefresh|PullToRefresh` in watch/) | - | - |
| Chapters in description / chapter list | see 4.2 | | |
| Hashtags / "Show transcript" / channel membership "Join" / "Super Thanks" / playlist panel on watch page | MISSING (searched watch/ for transcript, hashtag, Join, playlist panel) | - | - |

### 4.2 Player
| Feature | Status | Data evidence | UI evidence |
|---|---|---|---|
| Double-tap seek (+/-10 s thirds, center = play/pause) | WORKS | `GestureMath.DOUBLE_TAP_SEEK_MS=10_000`; `onDoubleTapSeek` `PlayerViewModel.kt:763` (accumulating `seekAccumMs`) | `PlayerGestures.kt:62-80`; overlay `DoubleTapSeekOverlay` |
| Horizontal drag scrub (+-120 s full width) | WORKS | `onScrubPreview/Commit` `:776-783` | `GestureOverlays.ScrubBubbleOverlay` |
| Seek bar drag | WORKS | `onTimeBarPreview/Commit` `:794-806` | `PlayerTimeBar.kt` (chapter ticks, sponsor segment overlay) |
| Seek-bar thumbnail (storyboard) preview | MISSING (searched `storyboard|spriteSheet|thumbnail` in `PlayerTimeBar/PlayerGestureOverlays/PlayerControls`; only time bubble) | no storyboard in `extractor` consumed by player | - |
| Long-press 2x speed | WORKS | `onSpeedHoldStart/End`, `engine.setSpeedHold` | `SpeedHoldOverlay` (`LONG_PRESS_MS = 400`) |
| Swipe to enter/exit fullscreen | WORKS | `onFullscreenToggle/SwipeExit/SwipeRestore` `:1046-1061` | `playerGestures` center vertical drag -> `FullscreenSwipeHandle`; page pull `WatchFullscreenPull.kt` |
| Auto fullscreen on rotation | WORKS | `FullscreenState`, `AutoFullscreen`, `onPhysicalOrientation` `:1071` | `PlayerWindowEffects` |
| Miniplayer (swipe-down to minimize, drag/dock, collapse to edge, pinch resize, dismiss, tap to restore) | WORKS | `enterMiniPlayer`, `onMiniSwipeRequested`, `onMiniClose`, `onMiniRestore` | `MiniPlayerWindow.kt`, `MiniPlayerChrome` |
| Pinch to zoom (fullscreen) | WORKS | UI-local `VideoZoom` (not persisted) | `PlayerGestures` `zoomEnabled`, `ZoomMath`, `PlayerSurface.kt:713` |
| Quality selection (+Auto, active-quality badge) | WORKS | `onQuality`, `qualities`, `activeQuality` | `PlayerDialog.Quality`; More sheet |
| Playback speed | WORKS | `onSpeed` | `PlayerDialog.Speed` |
| Captions/subtitles (select, on/off, auto-generated flag) | WORKS | `onSubtitle`, `SubtitleTrack` | `PlayerAnchorMenu.Subtitles`, `PlayerDialog.Captions`, `SubtitleOverlay` |
| Subtitle style editor (colors, size, bg, position) | WORKS (beyond YT) | `SubtitleStyle` persisted via `onSubtitleStyle` | `SubtitleStyleEditor.kt` |
| Audio track selection | WORKS | `onAudioTrack`, `audioTracks`, catalog supplement | `PlayerDialog.Audio` |
| Chapters (list + chip + timeline ticks + preview dialog) | WORKS | `Chapter` `extractor/Models.kt:64`, `state.chapters` | `ChaptersSheet`, `ChapterEntryChip`, `SegmentPreviewDialog` |
| Autoplay next | WORKS (via `LoopMode.QUEUE_NEXT`, default; related-next via `nextVideo` lambda `AppModule.kt:276`) | `PlayerViewModel.relatedNext`, `skipToNext` `:944` | End-behavior dialog `PlayerDialog.EndBehavior`; no separate YouTube-style autoplay toggle |
| Queue (view, reorder, remove, clear, play-next, enable) | WORKS | `QueueRepository`, `onQueue*` | `QueueSheet`, `QueueDragState` |
| Shuffle | PARTIAL: only as end-behavior `LoopMode.QUEUE_RANDOM` and `startPlaylist(shuffle=true)` `:869`; no in-queue shuffle toggle | | `PlayerUi.kt:302-309` icons |
| Repeat (one) | WORKS | `LOOP_ONE` | End-behavior dialog |
| Previous / Next | WORKS | `skipToPrevious/Next` | `CenterControls` |
| Picture-in-Picture (manual + auto-enter, remote actions: prev/play/next/audio) | WORKS | `PlaybackActivity.kt:107-260`, `PipAutoEnter`, `onPipEntered`, `pipAvailable` | More sheet `onPip`; `pip` param of `PlayerSurface` |
| Background playback / audio-only | WORKS (pref `ENABLE_BACKGROUND_PLAY`) | `PlaybackLifecycle.kt`, `audioOnlyBackground`, `playAudioOnlyInBackground` `:1545` | PiP action `ACTION_PIP_AUDIO` |
| Media notification + MediaSession | WORKS | `PlaybackService.kt:215`, `ServiceNotificationController`, `PlaybackCommandRouter` | system UI only (no app UI) |
| Cast (Chromecast-style + DLNA + link share) | WORKS | `cast/CastController`, `DlnaController`, `onCastDevice/Stop/ShareCastLink` | `CastDialog` from More sheet, `CastStatusBanners`; no dedicated top-bar cast button (More row only) |
| Sleep timer | MISSING (grep `sleep.?timer` across `YL/` -> 0 hits) | - | - |
| Brightness swipe (left 35%) / Volume swipe (right 35%) | WORKS | `PlayerWindowHost.onBrightness/onVolume`, `onEdgeSlider` | `EdgeSliderOverlay` |
| Screen lock | WORKS | `onLockToggle`, `setLocked` | `LockAffordance` |
| Resize mode (fit/fill/zoom) | WORKS (optionally remembered `REMEMBER_RESIZE_MODE`) | `onResize` | `ResizeDialog` |
| Custom gesture settings (14 prefs: 7 zones x windowed/fullscreen) | WORKS | `PreferenceKeys.kt:42-70`, `gesturePrefKey` `PlayerViewModel.kt:1615`, `gestureEnabled` `:241` | Settings UI lives outside these packages (settings pkg); surface reads `zoneEnabled` |
| Info / stats-for-nerds dialog | WORKS | itag, bitrate, etc. | `InfoDialog.kt` |
| Error / failure report dialog w/ retry | WORKS | `failureReport`, `onErrorDetails` | `PlaybackFailureDialog`, `ErrorOverlay` |
| Playback speed persistence, resume position (remote) | WORKS | `remoteResumePosition` (engine) | - |
| Double-tap seek amount configurable | MISSING (constant `10_000`) | | |

### 4.3 Shorts
| Feature | Status | Evidence |
|---|---|---|
| Vertical feed, swipe to change | WORKS | `ShortsPager` `VerticalPager` `ShortsScreen.kt:137` |
| Like / Dislike | WORKS (signed in) | `ShortsAction.Like/Dislike`, `VideoActions.rate` (`ShortsViewModel.kt:165`) |
| Comments sheet | WORKS | `CommentsPanel` in `ShortsActivity.kt:472` |
| Share | WORKS | `ShortsNavigationRequest.Share` |
| Subscribe | WORKS | `SubscribeButton` `ShortsScreen.kt:211` |
| Save to playlist / overflow menu | WORKS | `OpenSaveToPlaylist`, `saveSheet` |
| Description sheet | WORKS | `ShortsDescriptionSheet` |
| Tap pause, hold 2x, playback order, load-more | WORKS | `ShortPlaybackControls`, `onLoopMode` |
| Remix / sound page / audio attribution | MISSING (searched shorts/) |

### 4.4 Litube extras (MUST keep)
| Feature | Status | Evidence |
|---|---|---|
| SponsorBlock: categories (prefs-driven), fetch, countdown skip, manual chip, highlight (poi) jump, timeline markers | WORKS | `SponsorBlockManager.kt:19-135` (`SKIP_SPONSORS`, `SPONSOR_COUNTDOWN` prefs `PreferenceKeys.kt:23,32`); `SponsorSkipController`; `PlayerUiState.sponsorSegments/sponsorCountdownSec/sponsorChip/sponsorChipHighlight`; UI: `SponsorCountdownCard` (`PlayerGestureOverlays.kt:285`, used `PlayerSurface.kt:490`), chip, `PlayerTimeBar` segment drawing; actions `onSponsorSkipCancel/SkipNow/ChipSkip`. Category-picker UI is in settings (outside scope). |
| DeArrow titles + thumbnails | WORKS (prefs default off) | see 1.5 |
| Custom player gestures (zones, per-mode toggles) | WORKS | see 4.2 |
| Remember resize mode, subtitle style, mini-player geometry, background play, PiP toggle | WORKS | prefs |

---

## 5. Continuity: who owns the player

- **Owner object**: `PlaybackEngine` (`PlaybackApi`, Koin `single`) -> wrapped by `PlayerViewModel` (Koin `single`, "Process-scoped: background play, MediaSession keys and queue auto-advance must outlive the activity", `AppModule.kt:266-268`). `PlaybackSession.register(player)` publishes it (`PlaybackSession.kt`). `PlaybackService` (Media3 `MediaSession`) routes media-key/notification commands via `PlaybackCommandRouter`.
- **The video output** is a single `SurfaceView` created inside `PlayerSurface`; `WatchPlayer` attaches it on ON_START/ON_RESUME and detaches on ON_STOP/dispose (`WatchPlayer.kt:~62-82`): `playerViewModel.attachSurface/detachSurface`. `PlaybackLifecycle` hides video (`setVideoVisible(false)`) 700 ms after last activity stops, pauses unless background-play pref.
- **Watch <-> fullscreen**: not a new composition. `WatchActivity` keeps ONE `MiniPlayerWindow` (`:558`) whose `content` is `WatchPlayer`; it interpolates between `embedded*Dp` (the slot rect measured from `WatchScreen`'s `playerSlot` Box, `:524-526`) and window-filling (`fillsWindow = fullscreen`). Page uses `keepPlayerSlotInFullscreen = true` so the slot is still measured. Comment at `:515-517` states the invariant: "a mode switch keeps the composition, the SurfaceView and the prepared media".
- **Watch -> mini (same activity)**: swipe down -> `onMiniSwipeRequested` -> `PlayerUiState.mini = true`; `onMiniSwipeFinished` -> `prepareWindowHandoff { finish() }` finishes the Watch activity; the mini then lives in `ContentActivity` (`ContentActivity.kt:136-160`) at `WatchTransition.miniBounds`, restored by `onMiniRestore` / tap with a captured video frame (`WatchTransition.videoFrame/miniRestoreFrame`, `rememberWatchTransitionFrame`) so no black flash. `WatchActivity.handoffToMini` `:751-815`.
- **PiP**: handled in `PlaybackActivity` (`pipParams()`, auto-enter API 31+ `setAutoEnterEnabled`, legacy `onUserLeaveHint`, remote actions prev/toggle/next/audio, receiver). `inPip` StateFlow -> `WatchActivity` treats `fullscreen = playerState.fullscreen || pip` (`:~397`); `PlayerSurface(pip=true)` fills window. `PlaybackSession.updatePipHost` tracks the PiP host activity. Dismissed PiP -> `onPipDismissed()` stops/handles.
- **Shorts**: same VM; `onShortsOpened/Stopped/Closed` switch `mediaKind` and `setShortsMode` (separate playback order).
- **What the new UI MUST NOT break**:
  1. Do not create a second `SurfaceView` / second `PlayerSurface` per mode; keep a single overlay host with an interpolated rect.
  2. Keep reporting the slot rect (`onGloballyPositioned { boundsInRoot }`) from the page; keep `keepPlayerSlotInFullscreen`.
  3. Keep calling `attachSurface` on START/RESUME and `detachSurface` on STOP/dispose (`WatchPlayer`), and `initializeCast()`.
  4. Keep `withWatchPresentation` merge semantics (handoff state overrides only `visible/mini/fullscreen`).
  5. Keep `PlayerWindowEffects` (immersive bars + orientation) and `PlayerWindowHost` brightness/volume.
  6. Keep `miniHandoffPending/localMiniCollapse` logic, `entryProgress/playerDrawn` gating (page alpha tied to player's first draw, `watchPageProgress`) or the entrance animation shows a blank page.
  7. Keep back-handling order (`WatchActivity.kt:~430-452`): overlay -> search -> comments overlay -> comments -> `PlayerUi.nextBackStep(locked, fullscreen)`.
  8. On fullscreen/PiP, comments and item overlays auto-close (`LaunchedEffect`s `:~412-428`).
  9. Compose semantics: covered layers use `clearAndSetSemantics {}` so tests/a11y only see the top layer - preserve when re-layering.

---

## 6. UI coupling & redesign notes

### 6.1 Safe to rewrite (pure presentation)
`watch/presentation/WatchScreen.kt`, `WatchDescription.kt` (keep `parseDescription`/`timestampSeconds` - unit-tested), `WatchPreviews.kt`, `comments/presentation/CommentsPanel.kt`, `CommentRichBody.kt`, `CommentSheetMotion.kt`, `watch/chat/LiveChatPanel.kt`, `shorts/presentation/ShortsScreen.kt`, `ShortsDescriptionSheet.kt`, `ShortPlaybackControls.kt` (keep callback wiring), `dearrow/DeArrowDisplay.kt` (keep contract: return original until replacement), `player/surface/*` composables (sheets, dialogs, controls, queue, chapters, subtitle editor, overlays).

### 6.2 UI mixed with logic that must be preserved
- `WatchActivity.kt` (863): navigation handling, mini handoff, entry/transition clocks, page alpha, back stack, comment-list scroll restore, chat target routing (`liveChatTarget`), PiP/fullscreen auto-close effects. Treat as logic; restyle only inner pieces (`WatchScreen`, panels).
- `WatchLayout` (`WatchModels.kt:~160-271`): breakpoints (550/931/1280), sidebar widths (256/500), ActionBarHeight 48, grid math "pinned to docs/visual/home-measurements.md" - goldens depend on exact numbers; `WatchLayout.sidebar/pinnedPlayer/playerHeight` also feed `WatchActivity.kt` (hideWatchControls, panel layout) and `CommentsLayout.sheetTop`.
- `PlayerSurface.kt`: manages sheet/dialog state, zoom, subtitle clearance by measured bar heights (`topBarHeightPx`, `bottomBarHeightPx`, `trackClearancePx`, `skipCardPx`), `SideEffect` writing `FullscreenSwipeHandle.pictureAspectRatio/pictureResizeMode` (`:112-118`) - required by the window morph.
- `PlayerGestures.kt`: `TapGuard` (suppress tap after zoom/long-press), zone model (left 35% brightness, right 35% volume, center fullscreen), `LONG_PRESS_MS = 400`, vertical-feed mode for Shorts (`verticalFeed`), `restartKey`, `ForwardingGestures` bridging to `FullscreenSwipeHandle` (`PlayerSurface.kt:675-711`). If rewriting, keep `GestureCallbacks` and `GestureMath` (unit-tested: `GestureMathTest`).
- `MiniPlayerWindow.kt` (980): includes gesture + animation + persistence; high risk. Recommend restyle only `MiniPlayerChrome` (`PlayerControls.kt:620`) and stroke/scrim constants (`MiniPlayerLayout.STROKE_COLOR/SCRIM_COLOR/CORNER_DP`).
- `PlayerWindowHost.kt`: Android window side effects (brightness/volume/orientation) - keep.
- Identity/revision checks living in UI: `WatchActivity` consumes `WatchProvisionalStore`; `QueueDragState` captures `QueueOwner` at drag start and passes it to `onQueueMove(..., owner)` (`PlayerActions.kt:57-60`) so a commit from a switched identity is dropped - must keep passing `owner`. `WatchVideo.sessionGeneration`, `WatchRelated.feedbackGeneration` pass through unchanged.

### 6.3 Shared design system used
`ui/design`: `DesignTokens` (Background, Secondary, Red...), `AppText`, `AppIcon`, `AppIconButton`, `SubscribeButton`, `Artwork`, `WriteProgressOverlay`, `AppMotion`, `LocalMotionScale`, `motionDuration`, `appDescription` (a11y helper), `rememberHapticAction`. `ui/theme`: `AppTheme`, `AppStyle` (Panel, etc.). `ui/components`: `AppSheetHandle`, `YoutubeThumb`, `SearchPanel`, `rememberSearchRows`, `audioTrackLabel`, `WatchSheetHost`. Player surface also uses `MaterialTheme.colorScheme` + `AlertDialog` (AnchorMenus) - mixed token sources.

### 6.4 Hard-coded values
- Colors: `Color.White/Black/0x..` occurrences: PlayerUi 20, PlayerControls 16, PlayerGestureOverlays 14, PlayerSurface 7, SubtitleStyleEditor 4, MiniPlayerWindow 4, MiniPlayerLayout (`STROKE_COLOR 0x12FFFFFF`, `SCRIM_COLOR 0x66000000`), WatchScreen uses `DesignTokens.Red` for live badge, `Color.White`. Player chrome is intentionally dark/black overlay.
- Dimens: `WatchLayout` and `CommentsLayout` (`SheetRadius 12`, header 65, etc.) are CSS-px-ported constants; WatchScreen uses literal `dp` heavily (e.g. paddings 13/12/5/3). Mini sizes 190-420 dp, margins 12-24.
- Strings: mostly `stringResource` (player/surface ~190 uses). Hard-coded English in domain: `WatchFeedbackKind` reasons, `WatchVideoMenuAction` labels, `CommentMenuAction` labels, `AppNotice.Message("Live streams can't be seeked to a timestamp.")` (`WatchViewModel.kt:~215`), fallback suggestions "documentary/music/live" (`WatchActivity.kt:~590`). Domain enums carry display labels - a localization redesign must handle these.
- Accessibility: `appDescription(R.string...)` used on avatar/more; actions have contentDescriptions; touch targets mostly 48 dp (`PlayerControlSizingAndroidTest` asserts sizing); mini controls 30/34 dp (`MiniPlayerLayout.CONTROL_SIDE_DP/PLAY_DP`) - below 48 dp. Time bar has a11y semantics (`PlayerTimeBarA11yAndroidTest`).
- Tablet/landscape: handled in `WatchLayout` (sidebar >= 931 landscape, wide-short mode, grid at >= 550). Comments become a docked rail (`WatchLayout.sidebar` -> `sidebarCovered`, `LiveChatPanel` rail `live-chat-rail`). Player fullscreen orientation handled by window effects. Shorts has no tablet layout (not found).
- Scroll-state: watch lists `rememberSaveable(state.videoId, LazyListState.Saver)` x4 (`WatchScreen.kt:77-80`); comments list saved per `(videoId, sort)` in Activity; Shorts pager state per item id.

### 6.5 Test tags / semantics other code relies on
Watch: `watch-screen`, `watch-player-slot`, `watch-metadata`, `watch-meta`, `watch-more`, `watch-description`, `watch-live-badge`, `watch-actions`, `watch-action-{like,dislike,download,queue,save,open-with,share,report}`, `watch-action-avatar`, `watch-action-subscribe`, `watch-channel-name`, `watch-mini-backdrop`, `watch-mini-entry-bridge`, `comments-panel-host`, `live-chat-panel-host`, `live-chat-{rail,scrim,drag-header,close,loading,failed,retry,empty,list,message,ended,readonly,input,send,posting-restriction,signin}`. Player: `player-window`(34 uses), `player-buffering`, `player-frame-shutter`, `player-handoff-frame`, `player-sponsor-countdown`, `player-sponsor-chip`, `player-chapter-entry`, `player-dialog-{quality,speed,end-behavior,scrim}`, `player-modal-sheet`, `player-sheet-handle`, `player-center-controls`, `mini-viewport`, `queue-list`, `chapters-sheet-list`, `chapter-row-*`, `segment-row-*`, `control-frame`, `closing-window`. Shorts: `shorts-screen`, `shorts-pager`, `shorts-skeleton`, `shorts-append-retry`, `short-page-{id}`, `short-cover-{id}`, `short-more-{id}`, `short-channel-{id}`, `short-subscribe-{id}`, `short-title-{id}`, `rail-label-{description}`, `short-playback-controls`, `short-speed-hold`, `short-access-notice`, `short-buffering`, `short-paused`.

---

## 7. Tests touching these packages
Unit (`app/src/test/java/com/hhst/youtubelite/`): watch 11 files (WatchViewModelTest 49 @Test, WatchRatingFeedbackTest, WatchSaveOwnershipTest, WatchAvatarRecoveryTest, WatchProvisionalStoreTest, WatchPlayerStateTest, `chat/` 4, `presentation/WatchDescriptionTest`); player 36 files (PlayerViewModelTest 63 @Test, PlayerCallbackBridgeTest, FullscreenStateTest, QueueRepositoryTest, `surface/` AutoFullscreenTest, FullscreenSwipeMotionTest, GestureMathTest, MiniPlayerLayoutTest, PlayerUiTest, QueueReorderTest, SubtitleStyleTest, ZoomMathTest; `sponsor/` 4; `datasource/` 13; `engine/` 5; `service/` 1); comments 4 (CommentsViewModelTest 30, CommentTextTest, CommentWriteContinuityTest, CommentsRequestCountTest); shorts 5 (ShortsViewModelTest 17, hydration, rating hydration, preloader, RecentCommentsViewModelTest); dearrow 1 (DeArrowServiceTest). All are VM/pure-logic: **survive a UI redesign** provided the state/action types in sec. 2 are unchanged. Only `WatchDescriptionTest`, `PlayerUiTest`, `GestureMathTest` touch presentation-side pure helpers - keep those functions.

Instrumented (`app/src/androidTest/java/com/hhst/youtubelite/`): watch 15 files, player 59 (surface 18, engine 26, datasource 7, service 2, PipNavigationChain), comments 5, shorts 16. Categorized:
- **UI-structure dependent (need updating after redesign)**: watch `WatchScreenAndroidTest`, `WatchReferenceAndroidTest`, `WatchCommentsReferenceAndroidTest`, `WatchContentScrollAndroidTest`, `WatchFullscreenPullAndroidTest`, `WatchPageMotionAndroidTest`, `WatchWindowAppearanceAndroidTest`, `IconRenderingAndroidTest`, `chat/LiveChatPanelScreenshotAndroidTest`, `LiveChatDiscoveryUiAndroidTest`, `LiveChatRoutingHostAndroidTest`; player/surface `PlayerControlSizingAndroidTest`, `PlayerInteractionAndroidTest`, `PlayerTimeBarA11yAndroidTest`, `PlaybackControlsStateAndroidTest`, `QueueReorderAndroidTest`, `SheetSizingAndroidTest`, `FullscreenSheetAndroidTest`, `FullscreenMotionAndroidTest`, `MiniPlayerGestureAndroidTest`, `WindowTransitionAndroidTest`, `WatchEntryMotionAndroidTest`, `EmbeddedBoundsAndroidTest`, `ChapterEntryAndroidTest`, `SegmentDialogAndroidTest`, `SponsorTimelineAndroidTest`, `CastDialogAndroidTest`, `CastFailureDialogAndroidTest`, `PlaybackFailureDialogAndroidTest`, `ExperienceUpgradeAndroidTest`, `VideoReplacementFrameAndroidTest`; comments `CommentsPanelAndroidTest`, `CommentRichTextAndroidTest`, `CommentSheetMotionAndroidTest`, `WatchCommentsHostAndroidTest`; shorts `ShortsReferenceAndroidTest`, `ShortPlaybackControlsAndroidTest`, `ShortFramePresentationAndroidTest`, `ShortsHostAndroidTest`, `RecentCommentsStateAndroidTest`.
- **Golden-pixel**: `app/src/androidTest/assets/watch/goldens/` 6 PNG (`content|error|loading` x `390|1280`); `assets/comments/` 8 PNG; `assets/shorts/` 9 PNG; `assets/livechat/` 3 PNG (via `*ScreenshotAndroidTest`); `assets/player/` 0 PNG (only media fixtures `audio-original`, `freeze-motion`). **Goldens encode the old Litube/YouTube-mobile-web look and WILL all break**; regenerate or retire.
- **Backend/behaviour (UI-independent, no change)**: player/engine (26), datasource (7), service (2), `VideoRatingProbe`, `WatchServiceAndroidTest`, `YoutubeRelatedPaginationAndroidTest`, shorts preload/startup/HLS/TLS/prefetch/sequence/marathon tests. Some (`NativePlaybackHostAndroidTest`, `PipNavigationChainAndroidTest`, `BackMiniHandoffAndroidTest`, `PlayerFrameContinuityAndroidTest`, `WatchFullscreenSwipeAndroidTest`) drive `WatchActivity` and test continuity - they rely on `player-window`, `watch-player-slot` tags and window geometry.
- Test seams in production code that tests rely on: `WatchActivity.lastNavigationRequest/lastCommentRequest/lastDownloadSpec/lastQueuedItem/launchDownload`, debug intent extras `EXTRA_SCENARIO`, `EXTRA_COMMENTS_SCENARIO`, `EXTRA_CHAT_KEY` (`WatchActivity.kt:~175-182`), `ContentRepositories.fixtures(intent)`; fixtures repos `FixtureWatchRepository`, `FixtureCommentsRepository`, `FixtureShortsRepository`.

---

## 8. Risks / surprises
1. **`PlayerViewModel` implements UI-package interface** (`PlayerPlaybackActions` from `player.surface`). Renaming/moving `PlayerActions.kt` breaks the engine-side VM. Do not move; only add UI wrappers.
2. **`PlayerUiState` is the contract** shared with notification/PiP/cast logic; adding derived UI flags must go in UI layer, not the state.
3. **WatchActivity z-ordered layers are order-sensitive** (backdrop -> page -> overlay player -> search -> comments/chat -> item menu). The player overlay sits between page and panels so panels cover it; changing layering changes touch routing.
4. **Entry animation couples page alpha to player draw** (`playerDrawn`, `waitForEntryDraw`, `entryBackdrop` bitmap, `WatchTransition`). A new page design that re-times content alpha causes flashes; `watchPageContent` applies `graphicsLayer { alpha }` only (layout must be final from frame 0: "player slot keeps its final layout throughout navigation").
5. **Domain enums contain English UI strings** (`WatchVideoMenuAction.label`, `WatchFeedbackKind.title/reasons`, `CommentMenuAction.label`) and are used by fixtures; reasons are sent as `WatchNavigationRequest.Feedback(reason: String)`.
6. **Production vs fixture divergence**: prod hides Report row, hides ItemMenu rows lacking server tokens, hides unsupported comment menu actions; UI must continue to read `state.live`, `supportedMenuActions`.
7. **Mini player has three hosts** (Watch overlay, ContentActivity, YouActivity reference) with different insets (`56 dp` bottom dock; `WatchActivity.kt:~538-546` must match ContentActivity or handoff jumps). A new bottom-nav height must update both.
8. **DeArrow is not wired into the player UI** (title/notification still original). Parity extra if desired; engine `titleFetcher` exists in `PlaybackEngine` args (`AppModule.kt:250`).
9. **Tap/gesture arbitration is fragile**: two sibling `pointerInput` blocks sharing a `TapGuard`; Shorts uses same modifier with `verticalFeed=true`. Mini player pinch/drag handled in `MiniPlayerWindow` (`MiniPlayerHandle`) and `miniPlayerInteract` (`PlayerControls.kt:736`) - not in `playerGestures`.
10. **No `values-xx` audit done here**; Vietnamese/other strings may live in resources outside scope.
11. **Large rewrite targets by size** (UI only): WatchScreen 991, CommentsPanel 985, MiniPlayerWindow 980 (risky), PlayerControls 822, PlayerSurface 742, SubtitleStyleEditor 504, PlayerSheets 468, PlayerUi 411.
12. **Sleep timer / seek thumbnail / shuffle toggle are net-new** features requiring backend (engine) work - out of UI-only scope; flag as parity gaps rather than redesign tasks.
