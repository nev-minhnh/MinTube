# 02 - Browse features survey (home, explore, search, subscriptions, channel, post)

Source root `R = app/src/main/java/com/hhst/youtubelite/`. All paths below are repo-relative; abbreviate `R/` = that root. Line numbers are from the pristine Litube 4.0.0 checkout. Read-only survey; nothing in the repo was modified.

## 0. Architecture headline (read this first)

- **One Activity per screen, no Navigation-Compose.** `HomeActivity`, `ExploreActivity`, `SearchResultsActivity`, `SubscriptionsActivity`, `ChannelActivity`, `PostDetailActivity` each extend `ui/ContentActivity` / `ui/NativeActivity` and call `setAppContent { ... }`. Navigation is `startActivity` (`launchNavigation(...)`). Bottom-tab switching uses `MainTabs.intent(...)` with `FLAG_ACTIVITY_REORDER_TO_FRONT | SINGLE_TOP` (`R/ui/components/MainNavigation.kt:39-49`), so each tab Activity instance (and its Compose scroll state) is reused. Manifest: `app/src/main/AndroidManifest.xml:61` (Home), `:112` (Explore), `:114` (Search), `:129` (Channel), `:136` (PostDetail), `:107` (Subscriptions).
- **Pattern per feature**: `domain/*Models.kt` holds UiState + sealed `Action` + sealed `NavigationRequest` + Repository interface; `data/Fixture*Repository.kt` = debug-only fixture implementation; real implementation lives in `R/data/media/*` (owned by another agent). `presentation/*ViewModel.kt` exposes `uiState: StateFlow`, `onAction(Action)`, and a `Channel`-backed `navigationRequests` flow consumed by the Activity (the Activity, not the composable, performs navigation/share/voice/queue/download).
- **Composables are state-hoisted** (`XScreen(state, onAction, ...)`) -> good for a UI rewrite, but the Activities contain real logic (see section 5).
- **Fixture vs live**: `ContentRepositories.fixtures(intent)` (`R/data/media/ContentRepositories.kt:54`, debug only) swaps in fixtures; many VM constructor params are `null` in fixtures and non-null live (`live = saveRepository != null` etc.). Production code paths are the non-fixture ones.
- **Theme**: all screens wrap `AppTheme { ... }` (`R/ui/theme/Theme.kt:92`) and paint `DesignTokens.Background`. Every Activity calls `enableEdgeToEdge(SystemBarStyle.light(Color.WHITE, Color.WHITE), ...)` (12 occurrences repo-wide, e.g. `R/home/HomeActivity.kt` `initializeHome`, `R/search/SearchResultsActivity.kt:~65`) - hardcoded light system bars.

## 1. Package map

### home/ (2225 lines)
| File | Lines | Purpose |
|---|---|---|
| `home/HomeActivity.kt` | 276 | Host: builds VM (Factory), session refresh, navigation handler, shorts preloading, `PostCardActionHost`, link intent (`ACTION_VIEW`/`SEND` -> `OpenLink`), back handling, defers VM creation when launched as synthetic parent (`EXTRA_DEFER_HOME`). |
| `home/domain/HomeModels.kt` | 237 | `HomePage`, `FeedItem` (Video/Mix/Playlist/Shorts/Post), `HomeContent`, `HomeOverlay`, `HomeUiState`, `HomeAction`, `HomeNavigationRequest`, `HomeRepository`, `HomeLayout` (column breakpoints), `FeedMenuKind/Action`. Also shared by explore/search/subscriptions (`AppendState`, `FeedItem.Video`, `FeedMenuEndpoint`). |
| `home/data/FixtureHomeRepository.kt` | 68 | Fixture repo + `HomeScenario` enum (Empty/Loading/Error/PaginationError/...). |
| `home/presentation/HomeViewModel.kt` | 773 | State machine (load/append/refresh/menu/save sheet/feedback/session epochs/snapshot seeding). |
| `home/presentation/HomeScreen.kt` | 464 | Screen, top bar, bottom bar, chip strip, empty/error/skeleton/spinner, pull-to-refresh wiring, hide-on-scroll chrome. |
| `home/presentation/FeedCards.kt` | 248 | `FeedCard` dispatcher: Video, Mix, Shorts shelf, Playlist, Post cards. |
| `home/presentation/HomeOverlays.kt` | 102 | `SearchOverlay` (wraps `ui/design/SearchPanel`), `VideoMenu`, `MixMenu`. |
| `home/presentation/HomePreviews.kt` | 57 | Compose previews. |

### explore/ (924)
| File | Lines | Purpose |
|---|---|---|
| `explore/ExploreActivity.kt` | 383 | **Contains `ExploreViewModel` (lines 49-302) AND the Activity (304+)**. |
| `explore/domain/ExploreModels.kt` | 73 | `ExploreCategory` (Music/Gaming/News/Sports), `ExplorePage`, `ExploreSection`, `ExploreContent`, `ExploreUiState`, Action, NavigationRequest. |
| `explore/data/ExploreRepository.kt` | 15 | Interface: `explore(category)`, `exploreNext(category,key)`, `exploreShelf(category,key)`. Live impl `MediaExploreRepository` at `R/data/media/MediaLibraryRepositories.kt:263`. |
| `explore/data/FixtureExploreRepository.kt` | 76 | Fixtures. |
| `explore/presentation/ExploreScreen.kt` | 377 | Screen, skeleton, category chips, sectioned carousels, grid, tails. |

### search/ (1869)
| File | Lines | Purpose |
|---|---|---|
| `search/SearchResultsActivity.kt` | 196 | Host + navigation handler (watch/shorts/channel/playlist/share/queue/download/voice/verification). |
| `search/domain/SearchModels.kt` | 244 | `SearchPage/SearchResult/SearchChannel`, `SearchFilters(type, uploaded)`, `SearchUiState`, `SearchAction`, `SearchNavigationRequest`, `SearchRepository`, `SearchResultsLayout` (measured dimension constants). |
| `search/data/FixtureSearchRepository.kt` | 74 | Fixture. Live: `MediaSearchRepository` at `R/data/media/MediaCatalog.kt:168`. |
| `search/presentation/SearchResultsViewModel.kt` | 580 | State machine. |
| `search/presentation/SearchResultsScreen.kt` | 588 | Screen, filter bar, top bar, nav bar, list/stacked rows, skeleton, no-results/failed. |
| `search/presentation/ChannelResultsList.kt` | 78 | Channel results with Subscribe button. |
| `search/presentation/ShortsResultsList.kt` | 68 | Shorts grid. |
| `search/presentation/SearchPreviews.kt` | 41 | Previews. |
Playlist results are rendered by `playlist/presentation/PlaylistSearchList` (outside this package) at `SearchResultsScreen.kt:155-156`.

### subscriptions/ (1509)
| File | Lines | Purpose |
|---|---|---|
| `subscriptions/SubscriptionsActivity.kt` | 269 | Host. |
| `subscriptions/domain/SubscriptionsModels.kt` | 131 | `SubscriptionsPage`, `SubscribedChannel`, `SubscriptionsContent` (Loading/SignedOut/Feed/Failed), `SubscriptionsChannels`, overlay, chips, Action, NavigationRequest, `SubscriptionsLayout`. |
| `subscriptions/data/SubscriptionsRepository.kt` | 37 | Interface. Live: `MediaSubscriptionsRepository` at `R/data/media/MediaLibraryRepositories.kt:135`. |
| `subscriptions/data/FixtureSubscriptionsRepository.kt` | 80 | Fixture + `SubscriptionsFilter` enum (line 56) + `SubscriptionsScenario` + `subscriptionsSignedOutPrompt()`. |
| `subscriptions/presentation/SubscriptionsViewModel.kt` | 499 | State machine. |
| `subscriptions/presentation/SubscriptionsScreen.kt` | 493 | Screen, top bar, chip strip, avatar strip, feed grid, channels overlay, cards. |

