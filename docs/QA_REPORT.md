# MinTube QA report

Tester: independent QA agent. Build: master eb710bc (`app-debug.apk`, `com.nevminhnh.mintube.debug`) on AVD `mintube_tablet` (2560x1600, 320 dpi, API 36 swiftshader), anonymous, live network. After the coordinator's message, master 476751d (mini-player bottom-right, PiP from any screen, "Search" hint) was rebuilt (`qa-asm2`) and reinstalled, but the emulator became unusable before those changes could be verified (see "Not verified").

IMPORTANT CAVEAT. This run is INCOMPLETE. The host was heavily loaded (load average 15-23 shared with other emulators and Gradle). Cold start took 22-25 s, frames took 2-3 s, and Android raised ANR dialogs several times. Many items below are therefore "not exercised", not "failed". Nothing was faked; every PASS has a screenshot or a state read from `dumpsys media_session` / the accessibility tree.

## 1. Summary counts (feature matrix, 140 rows = 130 WORKS + 10 DATA_ONLY)

| Verdict | Rows |
|---|---|
| PASS | 54 |
| PASS (partial): element seen working, sub-behaviour not exercised | 36 |
| FAIL | 2 (SRCH-01, LIB-06) |
| NOT VERIFIABLE | 48 (sign-in needed: SUB-*, LIB-01/08/10, ACC-03/05, WATCH-03/15, SHORT-03/04, WATCH-14; mini-player/PiP rows PLY-07/20/21/29; the rest "not exercised, emulator overloaded") |

The `Verified on build` column in `docs/FEATURE_MATRIX.md` carries the per-row evidence.

## 2. Screen catalogue

See `docs/screens/index.md`. Delivered: tablet landscape light (about 20 screens) and dark (about 22, incl. all six channel tabs), 4 tablet portrait shots, 50+ feature evidence shots in `docs/screens/qa/`. NOT delivered: the phone set (`docs/screens/phone/` is empty), most of the portrait set, dark variants of several screens, post detail, mini-player shots.

## 3. Scenario checks

| Scenario | Result |
|---|---|
| Rotation on tablet (watch) | PARTIAL PASS. Landscape to portrait: playback continued (media session PLAYING/BUFFERING, position advanced), layout changed to a single scrolling column with comments teaser and Up-next grid (`docs/screens/tablet-portrait/watch-top-light.png`). In landscape the two columns scroll independently (`docs/screens/tablet/watch-scrolled-dark.png` vs `watch-related-scrolled-dark.png`). Rotation of Home/Search/Channel (rail <-> bottom bar): NOT checked on 476751d. Portrait Home does show the bottom bar, landscape the rail (`tablet-portrait/home-light.png`, `tablet/home-light.png`). |
| Airplane mode / offline | PASS with one defect (D3). Home: stale feed kept plus banner "Couldn't refresh. Try again." with Retry (`qa/offline-home.png`). Search: "Something went wrong / Please try again." with Retry (`qa/offline-search.png`). Watch: "Could not play this video" dialog with Retry / Copy all / Close (`qa/offline-watch.png`); after reconnecting, dialog Retry restored playback. Airplane mode was disabled afterwards. Home/Search Retry after reconnect was not pressed. |
| Empty lists | PASS. History "No videos / Videos you watch will show up here." (`qa/history-empty.png`), Playlists "No playlists / Playlists you create will show up here." (`qa/playlists-light.png`), Downloads "No downloads yet / Download from a video page, or share a video link to MinTube" (`qa/downloads-empty.png`). |
| Font scale 2.0 | NOT RUN. (font_scale left at 1.0.) |
| Private browsing toggle | PASS. Eye-slash badge appears beside the screen title (Settings, General, Playback, Notifications, Advanced) (`qa/ACC-06.png`); removed when switched off. Not checked on Home. |
| Mini-player (fixed corner, no drag, X, tap restores) | NOT VERIFIED. On eb710bc the transitional mini chrome (queue icon, X, expand) was seen but not exercised; per coordinator instruction the old bottom-left behaviour is recorded as PENDING. On 476751d the emulator hung (frozen frames, ANR on leaving the watch page). |
| PiP on leaving the app | NOT VERIFIED (same reason, behaviour changed in 476751d). |
| Seek-bar gradient and inset | PARTIAL PASS. Seek bar track shows a blue-to-pink gradient fill with a buffered segment and the Chapters chip sitting above it, inset from the screen edge (`qa/PLY-settings.png`, `qa/PLY-06.png`). Not measured in dp. |
| Glow behind player | PARTIAL. Soft coloured glow under the player visible in several watch shots (`qa/PLY-settings.png`, `tablet/watch-top-dark.png`); the Settings switch "Glow behind player" exists and is on (`tablet/settings-playback-dark.png`); turning it off and re-checking was not done. |
| No ads in two videos | PASS (observed 5 videos: Me at the zoo, Rick Astley, Big Buck Bunny, Snow Bear, a Vietnamese video; no ad UI, no pre-roll). |
| Language switch to Vietnamese | NOT RUN. Language row exists ("System default") in Settings > General. |

