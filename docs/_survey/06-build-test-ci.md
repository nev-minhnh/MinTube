# 06 - Build, test and release infrastructure survey

Scope: Gradle, proguard, lint, source sets, unit/instrumented/golden tests, CI, fastlane, README, Windows gotchas.
Read-only survey; no gradle run. Evidence is repo-relative `path:line`. Git HEAD = `5fee29b Import Litube 4.0.0 source (HydeYYHH, GPL-3.0), unmodified`.

## 1. Build

### 1.1 Files and versions
- `settings.gradle.kts:29` `rootProject.name = "litube"`; `include(":app")` (single module).
- `build.gradle.kts` (root): only `apply false` plugin aliases (android.application, kotlin.android, kotlin.compose, ksp). No tasks.
- Toolchain (`gradle/libs.versions.toml`): AGP 8.10.1, Kotlin 2.2.20, KSP 2.2.20-2.0.4, Compose BOM 2025.06.01, Media3 1.10.1, Room 2.8.4, Koin 4.1.0, WorkManager 2.10.5, Coil 3.3.0 (`coil-core` only), OkHttp 4.12.0, coroutines 1.10.2, Turbine 1.2.1, LeakCanary 2.14. Gradle wrapper 8.11.1 (`gradle/wrapper/gradle-wrapper.properties`).
- `libs.versions.toml:83` `msc = { module = "com.github.HydeYYHH:MediaServiceCore", version.ref = "msc" }` (v1.0.0) and `upnpcast = com.github.yinnho:UPnPCast v1.3.0`, both from JitPack. This coordinate is a dependency, NOT to be renamed.
- `gradle.properties`: `org.gradle.jvmargs=-Xmx2048m`, parallel, build cache, **configuration-cache=true**, `org.gradle.offline=false` (comment: AGP 8.10.1 only cached as POM/module, so --offline fails), http timeouts 120 s, TLS 1.2/1.3, `android.useAndroidX`, `nonTransitiveRClass`, `android.injected.testOnly=false`.
- Java/Kotlin target 17 (`app/build.gradle.kts:84-86,172`), core library desugaring (`desugar_jdk_libs_nio` 2.1.5). CI builds with JDK 21 (temurin).

### 1.2 `app/build.gradle.kts` essentials
| Item | Value | Line |
|---|---|---|
| namespace | `com.hhst.youtubelite` (R / BuildConfig package, manifest class names) | 28 |
| applicationId | `com.hhst.litube` | 32 |
| compileSdk / target / min | 36 / 36 / 24 | 29,33,34 |
| versionCode / versionName | 400 / "4.0.0" | 35,36 |
| BuildConfig fields | `DIAGNOSTIC_BUILD_ID` (SHA-256 of build inputs), `DIAGNOSTIC_DEPENDENCIES` | 37-38 |
| testInstrumentationRunner | `com.hhst.youtubelite.ContentTestRunner` | 39 |
| localeFilters | en, zh, zh-rTW + ~44 others (47 total) "aligned with AppLocale.OPTIONS and locales_config.xml" | 43-50 |
| lint | `abortOnError = true`, `MissingTranslation` downgraded to warning | 100-103 |
| unit tests | `isReturnDefaultValues = true`, maxHeapSize 256m, maxParallelForks 1; `animationsDisabled = true` | 105-114 |
| dependenciesInfo | not included in APK/AAB | 116-119 |
| packaging | excludes `/META-INF/{AL2.0,LGPL2.1}` | 94-98 |

### 1.3 Build types
| Type | Definition | applicationId | versionName | Purpose |
|---|---|---|---|---|
| `release` (L53-60) | minify + shrinkResources on; proguard-android-optimize + `proguard-rules.pro`; **no signingConfig** | `com.hhst.litube` | `4.0.0` | Distribution build. Output is unsigned. |
| `debug` (L61-65) | no minify; `applicationIdSuffix=".debug"`, `versionNameSuffix="-debug"` | `com.hhst.litube.debug` | `4.0.0-debug` | Day-to-day + the offline instrumented suite. Adds debug-only activities and LeakCanary. |
| `minifiedDebug` (L66-73) | `initWith(release)`, adds `proguard-r8-verification.pro`, `signingConfig = debug`, suffix `.minified`, `-minified`, `matchingFallbacks=release` | `com.hhst.litube.minified` | `4.0.0-minified` | R8/release-like verification of reflection/serialization keep rules (SponsorBlock, storage snapshots, settings backup). Installable, debug-signed. |
No other build types, no product flavors, no `signingConfigs {}` block anywhere (grep: `signingConfig` only at L69 = `getByName("debug")`).

### 1.4 Signing today
- Release: NOT signed. `README.md:82` says: unsigned APK at `app/build/outputs/apk/release/app-release-unsigned.apk`, "sign it with your release key". No keystore, no `signingConfigs`, no properties read, no secrets used in CI. `.gitignore` covers `*.apk`, `local.properties`, `release/`, `app/release/`.
- Debug and minifiedDebug: default Android debug keystore (`~/.android/debug.keystore`).

### 1.5 `testBuildType` switch (L76-81)
`testBuildType = -PandroidTestBuildType (default "debug")`. When `minifiedDebug`: runner becomes `com.hhst.youtubelite.player.sponsor.R8VerificationRunner` (plain `Instrumentation`, JUnitCore, `-e class a,b`) and the `androidTest` source dir is REPLACED by `src/r8AndroidTest/java` (so none of `src/androidTest` is compiled in that mode; `androidTest` assets are not touched by `setSrcDirs` of java only, they remain).

### 1.6 Custom Gradle tasks (`app/build.gradle.kts`)
- Config-time digest (L17-25): SHA-256 over every file under `src/main`, `build.gradle.kts`, both proguard files, `libs.versions.toml`, `settings.gradle.kts` -> `diagnosticBuildId` (first 12 bytes hex = 24 chars) -> BuildConfig. **Consequence: any edit to any `src/main` file (every UI change) changes the BuildConfig constant, invalidates the configuration cache and forces a recompile of BuildConfig-dependent code.** Also line-ending sensitive (see 9).
- `pruneDiagnosticArchives` (L124-154): keeps `-PdiagnosticArchiveLimit` (default 5, 1..100) dirs of 24-hex names under `build/outputs/diagnostic-mappings`.
- `archiveReleaseDiagnostics`, `archiveMinifiedDebugDiagnostics` (L155-168, `Copy` tasks): `dependsOn minify<V>WithR8`, copy `outputs/mapping/<variant>` into `outputs/diagnostic-mappings/<buildId>[/minifiedDebug]` + `build-id.txt`; wired as `finalizedBy` of `assembleRelease` / `assembleMinifiedDebug`. So `:app:assembleRelease` always also archives the mapping.