### channel/ (3315)
| File | Lines | Purpose |
|---|---|---|
| `channel/ChannelActivity.kt` | 195 | Host. Intent extra `channel_id` (`EXTRA_CHANNEL_ID`). |
| `channel/domain/ChannelModels.kt` | 425 | `ChannelProfile/Page/Video/Short/LiveVideo`, `ChannelRepository`, `ChannelSection<T>`, `ChannelTab`, `ChannelSortOption`, `ChannelUiState`, Action, NavigationRequest. |
| `channel/data/FixtureChannelRepository.kt` | 136 | Fixture. Live: `MediaChannelRepository` at `R/data/media/MediaRepositories.kt:160`. |
| `channel/presentation/ChannelViewModel.kt` | 762 | State machine. |
| `channel/presentation/ChannelScreen.kt` | 859 | Screen, tab bar, header, Home/Videos/Playlists tabs, skeleton, subscribe button. |
| `channel/presentation/ChannelCommunityTabs.kt` | 614 | Shorts / Live / Posts tabs, sort chips, post card, menu sheets. |
| `channel/presentation/ChannelAboutPanel.kt` | 117 | About overlay (bottom sheet / docked side panel on wide). |
| `channel/presentation/ChannelSearchTab.kt` | 146 | In-channel search tab. |
| `channel/presentation/ChannelPagination.kt` | 29 | Pagination helper. |
| `channel/presentation/ChannelDiscoveryPreviews.kt` | 32 | Previews. |

### post/ (1408)
| File | Lines | Purpose |
|---|---|---|
| `post/PostDetailActivity.kt` | 214 | Host; also owns a `CommentsViewModel` for post comments. |
| `post/PostVotes.kt` | 55 | Poll vote helper. |
| `post/domain/PostModels.kt` | 140 | `PostIdentity(channelId,postId)`, `PostSummary`, `PostDetail`, `PostPollData`, `PostRepository`, `PostActions`, UiState, Action, NavigationRequest, `PostLayout`. |
| `post/data/FixturePostRepository.kt`, `FixturePostCommentsRepository.kt` | 100, 87 | Fixtures. Live: `MediaPostRepository` (`R/data/media/MediaPostRepository.kt:32`, implements `PostRepository` + `PostActions`). |
| `post/presentation/PostDetailViewModel.kt` | 220 | Detail state machine. |
| `post/presentation/PostCardActionsViewModel.kt` | 100 | Shared like/dislike/vote VM for feed/channel post cards (`PostCardActionHost` in `PostVotes.kt`). |
| `post/presentation/PostDetailScreen.kt` | 282 | Detail screen. |
| `post/presentation/PostImageCarousel.kt`, `PostImageViewer.kt`, `PostPollChoices.kt` | 96, 69, 45 | Carousel, full-screen viewer, poll. |

## 2. Screens, ViewModels and the contract to preserve

Common conventions (all six VMs): `val uiState: StateFlow<XUiState>`; `fun onAction(action: XAction)`; `val navigationRequests: Flow<XNavigationRequest>` (BUFFERED `Channel`, `receiveAsFlow`); most have `val actionNotices: Flow<AppNotice>` (+ `AppNotice.SignIn`); `SavedStateHandle` persists query/chip/tab/overlay/sort; `updateSession(...)`/`refreshSession()` invoked by the Activity on account change (`observeAccountChanges` in `R/ui/AccountChanges.kt`). "Epoch/generation" counters in every VM drop stale results after an identity switch - keep VM untouched.

### 2.1 Home - `HomeScreen(state: HomeUiState, onAction: (HomeAction)->Unit, modifier, searchRows: SearchRows?, onWarmCandidate: ((String)->Unit)?, postInteractions: PostCardInteractions?)` (`home/presentation/HomeScreen.kt:~13`)
- VM: `HomeViewModel(repository: HomeRepository, savedState, scenario, loadOnStart, saveRepository: SaveRepository?, avatars: MediaAvatarDirectory?, snapshots: ContentSnapshots?, accountKey: ()->String, reportSignal: (()->Long)?, warm: (String)->Unit, now, seedDispatcher)` (`HomeViewModel.kt:40`).
- Public API: `uiState`, `navigationRequests`, `actionNotices`, `val writes: WriteFeedback` (rendered by `WriteProgressOverlay`), `setHideShorts(Boolean)`, `setSignedInPreview(Boolean)`, `updateSession(signedIn, key, avatar)`, `onForeground()`, `verificationCompleted(challengeUrl?)`, `onAction`.
- `HomeUiState` (`HomeModels.kt`): `content: HomeContent` (Loading | Feed(page, refreshing, append: AppendState, refreshFailed, userRefresh, emptyAutoLoads) | Empty(title,message,refreshing,userRefresh) | Failed), `query`, `selectedChip`, `overlay: HomeOverlay` (None | Search | Menu(itemId,kind,loading,failed) | Save(videoId,newPlaylist,title,description,live,choices,choicesFailed,pending) | Feedback(itemId,kind)), `signedInPreview`, `hideShorts`, `chips`, `avatar`, `live`, `sessionGeneration`, `verification`. Derived `suggestions` (fixture-only fake suggestions).
- `HomeAction`: OpenSearch, CloseOverlay, QueryChanged, SubmitSearch, SelectChip, OpenMenu(itemId, kind), MenuSelected, SaveDestinationSelected, SaveChoiceToggled, SaveChoicesRetry, OpenNewPlaylist, SaveDraftChanged, SubmitNewPlaylist, FeedbackReason, Navigate(HomeNavigationRequest), Refresh, LoadNext, AutomaticLoadNext, DismissVerification.
- `HomeNavigationRequest` (exits the Activity handles, `HomeActivity.kt:~180-238`): Search, Watch, Shorts, Channel, Playlist, Destination(HomeDestination), MenuAction, SaveToWatchLater/SaveToPlaylist/CreatePlaylist/Feedback/PostComments (no-op in Activity: handled inside VM), PostFeedback, OpenPost, OpenLink, OpenVerification, VoiceSearch, Explore. Comment in code: "New request types must be handled here."
- Domain calls: `HomeRepository.loadHome(filter, continuation)`, `.menu(video)`, `.feedback(video, action)`, `.filters`; `SaveRepository` (`choices`, `setContaining`, `create`) for the Save-to-playlist sheet and Watch later; `MediaAvatarDirectory` (avatar back-fill); `ContentSnapshots.read/write("home", account, chip, ...)` for cold-start paint; `PlaybackWatchReporting.anonymousReportSignal` as refresh trigger.
- Sheets/dialogs inside HomeScreen: search overlay (`ui/design/SearchPanel`), video action sheet (`VideoMenu`), Mix menu, Save-to-playlist sheet + New-playlist sheet (`SaveToPlaylistSheet`, `NewPlaylistSheet`, from `ui/design`/playlist/saves packages), Feedback reason sheet (`FeedbackSheet`), traffic-verification card + banner.

### 2.2 Explore - `ExploreScreen(state: ExploreUiState, onAction: (ExploreAction)->Unit, onBack: ()->Unit, modifier)` (`explore/presentation/ExploreScreen.kt:66`)
- VM: `ExploreViewModel(repository: ExploreRepository, scenario: ExploreScenario, savedState, avatars)` (`explore/ExploreActivity.kt:49`). Public: `uiState`, `navigationRequests`, `updateSession(generation)`, `onAction`.
- `ExploreUiState(category, content: ExploreContent (Loading | Ready(page, append, emptyAutoLoads) | Failed), verification)`.
- `ExploreAction`: Retry, DismissVerification, OpenVerification, SelectCategory, OpenVideo, OpenChannel, OpenPlaylist, LoadMore, AutomaticLoadMore, LoadShelf(index), AutomaticLoadShelf(index). Nav: Watch, Channel, Playlist, OpenVerification.
- Domain: `ExploreRepository.explore/exploreNext/exploreShelf`. VM caches pages per category in an `EnumMap` (tab switches keep content) and tracks consumed continuations.
- Reached from Home chip strip's leading "explore" button and Home empty state (`HomeNavigationRequest.Explore`).

