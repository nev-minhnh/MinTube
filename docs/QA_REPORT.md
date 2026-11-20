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