### 1.7 Repositories
- `settings.gradle.kts:1-15` pluginManagement: google (filtered to com.android*, com.google*, androidx*), mavenCentral, gradlePluginPortal, then **aliyun** `google`, `public`, `gradle-plugin`.
- `settings.gradle.kts:17-27` dependencyResolutionManagement `FAIL_ON_PROJECT_REPOS`: google, mavenCentral, **aliyun google + public**, `https://jitpack.io` (unfiltered). Aliyun entries come after the primary repos (fallback only); jitpack is required for MSC and UPnPCast.

### 1.8 Source sets
- `app/src/main` (production).
- `app/src/debug`: `AndroidManifest.xml` + 4 classes: `debug/reference/LayoutReferenceActivity.kt` (exported, `:layout_reference` process), `debug/reference/PageReferenceActivity.kt`, `extractor/ExtractionTestActivity.kt`, `player/surface/PlayerTestActivity.kt`. Used by androidTest (hence the R8 mode drops androidTest).
- `app/src/minifiedDebug/java/.../core/StorageVerification.kt` (`@Keep object`, exercised by `r8AndroidTest/.../StorageMinifiedAndroidTest.kt`); also `debugImplementation`-style dep `minifiedDebugImplementation(compose.ui.test.manifest)` (L217).
- `app/src/r8AndroidTest/java`: `StorageMinifiedAndroidTest.kt`, `player/sponsor/R8VerificationRunner.java`, `SponsorBlockMinifiedAndroidTest.kt` (3 files).
- `app/src/androidTest` (283 code files + `assets/` + `tools/casting/*.py,README.md`), `app/src/test` (249 files).
- All debug/minified sources live in package `com.hhst.youtubelite.*` (unchanged by the rename).

### 1.9 Proguard / lint
- `app/proguard-rules.pro` (53 lines): `-dontwarn` koin/jsoup/re2j, keep `@JavascriptInterface` methods, Gson TypeToken, ~30 `-keep class com.hhst.youtubelite.<pkg>.<Model> { <init>(...); <fields>; }` for serialized models (player queue, extractor models, diagnostics, downloader config, backups, `LocalLibraryStore$Data`, `HistoryEntry`, `FeedItem$Video`) and 4 download worker classes. All keyed to the Kotlin package -> unaffected by the rename (no `litube` string).
- `app/proguard-r8-verification.pro` (8 lines): keep `kotlin.**` and a few SponsorBlock / SettingsBackup classes; applies only to `minifiedDebug`.
- `app/lint.xml`: only `<ignore path="../gradle/libs.versions.toml"/>` for `AndroidGradlePluginVersion`.
- `app/ic_launcher-playstore.png` at module root (not referenced by the build).

### 1.10 Strings keyed to `com.hhst.litube` / `litube` in build files
`app/build.gradle.kts:32` (applicationId) and `settings.gradle.kts:29` (rootProject.name). Nothing in proguard, lint.xml, libs.versions.toml (only `HydeYYHH` in the MSC coordinate).

## 2. Rename to MinTube (`com.nevminhnh.mintube`, keep `com.hhst.youtubelite`)

### 2.1 Mandatory (build breaks or wrong identity)
1. `app/build.gradle.kts:32` `applicationId = "com.nevminhnh.mintube"` (namespace at L28 stays). Resulting ids: `com.nevminhnh.mintube`, `.debug`, `.minified`. Optionally bump versionCode/Name (L35-36).
2. `settings.gradle.kts:29` `rootProject.name = "mintube"` (cosmetic; affects IDE/Gradle name only; CI uses `:app:` paths so no breakage).
3. `app/src/main/res/values/strings.xml:3` `app_name` -> "MinTube" **and the same key in 92 locale files** (`values-*/strings.xml` line 2-3; each currently the literal "Litube", e.g. `values-zh/strings.xml:3`). Used via `@string/app_name` in `AndroidManifest.xml:48` and `PlaybackService.kt`/`DesignTokens.kt`. The launcher label also feeds `AboutScreen(applicationInfo.loadLabel(...))` (`ui/about/AboutActivity.kt:84`).
4. `.github/workflows/android.yml:89,170,171,175`: `com.hhst.litube.debug` -> `com.nevminhnh.mintube.debug`, `com.hhst.litube.minified` -> `com.nevminhnh.mintube.minified` (4 lines). The instrumentation component `"$package.test/$runner"` derives from it; the runner class names stay `com.hhst.youtubelite.*`.
5. `app/src/androidTest/.../downloader/android/DownloadAndroidHarness.kt:140,149,166`: hardcoded `content://com.hhst.litube.debug.download.fileprovider/...`; `.../watch/WatchReferenceAndroidTest.kt:91`: `run-as com.hhst.litube.debug`. These assert/replay against the installed debug package and will fail at runtime (not compile time) if not updated.
6. `app/src/androidTest/tools/casting/README.md` 4 lines with `com.hhst.litube...` (docs for manual adb runs; lines 21,53,78,79 and mentions of "Litube" at 35).
7. `README.md:7` (F-Droid, commented out), `README.md:80` (applicationId statement). See 8.

### 2.2 Authorities (derived, nothing to edit except tests)
`AndroidManifest.xml:203` `${applicationId}.download.fileprovider`, `:213` `${applicationId}.androidx-startup`. Runtime code builds the same string from `packageName` + `".download.fileprovider"` (`downloader/io/DownloadPublisherImpl.kt:27,366,378`; `gallery/GalleryActivity.kt:142`; `ui/about/AboutActivity.kt:137`). So authorities follow the id automatically. Only the three hardcoded test URIs in item 5 are literal.

