# MinTube UI plan

Status: Step 0 output. Source facts come from `docs/_survey/*.md`, `docs/ARCHITECTURE.md` and
`docs/FEATURE_MATRIX.md`. This file holds the decisions: the new design system, the screen list,
and how the work is split across agents.

## 1. Ground rules (apply to every agent)

1. **Backend is frozen.** No logic edits in `data`, `domain`, `extractor`, `player/engine` (and every
   non-`surface` player package), `downloader/{core,data,engine,io,net,resolve}`, `cast`, `core`,
   `diagnostics`, `di`. The ViewModel <-> domain contract stays as is. New UI state lives in the
   presentation layer. A change that seems to need a domain edit is escalated to the coordinator,
   who asks the owner.
2. **Keep the Activity-per-screen model.** There is no NavHost and none is introduced. Tabs stay
   separate Activities brought to front (`MainTabs.intent`), which is what keeps per-tab scroll
   state. Activities keep their wiring (ViewModel factories, fixture branches, navigation-request
   `when` blocks, share/download/voice side effects); only the composables they host are redesigned.
3. **One player.** `PlayerViewModel` and `PlaybackEngine` are singletons. No second player, no second
   `SurfaceView`. The watch page keeps the `watch-player-slot` and keeps reporting its bounds; the
   mini-player dock height and the bottom-bar height change together or not at all.
4. **CONTRIBUTING.md rules that UI code can break** (must not): moving a load or retry into a
   composable; swallowing `CancellationException` (e.g. `runCatching` around suspend calls); adding
   a UI-level retry around an account write; dropping the `feedbackGeneration == sessionGeneration`
   checks on menu rows; publishing results without the captured identity/revision.
5. **Test tags are a contract.** 1,400+ `onNodeWithTag` uses. Keep every existing tag string and the
   `<tag>-navigation` / `<tag>-nav-<Tab>` formats. A tag may move to a new node only if the node
   keeps the same meaning. New UI gets new tags.
6. **No test is deleted or disabled.** Unit-test count never drops below the Step 0 baseline.
7. **No fake features.** A feature marked `MISSING` in `FEATURE_MATRIX.md` gets no UI.
8. **Strings.** `NoticeText` maps English literals coming from ViewModels, so backend message text
   is never reworded. `TranslationResourcesTest` requires every key in all 91 locale directories:
   a new key is added to `values/` (English), `values-b+vi/` (real Vietnamese) and to the other 89
   directories with the English text verbatim. No machine translation.
9. **No YouTube identity.** No YouTube logo, name-as-brand, or red `#FF0000`/`#FF0033` as accent.

## 2. Design system "Mint"

Identity in one line: a calm, content-first video app; mint-green accent, soft ink neutrals,
generous rounding, quiet motion. Familiar layout, own face.

### 2.1 Color tokens

Single source of truth: `ui/design/AppPalette.kt`, mirrored into the Material3 `ColorScheme` in
`ui/theme` so the 373 `DesignTokens` uses and the 161 `MaterialTheme.colorScheme` uses resolve to
the same values. XML `colors.xml` / `themes.xml` (starting window, framework dialogs) follow.

| Token | Light | Dark | Use |
|---|---|---|---|
| Background | `#FAFBFA` | `#0D1110` | page |
| Panel | `#FFFFFF` | `#141918` | bars, sheets, cards |
| Surface | `#EEF2F0` | `#1D2423` | chips, fields, skeleton base |
| Ghost | `#E2E8E5` | `#27302E` | selected row, skeleton highlight |
| Ink | `#101514` | `#EDF2F0` | primary text, icons |
| Secondary | `#56615E` | `#A3AFAB` | metadata text |
| Border | Ink 10% | Ink 12% | hairlines |
| Accent (new; replaces `Red`) | `#0B8F6A` | `#4FD8AE` | selected tab, primary button, progress, links to action |
| OnAccent | `#FFFFFF` | `#06231A` | text on accent |
| AccentContainer | `#D6F3E8` | `#123A2E` | selected chip, tonal button |
| Danger | `#C4321F` | `#FF8A75` | errors, destructive actions, live badge |
| Link | `#0B62C4` | `#7DB8FF` | in-text links |
| Scrim / MenuScrim / StateLayer | unchanged ratios | | |

Contrast targets: body text >= 4.5:1, large text and icons >= 3:1, checked for both themes
(Accent on Panel light = 4.0:1, so Accent is used for fills, icons and >= 18sp text; small accent
text uses `#087456`). The token name `Red` is kept as a deprecated alias during migration so
feature branches compile, then removed in integration.