### Known issues re-check (`tools/qa/known-issues.md`, on eb710bc)

1. Empty avatar circles on tablet Home: FIXED. Avatars load (`tablet/home-light.png`); initial letter placeholders are shown briefly while images load.
2. Phone watch grey pill over thumbnail: NOT CHECKED (no phone run).
3. Sticky chip row translucency on phone: NOT CHECKED.
4. Home error on first tablet load: NOT REPRODUCED (first load fine; cold launch only slow).
5. Mini-player touch targets < 246 dp: NOT CHECKED.
6. Rail frame-late: NOT CHECKED. Sheets confined to the content area, rail visible beside the dimmed sheet in landscape: STILL PRESENT (docs/screens/tablet/download-options-light.png).
7. Home does not PiP from non-watch screens: superseded by 476751d; not verified.
8. Dash artifact under now-playing strip: NOT CHECKED.
9. Mini-player sits ~68 dp above bottom with rail: superseded by 476751d; not verified.
10. "Search YouTube" placeholder: STILL PRESENT on eb710bc (`qa/SRCH-01a.png`); 476751d changes it, not re-verified.
11. DiagnosticSaveAndroidTest SAF failures: NOT RUN (instrumentation tests are out of scope here).

## 4. Defects

Severity key: blocker = app unusable; major = core feature broken or crash; minor = cosmetic/edge.

| # | Sev | Title | Steps | Expected | Actual | Evidence |
|---|---|---|---|---|---|---|
| D1 | major | Search suggestions never appear | Home > search icon, type "how to" or "tay du", wait 5 s | Server suggestion rows under the field (endpoint `suggestqueries.google.com` answers from the host) | Only local history rows show; no remote suggestions. Panel is blank when there is no history match. | `qa/SRCH-01.png` |
| D2 | major (possibly environmental) | ANR / black screen when leaving the Watch page | Open a video, press Back. Also: cold start via a youtube.com deep link. | Watch closes or minimizes smoothly | "MinTube isn't responding" (Input dispatching timed out: no focused window) attributed to `WatchActivity`; screen black. Seen 3 times (01:24 first launch, 02:19 deep link, 02:57 Back from watch on 476751d). Host load was 15-23, so this may be amplified by the environment, but the "no focused window" pattern points at the activity transition. | `qa/DEF-anr.png`, `qa/DEF-anr-back-from-watch.png` |
| D3 | major (inconclusive) | Watch details pane stays on "Something went wrong" | Open `https://www.youtube.com/watch?v=aqz-KE-bpKQ` offline, reconnect, press page Retry repeatedly; also fresh launch of the same id | Details (title, actions, Up next) load after Retry | Retry shows skeletons then returns to the error 3 times. Playback itself recovered via the dialog Retry. The same id also failed on a clean relaunch, while other ids loaded, so this may be video- or rate-specific rather than offline recovery. | `qa/offline-watch-retry2.png`, `qa/DEF-watch-retry.png` |
| D4 | minor | History "pause" has no visible state | History > top-right menu > Pause watch history | Paused indicator and only "Resume" offered | No indicator on the screen; menu keeps offering both Pause and Resume. | `qa/LIB-06.png` |
| D5 | minor | Clear-history dialog says "on this account" while on device only | History > menu > Clear watch history (anonymous) | Wording about this device | "Your watch history will be cleared on this account." | `qa/history-confirm.png` |
| D6 | minor | Channel page on tablet landscape leaves little room for content | Open any channel in landscape | Tabs and content readable | Banner/header takes about two thirds of the height; videos row is cut at the bottom edge. | `tablet/channel-home-dark.png` |
| D7 | minor | Player controls are not exposed to accessibility | uiautomator dump while controls are shown | Buttons carry labels | Play/pause, prev/next, CC, gear, fullscreen are absent from the accessibility tree (Compose canvas without semantics). | n/a |
| D8 | minor | Diagnostics heading still says "Litube" | Trigger a playback failure (offline) | MinTube naming | Report text starts "Litube playback report" | `qa/offline-watch.png` |
| D9 | minor | Download options sheet hides most options below the fold | Watch > Download | Options visible or clearly scrollable | Sheet opens with Video/Audio + quality + Download only; audio track, subtitles, size, cover, file name, attachments-only require swiping the sheet up and expanding "More options". Works, but poor discoverability. | `qa/DL-01.png`, `qa/DL-01c.png` |