### 2.3 Search results - `SearchResultsScreen(...)` (`search/presentation/SearchResultsScreen.kt:66`)
- VM: `SearchResultsViewModel(repository: SearchRepository, savedState, query, scenario, signedIn: Boolean?, videoActions: VideoActions?, saveRepository: SaveRepository?, avatars)` (`SearchResultsViewModel.kt:26`). Public: `uiState`, `navigationRequests`, `actionNotices`, `writes`, `saveSheet` (a save-sheet controller with its own `state`/`writes`/`dismiss()`), `updateSession(signedIn,key)`, `verificationCompleted`, `updateQuery(query)`, `onAction`.
- `SearchUiState`: `query`, `content` (Loading | Results(page, loadingMore, loadMoreFailed, emptyAutoLoads) | Empty(query) | Failed), `signedInPreview`, `overlay` (None | Search | ItemMenu | PlaylistMenu), `draftQuery`, `filters: SearchFilters(type, uploaded)`, `filtersOpen`, `live`, `menuLoading/menuFailed`, `subscriptions: Map<channelId,Boolean>`, `subscriptionsPending`, `verification`.
- `SearchAction`: VisibleResults(query, filters, ids) (lazy metadata fill), OpenSearch/CloseSearch/QueryChanged/SubmitSearch, OpenResult, OpenPlaylist, OpenPlaylistMenu, SharePlaylist, Subscribe(channelId), OpenChannel, SelectDestination, OpenItemMenu, MenuAction, CloseItemMenu, OpenFilters/CloseFilters, SelectResultType, SelectUploadDate, VoiceSearch, OpenPageMenu, DismissVerification, OpenVerification, LoadMore, AutomaticLoadMore, Retry.
- Domain calls: `SearchRepository.search(query, filters)`, `.next(query, filters, cursor)`, `.metadata(result)`, `.menu(result)`, `.feedback(result, action)`; `VideoActions.subscribe(channelId, target)` (`watch/domain`); `SaveRepository` through `saveSheet`.
- Search input panel (suggestions/history/voice) is NOT in this package: shared `ui/design/SearchPanel.kt` + `ui/components/SearchRows.kt` fed by `MediaSearchSuggestions`.

### 2.4 Subscriptions - `SubscriptionsScreen(state: SubscriptionsUiState, onAction: (SubscriptionsAction)->Unit, modifier)` (`SubscriptionsScreen.kt:78`)
- VM: `SubscriptionsViewModel(repository, savedState, scenario, signedIn: Boolean (var), saveRepository: SaveRepository?, clock)` (`SubscriptionsViewModel.kt:29`). Public: `uiState`, `onForeground()`, `updateSession(value, key)`, `verificationCompleted`, `onAction`, plus `navigationRequests`.
- `SubscriptionsUiState`: `signedIn`, `content` (Loading | **SignedOut(prompt: SignInPrompt)** | Feed(page, append, emptyAutoLoads) | Failed), `channels` (Loading | Ready(channels, nextKey, append) | Failed), `overlay` (None | Search | Channels | Menu), `query`, `selectedChip: SubscriptionsChip` (All, Today, ContinueWatching, Unwatched, Live, Posts), `selectedChannel`, `chips` (= repository.supportedFilters), `live`, `menuLoading/menuFailed`, `verification`.
- `SubscriptionsAction`: Retry, LoadNext, OpenSearch, CloseOverlay, QueryChanged, SubmitSearch, SelectChip, ShowChannels, CloseChannels, RetryChannels, LoadMoreChannels, OpenVideo, OpenChannel, SelectChannel(id?), OpenMenu, MenuSelected, Destination(MainTab), SignIn, Manage, VoiceSearch, DismissVerification, OpenVerification. Nav: Watch, Channel, Search, MenuAction, Destination, OpenVerification, Manage, SignIn, VoiceSearch.
- Domain calls: `SubscriptionsRepository.subscriptions(filter, continuation)`, `.channelSubscriptions(channel, filter, continuation)`, `.subscribedChannelsPage(cursor)` (and `subscribedChannels()`), `.menu(video)`, `.feedback(video, action)`, `.supportedFilters`.

### 2.5 Channel - `ChannelScreen(...)` (`channel/presentation/ChannelScreen.kt:99`)
- VM: `ChannelViewModel(repository: ChannelRepository, savedState, channelId, scenario, videoActions: VideoActions?)` (`ChannelViewModel.kt:24`). Public: `uiState`, `navigationRequests`, `actionNotices`, `updateSignedIn(Boolean)`, `refreshSession()`, `verificationCompleted`, `onAction`.
- `ChannelUiState`: `channelId`, `content` (Loading | Ready(page: ChannelPage) | Failed), `selectedTab: ChannelTab` (Home, Videos, Shorts, Live, Playlists, Posts, Search), `overlay` (None | About | PlaylistMenu | ShortMenu | PostMenu), `searchQuery/submittedQuery`, `playlistSort`, per-tab `ChannelSection<T>` (Loading|Empty|Failed|Ready) for shorts/live/posts/playlists/videosTab/searchResults, per-tab `*NextKey` and `*Append: AppendState`, `shortsSort/liveSort: ChannelSortOption`, `expandedPosts`, `subscribePending`, `production`, `verification`, `signedIn`.
- `ChannelAction` (long list in `ChannelModels.kt:~330-380`): SelectTab, Subscribe, Share, OpenVideo/OpenPlaylist/OpenPlaylistMenu/SharePlaylist, SortPlaylists/SortShorts/SortLive, QueryChanged/SubmitSearch/RetrySearch/LoadSearchNext, Load{Videos,Shorts,Live,Posts,Playlists}Next, RetrySection(tab), OpenShort/OpenShortMenu/SelectShortMenu, OpenLive, OpenPost/TogglePostExpanded/OpenPostMenu/SelectPostMenu/PostFeedback/PostComments, OpenFullDescription (About), OpenLink, OpenSectionMenu, ChannelSearch, Retry, CloseOverlay, verification actions, SelectDestination.
- Domain calls: `ChannelRepository.channel/videos/shorts/shortsNext/live/liveNext/posts/postsNext/playlists/playlistsNext/searchVideos/searchNext`, `.serverSearch`; `VideoActions.subscribe(channelId, target)`.

### 2.6 Post detail - `PostDetailScreen(state, onAction, ..., commentsState: CommentsUiState, onCommentsAction: (CommentAction)->Unit)` (`PostDetailScreen.kt:49`)
- VM: `PostDetailViewModel(repository: PostRepository, savedState, identity: PostIdentity, scenario, live)` (`PostDetailViewModel.kt:33`); casts the repository to `PostActions` for write ops. Second VM in the Activity: `comments/presentation/CommentsViewModel` (`PostDetailActivity.kt:105-115`), repo `sources.comments()` (`MediaCommentsRepository`).
- `PostDetailUiState`: `identity`, `content` (Loading | Ready(post) | Failed), `selectedImage`, `overlay` (None | Menu | Image(index)), `commentsOpen`, `live`, `signedIn`, `actionPending`, `pendingAction`, `refreshing`.
- `PostDetailAction`: Retry, SelectImage, OpenImage, Like, Dislike, Vote(option, selected), Comments, CloseComments, OpenMenu, CloseOverlay, MenuSelected(PostMenuAction.Report), OpenChannel, SelectDestination. Nav: Feedback, Comments, MenuAction, Channel, Destination.
- Auth: write failures with `PostAuthorizationRequiredException` trigger `authorizations` -> Activity launches `account/ChatWebSignInActivity` (`PostDetailActivity.kt:73-76`); `SignInRequiredException` -> `AppNotice.SignIn`.
- Shared feed/channel post cards use `PostCardActionsViewModel.onAction(identity, PostDetailAction)` via `PostCardActionHost` (`PostVotes.kt`), exposing `PostCardInteractions`.