**Material You:** optional. A "Use wallpaper colors" switch (Android 12+) maps the dynamic scheme
onto Accent/AccentContainer only; neutrals stay ours. Off by default. It ships only if the flag
can be stored through the existing preference store without touching backend logic; otherwise it
is listed as not done.

Player and Shorts surfaces stay on a fixed dark palette (white on black with scrims) in both themes.

### 2.2 Typography

- Display / titles: **Be Vietnam Pro** SemiBold (OFL, full Vietnamese coverage; scripts it lacks
  fall back to the system font). Body and metadata: bundled **Roboto**.
- One named scale replaces the per-call `AppText(size, line)` literals:

| Style | Size / line (sp) | Weight | Use |
|---|---|---|---|
| Display | 24 / 30 | SemiBold | screen titles, empty-state headline |
| Title | 18 / 24 | SemiBold | section headers, watch title |
| Subtitle | 16 / 22 | Medium | card title (large), row title |
| Body | 14 / 20 | Regular | default text, card title (compact) |
| Label | 13 / 18 | Medium | buttons, chips |
| Caption | 12 / 16 | Regular | metadata, timestamps |
| Micro | 11 / 14 | Medium | nav labels, badges |

All sizes are `sp` and follow the system font scale. The brand wordmark no longer pins
`fontScale = 1`.

### 2.3 Spacing, shape, elevation

- Spacing scale (dp): 2, 4, 8, 12, 16, 20, 24, 32, 48. Page inset 16 (24 from 600dp width).
- Radius (dp): 8 (chips inside cards, badges), 12 (fields, small cards), 16 (thumbnails, cards),
  24 (sheets top, dialogs), full (pills, avatars, FAB-like buttons).
- Elevation: flat pages; hairline `Border` between bars and content; shadow only on floating
  layers (sheets, menus, mini-player, transient message).
- Touch targets: 48dp minimum everywhere, including chips (32dp visual, 48dp hit area) and
  overflow buttons on cards.

### 2.4 Motion

Keep `AppMotion` structure and the animator-scale handling. Values: content 160ms, window 260ms,
expand 200ms / collapse 120ms, emphasized easing unchanged. Shared-element style handoff from card
thumbnail to player (`WatchTransition`) is untouched. No motion when the system animator scale is 0.

### 2.5 Icons

`AppIcon` keeps its enum names (63 entries, used across the app) but every path is redrawn from
**Material Symbols Rounded** (Apache-2.0, license file already bundled), outline for idle and
filled for selected. This also removes glyphs that look traced from YouTube's set. XML player
drawables (`ic_player_*` etc.) are replaced the same way, keeping file names.

App icon: mint rounded square, white play triangle whose left edge forms an "M" notch; adaptive
icon (foreground, background, monochrome layer for themed icons), plus Play-store PNG and the
in-app mark (`LitubeMark` -> `MinTubeMark`, description string updated).

### 2.6 Shared components (Phase 1 delivers these; Phase 2 only consumes them)

| Component | Notes |
|---|---|
| `MinNavigationBar` | Replaces the four hand-copied bars. Home, Shorts, Subscriptions, You. 56dp tall (matches the mini-player dock literal), pill indicator in AccentContainer behind the selected icon, filled icon + Accent label when selected, `selected` semantics on all tabs. Keeps `<tag>-navigation` and `<tag>-nav-<Tab>` tags. Real account avatar on You (no `sample_bunny`). From 600dp width in landscape: navigation rail on the start edge. |
| `MinTopBar` | 56dp, wordmark or title, trailing actions (search, cast when available, account). Collapses on scroll via the existing `CollapsingAppBar`. |
| `VideoCard` / `VideoRow` / `ShortCard` / `ChannelRow` / `PlaylistCard` | One card family for all feeds: 16dp-radius thumbnail, duration badge, title (2 lines), metadata line, 48dp overflow. Registers artwork bounds with `WatchTransition` exactly as today. Grid: 1 column < 600dp, 2 columns 600-839dp, 3 columns from 840dp. |
| `FilterChips` | Scrollable row, selected = AccentContainer. |
| `Skeleton*` | Shimmer placeholders matching each card type. |
| `StateMessage` | Empty and error states: icon, headline, one line of help, primary action (Retry / Sign in / Browse). Offline has its own variant. |
| `MinButton` | Four kinds only: primary (Accent fill), tonal (AccentContainer), outline, text. Plus `SubscribeButton` restyled on top of it. |
| `MinSheet` / `MenuBottomSheet` | 24dp top radius, drag handle, max width 480dp centered on wide windows. Signature of `MenuBottomSheet` unchanged. |
| `MinDialog`, `MinSwitch`, `MinSlider`, `MinCheckbox`, `MinRadio`, `MinProgress` | Restyle of the existing `App*` wrappers in place (names kept to avoid churn). |
| `SearchPanel` | Restyled; history, suggestions and voice entry as today. |
| `TransientMessage` | Restyled snackbar-like pill above the bottom bar / mini-player. |
| `PrivateBrowsingBadge` | New, UI only: shown in the top bar and on the You tab while private browsing is on (state already exposed by the backend; verified in `FEATURE_MATRIX.md`). |