No blocker found, but the ANR (D2) would be a blocker if it reproduces on a normal device.

Other observations: the in-app mini transition and first Back from a Watch page opened from a deep link leaves the app entirely (expected, no parent screen). Update check in About gave no visible result within 8 s (not counted as a defect).

## 5. What could not be verified, and why

- Everything marked NOT VERIFIABLE needing sign-in: subscriptions, account history/watch later/liked, multi-account, comment post, like/dislike toggles, subscribe, cast sign-in, ACC-03/05.
- Mini-player (all behaviours), PiP, the 476751d changes, Home/Search/Channel rotation rails: the emulator froze (static frames, ANR) after rebuilding; two emulators from other agents kept host load above 15.
- Phone-size catalogue and most tablet-portrait/dark shots: out of time for the same reason; `docs/screens/phone/` is empty.
- Font scale 2.0, Vietnamese language, glow switch off, SponsorBlock skip and DeArrow replacement effects, cast (no receiver), live chat, queue operations (reorder/shuffle/repeat), download pause/resume/retry, backup import/export, update check result.
- Seek/scrub/hold-2x/pinch/brightness/volume gestures beyond double-tap seek (+10 s arc seen).
- Dark mode for the player controls and sheets beyond the screens listed in `docs/screens/index.md`.

Recommendation: rerun the unfinished parts on a quiet host (single emulator), starting with the mini-player and PiP on 476751d, the ANR in D2, and the phone catalogue.


## Pass 2

Tester: independent QA agent, pass 2. Build: master cabf459 (`:app:assembleDebug` via gbuild, 22 s), `com.nevminhnh.mintube.debug`, AVD `mintube_phone` (1080x2400, 420 dpi, API 36 swiftshader), anonymous, live network. One emulator of mine at a time; it was killed after the phone part. `tasklist | grep qemu` afterwards still lists processes, but they belong to other agents (see below).

IMPORTANT CAVEAT. The TABLET part was NOT run. When I finished the phone part and killed my emulator at 09:26, another agent was running instrumented tests (worktree `failing`, logs `fail-*AndroidTest`) on `mintube_tablet` (port 5556), and from about 09:32 `mintube_phone` (5554) was also booted by someone else, with `minmusic_phone` (5600) running. I waited about 18 minutes for the AVDs to free up; they did not. Starting a second instance of an in-use AVD, or killing another agent's emulator, was not mine to do, so I stopped. The host load average also reached 14.9 during my phone run while those started (one ANR, see N1).

### 1. Counts (matrix 140 rows, whole-matrix state after pass 2)