## 3. Data flow

- **Loading**: Repository (`suspend`) -> VM `load()` (cancels previous job, bumps `generation`, publishes Loading/refreshing) -> `_state.update` -> `collectAsStateWithLifecycle()` in Activity `setAppContent` -> composable.
- **Errors**: VM catches `MediaException`; `error.failure.isTrafficChallenge()` (YouTube `/sorry`) -> `verification: VerificationRequired` shown as `VerificationCard` (if no content) or `VerificationBanner` (if content loaded), Activity launches `core/TrafficVerificationActivity` and calls `verificationCompleted(...)` for one retry. Other errors -> `Failed` (full-screen retry) or, if content exists, `refreshFailed`/`AppendState.Failed` (inline RetryRow).
- **Paging**: opaque `nextKey` strings. Home/Explore/Search/Subscriptions all use the same pattern: composable derives `nearEnd` from `LazyGridState/LazyListState.layoutInfo` (last visible index >= total-3, `HomeScreen.kt` `nearEnd` block) and fires `AutomaticLoadNext`; VM caps empty auto-pages via `emptyAutoLoads >= 3` -> `automaticPagingPaused` -> composable shows a "Load more" pill (`testTag home-load-more`). Consumed continuations are tracked to avoid loops (`consumedContinuations`). Explore pages per shelf too (`LoadShelf(index)`).
- **Pull-to-refresh**: custom (not Material3) `ui/design/PullRefresh.kt` (`rememberPullRefreshState`, `pullRefreshGesture`, `PullRefreshIndicator`, `RefreshableContent`). Used in Home (`HomeScreen.kt` pullMotion), Explore (`RefreshableContent`, tag `explore-refresh`), Search (`SearchResultsScreen.kt:142`), Channel (`ChannelScreen.kt:173`), Subscriptions (pull gesture, `SubscriptionsScreen.kt`).
- **Skeletons**: hand-built per screen with `DesignTokens.Ghost` boxes (`HomeSkeleton`, `ExploreSkeleton`, `SearchSkeleton`, `ChannelSkeleton`), tagged `*-skeleton`, hidden from a11y with `clearAndSetSemantics {}`.
- **Cold-start snapshot**: Home only. `ContentSnapshots` paints last Video rows (`FeedItem.Video` is `@Serializable`) while live load runs (`HomeViewModel.seedSnapshot`, line 95). Rows only, not Shorts/Post/Mix.
- **Quiet refresh**: `HomeViewModel.onForeground()` refreshes if feed >15 min old (`QUIET_REFRESH_AFTER_MS`) or if a watch-report signal arrived; Subscriptions has `onForeground()` too.
- **Avatar back-fill**: Home/Explore/Search/Subscriptions call `MediaAvatarDirectory` to fill missing channel avatars after first paint (`fillMissingAvatars`).
- **Scroll preservation**: Home `rememberLazyGridState()` (saveable) + Activity instance reuse via REORDER_TO_FRONT; Explore stores per-category `LazyListState/LazyGridState/shelf states` with custom Savers (`ExploreScreen.kt:72-131`); Channel `rememberSaveable(saver = LazyListState.Saver)` per tab (`ChannelScreen.kt:115-122`); Subscriptions has dedicated scroll tests. Search: `rememberLazyListState()` (not explicitly saveable beyond Activity lifetime; search VM persists query/filters/overlay in SavedStateHandle).

## 4. Feature matrix

Legend: WORKS = data + UI; DATA_ONLY = data supports, UI partial; MISSING = not in data layer.