Adaptive layout helper: one `WindowClass` (compact < 600, medium 600-839, expanded >= 840) computed
from window width, replacing the scattered 528/600/960 literals in presentation code. Layout
constants that live in `domain/` files and are referenced by tests stay untouched.

## 3. Screens

Every screen keeps its Activity and ViewModel (see the screen table in `ARCHITECTURE.md`).
Each screen is delivered with: loading skeleton, empty state, error state with retry, light and
dark, compact / medium / expanded width, landscape, TalkBack labels, font scale 2.0 without clipping.

| # | Screen | Owner | Redesign notes |
|---|---|---|---|
| 1 | Home | A | New top bar, chips, card grid, shared bottom bar |
| 2 | Explore | A | Category header + same card family |
| 3 | Search (panel, results, filters, voice) | A | "All" tab mixes videos, channels, playlists, Shorts if the data layer returns them (matrix row decides) |
| 4 | Watch page | B | Player slot, title/actions row (like, dislike, share, save, download as tonal pills), channel row, expandable description, comments teaser, related list; two-pane (player + related) from 840dp |
| 5 | Player surface (controls, gestures, sheets: quality, speed, captions, audio, chapters, queue, more) | B | Restyle only inside `player/surface`; gesture handling code path unchanged; cast button promoted to the top row of controls when a route is available |
| 6 | Mini-player, PiP chrome | B | 56dp dock, progress line in Accent |
| 7 | Comments, replies, composer | B | Sheet on phone, side pane on expanded |
| 8 | Live chat | B | Same pattern as comments |
| 9 | Shorts | C | Full-bleed vertical pager, action rail in thumb zone, own dark chrome |
| 10 | Channel (header, tabs: Videos, Shorts, Playlists, Posts, About) | D | Banner + avatar header, scrollable tabs |
| 11 | Community post detail | D | Post card, images, comments |
| 12 | Subscriptions | D | Channel strip + feed; signed-out state with sign-in action |
| 13 | You / library | E | Account header, history shelf, playlists shelf, entries for Watch later, Liked, Downloads, Settings |
| 14 | History | E | List with remove; pause/resume shows current state only if the backend exposes it |
| 15 | Playlists list, Playlist detail | E | Header with Play all / Shuffle as visible buttons |
| 16 | Save-to-playlist sheet | E | |
| 17 | Downloads: options sheet, manager list, share-to-download | E | Moved from stock Material3 styling onto Mint tokens; `DownloadViewModel`, `DownloadUiMapper`, `DownloadPresentation` untouched |
| 18 | Account menu, sign-in (device code), account switcher, private browsing | F | Private-browsing indicator added |
| 19 | Settings (4 pages), Advanced, Backup/restore | F | Grouped list style; in-app mini-player switch surfaced if it is a pure UI addition |
| 20 | About | F | MinTube identity, fork notice, license, credits (MediaServiceCore, SmartTube, NewTube), diagnostics export |
| 21 | Cast device dialog, casting banner | F | |
| 22 | Share sheet entry, gallery | F | |
| 23 | Traffic verification card, notices | Phase 1 | Shared |

## 4. Rename and licensing (Phase 1)

- `applicationId` -> `com.nevminhnh.mintube`; Kotlin namespace stays `com.hhst.youtubelite`.
  Root project name -> `mintube`.
- `app_name` and the four other name-bearing keys in all locale files; `a11y_litube` text.
- Test and CI package ids (`DownloadAndroidHarness`, `WatchReferenceAndroidTest`, casting README,
  `android.yml`).
- About screen: source link and update check point at `nev-minhnh/MinTube`; a line states that
  MinTube is a fork of Litube 4.0.0 by HydeYYHH under GPL-3.0, with a link to the original.
- `LICENSE` untouched; all copyright notices kept. New README: fork statement, list of changes,
  credits kept, upstream download links and badges removed. `fastlane/metadata` updated.
- **Kept on purpose (backend identifiers):** the `Downloads/Litube` MediaStore folder, the
  `litube-settings` backup schema id and the cast User-Agent. Changing them means editing the
  download engine and breaking restore of old backups. Open question for the owner, see section 8.

## 5. Phases