| Verdict | Pass 1 | Pass 2 |
|---|---|---|
| PASS | 54 | 59 (+SRCH-01, LIB-06 from FAIL; +PLY-07, PLY-20, SET-03 from NOT VERIFIABLE) |
| PASS (partial) | 36 | 38 (+PLY-21, PLY-29 from NOT VERIFIABLE) |
| FAIL | 2 | 0 |
| NOT VERIFIABLE | 48 | 43 |

14 rows were re-verified and annotated `pass2:` in `docs/FEATURE_MATRIX.md` (12 PASS, 2 partial, 0 FAIL): NAV-01/02/04/05, SRCH-01, LIB-04, LIB-06, DL-01, PLY-07, PLY-20, PLY-21, PLY-29, SET-03, CH-01.

### 2. Scenario results (phone)

| Item | Result |
|---|---|
| Mini-player (phone) | PASS. Swipe down from watch leaves a mini at the bottom-right, above the bottom bar, on Home, over the Search overlay (it lifts above the keyboard), and on You (`phone/mini-over-home-*.png`). A drag across it does not move it. First tap shows controls (queue, expand, X, prev/pause/next); X closes it and pauses playback (position stopped); expand reopens the watch page. The Subscriptions tab is absent signed-out. Tablet landscape: NOT RUN. |
| PiP | PASS. Home key with the mini playing from Home, from You, from the Search overlay, and from the watch page opens a PiP window each time. It can be dragged, and Expand restores the screen it came from (HomeActivity, YouActivity, Search, WatchActivity). |
| Glow | PASS. Settings > Playback > "Glow behind player" off: the area around the player is the plain surface colour (250,250,251). On again: the tint returns (for example 243,246,237 at y=60; strongly pink on a warmer video). `phone/watch-glow-off-light.png` vs `watch-glow-on-light.png`. The effect is subtle on grey or green videos. |
| Catalogue | Light and dark delivered for all requested phone screens except Subscriptions (not reachable signed-out). See `docs/screens/index.md`. |
| Font scale 2.0 | Shot into `phone/font2/`, reset to 1.0. Home, Watch, You, Settings, General, download sheet and channel header are usable. Observations: labels wrap to two lines ("Watch later", "Download quality") but nothing is cut; titles ellipsize; "Local history and playl..." on You ellipsizes; the action row (Like/Dislike/Share/Save/Download) scrolls sideways with Download half hidden until swiped. The You tile did NOT break as "Download/s" (it shows "Downloads"). The mini-player covers the Settings and About rows on You at 2.0 (N3). |
| Vietnamese | Language dialog has 48 entries; Tiếng Việt applies immediately; all 764 string keys exist in `values-b+vi`. Shots in `phone/vi/`. English left over: see N2. Reset to System default afterwards. |
| Tablet rotation (Home, Search, Channel) | NOT RUN (AVD busy). |

### 3. Pass-1 follow-ups

| Item | Result |
|---|---|
| D1 suggestions | FIXED. "how to" lists tie a tie / draw / make slime / paper airplane and more, with "use suggestion" arrows (`phone/search-panel-*.png`). The field hint reads "Search". |
| D4 history pause | FIXED. Banner "Watch history is paused. Videos you watch on this device are not saved."; the menu then offers Resume (no Pause) plus Clear. |
| D5 clear wording | FIXED. "Your watch history will be cleared on this device. This can't be undone." |
| D6 compact channel header, tablet landscape | NOT RUN (tablet). The phone header at 1.0 and 2.0 is fine. |
| D9 download sheet opens tall | FIXED. Sheet top at y=600 of 2400; Video/Audio, quality, audio track, subtitles, size, More options and Download are all visible without dragging. The option values show skeletons ("Loading download options...") for 8-20 s on this emulator (N6). |
| D2 ANR on Back / deep link | NOT REPRODUCED. 3 cold deep-link starts (`youtube.com/watch?v=aqz-KE-bpKQ`, `am start -W` 4.8-5.0 s, watch page up after 12 s, Back leaves the app cleanly) and 3 Home > watch > Back cycles: no ANR (`am_anr` count 0, no "not responding" window). |

### 4. New defects

