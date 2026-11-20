# MinTube Feature Matrix (YouTube Android parity vs. Litube 4.0.0 codebase)

Baseline: `D:/Coding_Project/MinTube/repo` (pristine Litube 4.0.0). Built from survey reports `docs/_survey/01..06`, then re-verified row by row against source (grep + opening the cited files). Where verification contradicted a survey report, the row's Notes say `CORRECTED:` and the correction is summarised in the "Verification corrections" section.

Conventions
- Path prefix: every `path:line` is relative to `app/src/main/java/com/hhst/youtubelite/` (written `R/` only when ambiguous) unless it starts with `res/` or `AndroidManifest.xml` (relative to `app/src/main/`).
- Status: `WORKS` = data layer present and reachable in UI. `DATA_ONLY` = data/domain present, UI missing or partial (the gap is stated). `MISSING` = not in the data layer (not faked; searched terms listed in Notes). `UNVERIFIED` = could not decide.
- "Live" = production repositories (`data/media/*`); "fixture" = `*/data/Fixture*Repository.kt` used by golden/reference tests only. Status is judged on live behaviour.
- `Verified on build` is intentionally empty; a QA agent fills it later.

---

## 1. Navigation

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| NAV-01 | Bottom bar: Home | WORKS | `ui/components/MainNavigation.kt:28-33` (`MainTab`, `MainTabs.tabs`), `:39-41` intent to `HomeActivity` | `home/presentation/HomeScreen.kt:214,302` (`HomeBottomBar`) | Activity-per-tab model (`FLAG_ACTIVITY_REORDER_TO_FRONT`, `MainNavigation.kt:39-48`). Four separate bar implementations: `HomeBottomBar`, `MainNavigationBar` (`MainNavigation.kt:67`), `BrowseNavigationBar` (`ui/design/BrowseNavigationBar.kt:19`), none shared. |  |
| NAV-02 | Bottom bar: Shorts | WORKS | `ui/components/MainNavigation.kt:47-48` intent to `shorts/ShortsActivity` | `home/presentation/HomeScreen.kt:308`, `ui/components/MainNavigation.kt:72-85` | Tab removed when pref `enable_hide_shorts` is on (`MainNavigation.kt:33`, `extension/PreferenceKeys.kt:9`). The Shorts screen itself has no bar (grep `MainTab`, `NavigationBar` in `shorts/`: 0 hits), so Shorts is a dead end except via back. |  |
| NAV-03 | Bottom bar: Subscriptions | WORKS | `ui/components/MainNavigation.kt:32` (shown only when `signedIn`), `:43` | `subscriptions/presentation/SubscriptionsScreen.kt:178` | Absent for anonymous users by design (no local subscription store, see SUB-08). |  |
| NAV-04 | Bottom bar: You (library) | WORKS | `ui/components/MainNavigation.kt:45` | `library/presentation/YouScreen.kt:69`; avatar when signed in `MainNavigation.kt:78-79` | `selected` semantics only in `MainNavigationBar` (`:75`); the other bars never highlight the current tab. |  |
| NAV-05 | Explore entry point (extra) | WORKS | `home/domain/HomeModels.kt:196` (`HomeNavigationRequest.Explore`), `home/HomeActivity.kt:207` | `home/presentation/HomeScreen.kt:345,378` | Explore is not a bottom-bar tab; reached from the Home chip row / toolbar icon. |  |