### 4.1 Home & Explore
| Feature | Status | Data evidence | UI evidence / notes |
|---|---|---|---|
| Home feed (videos, Shorts shelf, mixes, playlists, community posts) | WORKS | `MediaHomeRepository.loadHome` `R/data/media/MediaCatalog.kt:68-100` -> `HomePage`; `FeedItem` sealed `HomeModels.kt:30-52` | `FeedCards.kt:40` dispatcher, `HomeScreen.kt` `homeCards` (Shorts/Post span full row) |
| Filter chips | WORKS (live set differs from fixture) | Live `filters = All, Music, Gaming, Live, News, Sports` (`MediaCatalog.kt:67`); signed-out non-All chips route to a "catalog" mode `HomeCatalog` (Popular, MusicCharts, Gaming, Sports, News; `HomeModels.kt:13`) | `ChipStrip` `HomeScreen.kt:~338`, tags `home-chips`; chips are plain `String`s (label localized only in catalog mode) |
| Pull-to-refresh | WORKS | `HomeAction.Refresh` -> `load(keepEmpty, userInitiated)` | `HomeScreen.kt` `pullMotion`/`PullRefreshIndicator("home-refresh-indicator")` |
| Infinite scroll | WORKS | `nextKey`, `consumedContinuations` | `nearEnd` -> `AutomaticLoadNext`; fallback "Load more" pill after 3 empty pages |
| Loading skeleton | WORKS | `HomeContent.Loading` | `HomeSkeleton` `HomeScreen.kt:~429` (`home-skeleton`, `skeleton-card-N`) |
| Empty state | WORKS | `HomeContent.Empty(title, message)`; server message `combined.messages.firstOrNull()` | `EmptyHome` `home-empty`, `empty-message` |
| Error state + retry | WORKS | `HomeContent.Failed`; `refreshFailed`; `AppendState.Failed` | `ErrorHome` `home-error`; `RetryRow` (refresh failed, append failed) |
| Scroll position kept on back | WORKS (by Activity retention) | n/a | `rememberLazyGridState` + Activity back stack; resets to top on chip/session change (`HomeScreen.kt` LaunchedEffect on selectedChip/sessionGeneration) |
| Card overflow menu (queue/download/watch later/save to playlist/share/not interested/don't recommend) | WORKS | `HomeRepository.menu/feedback`, `SaveRepository`; token-gated (`productionActions`, `HomeModels.kt:~146`) | `VideoMenu` (`HomeOverlays.kt:42`), Save sheet, Feedback sheet |
| Hide Shorts preference | WORKS | `PreferenceKeys.ENABLE_HIDE_SHORTS` via `setHideShorts` | `visibleItems` filter + `LocalHideShorts` hides nav tab |
| Bottom nav (Home/Shorts/Subscriptions/You) | WORKS | `MainTabs` | `HomeBottomBar`, tags `home-navigation`, `nav-<Name>` (Subscriptions only when signed in; You shows account avatar) |
| Explore: categories Music/Gaming/News/Sports | WORKS | `MediaExploreRepository` `MediaLibraryRepositories.kt:263`; `ExploreCategory` is a fixed 4-value enum (`ExploreModels.kt:9`) | `CategoryChips` tags `explore-chip-<Name>` |
| Explore: shelves/carousels with per-shelf paging | WORKS | `ExploreSection.nextKey`, `exploreShelf` | `SectionedContainer`, tags `explore-carousel-N`, `explore-shelf-N-*` |
| Explore: pull-to-refresh, skeleton, error+retry, per-category scroll kept | WORKS | `ExploreAction.Retry`, VM `pages` EnumMap | `RefreshableContent`, `ExploreSkeleton`, `LibraryError("explore-error")`, per-category Savers |
| Explore: playlists in category | WORKS | `ExplorePage.playlists` | `ExplorePlaylistCard` |
| Trending / "New to you" / "Recently uploaded" / "Mixes" / "Podcasts" chips | MISSING in live (fixture-only chips `HomeLayout.chips`, `HomeModels.kt:~232`) | - | - |

### 4.2 Search
| Feature | Status | Data evidence | UI evidence / notes |
|---|---|---|---|
| Suggestions while typing | WORKS | `MediaSearchSuggestions.rows(query)` (history matches first, max 3, then remote; `data/media/MediaSearchSuggestions.kt:~20-70`); remote `loadSearchSuggestions`, 'best-effort' | `SearchPanel` `ui/design/SearchPanel.kt:46`, tags `search-input`, `suggestion-N`; `rememberSearchRows` (`ui/components/SearchRows.kt:23`) |
| Search history + per-entry removal | WORKS | `PreferenceSearchHistoryStore` key `search_history:<account>`; `record()` skipped in private browsing; `remove(entry)` | `history-N` rows, remove button with a11y label `a11y_remove_from_search_history` |
| Voice search | WORKS | Platform `RecognizerIntent` + in-process `SpeechRecognizer` fallback + `RECORD_AUDIO` request (`R/ui/NativeActivity.kt:209-260`) | Mic button in `SearchPanel.kt:121` -> `SearchAction.VoiceSearch` -> Activity `openVoiceSearch()` |
| Filter: result type (All/Shorts/Channels/Playlists) | WORKS | `SearchResultType`, mapped to server filter chips in `MediaSearchRepository.search` (`MediaCatalog.kt:~180-195`) | `SearchFilterBar` with `SelectField` (`SearchResultsScreen.kt:242`, tags `search-filter-bar`, `search-filter-groups`) |
| Filter: upload date (Anytime/Today/Week/Month/Year) | WORKS | `SearchUploadDate` | same bar |
| Filter: duration | MISSING | no enum/param (`SearchFilters` has only `type`, `uploaded`; `SearchModels.kt:~57`) | - |
| Filter: sort by / features (HD, live, CC) / "Video" vs "Movie" type | MISSING | not in `SearchResultType`/`SearchFilters` | - |
| Results: videos | WORKS | `SearchPage.results` | `ListResultRow` (landscape/wide) / `StackedResultRow` (phone) |
| Results: channels (+ subscribe) | WORKS | `SearchPage.channels`, `VideoActions.subscribe` | `ChannelResultsList` + `ChannelSubscribe` (tag `search-channel-subscribe-<id>`) |
| Results: playlists | WORKS | `SearchPage.playlists` | `PlaylistSearchList` (playlist package) when type=Playlists |
| Results: Shorts | WORKS | `SearchPage.shorts` | `ShortsResultsList` grid -> `SearchNavigationRequest.Shorts` -> `ShortsActivity` |
| Mixed results (videos + channels + playlists inline in "All") | DATA_ONLY / partial | `SearchPage` carries all four lists | UI renders channels/playlists/shorts only when the matching type filter is selected (`SearchResultsScreen.kt:155-162`); "All" shows videos only. |
| Infinite scroll, skeleton, no-results, error+retry, pull-to-refresh | WORKS | `next(query, filters, cursor)`, `SearchContent.*` | `search-load-more`, `SearchSkeleton`, `SearchNoResults`, `SearchFailed`, `RefreshableContent` |
| Result overflow menu (queue, download, watch later, save, share, not interested, don't recommend) | WORKS | `SearchRepository.menu/feedback`; `SaveRepository` | `ItemMenu` overlay `search-menu-*` |
| Lazy metadata completion for sparse cards | WORKS | `SearchRepository.metadata` (player-details read) | `SearchAction.VisibleResults` emitted from the list |
| DeArrow titles | WORKS | `dearrow` pkg | `rememberDeArrowTitle(result.id, title)` `SearchResultsScreen.kt:453` |
| Search from Subscriptions/Library/History | WORKS (own overlays) | same `MediaSearchSuggestions` | `SubscriptionsOverlay.Search`, `LibraryNavigationRequest.VoiceSearch` |

### 4.3 Subscriptions
| Feature | Status | Data evidence | UI evidence / notes |
|---|---|---|---|
| Feed of subscribed channels' videos | WORKS (signed-in only) | `MediaSubscriptionsRepository.subscriptions` -> `loadBrowse(LibrarySection.SUBSCRIPTIONS.browseId)` `MediaLibraryRepositories.kt:135-165`; throws AUTH `sign_in_required` when `!context.signedIn` | `SubscriptionsFeed` grid `subscriptions-feed`, `SubscriptionsVideoCard` `subs-item-<id>` |
| Chips All / Unwatched / Continue watching / Live | WORKS (client-side filter of one page) | `supportedFilters = entries - Today - Posts` (`:141`); filtering by `progress`/`live` on loaded page (`:172-177`) | `SubscriptionsChipStrip` (tag `subscriptions-chips`) shows only `chips = supportedFilters` |
| Chips Today, Posts | MISSING (hidden in production) | `UNSUPPORTED` thrown; comment at `MediaLibraryRepositories.kt:139-140` | enum exists in domain + fixtures |
| Channel avatar strip + "all channels" directory | WORKS | `subscribedChannelsPage(cursor)` pages through the guide/channels browse; `hasNewContent` | `ChannelAvatarStrip` (`subscriptions-channels`, `subs-strip-<id>`, `subscriptions-manage`), `ChannelsOverlay` (`channels-overlay`, `channels-list`, `channels-row-<id>`), new-content dot |
| Per-channel feed (tap avatar) | WORKS | `channelSubscriptions(channel, filter, cont)` with `subscriptionParams` | `SelectChannel(id)`, back-to-all (`subscriptions-back-to-all`), "visit channel" (`subscriptions-visit-channel`) |
| Subscribe / unsubscribe | WORKS elsewhere (not on this screen) | `VideoActions.subscribe(channelId, target)` (`watch/domain`), used by Channel, Search channel results, Watch | Only Channel (`ChannelSubscribe`), Search (`ChannelSubscribe`) and Watch screens expose it. Subscriptions screen has `Manage` -> external (`SubscriptionsNavigationRequest.Manage`). |
| Notification bell preference per channel | MISSING | grep `Bell`/`bell`/notification-preference across `R/**/*.kt` finds only the unused icon path `AppIcon.Bell` (`R/ui/design/AppIcons.kt:67`) and no repository method | - |
| Works anonymous | **Signed-out only shows sign-in prompt** | No local subscription store: `LocalLibraryStore` has no subscriptions (grep "subscri" -> none). Anonymous users cannot subscribe: `VideoActions.subscribe` throws `SignInRequiredException` -> `AppNotice.SignIn` | `SubscriptionsContent.SignedOut(prompt)` from `SignedOutPrompts.forContent("subscriptions")`; bottom tab hidden when signed out (`MainTabs.tabs`, `MainNavigation.kt:31`) |
| Pull-to-refresh, skeleton, error+retry, paging | WORKS | `SubscriptionsContent.*`, `nextKey`, `emptyAutoLoads` | `SubscriptionsError` (`subscriptions-error`), `subscriptions-load-more`, `RetryAppend` |
| Menu (watch later/save/queue/download/not interested) | WORKS | `menu/feedback` + `SaveRepository` | `SubscriptionsOverlay.Menu` |

### 4.4 Channel
| Feature | Status | Data evidence | UI evidence / notes |
|---|---|---|---|
| Header (banner, avatar, name, handle, subscribers, video count, description, links) | WORKS | `ChannelProfile` from `MediaChannelRepository.channel` (`MediaRepositories.kt:164-190`). Links list: `ChannelProfile.links` present; live population not verified | `ChannelHeader` tags `channel-header/-banner/-avatar/-name/-handle/-description/-links` |
| Tab: Home (shelves) | WORKS | `ChannelPage.homeShelves` | `ChannelHomeTab` `channel-home-list` |
| Tab: Videos (+ infinite scroll) | WORKS | `videos(channelId, continuation/opened)` (deferred if not landing tab) | `ChannelVideosTab` `channel-videos-list`, `channel-videos-append` |
| Videos sort (Latest/Popular/Oldest) | MISSING for Videos tab | `ChannelSortOption` used only for Shorts/Live (client-side sort of loaded rows via `order`/`popularity`, `ChannelCommunityTabs.kt:66-72,175-181`) | Sort chips only in Shorts/Live (`ChannelSortChips`, tags `channel-shorts-sort-*`) |
| Tab: Shorts (+ sort, paging) | WORKS | `shorts/shortsNext` | `ChannelShortsTab`; opens `ChannelNavigationRequest.Short` -> Shorts player. Hidden by `LocalHideShorts` |
| Tab: Live (current + past, sort, paging) | WORKS | `live/liveNext`, `ChannelLivePage(current, past)` | `ChannelLiveTab`; `liveNow` stream open is a typed `LiveStream` request (not played by VOD player, per code comment `ChannelModels.kt:~250`) |
| Tab: Playlists (+ sort, paging) | WORKS | `playlists/playlistsNext`, `ChannelPlaylistSort` (Date added / Last video added) applied client-side | `ChannelPlaylistsTab` `channel-playlists-list` |
| Tab: Posts (community) | WORKS | `posts/postsNext` -> `MediaPostRepository.channelPosts` | `ChannelPostsTab`, `PostCard` (tags `channel-post-*`) |
| Tab: Search-in-channel | WORKS | `serverSearch=true`; `searchVideos/searchNext` (`MediaRepositories.kt:210-222`) | `ChannelSearchTab` (`channel-search-input`, `channel-search-list`) |
| Tab: Releases / Courses / Store / Podcasts | MISSING | `ChannelTab` enum has exactly 7 values (`ChannelModels.kt:~172`) | - |
| About (description, links, country/joined/views, full description) | WORKS (partial fields) | `ChannelProfile.fullDescription/country/joined/views/links`; live repo fills description/videoCount/canonicalUrl (`MediaRepositories.kt:171-173`); `country/joined/views/links` not set in `channel()` mapping | `ChannelAboutPanel` (docked panel >=931dp wide, bottom sheet otherwise), tags `channel-about-*` |
| Subscribe / unsubscribe button | WORKS (signed-in) | `VideoActions.subscribe` (server-confirmed; pending state; `UnconfirmedActionException`) | `ChannelSubscribe` `channel-subscribe`, unsubscribe confirmation via `ui/design/SubscriptionConfirmation.kt` (`rememberSubscriptionAction`) |
| Subscription bell / notification level | MISSING | see 4.3 | - |
| Share channel, share playlist | WORKS | `canonicalUrl`; `ChannelNavigationRequest.Share/SharePlaylist` | top bar actions |
| Channel pull-to-refresh, skeleton, error+retry, per-tab retry | WORKS | `ChannelAction.Retry/RetrySection` | `RefreshableContent`, `ChannelSkeleton`, `ChannelFailed`, `ChannelTabStatus` |
| Per-tab scroll position retained | WORKS | - | `rememberSaveable(saver = LazyListState.Saver)` for 7 tabs |

### 4.5 Community posts
| Feature | Status | Data evidence | UI evidence / notes |
|---|---|---|---|
| Post list (in channel Posts tab and Home feed) | WORKS | `ChannelPostsPage`, `FeedItem.Post` | `PostCard` (channel), `FeedCards.kt:188` PostCard (home) |
| Post detail | WORKS | `PostRepository.post(identity)` -> `MediaPostRepository.post` (`:89`) | `PostDetailScreen` `post-detail-page`, tags `post-detail-*` |
| Images (carousel + full-screen viewer) | WORKS | `PostDetail.images` | `PostImageCarousel`, `PostImageViewer` (`PostDetailOverlay.Image`) |
| Polls (display + vote) | WORKS | `PostPollData`, `PostActions.vote` (`MediaPostRepository.kt:120`) | `PostPollChoices` (45 lines), VM `Vote(option, selected)` |
| Like / dislike | WORKS (signed-in; may need web re-auth) | `PostActions.rate(identity, rating, previous)` (`:97`), `PostAuthorizationRequiredException` | `post-detail-like/-dislike`, `semantics{selected}`; card-level via `PostCardActionsViewModel` |
| Post comments (list, reply, like, sort...) | WORKS (delegated to `comments` package) | `MediaCommentsRepository` via `sources.comments()` with `postWebContext` | `CommentsPanel` embedded in `PostDetailScreen.kt:101`; `commentsOpen` persisted in SavedStateHandle; `EXTRA_OPEN_COMMENTS` deep-open from channel/feed |
| Post menu (Report) | DATA_ONLY / stub | `PostMenuAction.Report` only; `PostDetailUiState.live` hides it anonymously ("anonymous post pages carry no menu renderer") | Menu sheet exists; in channel `PostMenuAction` results in `ChannelNavigationRequest.PostMenu` (no-op beyond navigation request) |
| Share post, open author channel | WORKS (channel); share not found | `OpenChannel` action | `PostDetailAction.OpenChannel`; searched `Share` in `post/` -> not found |
| Extra: post expand/collapse (read more) in channel list | WORKS | `ChannelUiState.expandedPosts` (saved in SavedStateHandle) | `channel-post-read-more-*` / `show-less-*` |
| Extra: Home feed "Post" cards with images, like/dislike | WORKS | `FeedItem.Post` | `FeedCards.kt:188-248` |

## 5. UI coupling and redesign notes

### 5.1 Pure presentation (safe to rewrite)
- All `*Screen.kt`, `FeedCards.kt`, `HomeOverlays.kt`, `ChannelCommunityTabs.kt`, `ChannelAboutPanel.kt`, `ChannelSearchTab.kt`, `ChannelResultsList.kt`, `ShortsResultsList.kt`, `PostImageCarousel.kt`, `PostImageViewer.kt`, `PostPollChoices.kt`, `*Previews.kt`. They take `(state, onAction)` and have no domain calls. Keep the `onAction` / UiState signatures and everything is rewritable.
- Layout "math" objects in domain files are presentation constants and may be replaced: `HomeLayout`, `SubscriptionsLayout`, `SearchResultsLayout` (`SearchModels.kt:~160-244`, pixel-measured against youtube.com mobile web), `PostLayout`, `ChannelLayout`. Note they live in `domain/` files, and `HomeLayout.chips` (fixture chip list) and `HomeLayout.menuWidth` are referenced from VM/state defaults (`HomeUiState.chips = HomeLayout.chips`), so keep those declarations compiling.

### 5.2 Logic that lives in Activities (must be preserved/ported)
- **Navigation, side effects and platform intents are in the Activities, not the VMs**: `launchNavigation(Intent)`, `ShareGateway.share/sharePlaylist`, `ActivityMediaActionExecutor.execute(MediaActionItem, Queue|Download)` (`HomeActivity.kt:~191-200`, `SearchResultsActivity.kt:~100-114`), voice search (`openVoiceSearch()` in `NativeActivity`), `TrafficVerificationActivity` launcher, `ChatWebSignInActivity` launcher (post), `renderNotice(AppNotice)` (snackbar/sign-in), `observeAccountChanges`. A redesign that swaps Activities for a NavHost must reimplement every `NavigationRequest` mapping listed in 2.x.
- `HomeActivity`: shorts-feed preloading (`sources.shortsEntry.prepare()`, `ShortsMediaPreloader.preload`, `Extractor.warm`) tied to `uiState` readiness and data-saver pref (`HomeActivity.kt:~85-110`); deferred VM creation (`EXTRA_DEFER_HOME`) for synthetic parent after playback; `receiveLink` for external VIEW/SEND intents; `BackHandler` closes overlay else `moveTaskToBack`.
- Identity/session guards in composables: `HomeScreen` compares `displayedSession != state.sessionGeneration` to reset scroll; `VideoMenu` only renders feedback rows if `video.feedbackGeneration == state.sessionGeneration` (`HomeScreen.kt` Menu branch). Keep this check (tokens are session-scoped).
- `HomeScreen` warm-candidate effect (`onWarmCandidate`, 600 ms delay on the first >=50%-visible card) is a performance feature wired to `Extractor.warm`; keep the hook.
- Hide-on-scroll chrome (`chromeHidden`, `NestedScrollConnection`) and the custom pull indicator are coupled: the feed top inset constants (`feedTopInset = 98/110dp + chipExtraHeight`, `chromeTravel`) and `RefreshAnchor` assume specific top-bar (`DesignTokens.TopBarHeight`) and chip (48dp) heights. Any new header height requires re-deriving these.
- `ChannelScreen` mutates state through side-effects in composition: `if (hideShorts && selectedTab == Shorts) onAction(SelectTab(Home))` (`ChannelScreen.kt:110`).
- `ExploreScreen` Savers (`columnStatesSaver`, `gridStatesSaver`, `shelfStatesSaver`) persist scroll as `listOf(index, offset)`.
- `Subscriptions`: `readChannels` set saved in SavedStateHandle ("new content" dots cleared when a channel is opened).

### 5.3 Design-system components consumed (from `ui/design`, `ui/components`, `ui/theme`)
`AppTheme`, `DesignTokens` (Background, Surface, Ink, Secondary, Ghost, Link, NavigationDivider, GridGap, TopBarHeight, BottomBarHeight), `AppText(text, size=, line=, weight=, color=)` (px-style numbers, e.g. `size = 14, line = 20`), `AppIcon`/`AppIconButton(AppIcon.X, desc, diameter=)` (path-string icon set in `AppIcons.kt`), `AppMotion.scrollResponse`/`LocalMotionScale`/`motionDuration`, `Artwork` (Coil wrapper; `home/presentation/FeedCards.kt` also has a private `Artwork`), `PullRefresh.kt` (`rememberPullRefreshState`, `pullRefreshGesture`, `PullRefreshIndicator`, `RefreshableContent`), `SearchPanel`, `SelectField` (filter dropdown), `SubscribeButton`/`SubscriptionConfirmation` (`rememberSubscriptionAction`), `VerificationCard/Banner`, `MenuBottomSheet`, `BottomSheetDrag`, `CollapsingAppBar` (`appBar.connection`, used by Explore/Search/Channel), `WriteProgressOverlay`, `BrowseNavigationBar`, `ui/components`: `MainTabs`/`MainTab`, `PagingFooter`, `ConstrainedPage`, `SheetGeometry`, `LibraryError`, `LoadingSpinner`/`PillButton` (Home-internal, `internal`), `LitubeBrand`/`LitubeMark`, `LocalHideShorts`.
Branding strings: `HomeTopBar` renders `LitubeBrand`; empty state renders `LitubeMark`; content description `R.string.a11y_litube_home` - must be replaced for MinTube.

### 5.4 Hardcoded colors / dimens / strings
- Hardcoded `Color(0x...)` literals (15 total in these packages): `home/presentation/FeedCards.kt:221` (0F0F0F), `HomeScreen.kt:382` (909090 placeholder), `:460-461` (spinner EEEEEE/666666), `search/presentation/ChannelResultsList.kt:50-51`, `SearchResultsScreen.kt:435` (909090 progress bar), `channel/presentation/ChannelAboutPanel.kt:88` (link 065FD4), `ChannelScreen.kt:94,96` (SubscribeForeground F1F1F1, ChannelLink 065FD4), `ChannelSearchTab.kt:78,139-140`, `post/presentation/PostImageCarousel.kt:82`. Also ~37 uses of `Color.White/Black/Gray` across the packages (home 11, channel 13, search 4, post 5, explore 2, subs 2). Several composables branch on `DesignTokens.Background == Color.White` to pick colors (`ChannelScreen.kt:~778`), i.e. dark-theme handling is ad hoc.
- Pixel-sized typography/dimens everywhere (`AppText(size = 14, line = 20)`, `.dp` literals, tab heights); measured constants from YouTube mobile web (`SearchResultsLayout.*`, `PostLayout.*`, `ChannelLayout.*`).
- Strings: composables use `stringResource(R.string...)` (counts: home 39, explore 9, search 18, subs 33, channel 64, post 12), but domain enums still carry English labels (`HomeCatalog.filter`, `HomeFeedbackKind.reasons` strings, `FeedMenuAction.label`) with `*.labelResource`/`reasonResources` extension mappings in `ui/design/LocalizedLabels.kt`. `HomeLayout.chips` strings ("All","Music",...) are used as identity keys AND as labels for live chips (no localization for non-catalog chips; `HomeScreen.kt` ChipStrip `label = if (!catalog) chip`).
- Fake/fixture strings in the live domain: `HomeUiState.suggestions` (hardcoded "documentary/music/live" suggestions) is fixture-era; production uses `searchRows`.

### 5.5 Accessibility
- Reasonable coverage: `appDescription(R.string.a11y_...)`, `semanticsLabel`, `Role.Button/Tab`, `selectable(selected, role=Tab)`, `semantics{selected=...}` on like/dislike/tabs, `appPaneTitle` on sheets/overlays, `clearAndSetSemantics{}` on skeletons and on the background while an overlay is open (`HomeScreen.kt` Column modifier). Counts of a11y-ish markers: home 22, explore 7, search 12, subs 12, channel 17, post 12.
- Touch targets: icon buttons 40-48dp (`AppIconButton(diameter = 48.dp)`); Home chip is `widthIn(min=48dp)` but height driven by text (6dp vertical padding + 20sp line => ~32dp visible; surrounding LazyRow is 48dp but the clickable Box is not); the Explore button in chip strip is 48x32dp. These are below 48dp on the vertical axis.
- Font scaling is handled explicitly in Home (`chipExtraHeight = 20.sp.toDp() - 20.dp`) - preserve when redesigning headers.

### 5.6 Tablet / landscape
- Breakpoints (dp widths): `HomeLayout.columns(width)` 1/2/3/4/5/6 at 528/994/1320/1646/1972; outer padding 0 below 528; shorts shelf columns `columns*2` (2 under 600); same table duplicated in `SubscriptionsLayout`. Search: `listLockup(width,height)` switches to horizontal 200x112 rows when width>=416 & landscape, content column max 840dp; channel About docks as a side panel at >=931dp wide (`ChannelAboutPanel.kt:35`: panel 256dp, 500dp at >=1280). Menus: `HomeLayout.menuWidth` 400dp in landscape. Activities do not lock orientation in manifest for these screens (only a player activity has `configChanges` at `AndroidManifest.xml:123`).
- Golden tests only render widths 390 and 1280.

## 6. Tests touching these packages

### Unit tests (`app/src/test/java/com/hhst/youtubelite/<pkg>/`, JVM; ViewModel/state-machine tests with fixtures/fakes; UI-structure independent)
- home: `HomeViewModelTest` (676), `HomeWriteOrderingTest` (215), `HomeForegroundTest` (154), `FeedDisplayCompletenessTest` (131), `HomeDiscoveryViewModelTest` (129) = 5 files, 1305 lines.
- explore: `ExploreViewModelTest` (420), `ExplorePagingBudgetTest` (114) = 2 files, 534.
- search: `SearchResultsViewModelTest` (755), `SearchLoadingPerformanceTest` (297), `SearchActionOwnershipTest` (68) = 3 files, 1120.
- subscriptions: `SubscriptionsViewModelTest` (764), `FixtureSubscriptionsRepositoryTest` (20) = 2 files, 784.
- channel: `ChannelViewModelTest` (415), `ChannelContinuationHistoryTest` (288), `ChannelLoadingStabilityTest` (202) = 3 files, 905.
- post: `PostDetailViewModelTest` (256), `PostCardActionsViewModelTest` (134) = 2 files, 390.
- Totals: 17 unit-test files, ~5,040 lines. They construct VMs with the exact constructor signatures listed in section 2 and assert StateFlow transitions/NavigationRequests/Action semantics: **they are the executable contract**; they do not touch Compose and survive a UI rewrite as long as VMs/domain are untouched. Also relevant elsewhere: `app/src/test/.../data/media/*` (repositories) and `navigation/` tests (not inspected).

### Instrumented tests (`app/src/androidTest/java/com/hhst/youtubelite/<pkg>/`) - UI structure dependent
- home (11 files, ~1,880 lines): `HomeScreenAndroidTest`, `HomeScreenshotAndroidTest` (581; golden matrix 390/1280 x empty/feed/loading/error/feed-shorts/feed-post-top/feed-post + search/menu; `recordHomeGoldens` arg), `HomePageReferenceAndroidTest`, `HomeRefreshAndroidTest`, `HomeRefreshEvidenceAndroidTest`, `HomeLoadingContinuityAndroidTest`, `HomePreloadAndroidTest`, `HomeDiscovery*`, `TrendingAvailabilityAndroidTest`, `presentation/HomeMenuAndroidTest`.
- explore (5 files, ~460): `ExploreHostAndroidTest`, `ExplorePaginationAndroidTest`, `ExplorePaginationNetworkAndroidTest`, `ExplorePagingBudgetAndroidTest`, `ExploreScreenshotAndroidTest`.
- search (3 files, ~1,140): `SearchResultsScreenAndroidTest` (441), `SearchResultsScreenshotAndroidTest` (502), `SearchResultsReferenceAndroidTest` (199).
- subscriptions (5 files, ~560): `SubscriptionsHostAndroidTest`, `SubscriptionsPresentationAndroidTest`, `SubscriptionsScrollAndroidTest`, `SubscriptionsScrollRestorationAndroidTest`, `SubscriptionsSignalsAndroidTest`. No dedicated golden test here; its goldens are in `assets/library/goldens/subscriptions-{390,1280,signed-out-390}.png` (owned by the library screenshot test).
- channel (12 files, ~2,070): `ChannelScreenAndroidTest`, `ChannelScreenshotAndroidTest`, `ChannelReferenceAndroidTest` (344), `ChannelCommunity{Host,Reference,Screenshot}AndroidTest`, `ChannelDiscovery{Reference,Screen}AndroidTest`, `ChannelMusicContent`, `ChannelPagination`, `ChannelTabContinuity`, `ChannelTypography`.
- post (4 files, ~530): `PostDetailHostAndroidTest`, `PostInteractionContinuityAndroidTest`, `ProductionPostActionsAndroidTest`, `ProductionPostLinkAndroidTest`.
- Total ~40 instrumented test files, ~6,485 lines.
- **Goldens** (pixel, `app/src/androidTest/assets/<feature>/goldens` and `/channel/{community,discovery}`): home 28 files, explore 12, search 12, channel 26 (+ community/discovery), post 2, plus `library/goldens/subscriptions-*` (3). They are pixel baselines of the current YouTube-mobile-web-matched layout: **every one will fail after the redesign** and must be re-recorded or deleted (record via instrumentation args like `recordHomeGoldens=true`).
- Tests depend on: `testTag` strings (section 2/4 tags: ~210 tags counted: home 31, explore 18, search 38, subs 23, channel 75, post 25), `Role.Tab`/`selected` semantics, localized text/a11y labels, and fixture scenario intent extras (`home_scenario`, `channel_scenario`, `fixture_scene`, `*_scenario`, `signed_in_preview`). To keep instrumented tests viable, preserve the tag names on equivalent elements or plan to rewrite them. Several `*ReferenceAndroidTest` classes compare to measured YouTube reference geometry (`*Layout` constants); these will need new expectations.

## 7. Risks and surprises for the UI rewriter

1. **Not a NavHost app.** Each screen is an Activity with its own VM + `setAppContent`; tab bars are re-implemented per screen (`HomeBottomBar`, `SearchNavigationBar`, `ChannelNavigationBar`, `BrowseNavigationBar`, Subscriptions/Library variants) and each switches tabs via `MainTabs.intent`. Consolidating into a single shell would be a *navigation* change: the six Activities, ~40 NavigationRequest types and Back handling (`moveTaskToBack` on Home; Search finishes itself after tab switch `SearchResultsActivity.kt:~76-80`) would all need remapping. Safest path: restyle in place, keep Activities.
2. **`ExploreViewModel` is declared inside `ExploreActivity.kt`** (not in presentation/). A file move would also change the unit-test import.
3. **Domain files contain presentation constants** (`*Layout` objects, `HomeLayout.chips`, labels) and fixture-era defaults (`HomeUiState.chips`, `HomeUiState.suggestions`, `HomeFeedbackKind` English strings). Do not delete them; test code and VMs reference them.
4. **Fixture vs live divergence** in the UI: fixture-only menu rows (Queue/Download/Share/Report in `FeedMenuKind.actions`) vs live (`productionActions`: no feedback rows unless a server token exists for the current session generation). A redesigned menu must call `visibleActions(live, signedIn, video)` rather than render `kind.actions`.
5. **Identity/session correctness leaks into UI**: `feedbackGeneration == sessionGeneration` check at menu render, `displayedSession` scroll reset, `refreshSession()` in Activities `onResume`. Skipping these re-introduces cross-account token bugs.
6. **Hide-Shorts preference is applied in the UI layer** (filtering `FeedItem.Shorts` and `Video.short` in `HomeScreen.visibleItems`, hiding `ChannelTab.Shorts`, nav tab) via `LocalHideShorts`/`state.hideShorts`; must be re-applied in any new layout.
7. **Gaps vs YouTube parity** (verified absent): search duration/sort filters; inline channel/playlist rows in the "All" search results; subscription notification bell; local (anonymous) subscriptions; Subscriptions "Today"/"Posts" lanes (UNSUPPORTED server-side); channel Videos tab sorting and Releases/Courses/Store tabs; post sharing; channel About country/joined/views/links not populated by the live `channel()` mapping (fields exist). Home live filter set is a fixed 6 chips, not YouTube's dynamic chip list.
8. **Signed-out behaviour**: Subscriptions tab is not shown at all in the bottom bar when signed out (`MainTabs.tabs`) and the `SignedOut` prompt only appears if the screen is opened directly (e.g. from a tab-switch with `signedInPreview`). Subscribe buttons stay visible signed-out and surface an `AppNotice.SignIn` after the server read refuses.
9. **Branding** hardcoded in presentation: `LitubeBrand` (Home top bar), `LitubeMark` (Home empty), `R.string.a11y_litube_home`, sample drawable `R.drawable.sample_bunny` as default You-tab avatar when signed in without photo (`HomeScreen.kt:~290`), fixture "Blender Studio" strings only in fixtures.
10. **System bars forced light** (`SystemBarStyle.light(WHITE, WHITE)`) in every Activity, and `AppTheme` content uses `DesignTokens.Background == Color.White` branching; a dark/dynamic MinTube theme needs these Activity calls changed (they are in Activity files, which count as UI).
11. **Custom pull-to-refresh** (not Material3 `PullToRefreshBox`); the indicator is anchored to a computed chrome offset; replacing it means replacing `canPull` gating and `refreshing` mapping (`userRefresh` vs quiet refresh flags in `HomeContent.Feed/Empty`: only `userRefresh=true` should show the indicator).
12. **Performance hooks** (prewarm, shorts preload, snapshot seeding) are triggered from UI/Activity code; losing `onWarmCandidate`/`warm` wiring silently degrades tap-to-play latency without failing tests.
13. **Search suggestion/history UI is shared** (`ui/design/SearchPanel.kt`) by Home, Search, Subscriptions, Library/History/Playlists; redesign it once, and its testTags (`search-overlay`, `search-input`, `search-panel-back`, `search-suggestions`, `history-N`, `suggestion-N`) are used by tests in several packages.