**Phase 1 - foundation (one agent, sequential, on `master`):** tokens, typography, icons, shared
components of 2.6, `MinNavigationBar` wired into the four call sites, adaptive helper, rename,
app icon, README/fastlane/About, `.gitignore` (signing files; `docs/` un-ignored), CI package ids.
Gate: unit tests >= baseline, lint clean, `assembleDebug` + `assembleDebugAndroidTest` pass.

**Phase 2 - parallel, one git worktree and branch per agent under `worktrees/`:**

| Agent | Branch | Owns (may edit) |
|---|---|---|
| A | `ui/browse` | `home/presentation`, `explore`, `search/presentation` + their Activities' composable hosting, their androidTest packages |
| B | `ui/watch` | `watch/presentation`, `player/surface`, `comments/presentation`, live chat UI, their androidTest packages |
| C | `ui/shorts` | `shorts/presentation`, its androidTest package |
| D | `ui/channel` | `channel/presentation`, `post/presentation`, `subscriptions/presentation`, tests |
| E | `ui/library` | `library/presentation`, `playlist/presentation`, `saves`, `downloader/ui`, `gallery`, tests |
| F | `ui/account` | `account`, `settings/presentation`, `ui/about`, `ui/extension`, cast UI files in `player/surface` agreed with B, `share`, tests |

Rules: an agent edits only its packages. Shared code (`ui/design`, `ui/theme`, `ui/components`,
`res/values*/strings.xml`) is changed only by the coordinator: agents send a request (component,
reason, proposed signature); string additions are collected in a per-branch file
`docs/_strings/<branch>.tsv` (key, English, Vietnamese) and merged into the 91 locale files by the
coordinator during integration, which avoids six-way conflicts in `strings.xml`. Each agent builds
with its own Gradle project cache directory and runs the unit tests of its packages before handing over.

**Phase 3 - integration:** merge branches one at a time (C, D, A, E, F, B: smallest first, player
last), after each merge: `testDebugUnitTest`, `lintDebug`, `assembleDebug`,
`assembleDebugAndroidTest`. Then the offline device suite from `android.yml`.

**Phase 4 - independent QA (fresh agents that wrote no code):** walk every `WORKS` row of
`FEATURE_MATRIX.md` on the emulator with the real build and fill the `Verified on build` column;
consistency review across screens; rotation, offline, empty lists, account switch, font scale 2.0,
TalkBack labels; screenshots of every screen, light and dark, into `docs/screens/`. Defects go back
to the owning agent.

**Phase 5 - goldens, CI, release:** regenerate goldens deliberately in a dedicated commit (reason
recorded), signing key, release job, tag `v1.0.0` once CI is green on `master` and QA has signed
off the matrix; download the published APK, install and smoke-test it.

## 6. Tests

- Unit tests: all kept and passing; presentation-facing ones (about 45 files) keep working because
  UiState and action types do not change.
- Instrumented UI tests: updated by the owning agent in the same branch, keeping what each test
  checks. Tags preserved (rule 5).
- New flow tests (instrumented, fixture-backed, offline): open video, search, subscribe, add to
  playlist, comment, download, mini-player.
- Goldens: all will differ after the redesign. Re-recorded with the existing recorder
  (`-e record<X>Goldens true`, API 35, SwiftShader), reviewed by eye, committed separately.

## 7. Risks

| Risk | Handling |
|---|---|
| No screenshot test runs in CI, so nothing flags visual breakage automatically | Phase 4 screenshots + golden re-record are mandatory gates, not optional |
| `ContentActivity` mini overlay uses the second `AppTheme` without `LocalAppColors` | Phase 1 unifies the two `AppTheme`s so tokens resolve correctly inside the mini-player |
| Every `src/main` edit invalidates the Gradle configuration cache (source hash in BuildConfig) | Accept; per-worktree builds are sequenced to stay within RAM |
| Six agents editing `strings.xml` in 91 locales | Coordinator-only merge via `docs/_strings/*.tsv` |
| Emulator on this Windows machine needs a working hypervisor (WHPX) | Verified at the start of Phase 1; if unavailable, device tests run only in CI and Phase 4 uses CI artifacts plus a local software-rendered emulator |
| GitHub token lacks `workflow` scope | Owner runs `gh auth refresh -h github.com -s workflow` before the first push |

## 8. Open questions for the owner (defaults apply if not answered)

1. Download folder stays `Downloads/Litube` (default) or becomes `Downloads/MinTube` (needs an
   edit in the download engine, outside the allowed boundary).
2. Update check on the About screen: point at `nev-minhnh/MinTube` releases (default) or remove.
3. Accent color: mint `#0B8F6A` / `#4FD8AE` (default).