## 2. Home and Explore

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| HOME-01 | Home feed (videos, Shorts shelf, mixes, playlists, posts) | WORKS | `data/media/MediaCatalog.kt:68` (`MediaHomeRepository.loadHome`); `home/domain/HomeModels.kt:28-52` (`FeedItem`) | `home/presentation/FeedCards.kt` (dispatcher), `home/presentation/HomeScreen.kt` | Post cards in feed: `FeedCards.kt:188`. |  |
| HOME-02 | Filter chips | WORKS | `data/media/MediaCatalog.kt:67` (`filters = All, Music, Gaming, Live, News, Sports`); `home/domain/HomeModels.kt:12-13` (`HomeCatalog`) | `home/presentation/HomeScreen.kt:155,176,206,338` (`ChipStrip`) | Live chip set differs from fixture set (`HomeModels.kt:236`: Mixes/Nature/Podcasts/Recently uploaded/New to you exist only in fixtures, see HOME-09). Signed-out non-All chips switch to a "catalog" mode. |  |
| HOME-03 | Pull to refresh | WORKS | `home/domain/HomeModels.kt:215` (`HomeAction.Refresh`) | `home/presentation/HomeScreen.kt:121-123,212` (`rememberPullRefreshState`, `PullRefreshIndicator`) | Custom pull physics in `ui/design/PullRefresh.kt`, tuned by tests. |  |
| HOME-04 | Infinite scroll | WORKS | `home/domain/HomeModels.kt` (`nextKey`, `AppendState`); `home/presentation/HomeViewModel.kt` | `home/presentation/HomeScreen.kt:143-149` (`nearEnd` -> `HomeAction.AutomaticLoadNext`) | Manual "Load more" fallback after repeated empty pages. |  |
| HOME-05 | Hide-Shorts preference (extra) | WORKS | `extension/PreferenceKeys.kt:9`; `home/HomeActivity.kt:254` | `settings/presentation/SettingsScreen.kt:141-142`; `home/presentation/HomeScreen.kt:66,308` | Also hides Shorts tab (NAV-02), channel Shorts tab (CH-03) and search Shorts filter (`search/presentation/SearchResultsScreen.kt:262`). |  |
| HOME-06 | Feed card overflow menu (queue, download, watch later, save, share, not interested, don't recommend) (extra) | WORKS | `home/domain/HomeModels.kt:74,76,150-162` (`menu`, `feedback`, `FeedMenuAction`, `FeedMenuKind`) | `home/presentation/HomeOverlays.kt:42` (`VideoMenu`) | Feedback rows only render when token present and `feedbackGeneration == sessionGeneration` (identity check lives in the composable). |  |
| HOME-07 | Explore: category chips Music/Gaming/News/Sports | WORKS | `data/media/MediaLibraryRepositories.kt:263-272` (`MediaExploreRepository`); `explore/domain/ExploreModels.kt:7-8` | `explore/presentation/ExploreScreen.kt:170` (`CategoryChips`) | Fixed 4-value enum; no Trending/Movies/Live explore categories. |  |
| HOME-08 | Explore: shelves, per-shelf paging, refresh, playlists | WORKS | `data/media/MediaLibraryRepositories.kt:268,272,279,290` (`exploreNext`, `exploreShelf`, `ExploreSection`) | `explore/presentation/ExploreScreen.kt:85,100,143,316` | Per-category scroll kept via Savers. |  |
| HOME-09 | Extra home chips: Trending / New to you / Recently uploaded / Mixes / Podcasts | MISSING | Only `home/domain/HomeModels.kt:236` (`HomeLayout.chips`, fixture list); live list is `data/media/MediaCatalog.kt:67` | - | Searched: `Trending`, `Recently uploaded`, `New to you`, `Podcasts` in `home/`, `data/media/`. Not offered live. |  |

## 3. Search

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| SRCH-01 | Suggestions while typing | WORKS | `data/media/MediaSearchSuggestions.kt:23-30,45-61` (`mergeSearchRows`, `rows`) | `ui/design/SearchPanel.kt:46-55,125`; `ui/components/SearchRows.kt:23` | Remote suggestions are best-effort (failure -> empty list, `:56-58`). |  |
| SRCH-02 | Search history (list + per-entry removal) | WORKS | `data/media/MediaSearchSuggestions.kt:10` (`PreferenceSearchHistoryStore`), `:69-77` (`record`, `remove`) | `ui/design/SearchPanel.kt:125-133` (`history-N` rows, remove button) | History is per-account key; `record()` skipped in private mode (`:71`). No "clear all history" action found (grep `clear` in `SearchPanel`, `MediaSearchSuggestions`: none). |  |
| SRCH-03 | Voice search | WORKS | `ui/NativeActivity.kt:209-260` (`RecognizerIntent` + `SpeechRecognizer` fallback, `RECORD_AUDIO`) | `ui/design/SearchPanel.kt:121`; `search/presentation/SearchResultsScreen.kt:186,312`; `search/SearchResultsActivity.kt:97` | Platform side lives in `NativeActivity` (infrastructure; keep). |  |
| SRCH-04 | Filter: result type (All / Shorts / Channels / Playlists) | WORKS | `search/domain/SearchModels.kt:43`; `data/media/MediaCatalog.kt:193-209` (`search`) | `search/presentation/SearchResultsScreen.kt:140,242-262` (`SearchFilterBar`) | No "Videos only" / "Movies" type. |  |
| SRCH-05 | Filter: upload date (Anytime/Today/Week/Month/Year) (extra) | WORKS | `search/domain/SearchModels.kt:45-51,53-56` (`SearchUploadDate`, `SearchFilters`) | `search/presentation/SearchResultsScreen.kt:242` | Applied by label match against server filter chips (`MediaCatalog.kt:201-204`). |  |
| SRCH-06 | Filter: duration (extra) | MISSING | `SearchFilters` has only `type`, `uploaded` (`search/domain/SearchModels.kt:53-56`); `MediaCatalog.kt:201` only walks `uploaded` and `type` labels | - | Searched in `app/src/main`: `duration` (only result-card duration display), `SearchDuration`, `"Duration"`, `filter_duration`. Server filter chips are matched generically (`MediaCatalog.kt:202`), so a duration group might be selectable, but domain model + `transition()` (`:230-260`) would need to change (backend). |  |
| SRCH-07 | Filter: sort by (relevance/date/views/rating) and features (HD, live, CC) (extra) | MISSING | Not in `SearchFilters` / `SearchResultType` (`search/domain/SearchModels.kt:43-56`) | - | Searched: `sort by`, `sortBy`, `Sort by`, `features`, `"HD"` across `app/src/main` (only channel sort chips, `channel/presentation/ChannelScreen.kt:245`). |  |
| SRCH-08 | Results: videos | WORKS | `search/domain/SearchModels.kt:3` (`SearchPage.results`) | `search/presentation/SearchResultsScreen.kt:162` (`SearchList`) | Lazy metadata completion via `SearchRepository.metadata`. |  |
| SRCH-09 | Results: channels (+ subscribe) (extra) | WORKS | `search/domain/SearchModels.kt:5,9`; `data/media/MediaCatalog.kt:320`; `watch/domain/VideoActions.kt:15` | `search/presentation/SearchResultsScreen.kt:158` (`ChannelResultsList`) | Subscribe requires sign-in. |  |
| SRCH-10 | Results: playlists / Shorts (extra) | WORKS | `search/domain/SearchModels.kt:4,6` | `search/presentation/SearchResultsScreen.kt:156,160`; `search/presentation/ShortsResultsList.kt` | Only when matching type filter is selected. |  |
| SRCH-11 | Mixed results in "All" (videos + channels + playlists inline) (extra) | DATA_ONLY | `search/domain/SearchModels.kt:3-7` (`SearchPage` carries `results`, `playlists`, `channels`, `shorts`); `data/media/MediaCatalog.kt:320` builds channels from any search page | `search/presentation/SearchResultsScreen.kt:156-162`: "All" renders `SearchList(content.page.results)` only | UI-only gap: render channel/playlist/shorts lists from the same page in the All view. |  |
| SRCH-12 | Search paging, empty, error, refresh | WORKS | `search/domain/SearchModels.kt` (`nextCursor`, `SearchContent`) | `search/presentation/SearchResultsScreen.kt:155,242` and `RefreshableContent` | Search from Subscriptions / Library overlays reuses the same suggestions. |  |

## 4. Subscriptions

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| SUB-01 | Subscriptions feed | WORKS | `data/media/MediaLibraryRepositories.kt:134-166` (`MediaSubscriptionsRepository.subscriptions`); throws AUTH when signed out `:157-158` | `subscriptions/presentation/SubscriptionsScreen.kt:292` | Signed-out shows a sign-in prompt (`SubscriptionsScreen.kt:171`). |  |
| SUB-02 | Chips: All / Unwatched / Continue watching / Live | WORKS | `data/media/MediaLibraryRepositories.kt:141,172-177` | `subscriptions/presentation/SubscriptionsScreen.kt:199` (`SubscriptionsChipStrip`) | Client-side filter of the loaded page only (not server-side). |  |
| SUB-03 | Chips: Today, Posts | MISSING | `data/media/MediaLibraryRepositories.kt:139-141,152-153` (`supportedFilters` excludes them; `subscription_filter_unavailable`) | - | Enum values exist in domain/fixtures only. Searched: `Today`, `Posts` in `subscriptions/`. |  |
| SUB-04 | Channel avatar strip + all-channels directory | WORKS | `data/media/MediaLibraryRepositories.kt:178-190` (`subscribedChannelsPage`) | `subscriptions/presentation/SubscriptionsScreen.kt:215,248,433` | New-content dot via `hasNewContent`. |  |
| SUB-05 | Per-channel feed | WORKS | `data/media/MediaLibraryRepositories.kt:146-148` (`channelSubscriptions`) | `subscriptions/presentation/SubscriptionsScreen.kt:187` (`subscriptions-back-to-all`) |  |  |
| SUB-06 | Subscribe / unsubscribe on Subscriptions screen (extra) | DATA_ONLY | `watch/domain/VideoActions.kt:15`, `data/media/MediaVideoActions.kt:35` (`subscribe`) | Not on this screen (only Channel, Search, Watch, Shorts expose it); screen has `Manage` -> external request | UI-only: reuse `SubscribeButton`/`rememberSubscriptionAction` from `ui/design`. |  |
| SUB-07 | Notification bell per channel (extra) | MISSING | - | Only the unused icon `AppIcon.Bell` at `ui/design/AppIcons.kt:67` | Searched across `app/src/main`: `Bell`, `bell`, `notificationPref`, `NotificationLevel`, `notification_level`, `notify me`, `all notifications`. No repository method for subscription notification level. |  |
| SUB-08 | Local (anonymous) subscriptions (extra) | MISSING | `data/media/LocalLibraryStore.kt` has history + playlists only (`:79-157`); `VideoActions.subscribe` -> `SignInRequiredException` | - | Searched `subscri` in `LocalLibraryStore.kt`: none. Anonymous users cannot subscribe. |  |

## 5. Watch page

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| WATCH-01 | Expandable description (links, timestamps seek) | WORKS | `watch/domain/WatchModels.kt:5-30` (`WatchVideo.description`); `watch/presentation/WatchDescription.kt:47` (`parseDescription`); `watch/presentation/WatchViewModel.kt:214` (`SeekToTimestamp`, blocked for live) | `watch/presentation/WatchScreen.kt:487-510` (`watch-more` toggle), `:453` (`WatchDescription`) | Timestamp parsing is unit-tested; keep. |  |
| WATCH-02 | Channel row (avatar, name, open channel) | WORKS | `watch/domain/WatchModels.kt` (`channelId`, `avatar`) | `watch/presentation/WatchScreen.kt:511-526` | |  |
| WATCH-03 | Subscribe | WORKS | `watch/domain/VideoActions.kt:15`; `data/media/MediaVideoActions.kt:35`; `watch/presentation/WatchViewModel.kt:354` | `watch/presentation/WatchScreen.kt:523` (`SubscribeButton`) | Needs sign-in; no bell (SUB-07). |  |
| WATCH-04 | Like | WORKS | `watch/domain/VideoActions.kt:14`; `data/media/MediaVideoActions.kt:15`; `watch/presentation/WatchViewModel.kt:232,460` | `watch/presentation/WatchScreen.kt:531,540-550` | Needs sign-in. |  |
| WATCH-05 | Dislike (with counts) | WORKS | `watch/domain/VideoActions.kt:14,22` (`VoteCountRepository`); `watch/presentation/WatchViewModel.kt:233`; `watch/domain/WatchModels.kt:19-20` | `watch/presentation/WatchScreen.kt:532,542` | Counts shown per prefs `enable_display_dislikes` / `enable_show_likes` (`settings/presentation/AdvancedScreen.kt:58-60`). |  |
| WATCH-06 | Share | WORKS | `share/ShareGateway.kt:25`; `watch/presentation/WatchViewModel.kt:237`; `watch/WatchActivity.kt:283` | `watch/presentation/WatchScreen.kt:536` | Plain `ACTION_SEND` of `https://youtu.be/<id>` (`share/VideoShare.kt:43`). |  |
| WATCH-07 | Save to playlist / Watch Later (+ create) | WORKS | `saves/domain/SaveModels.kt:21-28` (`SaveRepository`); `watch/presentation/WatchViewModel.kt:238` (`openSave`) | `watch/presentation/WatchScreen.kt:535` (action bar Save), `:161-220` (overlays, save sheet, new playlist sheet) | `:161` `WatchVideoMenu` is the related-item menu, not the Save sheet. |  |
| WATCH-08 | Download | WORKS | `watch/presentation/WatchViewModel.kt:234`; `watch/WatchActivity.kt:284-296` (`DownloadSingleSpec`) | `watch/presentation/WatchScreen.kt:533` | See DL-* rows. |  |
| WATCH-09 | Add to queue / Open with (extra) | WORKS | `watch/presentation/WatchViewModel.kt:235-236`; `share/ShareGateway.kt:30` | `watch/presentation/WatchScreen.kt:534,537` | |  |
| WATCH-10 | Report video | MISSING | `watch/presentation/WatchViewModel.kt:348` only opens a fixture overlay; `watch/domain/WatchModels.kt:117` (`Report` reasons); `WatchRepository.feedback` accepts only `WatchVideoMenuAction` (`WatchModels.kt:65`); no token path in `data/media/MediaRepositories.kt:86` | `watch/presentation/WatchScreen.kt:538-539` filters Report out when `live` | CORRECTED: survey 03 said DATA_ONLY. Searched: `Report`, `reportToken`, `feedbackToken` in `watch/domain`, `data/media/MediaRepositories.kt`. UI exists only in fixture mode; no production submission. |  |
| WATCH-11 | Recommended / related videos + paging | WORKS | `watch/domain/WatchModels.kt:59-66` (`WatchRepository.next`); `data/media/MediaRepositories.kt:82` | `watch/presentation/WatchScreen.kt:290,751-` (`RelatedPaging`, `WatchRelatedFeed`, phone/list/grid/sidebar variants) | Item menu token-gated (`WatchModels.kt:137-142`). |  |
| WATCH-12 | Comments teaser + open sheet | WORKS | `comments/domain/CommentModels.kt:54-61` (`CommentsRepository`) | `watch/presentation/WatchScreen.kt:266,588` (`WatchComments`); `comments/presentation/CommentsPanel.kt:86` | Live streams show chat instead (`WatchScreen.kt:589-593`). |  |
| WATCH-13 | Comments: read, sort Top/Newest, replies, paging | WORKS | `comments/domain/CommentModels.kt:55-63`; `data/media/MediaCommentsRepository.kt:~165-175` (`repliesPage`) | `comments/presentation/CommentsPanel.kt:556-566` (`SortMenu`) | |  |
| WATCH-14 | Comments: post / reply / delete | WORKS | `data/media/MediaCommentsRepository.kt:46,178-208` (`perform`: Submit/Reply/Delete) | `comments/presentation/CommentsPanel.kt:324` (composer), `:422` (`DeleteCommentSheet`), `:194` | Signed in + web authorisation (`webContext`, `MediaCommentsRepository.kt:44`). |  |
| WATCH-15 | Comment like / dislike (extra) | WORKS | `data/media/MediaCommentsRepository.kt:180-181` (`rate`) | `comments/presentation/CommentsPanel.kt:900,923` (`CountedAction`) | |  |
| WATCH-16 | Comment edit (extra) | MISSING | `comments/domain/CommentModels.kt:135` typed `EditComment`; but `MediaCommentsRepository.supportedMenuActions` = {Reply, Delete} (`:46`) and `perform` falls to `else -> throw UnsupportedOperationException()` (`:~207`) | Menu filtered by `comments/presentation/CommentsPanel.kt:311-320` | CORRECTED: survey 03 said DATA_ONLY. Request type exists only in the domain contract/fixtures; the live repository does not implement it. Searched: `EditComment`, `editComment`, `updateComment` in `data/media/`. |  |
| WATCH-17 | Comment report (extra) | MISSING | `comments/domain/CommentModels.kt:151` typed `ReportComment`; not implemented live (same `perform` else-branch) | filtered by `CommentsPanel.kt:311-320` | CORRECTED: survey 03 said DATA_ONLY. Searched: `ReportComment`, `reportComment` in `data/media/`. |  |
| WATCH-18 | Live chat (read, replay, send) | WORKS | `watch/chat/LiveChatViewModel.kt:28-39` (`LiveChatGateway.listen/replay/send`), `:41` (`MediaLiveChatGateway`) | `watch/chat/LiveChatPanel.kt:61` | Posting needs separate web sign-in (`account/ChatWebSignInActivity.kt`, `ChatAuthorizationRequiredException`, `LiveChatViewModel.kt:82`). |  |
| WATCH-19 | Pull to refresh on watch page (extra) | MISSING | - | - | Searched `pullRefresh`, `PullRefresh`, `rememberPullRefreshState` in `watch/`, `shorts/`, `comments/`: none. Error state has Retry only (`WatchScreen.kt:415`). |  |
| WATCH-20 | Transcript / hashtags / Join / Super Thanks (extra) | MISSING | - | - | Searched (case-insensitive) `transcript`, `hashtag`, `membership`, `Super Thanks`, `"Join"` in `watch/` (the only `membership` hit in `data/media/` is playlist membership, unrelated). |  |

## 6. Player

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| PLY-01 | Double-tap seek | WORKS | `player/surface/GestureMath.kt:20` (`DOUBLE_TAP_SEEK_MS=10_000`); `player/PlayerViewModel.kt:763` | `player/surface/PlayerGestures.kt:90,92`; `player/surface/PlayerGestureOverlays.kt:168` | Left/right thirds seek, center play/pause. |  |
| PLY-02 | Scrubbing (horizontal drag) | WORKS | `player/PlayerViewModel.kt:776,783` | `player/surface/PlayerGestureOverlays.kt:218` (`ScrubBubbleOverlay`) | |  |
| PLY-03 | Seek bar drag (+ chapter ticks, sponsor segments) | WORKS | `player/PlayerViewModel.kt:794,806` | `player/surface/PlayerTimeBar.kt:41,144` | |  |
| PLY-04 | Seek-bar thumbnail (storyboard) preview | MISSING | - | - | Searched across `app/src/main`: `storyboard`, `spriteSheet`, `sprite`, `seekThumb`, `previewFrame`. Only a time bubble exists. |  |
| PLY-05 | Hold to speed up | WORKS | `player/PlayerViewModel.kt:824-828`; `player/engine/PlaybackEngine.kt:192,1110` (`setSpeedHold`) | `player/surface/PlayerGestures.kt:50,154` (`LONG_PRESS_MS=400`); `PlayerGestureOverlays.kt:204` | |  |
| PLY-06 | Swipe to fullscreen (enter/exit) | WORKS | `player/PlayerViewModel.kt:1046,1052,1054,1061` | `player/surface/PlayerGestures.kt:288,295`; `player/surface/FullscreenSwipeMotion.kt`; `watch/presentation/WatchFullscreenPull.kt` | Also auto-fullscreen on rotation: `player/PlayerViewModel.kt:1071`, `player/surface/AutoFullscreen.kt:9`. |  |
| PLY-07 | Miniplayer (in-app) | WORKS | `player/PlayerViewModel.kt:630,644,1237,1243` | `player/surface/MiniPlayerWindow.kt:133`; `player/surface/PlayerControls.kt:620` (`MiniPlayerChrome`) | Swipe-down, drag/dock, pinch resize, dismiss, tap to restore. High-risk gesture/animation file (980 lines). |  |
| PLY-08 | Pinch to zoom (fullscreen) | WORKS | UI-local `player/surface/ZoomMath.kt:6` (`ZoomMath`) | `player/surface/PlayerGestures.kt:66,181`; `player/surface/PlayerSurface.kt:124,383` | Not persisted. |  |
| PLY-09 | Quality selection | WORKS | `player/PlayerViewModel.kt:846` | `player/surface/PlayerSheets.kt:111,243` (`PlayerDialog.Quality`) | Includes Auto and active-quality badge. |  |
| PLY-10 | Playback speed | WORKS | `player/PlayerViewModel.kt:818` | `player/surface/PlayerSheets.kt:110,253` | Persisted when `remember_playback_speed` (`AdvancedScreen.kt:66`). |  |
| PLY-11 | Captions / subtitles | WORKS | `player/PlayerViewModel.kt:849`; `player/engine/PlaybackEngine.kt:112` (`SubtitleTrack`) | `player/surface/PlayerSheets.kt:281` (`PlayerDialog.Captions`); `player/surface/SubtitleOverlay.kt:34` | |  |
| PLY-12 | Subtitle style editor (extra) | WORKS | `player/PlayerViewModel.kt:862`; `player/surface/SubtitleStyle.kt:12` | `player/surface/SubtitleStyleEditor.kt` | Beyond YouTube. |  |
| PLY-13 | Audio track selection | WORKS | `player/PlayerViewModel.kt:858`; `player/datasource/AudioTrackIdentity.kt:6` | `player/surface/PlayerSheets.kt:119,268` | |  |
| PLY-14 | Chapters (list, chip, ticks, preview) | WORKS | `extractor/Models.kt:64` (`Chapter`), `:136` (`ChapterList`); `player/PlayerViewModel.kt:119` | `player/surface/ChaptersSheet.kt:53,103`; `player/surface/PlayerSheets.kt:168`; `player/surface/AnchorMenus.kt:160` | |  |
| PLY-15 | Autoplay next | WORKS | `player/PlayerViewModel.kt:968-971,991` (pref `ENABLE_AUTOPLAY` + `LoopMode.QUEUE_NEXT`); `di/AppModule.kt:278` (`nextVideo`) | `settings/presentation/AdvancedScreen.kt:67-68` (autoplay toggle); `player/surface/PlayerSheets.kt:234-242` (end behavior) | CORRECTED: survey 03 said "no separate autoplay toggle"; `enable_autoplay` is rendered in Advanced settings (survey 05 lists it). Not on the player/watch page itself. |  |
| PLY-16 | Queue: view / reorder / remove / clear / play next | WORKS | `player/QueueRepository.kt:84-157` (`setEnabled`, `add`, `addNext`, `remove`, `clear`, `move`); `player/PlayerViewModel.kt:1110-1129` | `player/surface/QueueSheet.kt:62`; `player/surface/QueueDragState.kt`; `player/surface/LibraryQueueSheet.kt` | Queue also reachable from You tab (`library/presentation/YouScreen.kt:129`). |  |
| PLY-17 | Queue: shuffle | WORKS | `player/engine/PlaybackEngine.kt:104` (`LoopMode.QUEUE_RANDOM`); `player/QueueRepository.kt:211` (`random`); `player/PlayerViewModel.kt:869-873` (`startPlaylist(shuffle=true)`) | `player/surface/PlayerSheets.kt:234-242` (End-behavior dialog entry "Shuffle"); `player/surface/PlayerUi.kt:302,309`; `playlist/presentation/PlaylistScreen.kt:149` | Behaviour is "pick a random queue item at end", reached through the End-behavior dialog; there is no shuffle toggle on the queue sheet itself (grep `shuffle` in `QueueSheet.kt`, `LibraryQueueSheet.kt`: none). |  |
| PLY-18 | Queue: repeat (one) | WORKS | `player/engine/PlaybackEngine.kt:100-104` (`LOOP_ONE`) | `player/surface/PlayerSheets.kt:234-242`; `PlayerUi.kt:299` | No "repeat all" distinct from QUEUE_NEXT. |  |
| PLY-19 | Previous / next | WORKS | `player/PlayerViewModel.kt:944` (`skipToNext`), `player/QueueRepository.kt:197,205` | `player/surface/PlayerControls.kt` (center controls) | |  |
| PLY-20 | Picture-in-picture | WORKS | `player/PlaybackActivity.kt:180-182,216` (`onUserLeaveHint`, `pipParams`); `player/PlayerViewModel.kt:494,1535`; `core/PipAutoEnter.kt` | `player/surface/PlayerSheets.kt:118,349` (More sheet row); toggle `settings/presentation/SettingsScreen.kt:176` | Remote actions prev/play/next/audio. |  |
| PLY-21 | Background play / audio-only | WORKS | `player/PlaybackActivity.kt:76,127`; `player/PlayerViewModel.kt:1545`; `player/PlaybackLifecycle.kt`; `player/service/PlaybackService.kt` | `settings/presentation/SettingsScreen.kt:174` | Media notification + MediaSession are system UI only. |  |
| PLY-22 | Cast (Chromecast + DLNA + link) | WORKS | `cast/CastController.kt:52,98-99`; `cast/DlnaController.kt:94-99`; `player/PlayerViewModel.kt:1139,1162,1181` | `player/surface/PlayerSheets.kt:116,348` (More row only); `player/surface/PlayerCastDialog.kt:43`; `player/surface/PlayerControls.kt:483` (`CastStatusBanners`) | No cast icon in the player top bar, none in Shorts (`shorts/ShortsActivity.kt:277` stubs `shareCastLink`), none in Watch/Home app bars. |  |
| PLY-23 | Cast remote control UI (extra) | DATA_ONLY | `cast/CastController.kt:98`, `cast/DlnaController.kt:94` (`remotePlayer()` returns a Media3 `Player`) | No dedicated remote UI; normal player controls drive it; banner + "Stop casting" in dialog (`PlayerControls.kt:483`, `PlayerCastDialog.kt`) | Candidate for a richer cast panel; transport already available through `remotePlayer()`. |  |
| PLY-24 | Cast queue (extra) | MISSING | `player/engine/PlaybackEngine.kt:604` comment "No skipping while casting, by design" | - | Searched `queue` in `cast/` (only `SynchronousQueue`). Single video via manifest/proxy. |  |
| PLY-25 | Sleep timer | MISSING | - | - | Searched across `app/src/main` (java + res): `sleep timer`, `sleepTimer`, `sleep.?timer`, `stop after`, `countdown_timer`, `auto.?stop`, `timer` in `player/`, `settings/`, `extension/` (only unrelated `MediaOpenTimeout`, auto-hide timer). Only hit for "sleep" in res is an Afrikaans/Dutch word for "drag". |  |
| PLY-26 | Brightness / volume edge swipe (extra) | WORKS | `player/surface/PlayerWindowHost.kt`; `player/surface/GestureMath.kt` | `player/surface/PlayerGestureOverlays.kt:230` (`EdgeSliderOverlay`) | Left/right 35% zones. |  |
| PLY-27 | Screen lock, resize mode, stats-for-nerds, failure dialog (extra) | WORKS | `player/PlayerViewModel.kt:1093,1095`; `player/PlayerViewModel.kt` (`failureReport`) | `player/surface/PlayerControls.kt:600`; `player/surface/InfoDialog.kt:44,90`; `player/surface/PlaybackFailureDialog.kt:32` | Resize remembered when `remember_resize_mode`. |  |
| PLY-28 | Configurable double-tap seek amount (extra) | MISSING | `player/surface/GestureMath.kt:20` constant 10 s | - | Searched `settings/`, `extension/` for `seek_interval`, `SEEK_AMOUNT`, `doubleTapSeek`: none. |  |
| PLY-29 | In-app mini-player setting (extra) | DATA_ONLY | `extension/PreferenceKeys.kt:20,96` (`enable_in_app_mini_player`, default true); consumed `player/PlayerViewModel.kt:504,645`; `watch/WatchActivity.kt:758` | Not rendered: `settings/presentation/SettingsScreen.kt` / `AdvancedScreen.kt` have no row. Only the dead legacy catalog `extension/Extension.kt:97` references it (`Extension.catalog` has no callers) | UI-only: add a toggle bound to the existing key. |  |

## 7. Shorts

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| SHORT-01 | Vertical feed | WORKS | `shorts/data/ShortsRepository.kt:6-9`; `data/media/MediaLibraryRepositories.kt:300` (`MediaShortsRepository`); `data/media/ContentRepositories.kt:41-46` | `shorts/presentation/ShortsScreen.kt:95,137` (`VerticalPager`) | Entry preload disabled in private browsing (`ContentRepositories.kt:40`). |  |
| SHORT-02 | Swipe to change | WORKS | `shorts/presentation/ShortsViewModel.kt` | `shorts/presentation/ShortsScreen.kt:137-140` (pager `settledPage`) | |  |
| SHORT-03 | Like | WORKS | `shorts/presentation/ShortsViewModel.kt:160,165-178` -> `VideoActions.rate` | `shorts/presentation/ShortsScreen.kt:187-189` | Needs sign-in. |  |
| SHORT-04 | Dislike (extra) | DATA_ONLY | `shorts/domain/ShortsModels.kt:96` (`ShortsAction.Dislike`); `shorts/presentation/ShortsViewModel.kt:161` -> `rate(..., Dislike)` | Rail has Like / Comments / Share only (`shorts/presentation/ShortsScreen.kt:187-195`); no dispatch of `ShortsAction.Dislike` anywhere in `shorts/presentation/*Screen*` | CORRECTED: survey 03 said Like/Dislike WORKS. UI-only: add a dislike rail button. |  |
| SHORT-05 | Comments | WORKS | `comments/domain/CommentModels.kt:54` (reused) | `shorts/presentation/ShortsScreen.kt:191-193`; `shorts/ShortsActivity.kt:472` (`CommentsPanel`) | |  |
| SHORT-06 | Share | WORKS | `share/ShareGateway.kt:25`; `shorts/ShortsActivity.kt:224,227,291` | `shorts/presentation/ShortsScreen.kt:195` | |  |
| SHORT-07 | Subscribe, save to playlist, description sheet, tap-pause/hold-2x/playback order (extra) | WORKS | `watch/domain/VideoActions.kt:15`; `shorts/ShortsActivity.kt:284` | `shorts/presentation/ShortsScreen.kt:211` (`SubscribeButton`); `shorts/ShortsActivity.kt:518` (`ShortsDescriptionSheet`); `shorts/presentation/ShortPlaybackControls.kt:38` | |  |
| SHORT-08 | Remix / sound page / audio attribution (extra) | MISSING | - | - | Searched `remix`, `Remix`, `sound`, `audio attribution` in `shorts/`: none. |  |

## 8. Channel

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| CH-01 | Channel header, banner, subscribe | WORKS | `data/media/MediaRepositories.kt:164-190` (`MediaChannelRepository.channel`); `channel/domain/ChannelModels.kt:8-26` | `channel/presentation/ChannelScreen.kt:619` (`ChannelHeader`), `:775` (`ChannelSubscribe`) | Subscribe is server-confirmed; no bell (SUB-07). |  |
| CH-02 | Tab: Videos | WORKS | `data/media/MediaRepositories.kt:224,226` (`videos`) | `channel/presentation/ChannelScreen.kt:475` (`ChannelVideosTab`) | No sort control on Videos (Latest/Popular/Oldest only in Shorts/Live, `channel/domain/ChannelModels.kt:143`). |  |
| CH-03 | Tab: Shorts | WORKS | `data/media/MediaRepositories.kt:235-246` (`shorts`, `shortsNext`) | `channel/presentation/ChannelCommunityTabs.kt:52` (`ChannelShortsTab`) | Hidden when hide-Shorts pref is on (`ChannelScreen.kt:108-111,398`). |  |
| CH-04 | Tab: Playlists | WORKS | `data/media/MediaRepositories.kt:197-215` (`playlists`, `playlistsNext`) | `channel/presentation/ChannelScreen.kt:235` (`ChannelPlaylistsTab`) | Sort (`ChannelPlaylistSort`, `ChannelModels.kt:172`) applied client-side. |  |
| CH-05 | Tab: Community posts | WORKS | `data/media/MediaRepositories.kt:273-275` -> `data/media/MediaPostRepository.kt:73,81` | `channel/presentation/ChannelCommunityTabs.kt:297,351` (`ChannelPostsTab`, `PostCard`) | |  |
| CH-06 | About (description, stats, links) | WORKS | `channel/domain/ChannelModels.kt:8-26`; live mapping sets name/handle/subscribers/description/videoCount/canonicalUrl only (`data/media/MediaRepositories.kt:171-173`) | `channel/presentation/ChannelAboutPanel.kt:44,79-96`; opened from `ChannelScreen.kt:226` | About is a docked panel / bottom sheet, not a `ChannelTab` (`ChannelModels.kt:160-162`: Home, Videos, Shorts, Live, Playlists, Posts, Search). See CH-10 for the fields that stay empty. |  |
| CH-07 | Tab: Home shelves (extra) | WORKS | `data/media/MediaRepositories.kt:178-188` (`ChannelHomeShelf`) | `channel/presentation/ChannelScreen.kt:425` | |  |
| CH-08 | Tab: Live (current + past) (extra) | WORKS | `data/media/MediaRepositories.kt:253-265` | `channel/presentation/ChannelCommunityTabs.kt:162` | Opening a current stream emits a `LiveStream` request not played by the VOD player. |  |
| CH-09 | Search inside channel (extra) | WORKS | `data/media/MediaRepositories.kt:212,218` (`searchVideos`, `searchNext`) | `channel/presentation/ChannelSearchTab.kt:36` | |  |
| CH-10 | About: links, country, joined date, total views (extra) | MISSING | Model fields exist (`channel/domain/ChannelModels.kt:17,19-22`) but live `channel()` never sets them (`data/media/MediaRepositories.kt:171-173`); only `channel/data/FixtureChannelRepository.kt:70` fills `links` | UI renders them when non-blank (`ChannelAboutPanel.kt:79-96`) | Searched `joined`, `country =`, `links =` in `data/media/`, `channel/data/`. The About panel therefore shows only description, subscribers, video count, URL live. Needs mapping change (backend). |  |
| CH-11 | Tabs Releases / Courses / Store / Podcasts / Membership (extra) | MISSING | `channel/domain/ChannelModels.kt:160-162` | - | Searched `Releases`, `Courses`, `Store`, `Podcasts` in `channel/`. |  |
| CH-12 | Channel share / playlist share (extra) | WORKS | `share/ShareGateway.kt:45,50`; `channel/domain/ChannelModels.kt:232,235` | `channel/ChannelActivity.kt:124` and top bar actions | Playlist share only accepts `PL...` ids (`share/PlaylistShare.kt:3-4`). |  |

## 9. Library (You)

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| LIB-01 | History (account) | WORKS | `data/media/MediaLibraryRepositories.kt:53-64` | `library/presentation/YouScreen.kt:125`; `library/HistoryActivity.kt` | Grouped by day section. |  |
| LIB-02 | History (device / anonymous) (extra) | WORKS | `data/media/LocalLibraryStore.kt:79-123` (`history`, `record`, `remove`, `restore`, `clear`, `pause`) | same screens | |  |
| LIB-03 | History: remove item | WORKS | `data/media/MediaLibraryRepositories.kt:65-72`; `data/media/LocalLibraryStore.kt:89` | `library/domain/LibraryModels.kt:122` (`HistoryMenuAction`); `library/HistoryActivity.kt:174-182` | Undo banner for the device lane. |  |
| LIB-04 | History: clear all | WORKS | `data/media/MediaLibraryRepositories.kt:96` | `library/HistoryActivity.kt:184-198` (manage sheet -> confirm dialog) | |  |
| LIB-05 | History: pause / resume recording | WORKS | `data/media/MediaLibraryRepositories.kt:74-95` (`setHistoryPaused`) | `library/HistoryActivity.kt:184-190` (both "Pause" and "Resume" always listed) | Fine for action; see LIB-06 for state. |  |
| LIB-06 | History: show paused state (extra) | DATA_ONLY | `data/media/LocalLibraryStore.kt:84` (`paused()`, device lane only, no callers); remote read exists only inside the pause write (`MediaLibraryRepositories.kt:85-91`) | No paused indicator in `library/presentation/*`, `library/HistoryActivity.kt` | `LibraryRepository` interface exposes no `paused` read (`library/data/LibraryRepository.kt:25` only `setHistoryPaused`). Surfacing it for the account lane needs an interface addition (backend); device lane could be UI-only. |  |
| LIB-07 | History: search inside history (extra) | MISSING | - | Search icon opens global search overlay, not an in-history filter | Searched `query`/`filter` in `library/` repos: none. |  |
| LIB-08 | Watch Later (account) | WORKS | `data/media/MediaSaveRepository.kt:52-58` (`saveWatchLater`, `"WL"`); `library/presentation/LibraryViewModels.kt:154` | `library/presentation/YouScreen.kt:127` | Opens as a playlist. |  |
| LIB-09 | Watch Later (device) (extra) | WORKS | `data/media/LocalLibraryStore.kt:199` (`WATCH_LATER`); `data/media/MediaSaveRepository.kt:56` | `library/presentation/YouScreen.kt:127` | |  |
| LIB-10 | Liked videos (account) | WORKS | `library/presentation/LibraryViewModels.kt:156` (playlist id `"LL"`) | `library/presentation/YouScreen.kt:128` (only when signed in) | |  |
| LIB-11 | Liked videos (anonymous / local) (extra) | MISSING | `library/presentation/LibraryViewModels.kt:155` comment "Liked has no device lane" | Row hidden when signed out (`YouScreen.kt:128`) | Searched `liked`, `LL` in `LocalLibraryStore.kt`: none. |  |
| LIB-12 | Playlists list (account + device) | WORKS | `data/media/MediaLibraryRepositories.kt:114,118`; `data/media/LocalLibraryStore.kt:124` | `library/presentation/YouScreen.kt:126`; `library/PlaylistsActivity.kt` | Playlist row has no menu (no rename/delete/share). |  |
| LIB-13 | Downloaded videos entry | WORKS | `downloader/ui/DownloadUi.kt:50` (`openManager`) | `library/presentation/YouScreen.kt:130`; `library/YouActivity.kt:124-125` | Manager details in DL-* rows. |  |
| LIB-14 | Queue entry (extra) | WORKS | `player/QueueRepository.kt` | `library/presentation/YouScreen.kt:129`; `player/surface/LibraryQueueSheet.kt` | |  |
| LIB-15 | Your videos / clips / purchases / "Your data" rows (extra) | MISSING | `library/domain/LibraryModels.kt:73-76` (`LibraryRow` has 6 entries) | - | Searched `Your videos`, `clips`, `purchases` in `library/`. |  |
| LIB-16 | Local <-> account import / merge (extra) | MISSING | - | - | Searched `merge`, `import` in `library/`, `data/media/LocalLibraryStore.kt` (settings import is a different feature). |  |

### Playlists (detail screen and editing)

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| PL-01 | View playlist (hero, paged list, owner) | WORKS | `playlist/domain/PlaylistModels.kt:51-55` (`PlaylistRepository`) | `playlist/presentation/PlaylistScreen.kt:52` | |  |
| PL-02 | Play all | WORKS | `playlist/domain/PlaylistModels.kt:96,126` (`PlayAll`, `Queue`) | `playlist/presentation/PlaylistScreen.kt:281` (`playlist-play-all`) | |  |
| PL-03 | Shuffle play | WORKS | `playlist/domain/PlaylistModels.kt:97` | `playlist/presentation/PlaylistScreen.kt:149-152` (overflow menu, not primary button) | |  |
| PL-04 | Download whole playlist (extra) | WORKS | `playlist/domain/PlaylistModels.kt:107,131`; `downloader/ui/DownloadUi.kt:39` | `playlist/presentation/PlaylistScreen.kt:150-152` | |  |
| PL-05 | Create playlist (from a video) | WORKS | `saves/domain/SaveModels.kt:25` (`create(title, description, videoId)`) | `watch/presentation/WatchScreen.kt:180-198` (`NewPlaylistSheet`, `SaveToPlaylistSheet`), save sheets `saves/presentation/SaveSheets.kt:70` | Creation always needs a video. |  |
| PL-06 | Create empty playlist from Playlists list (extra) | MISSING | `saves/domain/SaveModels.kt:25` requires `videoId` | - | Searched `create` in `library/`, `playlist/`: none. Needs interface change (backend). |  |
| PL-07 | Add / remove video via save sheet | WORKS | `saves/domain/SaveModels.kt:23` (`setContaining`, add and remove); `data/media/MediaSaveRepository.kt:49-80` (`editPlaylist`) | `saves/presentation/SaveSheets.kt:70` (`SaveToPlaylistSheet`) | |  |
| PL-08 | Remove video from inside the playlist screen (extra) | DATA_ONLY | `saves/domain/SaveModels.kt:23` `setContaining(id, videoId, false)`; `data/media/LocalLibraryStore.kt:136` (`removeFromPlaylist`) | `playlist/domain/PlaylistModels.kt:117-119`: `PlaylistVideoAction` = {WatchLater, Save} only | UI-only candidate: add a "Remove from playlist" menu entry that calls `SaveRepository.setContaining(id, videoId, false)` (`PlaylistViewModel` already receives `saveRepository`, `playlist/presentation/PlaylistViewModel.kt:29`). |  |
| PL-09 | Rename playlist (extra) | MISSING | - | - | Searched across `app/src/main`: `rename`, `renamePlaylist`, `setPlaylistTitle`, `editPlaylist` (only used for membership at `data/media/MediaSaveRepository.kt:75`), `editTitle`. |  |
| PL-10 | Delete playlist (extra) | MISSING | - | - | Searched: `deletePlaylist`, `removePlaylist`, `delete` in `saves/`, `playlist/`, `library/`. |  |
| PL-11 | Reorder videos in playlist (extra) | MISSING | - | - | Searched: `reorder`, `moveInPlaylist`, `reorderPlaylist`. `player/surface/QueueDragState.kt` reorders the player queue only. |  |
| PL-12 | Playlist privacy / sort / collaborative / description edit (extra) | MISSING | No field in `PlaylistSummary` / `NewPlaylistDraft` | - | Searched: `privacy` (only watch-reporting privacy), `PlaylistPrivacy`, `unlisted`. |  |
| PL-13 | Save someone else's playlist to library (extra) | DATA_ONLY | `saves/domain/SaveModels.kt:28` (`savePlaylistToLibrary`); `data/media/MediaSaveRepository.kt:108` | Bookmark button `playlist/presentation/PlaylistScreen.kt:290` (hidden for device lists) | No "saved" state shown and no unsave (no `unsavePlaylist` in `SaveRepository`); unsave would need backend. Save action itself is reachable. |  |

## 10. Share

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| SHARE-01 | Share video link | WORKS | `share/ShareGateway.kt:25`; `share/VideoShare.kt:36-49` | Watch `WatchScreen.kt:536`, Shorts, feed menus (`ui/ActivityMediaActionExecutor.kt`) | Clipboard fallback when no handler. |  |
| SHARE-02 | Share with timestamp (extra) | MISSING | `share/VideoShare.kt:36-44` builds `https://youtu.be/<id>` with no time parameter | - | Searched `[?&]t=`, `startSeconds`, `start_time`, `startAt`, `timestamp` in `share/`, `watch/`. Inbound links do parse `t` (`navigation/ContentLink.kt:7`, `player/PlayerViewModel.kt:508`), and `comments/CommentLinks.kt:29` builds `&t=` for comment links only. Backend change: `ShareGateway.share` needs a position argument. |  |
| SHARE-03 | Copy link (extra) | WORKS | `share/ShareGateway.kt:70` | Only via feed action executor (`ui/ActivityMediaActionExecutor.kt:42`) | Not on the Watch page action bar. UI-only to add there. |  |
| SHARE-04 | Share post (extra) | MISSING | `share/ShareGateway.kt` has `share`, `openWith`, `sharePlaylist`, `shareChannel`, `copyLink` only | - | Searched `Share` in `post/`: none. |  |

## 11. Downloads (Litube-specific, must keep)

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| DL-01 | Download options (video / audio-only, quality, audio track/bitrate, subtitles, cover, file name template, attachments only, size preview) | WORKS | `downloader/core/DownloadModels.kt:80-101` (`DownloadConfig`) | `downloader/ui/DownloadSheets.kt:207,219,235-236,356,364` | Quality list in settings pane is hardcoded `144p..2160p` (`DownloadScreens.kt:452`). |  |
| DL-02 | Batch confirm (playlist / queue) | WORKS | `downloader/core/DownloadSnapshotGuard.kt`; `downloader/core/DownloadModels.kt` (`BatchSnapshot`) | `downloader/ui/DownloadSheets.kt:481` (`BatchConfirmSheet`) | |  |
| DL-03 | Download manager: list, filters, progress | WORKS | `downloader/core/DownloadCoordinator.kt:31`; `downloader/core/DownloadModels.kt:144` (`DownloadFilter`); `downloader/ui/DownloadViewModel.kt:49-55` | `downloader/ui/DownloadScreens.kt:94,269`; `downloader/ui/DownloadComponents.kt:145` | |  |
| DL-04 | Pause / resume / cancel / retry / redownload | WORKS | `downloader/ui/DownloadViewModel.kt:148-162` | `downloader/ui/DownloadPresentation.kt:45-54` (`DownloadRowAction`) | |  |
| DL-05 | Delete (record / record + files), clear history | WORKS | `downloader/core/DownloadModels.kt:45-48` (`RemoveMode`) | `downloader/ui/DownloadScreens.kt:622` (`DeleteDialog`) | |  |
| DL-06 | Download settings (Wi-Fi only, max connections, default quality) | WORKS | `downloader/core/DownloadSettings.kt:16-19`; `downloader/core/PreferenceDownloadPrefs.kt:12-14` | `downloader/ui/DownloadScreens.kt:447,451-470` | Reached from Settings > Advanced > Downloads. |  |
| DL-07 | Notifications + background scheduling | WORKS | `downloader/engine/AndroidDownloadNotifications.kt`; `downloader/engine/BackgroundDownloadScheduler.kt:21` | system notification actions (`downloader/ui/DownloadActions.kt`) | |  |
| DL-08 | Entry points (watch, player, queue, feed, system share) | WORKS | `downloader/ui/DownloadUi.kt:26,39,50`; `downloader/ui/DownloadEntries.kt` | `watch/WatchActivity.kt:284-296`; `player/surface/PlayerSheets.kt:126,162,165`; `downloader/ui/DownloadShareActivity.kt` | |  |
| DL-09 | Share / open downloaded file (external) (extra) | WORKS | `downloader/ui/DownloadFileShare.kt:17` (`ACTION_VIEW`, FileProvider) | `downloader/ui/DownloadScreens.kt:155` | Opens with an external app. |  |
| DL-10 | Play downloaded video inside the app (extra) | MISSING | No code resolves `publishedUris` into the player | `downloader/ui/DownloadScreens.kt:155` uses external `ACTION_VIEW` | Searched outside `downloader/`: `offline`, `downloadedUri`, `localFile`, `publishedUri`, `isDownloaded`, `openDownloaded`, `playDownload`, `watchPageDownloaded`, `observeVideo`: none. Offline fixture exists only in tests. Backend (player source resolver) change required. |  |
| DL-11 | "Downloaded" badge on watch page / rows (extra) | DATA_ONLY | `downloader/ui/DownloadUiState.kt:27,50,90,118` (`watchPageDownloaded`); `downloader/ui/DownloadViewModel.kt:64` (`observeVideo`) | Only consumer is the download sheet's existing-task check (`downloader/ui/DownloadSheets.kt:195`); nothing in watch/home/search | UI-only: collect `DownloadViewModel.observeVideo(videoId)` for badges. |  |
| DL-12 | Storage location choice (extra) | MISSING | Fixed `Downloads/Litube` (`downloader/io/DownloadPublisherImpl.kt:301,375`) | - | Searched `OpenDocumentTree`, `folder`, `location` in `downloader/ui/`. |  |

## 12. Account

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| ACC-01 | Anonymous use | WORKS | `data/media/MediaSession.kt:68-70,107` (visitor identity); `data/media/AnonymousHomeIdentity.kt` | All feeds work signed out; sign-in prompts in `library/presentation/YouScreen.kt:64,223,295` | Subscriptions, Liked, comment posting need sign-in. |  |
| ACC-02 | Sign in (device code) | WORKS | `account/data/SignInService.kt:21-26,40` | `account/SignInActivity.kt`; `account/SignInFlow.kt`; triggered `library/YouActivity.kt:127,284`, `ui/Notices.kt:14` | |  |
| ACC-03 | Multi-account switching | WORKS | `data/media/MediaSession.kt:209,286` (`accounts`, `selectAccount`) | `account/presentation/AccountMenuSheet.kt:74` (`AccountSwitchSheet`); `library/YouActivity.kt:264` | Reachable only through You tab account menu. |  |
| ACC-04 | "On this device" mode (anonymous while keeping accounts) (extra) | WORKS | `data/media/MediaSession.kt:155,162` (`anonymousMode`, `setAnonymousMode`) | `account/presentation/AccountMenuSheet.kt:100` (`account-switch-device`) | |  |
| ACC-05 | Add account / sign out (extra) | WORKS | `data/media/MediaSession.kt:249,299` | `account/presentation/AccountMenuSheet.kt:146`; `library/YouActivity.kt:85,129,288` (confirm dialog) | |  |
| ACC-06 | Private browsing | WORKS | `extension/PreferenceKeys.kt` (`private_browsing`); `data/media/MediaSession.kt:153` (`privateBrowsing`); polled by `data/media/MediaSearchSuggestions.kt:71`, `data/media/WatchTimeReporter.kt`, `data/media/PlaybackWatchReporting.kt`, `data/media/MediaResumePosition.kt`, `player/engine/PlaybackEngine.kt`, `shorts/data/ShortsMediaPreloader.kt:112-231` | `settings/presentation/SettingsScreen.kt:144-145` (toggle, summary "Don't send watch progress or save local history", `res/values/strings.xml:449-450`) | Toggle only. |  |
| ACC-07 | Private-browsing indicator (extra) | DATA_ONLY | `data/media/MediaSession.kt:153` (`privateBrowsing()` polled, not observed); `di/AppModule.kt:105-108` pref listener; `extension/ExtensionManager` pref | None: grep `incognito`, `privateBrowsing`, `PRIVATE_BROWSING` in `java/` outside `data/`, `player/`, `extension/` finds only `settings/presentation/SettingsScreen.kt:144-145` and `di/AppModule.kt` | UI-only: a pref listener already exists (`AppModule.kt:105-108`); an indicator can observe `ExtensionManager`/prefs. A live `StateFlow` would be cleaner (backend, optional). |  |
| ACC-08 | Manage Google account / "Your data" (extra) | MISSING | - | - | Searched `myaccount`, `manage account`, `Your data` in `account/`, `library/`: none. |  |
| ACC-09 | Account menu (view channel, switch, settings, about, sign out) (extra) | WORKS | `account/domain/AccountModels.kt:3` (`AccountMenuAction`) | `account/presentation/AccountMenuSheet.kt` | |  |

## 13. Other (settings, appearance, locale, deep links)

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| SET-01 | Settings screens | WORKS | `extension/PreferenceKeys.kt`; `extension/ExtensionManager.kt` | `settings/presentation/SettingsScreen.kt`, `settings/presentation/AdvancedScreen.kt`; entry `library/YouActivity.kt:130` | Only entered from the You tab, not from the Home toolbar. |  |
| SET-02 | Light / dark / system theme | WORKS | `extension/ExtensionManager.kt:110-115`; `ui/theme/AppStyle.kt:15,17-21` | `settings/presentation/SettingsScreen.kt:128-136` (dialog) | Not included in settings backup. |  |
| SET-03 | Multi-language | WORKS | `core/AppLocale.kt:15` (`OPTIONS`); `res/values-b+vi/` (Vietnamese present) | `settings/presentation/SettingsScreen.kt:137-140` (language dialog) | API < 33 uses wrap + recreate. |  |
| SET-04 | Open YouTube links from other apps | WORKS | `navigation/ContentLink.kt:7-27` (`parse`); `navigation/ContentLinkActivity.kt:17`; `AndroidManifest.xml:73-88` (VIEW hosts youtube.com, www/m/music, youtu.be) | `AndroidManifest.xml:90-95` (`SEND text/plain` also registered on `ContentLinkActivity`) | Video/Short open directly; channel/playlist/search/post/feed forwarded to Home. `ContentLink.parse` is security-sensitive and unit-tested. |  |
| SET-05 | Data saver, haptic strength, backup import/export (extra) | WORKS | `extension/PreferenceKeys.kt`; `extension/SettingsBackup.kt` | `settings/presentation/SettingsScreen.kt:146-147`; `AdvancedScreen.kt:107`; `ui/extension/SettingsDataScreen.kt` | Backup excludes theme, language, queue, history. |  |
| SET-06 | About, cache clear, update check (extra) | WORKS | `ui/about/AboutActivity.kt:85-110` | `ui/about/AboutScreen.kt` | Update check points at the upstream GitHub repo (branding item for the redesign). |  |
| SET-07 | Notification settings (extra) | WORKS | system channel state | `settings/presentation/SettingsScreen.kt:50-54,75` | Opens system settings only. |  |
| SET-08 | Default playback quality / speed / tab, captions default (extra) | MISSING | `extension/PreferenceKeys.kt` has only `remember_quality`, `remember_playback_speed` | - | Searched `default_quality` (download only), `default_speed`, `default_tab`, `captions default` in `extension/`, `settings/`. |  |
| SET-09 | In-app feedback / report-a-problem (extra) | MISSING | `feedback/presentation/` is recommendation feedback only | - | Searched `report a problem`, `send feedback` (feed-level only), `bug report` in `ui/`, `settings/`. |  |

## 14. Litube-specific features (must be kept)

| # | Feature | Status | Data-layer evidence | UI evidence | Notes | Verified on build |
|---|---|---|---|---|---|---|
| LIT-01 | SponsorBlock: fetch + categories + auto-skip | WORKS | `player/sponsor/SponsorBlockManager.kt:19,135,156` (`sponsor.ajay.app`, prefs-driven categories); `di/AppModule.kt:230`; `player/sponsor/SponsorSkipController.kt` | Category toggles `settings/presentation/AdvancedScreen.kt:162-171` | |  |
| LIT-02 | SponsorBlock: countdown card, manual skip chip, timeline segments | WORKS | `player/PlayerViewModel.kt:1216-1224` (`onSponsorSkipCancel/SkipNow/ChipSkip`) | `player/surface/PlayerGestureOverlays.kt:285` (`SponsorCountdownCard`), `player/surface/PlayerSurface.kt:490`, `player/surface/PlayerTimeBar.kt:45` | |  |
| LIT-03 | SponsorBlock: per-category action (mute / ask), colours, server URL (extra) | MISSING | `extension/PreferenceKeys.kt` has booleans only | - | Searched `mute`, `ask` in `player/sponsor/`; server URL hardcoded (`SponsorBlockManager.kt:135`). |  |
| LIT-04 | DeArrow titles | WORKS | `dearrow/DeArrowService.kt:43,92`; prefs `dearrow_titles` | `dearrow/DeArrowDisplay.kt:20` (`rememberDeArrowTitle`), used in `home/presentation/FeedCards.kt:106`, `search/presentation/SearchResultsScreen.kt:453`, `channel/presentation/ChannelScreen.kt:566`, `explore/presentation/ExploreScreen.kt:365`; toggle `settings/presentation/AdvancedScreen.kt:80-82` | Default off. |  |
| LIT-05 | DeArrow thumbnails | WORKS | `dearrow/DeArrowService.kt:92`; `dearrow/DeArrowDisplay.kt:36,51,67` | toggle `settings/presentation/AdvancedScreen.kt:83-84` | Default off. |  |
| LIT-06 | Download manager | WORKS | see DL-01..DL-09 | see DL-01..DL-09 | Kept as a unit. |  |
| LIT-07 | Custom player gestures (7 gestures x windowed/fullscreen) | WORKS | `extension/PreferenceKeys.kt:42-70`; `player/PlayerViewModel.kt:241,1615` (`gestureEnabled`, `gesturePrefKey`) | `settings/presentation/AdvancedScreen.kt:118-160` | Advanced row writes two keys atomically; windowed/fullscreen rows zipped by position, so reordering rows is a bug. |  |
| LIT-08 | Error diagnostics: structured log + export | WORKS | `diagnostics/AppLog.kt:23,57-60,156` (crash handler, `export`); `diagnostics/DiagnosticEvent.kt` | `ui/about/AboutActivity.kt:54-65,128-166` (Export logs / Save logs) | |  |
| LIT-09 | Error diagnostics: copyable failure reports (playback, cast, notices) | WORKS | `diagnostics/PlaybackFailureReport.kt`; `cast/CastFailureReport.kt`; `diagnostics/ActionFailureReport.kt` | `player/surface/PlaybackFailureDialog.kt:32`; `player/surface/CastFailureDialog.kt`; `ui/Notices.kt:20-30` | All three entry points must survive the redesign. |  |

---

## Summary counts per status

| Status | Count |
|---|---|
| WORKS | 130 |
| DATA_ONLY | 10 |
| MISSING | 35 |
| UNVERIFIED | 0 |
| Total rows | 175 |

## Missing list (MISSING features and the reason)

- HOME-09 Extra home chips: Trending / New to you / Recently uploaded / Mixes / Podcasts: Searched: `Trending`, `Recently uploaded`, `New to you`, `Podcasts` in `home/`, `data/media/`. Not offered live.
- SRCH-06 Filter: duration (extra): Searched in `app/src/main`: `duration` (only result-card duration display), `SearchDuration`, `"Duration"`, `filter_duration`. Server filter chips are matched generically (`MediaCatalog.kt:202`), so a duration group might be selectable, but domain model + `transition()` (`:230-260`) would need to change (backend).
- SRCH-07 Filter: sort by (relevance/date/views/rating) and features (HD, live, CC) (extra): Searched: `sort by`, `sortBy`, `Sort by`, `features`, `"HD"` across `app/src/main` (only channel sort chips, `channel/presentation/ChannelScreen.kt:245`).
- SUB-03 Chips: Today, Posts: Enum values exist in domain/fixtures only. Searched: `Today`, `Posts` in `subscriptions/`.
- SUB-07 Notification bell per channel (extra): Searched across `app/src/main`: `Bell`, `bell`, `notificationPref`, `NotificationLevel`, `notification_level`, `notify me`, `all notifications`. No repository method for subscription notification level.
- SUB-08 Local (anonymous) subscriptions (extra): Searched `subscri` in `LocalLibraryStore.kt`: none. Anonymous users cannot subscribe.
- WATCH-10 Report video: CORRECTED: survey 03 said DATA_ONLY. Searched: `Report`, `reportToken`, `feedbackToken` in `watch/domain`, `data/media/MediaRepositories.kt`. UI exists only in fixture mode; no production submission.
- WATCH-16 Comment edit (extra): CORRECTED: survey 03 said DATA_ONLY. Request type exists only in the domain contract/fixtures; the live repository does not implement it. Searched: `EditComment`, `editComment`, `updateComment` in `data/media/`.
- WATCH-17 Comment report (extra): CORRECTED: survey 03 said DATA_ONLY. Searched: `ReportComment`, `reportComment` in `data/media/`.
- WATCH-19 Pull to refresh on watch page (extra): Searched `pullRefresh`, `PullRefresh`, `rememberPullRefreshState` in `watch/`, `shorts/`, `comments/`: none. Error state has Retry only (`WatchScreen.kt:415`).
- WATCH-20 Transcript / hashtags / Join / Super Thanks (extra): Searched (case-insensitive) `transcript`, `hashtag`, `membership`, `Super Thanks`, `"Join"` in `watch/` (the only `membership` hit in `data/media/` is playlist membership, unrelated).
- PLY-04 Seek-bar thumbnail (storyboard) preview: Searched across `app/src/main`: `storyboard`, `spriteSheet`, `sprite`, `seekThumb`, `previewFrame`. Only a time bubble exists.
- PLY-24 Cast queue (extra): Searched `queue` in `cast/` (only `SynchronousQueue`). Single video via manifest/proxy.
- PLY-25 Sleep timer: Searched across `app/src/main` (java + res): `sleep timer`, `sleepTimer`, `sleep.?timer`, `stop after`, `countdown_timer`, `auto.?stop`, `timer` in `player/`, `settings/`, `extension/` (only unrelated `MediaOpenTimeout`, auto-hide timer). Only hit for "sleep" in res is an Afrikaans/Dutch word for "drag".
- PLY-28 Configurable double-tap seek amount (extra): Searched `settings/`, `extension/` for `seek_interval`, `SEEK_AMOUNT`, `doubleTapSeek`: none.
- SHORT-08 Remix / sound page / audio attribution (extra): Searched `remix`, `Remix`, `sound`, `audio attribution` in `shorts/`: none.
- CH-10 About: links, country, joined date, total views (extra): Searched `joined`, `country =`, `links =` in `data/media/`, `channel/data/`. The About panel therefore shows only description, subscribers, video count, URL live. Needs mapping change (backend).
- CH-11 Tabs Releases / Courses / Store / Podcasts / Membership (extra): Searched `Releases`, `Courses`, `Store`, `Podcasts` in `channel/`.
- LIB-07 History: search inside history (extra): Searched `query`/`filter` in `library/` repos: none.
- LIB-11 Liked videos (anonymous / local) (extra): Searched `liked`, `LL` in `LocalLibraryStore.kt`: none.
- LIB-15 Your videos / clips / purchases / "Your data" rows (extra): Searched `Your videos`, `clips`, `purchases` in `library/`.
- LIB-16 Local <-> account import / merge (extra): Searched `merge`, `import` in `library/`, `data/media/LocalLibraryStore.kt` (settings import is a different feature).
- PL-06 Create empty playlist from Playlists list (extra): Searched `create` in `library/`, `playlist/`: none. Needs interface change (backend).
- PL-09 Rename playlist (extra): Searched across `app/src/main`: `rename`, `renamePlaylist`, `setPlaylistTitle`, `editPlaylist` (only used for membership at `data/media/MediaSaveRepository.kt:75`), `editTitle`.
- PL-10 Delete playlist (extra): Searched: `deletePlaylist`, `removePlaylist`, `delete` in `saves/`, `playlist/`, `library/`.
- PL-11 Reorder videos in playlist (extra): Searched: `reorder`, `moveInPlaylist`, `reorderPlaylist`. `player/surface/QueueDragState.kt` reorders the player queue only.
- PL-12 Playlist privacy / sort / collaborative / description edit (extra): Searched: `privacy` (only watch-reporting privacy), `PlaylistPrivacy`, `unlisted`.
- SHARE-02 Share with timestamp (extra): Searched `[?&]t=`, `startSeconds`, `start_time`, `startAt`, `timestamp` in `share/`, `watch/`. Inbound links do parse `t` (`navigation/ContentLink.kt:7`, `player/PlayerViewModel.kt:508`), and `comments/CommentLinks.kt:29` builds `&t=` for comment links only. Backend change: `ShareGateway.share` needs a position argument.
- SHARE-04 Share post (extra): Searched `Share` in `post/`: none.
- DL-10 Play downloaded video inside the app (extra): Searched outside `downloader/`: `offline`, `downloadedUri`, `localFile`, `publishedUri`, `isDownloaded`, `openDownloaded`, `playDownload`, `watchPageDownloaded`, `observeVideo`: none. Offline fixture exists only in tests. Backend (player source resolver) change required.
- DL-12 Storage location choice (extra): Searched `OpenDocumentTree`, `folder`, `location` in `downloader/ui/`.
- ACC-08 Manage Google account / "Your data" (extra): Searched `myaccount`, `manage account`, `Your data` in `account/`, `library/`: none.
- SET-08 Default playback quality / speed / tab, captions default (extra): Searched `default_quality` (download only), `default_speed`, `default_tab`, `captions default` in `extension/`, `settings/`.
- SET-09 In-app feedback / report-a-problem (extra): Searched `report a problem`, `send feedback` (feed-level only), `bug report` in `ui/`, `settings/`.
- LIT-03 SponsorBlock: per-category action (mute / ask), colours, server URL (extra): Searched `mute`, `ask` in `player/sponsor/`; server URL hardcoded (`SponsorBlockManager.kt:135`).

## UI-only opportunities (DATA_ONLY rows the redesign can surface without backend changes)

- SRCH-11 Mixed search results in All view: `SearchPage` already carries `channels`, `playlists`, `shorts`; render them inline in `SearchResultsScreen.kt:156-162`.
- SUB-06 Subscribe/unsubscribe on the Subscriptions screen: reuse `VideoActions.subscribe` via `rememberSubscriptionAction`.
- SHORT-04 Shorts dislike button: `ShortsAction.Dislike` is fully handled by `ShortsViewModel.kt:161`.
- PLY-23 Richer cast remote UI: `remotePlayer()` already exposes a Media3 `Player`; add a dedicated cast control / top-bar cast button (entry today is only the More sheet).
- PLY-29 In-app mini-player setting: add a toggle bound to `enable_in_app_mini_player` (read by `PlayerViewModel.kt:504,645`).
- PL-08 Remove from playlist inside the playlist screen: `SaveRepository.setContaining(id, videoId, false)` exists (account + device).
- DL-11 "Downloaded" badge: collect `DownloadViewModel.observeVideo` and show it on watch page / rows.
- ACC-07 Private-browsing indicator: the pref listener exists; surface state in the chrome (also SHARE-03 copy-link on the Watch action bar uses existing `ShareGateway.copyLink`).
- LIB-06 Paused-history indicator for the device lane (`LocalLibraryStore.paused()`); the account lane needs backend (see below).
- PLY-17 / PLY-15 discoverability: shuffle/repeat/autoplay already exist in End-behavior dialog and Advanced settings; they can be promoted to visible controls without data changes.

## Would need backend changes (look like UI gaps, but are not)

- WATCH-10 Report video and WATCH-16/17 comment edit/report: live repositories do not implement them (missing server tokens / `perform` throws `UnsupportedOperationException`); fixtures show UI that cannot ship.
- SRCH-06 / SRCH-07 Search duration, sort, features: `SearchFilters` and `MediaCatalog.transition()` handle only type + upload date.
- SUB-03 Subscriptions Today/Posts chips and SUB-07 notification bell: no server lane / no repository method.
- SUB-08 Anonymous subscriptions: no local store (`LocalLibraryStore`).
- PLY-04 Storyboard thumbnails: extractor output not consumed by player.
- PLY-24 Cast queue; PLY-25 Sleep timer; PLY-28 configurable seek amount (constant in `GestureMath`): engine/preferences work needed.
- CH-10 Channel About links/country/joined/views: `MediaChannelRepository.channel` does not map them.
- DL-10 In-app playback of downloads: player source resolver must accept local `publishedUris`; DL-12 storage location.
- PL-06 Create empty playlist, PL-09 rename, PL-10 delete, PL-11 reorder, PL-12 privacy, PL-13 unsave: `SaveRepository`/`PlaylistRepository` have no such operations.
- LIB-06 (account lane) paused state: `LibraryRepository` exposes only `setHistoryPaused`, no read.
- SHARE-02 Share with timestamp: `ShareGateway.share(videoId)` has no position parameter.
- LIB-11 Local liked videos, LIB-07 in-history search, SET-08 default quality/speed settings: new data/prefs.

## Verification corrections vs. survey reports

- WATCH-10 (03: DATA_ONLY) -> MISSING: no production report path.
- WATCH-16 / WATCH-17 (03: DATA_ONLY) -> MISSING: `MediaCommentsRepository.perform` throws for Edit/Report.
- SHORT-04 (03: Like/Dislike WORKS) -> Dislike is DATA_ONLY: no button in the Shorts rail.
- PLY-15 (03: "no separate autoplay toggle") -> a toggle exists in Advanced settings (`AdvancedScreen.kt:67-68`).
- Post "Report" (02: DATA_ONLY / stub): `PostRepository` has no report operation (`post/domain/PostModels.kt:45-57`), so it is not listed as DATA_ONLY; not a feature in the requested list, recorded here only.
- LIB-06 (04: DATA_ONLY, implied UI-only): only true for the device lane; the account lane has no read in `LibraryRepository`.
- Line-number drift only (content correct): 04 `LibraryViewModels.kt:~162-164` is actually `:154-156`; 02 `SearchModels.kt:~57` is `:53-56`; 02 `HomeScreen.kt:~338` is `:338` exact.