| # | Sev | Title | Steps | Expected | Actual | Evidence |
|---|---|---|---|---|---|---|
| N1 | minor (inconclusive, possibly environmental) | ANR while scrolling the Language dialog | A 1078p video playing in the background (about 150 % CPU in the app, PLAYING/BUFFERING state flapping), Settings > General > Language, scroll the dialog with swipes | Dialog scrolls | "MinTube isn't responding" in SettingsActivity (input dispatching timed out, 5 s, MOVE at 540,1300) at 09:14 with host load 14.9 and other emulators booting. Not reproduced on retry with the app freshly started (language picked without any ANR). | `qa/DEF-anr-language-dialog.png`, `am_anr` event 09:14:38 |
| N2 | minor | English left over in the Vietnamese UI | Set Tiếng Việt; open You, History, Home, Watch | Local strings translated | Hard-coded English in the data layer: You header "On this device" / "Local history and playlists" (`MediaLibraryRepositories.kt:28`), History section header "On this device" (`PlaybackWatchReporting.kt:233`, `MediaSaveRepository.kt:181`), playlist summary "On this device" (`LocalLibraryStore.kt:157`), and `ui_playlist_owner` ("%1$s - Playlist") identical to English. Server-supplied strings "1.5M views", "5 days ago", "Nov 15, 2026", "7 hours ago" in feed, watch and comments are English too. | `phone/vi/you-light.png`, `phone/vi/watch-top-light.png`, `phone/vi/comments-light.png` |
| N3 | minor | Mini-player covers list rows at large font | Font 2.0, play a video, minimize, open You | Rows reachable | The mini (about 495x275 px) sits over the right half of the Settings and About rows on You; tapping the right half of a row hits the mini (shows its controls) instead. Rows work when tapped on the left half. | `phone/font2/you-light.png` |
| N4 | minor (test pollution) | Downloads list shows ghost items | Open Downloads | Empty or real items | Rows "death running / Downloading", "death paused / Paused", "Font clip / Downloading", "MediaStore clip ... / File not found" that never finish. They come from earlier instrumented-test runs in the AVD data, not from this session, but a stuck "Downloading" row cannot be cleared except through its menu. | `phone/downloads-light.png`, `phone/downloads-dark.png` |
| N5 | minor (inconclusive) | One video failed to start | Dark pass: first Home item (THVL Bolero, id eGfARtPQmSQ) | Plays | Dialog "Could not play this video", Phase: prepare, network validated. Seen once while 3 emulators ran on the host; the next 20+ videos played. The report text now begins "MinTube playback report" (D8 fixed). | not kept (overwritten) |
| N6 | minor | Slow download options | Watch > Download | Options within a few seconds | "Loading download options..." with skeleton rows for 8-22 s before 1078p / Default / Off / 1.50 GB appear (host load was high). | `phone/download-sheet-light.png` (loaded) |
| N7 | cosmetic | Focus highlight on the app title | Cold start Home (light), first frame | No highlight | A rounded grey box surrounds the "MinTube" logo and title for a moment after returning to Home; gone later. | `phone/home-light.png` (top left) |

No blocker and no major defect found in pass 2.

### 5. Still unverifiable, and why

- Sign-in needed (unchanged): NAV-03, SUB-01/02/04/05/06, WATCH-03/14/15, SHORT-03/04, LIB-01/08/10, ACC-03/05.
- AVD contention (other agents held 5554, 5556 and 5600, see the caveat): everything tablet-related, namely mini-player and PiP on tablet landscape, rail/bottom-bar rotation on Home/Search/Channel, D6 compact channel header, tablet shots.
- Rows from the overloaded-host list that I did not reach before losing the emulator, so they stay NOT VERIFIABLE: SRCH-05, WATCH-06, WATCH-18, PLY-02/05/08/13/16/17/18/22/23/26, SHORT-05, CH-12, PL-01/02/03/04/07/08/13, DL-02/04/11, LIT-02/08. Priorities for a pass 3 on a free host: queue (PLY-16/17/18), playlist screens (PL-*), download pause/resume (DL-04), gestures (PLY-02/05/08/26).
- Cast (no receiver on an emulator), live chat, SponsorBlock timeline effects, screen-off background audio.