### 2.3 Brand strings NOT required to change for the build (decide deliberately)
- Resource `app_name` plus 4 more keys in `values/strings.xml`: `a11y_litube` (:412), `a11y_litube_home` (:413), `settings_about_title` "About Litube" (:435), `settings_open_system_settings_summary` (:462). The same 5 keys exist in 92 locale files -> 461 matching lines over 93 files. **`ui/TranslationResourcesTest.kt` requires every `values/strings.xml` key to exist non-blank in every locale dir, so renaming/removing a key forces editing all ~93 files** (adding keys requires `translatable="false"` or full coverage).
- Drawable `app/src/main/res/drawable/ic_litube_mark.xml` (+ Kotlin `LitubeMark`/`LitubeBrand` in `ui/design/DesignTokens.kt:228,233`; used by `HomeScreen.kt:294,374`, `SubscriptionsScreen.kt:190`, `YouScreen.kt:95`). In scope of the redesign.
- Launcher icons: `mipmap-*/ic_launcher*.png`, `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `mipmap-anydpi ic_launcher.xml`/`ic_launcher_round.xml`, `app/ic_launcher-playstore.png`.
- `res/xml/download_file_paths.xml:6` `<external-files-path path="Download/Litube/">` is coupled to `DownloadPublisherImpl.kt:375` (`File(getExternalFilesDir(DIRECTORY_DOWNLOADS), "Litube")`), `:301` (MediaStore `RELATIVE_PATH = Downloads/Litube`) and `:282` (`LIKE %Litube%` query). Backend folder name; if it is changed all four must change together (and `DownloadPublisherImplTest`). Recommend leaving, or change as one backend-owned task.

### 2.4 Data-compat identifiers that must NOT be renamed casually
- `SettingsBackup.SCHEMA = "litube-settings"` (`extension/SettingsBackup.kt:30`), file name `litube-settings.json` (`ui/extension/SettingsDataScreen.kt:170`), asserted by `test/.../extension/SettingsBackupTest.kt:107-124` and `r8AndroidTest/.../SponsorBlockMinifiedAndroidTest.kt:13`. Changing it breaks import of old backups.
- Media3 extras key `litube.diagnostic_context` (`diagnostics/PlaybackTelemetry.kt:20`, `player/datasource/MediaSourceResolver.kt:101`; `DiagnosticExportAndroidTest.kt:197`). Internal both ends; safe but backend.
- UI-less diagnostic report headers: "Litube cast report/action report/playback report/diagnostic report" (`cast/CastFailureReport.kt:15`, `diagnostics/ActionFailureReport.kt:39`, `PlaybackFailureReport.kt:25`, `DiagnosticArchive.kt:104`); `CastFailureDialogAndroidTest.kt:38` embeds one. User-Agent `Litube/1.0 UPnP/1.1` (`cast/DlnaTransport.kt:35`). Temp file `litube-diagnostics-*.zip` (`AppLog.kt:183`). `litube YouTube engine v1` (`extractor/ExtractionDiagnostics.kt:37`). Backend: leave.
- `GalleryActivity.kt:126` default save name `LiTube-<ms>.<ext>` (presentation-ish; user-visible file name).
- Upstream links: `ui/about/AboutActivity.kt:87` GitHub API `repos/HydeYYHH/litube/releases/latest` (self-update check: would compare against the UPSTREAM release tag; point it at the MinTube repo or disable), `:175` `SOURCE = "https://github.com/HydeYYHH/litube"` (About -> Source).
- Fixtures: "Litube Studio", "@litube", "Litube Maker" in main fixtures `library/data/FixtureLibraryRepository.kt:39`, `playlist/data/FixturePlaylistRepository.kt:34-35`, `library/YouActivity.kt:257`, `account/domain/AccountModels.kt:7` ("About Litube" menu label) and mirrored in tests (see 2.6).

### 2.5 Grep results (whole repo, `git grep -I`, excluding `docs/`; case-insensitive total of `litube` = 566 lines in 117 files)
| Pattern | Lines | Files | Where |
|---|---|---|---|
| `com.hhst.litube` (exact) | 15 | 6 | android.yml 4; README 2 (L7 commented F-Droid, L80); app/build.gradle.kts 1; DownloadAndroidHarness 3; WatchReferenceAndroidTest 1; androidTest/tools/casting/README.md 4 |
| `litube` (case-sensitive) | 466+ | 97 | README 16; android.yml 4; build.gradle.kts 1; settings.gradle.kts 1; androidTest .kt 14 files (account 4, cast 2, diagnostics 2, DownloadAndroidHarness 3, library 1, player 1, watch 1); tools/casting README 4; main .kt 17 files; **res: 461 lines over 93 files incl. `values/strings.xml` + `xml/download_file_paths.xml`**; fastlane `title.txt` is capital-L only |
| `Litube` | many | (see categories) | README 9; CONTRIBUTING 1; `.github/assets/litube-icon.svg` 2; main .kt 14 files; androidTest 8 files; tools/casting README 1; res values 5/locale |
| `LiTube` | 3 | 3 | `gallery/GalleryActivity.kt:126`; `androidTest/.../ui/LocalizationAndroidTest.kt:89`, `ResponsiveExperienceAndroidTest.kt:71` (`AboutScreen("LiTube", "3.0.0-devx", ...)` just a string arg) |
| `HydeYYHH` | 32 | 4 | README 15; CONTRIBUTING 1 (L99 MSC repo link); `libs.versions.toml:83`; `AboutActivity.kt:87,175` |
Category totals (lines matching `litube` case-insensitively): main Kotlin 32 lines / 23 files; main res 461 / 93; androidTest Kotlin 25 / 16; androidTest tools README 5; unit tests 9 / 4; workflow 8 (4 exact id + 4 others `litube` inside) ; README 23; CONTRIBUTING 1; gradle 2; fastlane 1; assets svg 2.
Files in repo whose NAME contains litube: `.github/assets/litube-icon.svg`, `app/src/main/res/drawable/ic_litube_mark.xml`. No `mintube` anywhere yet.

### 2.6 Tests that hard-code brand strings (update with the redesign)
- Unit: `test/.../library/LibraryViewModelTest.kt:511,552` ("Litube Studio" from fixture), `extension/SettingsBackupTest.kt` (schema, keep), `cast/protocol/CastV2ChannelCloseTest.kt:39` (`urn:x-cast:com.litube.test`, arbitrary), `downloader/regression/DownloaderRegressionTest.kt:344` (temp dir prefix).
- androidTest: `account/AccountSettingsAndroidTest.kt:63-94` (`hasText("Litube Studio")`, `"@litube"`, `"About Litube"`, tags `account-switch-litube`, `account-switch-litube-maker`), `home/HomeScreenshotAndroidTest.kt:548` (`onNodeWithText("Litube")` brand height == 28 dp), `home/HomeRefreshEvidenceAndroidTest.kt:89`, `library/LibraryIdentityAndroidTest.kt:16`, `ui/LocalizationAndroidTest.kt:89`, `ui/ResponsiveExperienceAndroidTest.kt:71`, cast tests' "Litube ..." titles. The brand "Litube" text/height assertion (HomeScreenshotAndroidTest:548) and golden pixels embedding the logo are the redesign-sensitive ones.

## 3. Unit tests (`app/src/test`)
- 249 files: 246 `.kt` + 3 resources (`resources/downloader/media/aac.m4a`, `avc_aac.mp4`, `README.md`).
- Frameworks: **JUnit 4 only** (`org.junit.Test/Assert/rules`; 340 import lines), `kotlinx-coroutines-test` (`runTest`, `Dispatchers.setMain` in ~43 files; 71 files use `runTest`), OkHttp `MockWebServer` (45 imports; extractor/net/downloader), Turbine (1 file: `core/WriteFeedbackTest.kt`). **No Robolectric, no MockK, no Mockito, no JUnit5, no Compose test rule** (grep of imports). Hand-written fakes. `isReturnDefaultValues=true` masks `android.*` stubs. `TranslationResourcesTest` reads `src/main/res` from the filesystem.
- Files per package (under `com.hhst.youtubelite`): data/media 49; extractor 21; core 13; player/datasource 13; cast 10 (+3 cast/protocol); downloader/core 10; downloader/engine 9; diagnostics 8; player/surface 8; downloader/ui 6, io 5, net 4, resolve 4, regression 1; library 6; watch 6 (+4 watch/chat, +1 watch/presentation); home 5; player 5, player/engine 5, player/sponsor 4, player/service 1; playlist 4; extension 4; channel 3; account 3; search 3; shorts 5; ui 3 (+1 ui/about, +2 ui/design); comments 4; explore 2; browser 2; post 2; saves 2; subscriptions 2; dearrow 1; navigation 1; share 1.
- **UI-redesign-at-risk unit tests** (they import presentation-package types or test UI-layer pure functions living in presentation packages; keep class names/signatures, or update tests):
  - ViewModel tests importing `*.presentation.*ViewModel` (45 files import presentation/ui types; ~25 are VM tests): HomeViewModel (5 files: `HomeViewModelTest`, `HomeDiscoveryViewModelTest`, `HomeForegroundTest`, `HomeWriteOrderingTest`, `FeedDisplayCompletenessTest`), SearchResultsViewModel (5), PlaylistViewModel (5), WatchViewModel (4 + `WatchPlayerStateTest`, `WatchAvatarRecoveryTest`, `WatchRatingFeedbackTest`, `WatchSaveOwnershipTest`), ShortsViewModel (4) + `RecentCommentsViewModelTest`, CommentsViewModel (4), ChannelViewModel (4), SubscriptionsViewModel (3), History/Collection/YouViewModel (3+3+2), PostDetail/PostCardActions VMs, `SaveSheetController` (2), `DownloadViewModel`+`DownloadUiMapper`+`DownloadPresentation`+`DownloadEntries`+`DownloadActions`+`DownloadRowAction` (`downloader/ui` package: 6 test files), `SignInViewModelTest`, `ExploreViewModelTest`, `ui/BrowseContinuationTest`, `ui/RefreshFailureContinuityViewModelTest`, `LiveChatViewModelTest`, `PlayerViewModelTest`. They assert UiState/VM contract (StateFlow content, ownership, pagination), NOT Compose. A UI rewrite that keeps the VM/UiState layer untouched leaves them green.
  - Pure UI-math in presentation/design packages (same-package, no imports): `ui/design/GestureResistanceTest`, `ui/design/MotionScaleTest`, `watch/presentation/WatchDescriptionTest`, `player/surface/{AutoFullscreenTest,FullscreenSwipeMotionTest,GestureMathTest,MiniPlayerLayoutTest,PlayerUiTest,QueueReorderTest,SubtitleStyleTest,ZoomMathTest}`, `ui/about/StableVersionTest`. These break only if the functions/objects they test are deleted or moved.
  - `ui/TranslationResourcesTest` (resource catalogue completeness across all 92 locale dirs + format argument checks). Any string key change must update all locales.
  - Zero tests touch `@Composable` code.
- Pure backend (safe): data/media, extractor, core, cast, downloader (non-ui), diagnostics, player/datasource, player/engine, player/sponsor, extension, dearrow, navigation, share, browser.

## 4. Instrumented tests
- `app/src/androidTest`: 283 code files (`.kt`) incl. `ContentTestRunner.kt` + 138 golden PNGs + 28 other asset files (cast certs/media, downloader media, player `.ts/.mp4`, `localization/catalog.json`) + `tools/casting/{README.md,media_sample_server.py,quality_samples.py}`; total 457 files, 40 MB of assets (goldens ~34 MB).
- `app/src/r8AndroidTest`: 3 files (see 1.8).
- Files per package under `java/com/hhst/youtubelite`: player/engine 27; data/media 23; player/surface 22; extractor 21; shorts 16; ui 16 (+5 ui/design); downloader/android 14; account 13; channel 12; cast 12 (+1 protocol); watch 12 (+3 chat); home 10 (+1 presentation); core 9; library 8; player/datasource 7; comments 5; diagnostics 5; explore 5; subscriptions 5; post 4; browser 3; menus 3; playlist 3; search 3; settings 3; player/service 2; downloader/io 2; navigation 2; batchd 1; di 1; downloader/data 1; gallery 1; player 1; root 1 (`ContentTestRunner.kt`).
- Runner `ContentTestRunner` (`androidTest/.../ContentTestRunner.kt`): extends `AndroidJUnitRunner`; sets `ContentSource.fixtures = (arguments["network"] != "1")` before `super.onCreate`. So every test is OFFLINE with fixture/fake backends by default; `-e network 1` switches to live YouTube (tests named `*Network*`, `Msc*Live*`, `YoutubePlayback*`, `ContentCatalog*`, and the casting tools). Fixtures live in main: `FixtureLibraryRepository`, `FixturePlaylistRepository`, `HomeScenario`, etc. (`home/data/HomeScenario` imported by `HomeScreenshotAndroidTest:21`); Replay tests load JSON in `assets` or in-source (`MscWatchCommentsReplayAndroidTest`, `MscLiveChannelReplayAndroidTest`). `DeviceEvidence` (`core/DeviceEvidence.kt`) writes screenshots/UI dumps to `/data/local/tmp/device-evidence` and JSON to `filesDir/device-evidence` (CI pulls them).
- Gating: 17 classes carry `@SdkSuppress(minSdkVersion = 35[, maxSdkVersion = 35])` (all screenshot/golden ones: "API 35/SwiftShader"), 3 `minSdk 26`.
- Runner R8: `R8VerificationRunner` (Java `Instrumentation`, runs `-e class` with JUnitCore, preserves `Objects.requireNonNull` overloads with `@Keep`), default class `SponsorBlockMinifiedAndroidTest`.
- **How UI is located** (androidTest): `onNodeWithTag` 1459 uses, `onAllNodesWithTag` 120, `hasTestTag` 88; `onNodeWithText` 301, `onAllNodesWithText` 28, `hasText` 21; `onNodeWithContentDescription` 185, `hasContentDescription` 6. i.e. test tags dominate, text/contentDescription second. Main code has 482 `testTag(` calls in 58 files; `testTagsAsResourceId` not used (no uiautomator tag lookup). Screen-level `captureToImage()` is taken from `onNodeWithTag("<screen>")`.
- Most-used tags in tests (uses) -> definition in main:
  `search-input` 37 -> `ui/design/SearchPanel.kt:107`; `player-window` 34 -> `player/surface/MiniPlayerWindow.kt:920`; `home-feed` 31 -> `home/presentation/HomeScreen.kt:188`; `comments-list` 24 -> `comments/presentation/CommentsPanel.kt:679`; `comments-panel` 22 -> `CommentsPanel.kt:229`; `video-menu` 21 -> default `tag` param of `ui/design/MenuBottomSheet.kt:87` (set at `home/presentation/HomeOverlays.kt:49`); `shorts-pager` 16 -> `shorts/presentation/ShortsScreen.kt:138`; `home-screen` 16 -> `HomeScreen.kt:67`; `comments-close` 16; `watch-screen` 15 -> `watch/presentation/WatchScreen.kt:85`; `subscriptions-feed` 15 -> `subscriptions/presentation/SubscriptionsScreen.kt:292`; `player-buffering` 15; `channel-posts-list` 15; `you-library` 14 -> `library/presentation/YouScreen.kt:108`; `search-results-list` 14; `search-overlay` 14 -> `SearchPanel.kt:100`; `watch-video-menu` 13; `home-topbar` 13 -> `HomeScreen.kt:290`; `home-refresh-indicator` 13; `comments-sort` 13; `search-query` 11; `queue-list` 11; `post-poll-option-0`, `post-detail-like`, `comment-c1-reply-toggle` 11 each; `save-to-playlist` 10; `watch-title`, `watch-comments-card`, `short-buffering`, `search-title-harbour`, `search-filter-bar`, `post-detail-feedback` 9 each. Tags with dynamic suffix: `skeleton-thumb-N`, `search-title-<id>`, `account-switch-<id>`, `reply-<id>-more`.
  **Redesign rule: every tag above (and the other ~400 in main) is test contract; keep tag strings and the node hierarchy they are attached to (e.g. `skeleton-thumb-0` geometry is asserted to 0.5 px at `HomeScreenshotAndroidTest.kt:50-60`).**
- Pixel/geometry-asserting tests (break on layout change even with tags kept): `HomeScreenshotAndroidTest` (`loadingAnchorsMatchMeasuredGeometry` exact dp values, `Litube` brand 28 dp height, top-bar/tab fit), the 15 screenshot classes in 5, plus `player/surface/PlayerControlSizingAndroidTest`, `ui/CompactControlsAndroidTest`, `ResponsiveExperienceAndroidTest`, `LocalizationAndroidTest`, `ui/design/*AndroidTest` (5, motion/sheets/search).

### 4.1 CI vs local
CI runs only the `classes=(...)` list in `android.yml:141-166` on an API 35 google_apis x86_64 pixel_6 emulator with iptables blocking 80/443/udp443, wifi/data off: 24 entries = 22 whole classes (browser TransientUi/ExternalRoute; player/engine PipDismiss, PipReturn, TransportRecovery, PinnedVideoRecovery, MediaResponseRecovery, PlaybackAttemptLifecycle, RemoteResume, WatchStartSeconds, CastStateIsolation, OriginalAudio; `NamedAudioAndroidTest#namedAudioSurvivesQualityChanges`; diagnostics DiagnosticSave; player/surface FullscreenSheet, PlayerControlSizing; player/datasource PlaybackLoadError; core TrafficVerificationLifecycle; downloader/android DownloadOptions, DownloadIntent; downloader/io LocalPublish; data/media SearchIdentity) + 2 `CastChannelAndroidTest#...` methods), then `PipAudioOnlyAndroidTest` separately, then on minifiedDebug `SponsorBlockMinifiedAndroidTest` via `R8VerificationRunner`. Success criterion: `^OK \([1-9][0-9]* tests?\)` in each result file.
**Not in CI (local-only): all 15 screenshot/golden classes, all `*Network*`/live classes, cast software-receiver tests, most compose UI behavior classes (home/search/comments/channel/shorts/watch/account/library/settings/ui/*), `StorageMinifiedAndroidTest`, `WatchReferenceAndroidTest`.** Instrumented UI tests are therefore NOT a CI gate: only compilation (`assembleDebugAndroidTest`) is.

## 5. Golden images
- Location: `app/src/androidTest/assets/<feature>/goldens/*.png`. **138 PNGs, 15 feature dirs**: home 28, channel 10, explore 12, playlist 18, library 13, search 12, comments 8, settings 8, shorts 9, watch 6, account 4, post 2, feedback 2, saves 3, livechat 3. Raw channel `assets/channel/{community,discovery}/*.png` (16 files) and `assets/channel/community` are non-golden fixtures/second-level baselines (`ChannelCommunityScreenshotAndroidTest` and `ChannelDiscoveryScreenAndroidTest` have their own baseline sets inside `assets/channel/...`). Size on disk: home 7.9 MB, explore 5.6, shorts 5.5, playlist 3.7, search 2.1, library 2.3, channel 1.9 ...
- Naming: `<scene>-<widthDp>[-font2|-landscape].png`. Widths: 320, 390 (phone portrait 390x844), 411, 600, 840, 1280 (1280x800 tablet), 1600 (explore/shorts). `-font2` = 390x844 with font scale 2.0 (`Density(1f, 2f)`); `-landscape` = 844x390. Density is forced to 1f (`CompositionLocalProvider(LocalDensity provides Density(1f, scale))`) so px == dp. Examples: `home/goldens/feed-390.png`, `explore-music-390-landscape.png`, `settings-home-390-font2.png`, `watch/goldens/content-1280.png`, `account-menu-390-font2.png`. Scenes: loading/error/empty/feed/feed-shorts/feed-post(-top)/menu(-mix/-post/-short(s))/search(-empty) (home); results/empty/loading/error/filters/menu (search); content/error/loading (watch); etc.
- Comparison (identical in ~13 copies of a private `capture(name, output, record, goldens)`; e.g. `home/HomeScreenshotAndroidTest.kt:562-579`):
  1. `compose.onNodeWithTag("<screen-tag>").captureToImage().asAndroidBitmap()` (e.g. `home-screen`, `comments-panel`, `you-library`...).
  2. ALWAYS writes `<name>.png` (PNG 100%) into the app's external files dir (see below).
  3. If `record == true` OR `name !in goldens` (a hard-coded Set of expected names in the test, or `assets.list`), it returns without asserting (NEW SCENES WITHOUT A GOLDEN ARE SILENTLY NOT COMPARED).
  4. Else loads `assets/<feature>/goldens/<name>.png` from the TEST APK's assets (`InstrumentationRegistry.getInstrumentation().context.assets`), asserts equal width/height, counts pixels where ANY of R,G,B channel differs by `> 2` (alpha ignored), and asserts `changedPixels / totalPixels <= 0.001f` (0.1 %). Failure message: "<name> changed N pixels; inspect the saved screenshot".
  The same rule is used by every class (Shorts/Watch/LiveChat use a `changed` list but identical 0.001 ratio; Channel community/discovery and Playlist inline the same 0.001).
- Re-generation (no script in repo; `/scripts/` is gitignored and absent): run the class on an API 35 emulator with the debug build and the per-feature instrumentation arg `-e recordXGoldens true`, e.g. `adb shell am instrument -w -e class com.hhst.youtubelite.home.HomeScreenshotAndroidTest -e recordHomeGoldens true com.nevminhnh.mintube.debug.test/com.hhst.youtubelite.ContentTestRunner`; then pull the output dir and copy PNGs into `assets/<feature>/goldens/`, rebuild the test APK. Args (grep `getString("record...")`):

| Arg | Consumers | Output dir (under `getExternalFilesDir(null)` = `/sdcard/Android/data/<appId>.debug/files/`, except noted) |
|---|---|---|
| `recordHomeGoldens` | `home/HomeScreenshotAndroidTest` (4 tests) | `home-acceptance` |
| `recordSearchGoldens`, `recordMenuGoldens` | `search/SearchResultsScreenshotAndroidTest` | `search-acceptance` |
| `recordMenuGoldens`, `recordCommentsGoldens` | `comments/CommentsPanelScreenshotAndroidTest` (menus share `recordMenuGoldens`) | `comments-acceptance` |
| `recordChannelGoldens` | `channel/ChannelScreenshotAndroidTest` | `channel-acceptance` |
| `recordCommunityGoldens` | `channel/ChannelCommunityScreenshotAndroidTest` (baselines under `assets/channel/community`) | `community-ui` |
| `recordDiscoveryGoldens` | `channel/ChannelDiscoveryScreenAndroidTest` (`assets/channel/discovery`) | `filesDir/discovery-acceptance` (internal; needs `run-as`) |
| `recordExploreGoldens` | `explore/ExploreScreenshotAndroidTest` | `explore-acceptance` |
| `recordShortsGoldens` | `shorts/ShortsScreenshotAndroidTest` | `shorts-acceptance` |
| `recordLibraryGoldens` | `library/LibraryScreenshotAndroidTest` (library + subscriptions) | `library-acceptance` |
| `recordBatchCGoldens` | `account/AccountSettingsScreenshotAndroidTest` (account + settings goldens) | `batch-c-acceptance` |
| `recordBatchDGoldens` | `batchd/BatchDScreenshotAndroidTest` (saves, feedback, post) | `batch-d-acceptance` |
| `recordPlaylistGoldens` | `playlist/PlaylistScreenAndroidTest` | `filesDir/playlist-acceptance` (internal) |
| `recordWatchGoldens` | `watch/WatchScreenshotAndroidTest` | `watch-acceptance` |
| `recordLiveChatGoldens` | `watch/chat/LiveChatPanelScreenshotAndroidTest` | `livechat-acceptance` |
  Note: `HomeScreenshotAndroidTest:136+` also emit non-golden evidence (`feed-320-font2` etc.) with `record=true` forced. `HomeRefreshEvidenceAndroidTest` has its own `home-refresh-evidence` output dir.
- All screenshot classes are API 35 only (`@SdkSuppress(35,35)`), SwiftShader renderer, so goldens are tied to that renderer; regenerating on a different GPU/API will diff text anti-aliasing >0.1 %.
- Consumers by golden dir: home -> HomeScreenshot; search -> SearchResultsScreenshot; comments -> CommentsPanelScreenshot; channel -> ChannelScreenshot (+Community/Discovery); explore -> ExploreScreenshot; shorts -> ShortsScreenshot; watch -> WatchScreenshot; library -> LibraryScreenshot; playlist -> PlaylistScreen; account+settings -> AccountSettingsScreenshot; saves+feedback+post -> BatchDScreenshot; livechat -> LiveChatPanelScreenshot.
- **Every golden embeds pixels of the CURRENT design (logo, colors, spacing); after the redesign ALL 138 goldens fail (when run) and must be re-recorded; because they are not run in CI, nothing fails automatically.**

## 6. CI workflow `.github/workflows/android.yml` (195 lines; the only workflow)
Triggers: `pull_request`, `push` to `master` and `v4`, tags `v*`, `workflow_dispatch`. `permissions: contents: read`. Concurrency group per ref, cancel-in-progress.
**Job `build`** (ubuntu-latest, 45 min): checkout@v6; setup-java@v5 temurin 21; `android-actions/setup-android@v3` (platform-tools, platforms;android-36, build-tools;36.0.0); `gradle/actions/setup-gradle@v6`;
1. step "Unit tests, Lint and application builds": `bash gradlew :app:testDebugUnitTest :app:lintDebug :app:lintVitalRelease :app:assembleRelease :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process "-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8" --stacktrace`;
2. step "Build debug-signed R8 verification APKs": `bash gradlew -PandroidTestBuildType=minifiedDebug :app:assembleMinifiedDebug :app:assembleMinifiedDebugAndroidTest` (same flags);
3. upload `android-reports` (`app/build/reports`, `app/build/test-results`, if: always);
4. upload `android-apks-and-mapping`: `app/build/outputs/apk`, `outputs/mapping/release`, `mapping/minifiedDebug`, `mapping/minifiedDebugAndroidTest`, `outputs/diagnostic-mappings` (error if none).
**Job `offline-device`** (needs build; 40 min): downloads that artifact to `artifacts/`; enables KVM; writes `$RUNNER_TEMP/device.sh` (heredoc, syntax-checked with `bash -n`); runs it inside `reactivecircus/android-emulator-runner@v2` (api 35, google_apis, x86_64, pixel_6, no-window, swiftshader_indirect). Script: network blocked via iptables, launcher-ready wait, installs `artifacts/apk/debug/app-debug.apk` + `artifacts/apk/androidTest/debug/app-debug-androidTest.apk`, then `artifacts/apk/minifiedDebug/app-minifiedDebug.apk` + androidTest APK; `run_suite <package> <classes> <label> [runner]` uses `adb shell am instrument -w -r -e class ... "$package.test/$runner"`. Collects logcat, window dump, `/data/local/tmp/device-evidence`, and `/data/user/0/<pkg>/files/device-evidence`; uploads `android-offline-device-results`.
Hard-coded ids to change: lines 89 (loop over `com.hhst.litube.debug com.hhst.litube.minified`), 170, 171, 175. Everything else (runner class names `com.hhst.youtubelite.ContentTestRunner`, `R8VerificationRunner`, test class FQNs) stays. APK file names (`app-debug.apk`, `app-minifiedDebug.apk`) derive from the module name `app`, not from applicationId/rootProject name, so they do not change.
**There is no release job.** Tags `v*` trigger the same `build` + `offline-device` jobs, no signing, no GitHub Release, no secrets. A tag-triggered signed release job must:
- build with `:app:assembleRelease` (R8 on; also runs `archiveReleaseDiagnostics`, which depends on `minifyReleaseWithR8`); optionally `:app:bundleRelease`;
- output: `app/build/outputs/apk/release/app-release-unsigned.apk` (today) -> after adding a signingConfig: `app-release.apk`; mapping at `app/build/outputs/mapping/release/` and `outputs/diagnostic-mappings/<buildId>/`;
- release needs NO keystore today; to sign, either add a `signingConfigs.release` in `app/build.gradle.kts` reading env/`-P` properties (and attach `signingConfig = signingConfigs.getByName("release")` in the `release` build type, minifiedDebug is `initWith(release)` but overrides `signingConfig`=debug at L69, so it stays unaffected) or sign out of band with `apksigner` (build-tools 36.0.0 is already installed in the job) from a base64 secret keystore;
- needs `permissions: contents: write` (currently `read`) for `gh release create`/`softprops/action-gh-release`, depends on `build` (to reuse artifact `android-apks-and-mapping`, whose path contains `apk/release/`);
- lint: `lintVitalRelease` already runs during `assembleRelease`.
No `fastlane` automation scripts exist (metadata only).

## 7. `fastlane/metadata/android/en-US`
- Single locale `en-US`: `title.txt` (content `Litube`, no trailing newline, 1 line), `short_description.txt` ("An unofficial, open-source YouTube client for Android."), `full_description.txt` (HTML-ish bullet list, 6 features; **no** "Litube" word), `changelogs/` (versionCode-named: 15-21, 200-202, 210-214, 300-303, 400; `400.txt` is 4 bullet lines about native screens/MSC/sign-in code, no brand name), `images/icon.png`, `images/phoneScreenshots/1-4.png`. Only `title.txt` mentions Litube (grep -i across fastlane = 1 match). A 4.1 changelog would be `<versionCode>.txt`.

## 8. README.md (139 lines) and other docs
Sections: header (icon `.github/assets/litube-icon.svg`, title, tagline), badge row (F-Droid badge COMMENTED OUT, pointing at `f-droid.org/packages/com.hhst.litube/`; GitHub download SVG -> `github.com/HydeYYHH/litube/releases`; shields: version v4.0.0, Android 7.0+, GPL-3.0), acknowledgement paragraph (L19), intro, Previews (4 PNGs in `.github/assets/screenshots/`, light theme, described by that dir's README), Features (6 bullets), Downloads (upstream releases link L46 + requirements: Android 7/API 24, WebView, PiP API 26), Milestones table (v1 `releases/tag/v1.4.9`, v2 `v2.0.0`, v3 `v3.0.0`, v4.0.0 -> all `HydeYYHH/litube`), Build from source (JDK 21, SDK 36, build-tools 36.0.0, Gradle 8.11.1; `git clone --branch v4 https://github.com/HydeYYHH/litube.git`, `cd litube`; MSC from JitPack; L80 ids `com.hhst.litube.debug` / `com.hhst.litube`; L82 unsigned release path), collapsible "Tests and verification" and "Tech stack", Contributing (issues link `HydeYYHH/litube/issues`), Acknowledgements (L120-122), Star History (4 refs to `HydeYYHH%2Flitube`, L127-131), footer "Litube - Licensed under GPL v3".
- **Must preserve (attribution/license):** MediaServiceCore fork by HydeYYHH (links at L19,108,120 and `CONTRIBUTING.md:99`), SmartTube (`yuliskov/SmartTube`, "YouTube engine used by SmartTube"), NewTube (`aleixrodriala/newtube`: "inspiration" plus L122 "Parts of the Cast implementation are adapted from NewTube, with the original license and attribution retained"), AndroidMedia3, SponsorBlock, DeArrow. Also keep GPL-3.0 notice/`LICENSE`, and license assets: `app/src/main/assets/licenses/newtube-MIT.txt`, `dearrow-attribution.txt`, `chromium-BSD.txt`, `ejs/LICENSE`, `potoken/BGUTILS-LICENSE`, `barlow_semicondensed-OFL.txt`, `roboto-license.txt`, `material-icons-LICENSE.txt`. Upstream-fork credit to HydeYYHH/Litube is a GPL good-practice addition.
- To change: title/header/alt texts/footer, upstream release+issue+clone+star-history URLs (L9,46,54-57,66-67,116,127-131), L80 ids, new icon asset, screenshots (and `.github/assets/screenshots/README.md` which says they are unchanged v4 captures), version badge, milestones (keep upstream history or reword).
- `CONTRIBUTING.md`: title "Contributing to Litube" (L1) and MSC repo link (L99); references android.yml gates (R8/minifiedDebug).

## 9. Windows / JDK 21 gotchas
- Local state: `local.properties` exists (sdk.dir set, gitignored, not read here); `java` is not on the Git Bash PATH of this survey shell (JDK not verified). Use `gradlew.bat` (README L78) or `bash gradlew`; CI uses `bash gradlew`.
- `.gitattributes` (4 lines) only marks `app/src/main/assets/ejs/*`, `potoken/bgutils.js`, `potoken/BGUTILS-LICENSE` as `-text` ("must retain exact pinned bytes"). There is NO `* text=auto`; `core.autocrlf` is `false` in this checkout and `git ls-files --eol` shows 1318 LF text files, 196 binary, 5 `none`, and exactly **one CRLF-in-index file `app/src/main/assets/ejs/yt.solver.core.js`** (intentional, pinned). Do not let an editor or `git add --renormalize`/autocrlf normalise it; the digest and potentially a checksum/loader depend on exact bytes. Do not enable autocrlf=true globally for this repo.
- Because `app/build.gradle.kts:17-25` hashes every `src/main` file, `diagnosticBuildId` depends on line endings: a CRLF checkout produces a different id than CI (cosmetic for diagnostics, but breaks "identity matches mapping archive" assumptions).
- Config cache + digest: every edit to a `src/main` file re-runs configuration; expect slower incremental builds. `-Xmx2048m` in `gradle.properties` is tight for AGP+KSP+Compose; CI overrides with 3g and `-Pkotlin.compiler.execution.strategy=in-process`, `--max-workers=2`; replicate on 16 GB machines.
- Long paths: longest tracked path is 104 chars (e.g. `app/src/test/.../*Test.kt`); with `app/build/intermediates/...` and Windows' 260-char MAX_PATH, enable `git config core.longpaths true` and Windows LongPathsEnabled; keep the checkout path short (`D:/Coding_Project/MinTube/repo` is fine).
- `.gitignore` ignores `docs/`, `/scripts/`, `*.patch`, `*.apk`, `release/`, `.codegraph/`, `external/`: **this survey file (`docs/_survey/`) is untracked/ignored**; any helper scripts under `/scripts/` will not be committed.
- Bash-only scripts: CI's `device.sh` (bash arrays, `set -euo pipefail`, `adb` + iptables) is Linux-emulator only; `androidTest/tools/casting/*.py` need Python 3 plus `adb forward`. Gradle build files are cross-platform (use `invariantSeparatorsPath`). `DeviceEvidence.shell` uses `run-as`, `uiautomator`, `screencap` (device-side, OK on any host).
- `TranslationResourcesTest` reads files with relative `src/main/res` or `app/src/main/res`, so run Gradle from repo root or `app` (default Gradle behaviour is fine).
- Pinned bytes elsewhere: `CastTrustDataTest` verifies SHA-256 of cast trust certificates; `PublishedStreamIntegrityTest`/downloader tests use `MessageDigest` on generated data (not repo assets). `androidTest/assets/cast/*.der|*.p12|*.pk8` are binary: git marks them `-text` automatically.
- AGP 8.10.1 requires JDK 17+, compiles with 21; `kotlin.jvmTarget=17` and `JavaVersion.VERSION_17` need no toolchain download. No Gradle toolchain resolver is configured (no foojay plugin): JDK 21 must be installed and `JAVA_HOME` set.

## 10. Quick checklist for the rename + redesign
1. `app/build.gradle.kts:32`, `settings.gradle.kts:29`, 93 `strings.xml` (5 keys/file; at minimum `app_name`) -> new brand.
2. `android.yml:89,170,171,175` ids.
3. androidTest literals: `DownloadAndroidHarness.kt:140,149,166`, `WatchReferenceAndroidTest.kt:91`, `tools/casting/README.md`.
4. Keep: namespace, all `com.hhst.youtubelite` class names, proguard rules, test tags, `litube-settings` schema, MSC coordinate.
5. Decide: update-check/SOURCE URLs in `AboutActivity.kt:87,175`, `Downloads/Litube` folder (backend), logo drawable/`LitubeMark`, README/fastlane title/CONTRIBUTING.
6. Add release signing + release job (none exists), `contents: write`, secrets for keystore.
7. Re-record all 138 goldens on an API 35 emulator after the redesign (no CI gate).
