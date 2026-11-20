<div align="center">
  <h1><img src=".github/assets/mintube-icon.svg" alt="MinTube icon" width="80" height="80"><br>MinTube</h1>
  <p>An unofficial, open-source YouTube client for Android with a calm, redesigned interface.</p>

  <p>
    <img src="https://img.shields.io/badge/Android-7.0%2B-0B8F6A?style=flat-square" alt="Android 7.0 or later">
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-596273?style=flat-square" alt="GPL v3 license"></a>
  </p>
</div>

**MinTube is a fork of [Litube](https://github.com/HydeYYHH/litube) 4.0.0 by HydeYYHH, licensed under GPL-3.0.**
The playback, extraction, download and account code is Litube's; MinTube changes the interface and the app identity.
MinTube is not affiliated with YouTube or Google, and it is not the upstream project: please do not report MinTube
issues to Litube.

## What MinTube changes

- **Redesigned interface, "Mint".** A new design system: mint-green accent, soft ink neutrals, generous rounding,
  Be Vietnam Pro titles, Material Symbols Rounded icons, one shared bottom navigation bar and one card family across
  feeds. The plan is in [docs/UI_PLAN.md](docs/UI_PLAN.md).
- **New name and application ID.** The app is `com.nevminhnh.mintube` (`com.nevminhnh.mintube.debug` for debug
  builds), so it installs side by side with Litube and never replaces it.
- **New icon.** An adaptive launcher icon with a themed (monochrome) layer.
- **Its own update check.** The About screen points at the MinTube repository and links back to the original project.

Kept on purpose, so existing data keeps working: downloads are still saved under `Downloads/Litube`, and settings
backups keep the `litube-settings` format, so a Litube backup can be restored in MinTube.

## Screenshots

New screenshots live in [`docs/screens/`](docs/screens/). They are added once the redesigned screens have been
verified on a device.

## Features

- **Browse YouTube.** Explore your home feed, search for videos, and browse channels, playlists, Shorts, and your library.
- **Play your way.** Enjoy ad-free playback with controls for video quality, speed, subtitles, and audio tracks. Keep listening in the background, use picture-in-picture or the mini-player, or cast to another device.
- **Download for later.** Save videos, audio, subtitles, or entire playlists. Choose the quality and language, and pause or resume downloads as needed.
- **Keep things organized.** Build queues with shuffle, repeat, and autoplay. Watch history and Watch later are available even when you're signed out.
- **Multiple accounts, all in one.** Easily switch between your anonymous account and multiple signed-in accounts.
- **Make it yours.** Customize SponsorBlock, playback gestures, themes, and language. Enable DeArrow for alternative titles and thumbnails.

**Requirements:** Android **7.0 / API 24** or later and an up-to-date **Android System WebView**. Picture-in-picture requires Android **8.0 / API 26** or later and a compatible device.

## Build from source

Use **JDK 21**, **Android SDK Platform 36**, and **Build Tools 36.0.0**. The repository includes a **Gradle 8.11.1** wrapper.

```sh
git clone https://github.com/nev-minhnh/MinTube.git
cd MinTube
```

Gradle downloads MediaServiceCore v1.0.0 from JitPack using the version pinned in `gradle/libs.versions.toml`.

Set `ANDROID_HOME` to your Android SDK directory, or set `sdk.dir` in an untracked `local.properties` file. Then build the debug APK:

```sh
./gradlew :app:assembleDebug
```

On Windows, replace `./gradlew` with `.\gradlew.bat` in these commands.

The debug APK is saved to `app/build/outputs/apk/debug/app-debug.apk`. Debug builds use the application ID `com.nevminhnh.mintube.debug`; release builds use `com.nevminhnh.mintube`. The Kotlin package and namespace remain `com.hhst.youtubelite`, as in Litube.

To build the release APK, run `./gradlew :app:assembleRelease`. The unsigned APK is saved to `app/build/outputs/apk/release/app-release-unsigned.apk`; sign it with your own release key before distribution. Signing material must never be committed.

<details>
<summary><strong>Tests and verification</strong></summary>

Run unit tests and Android lint checks:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Build the instrumented test APK:

```sh
./gradlew :app:assembleDebugAndroidTest
```

Instrumented tests use local fixtures by default. To run live-service read checks, pass `network=1` as an instrumentation runner argument. See [CONTRIBUTING.md](CONTRIBUTING.md) and the [Android workflow](.github/workflows/android.yml) for device testing and release verification, including R8 checks.

</details>

<details>
<summary><strong>Tech stack</strong></summary>

MinTube is built with **Kotlin**, Jetpack Compose / Material 3, **AndroidX Media3**, **coroutines / StateFlow**, **Koin**, **Room**, **WorkManager**, and **OkHttp**, all inherited from Litube.

Content and stream extraction are provided by the [MediaServiceCore fork maintained for Litube](https://github.com/HydeYYHH/MediaServiceCore).

</details>

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) for the project structure, coding conventions, and validation steps, and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the invariants a UI change must not break.

When reporting a bug, include your app version, device model, Android version, steps to reproduce the issue, and what you expected to happen. Describe what happened instead and attach a diagnostic archive if available (About > Export diagnostic logs).

## Acknowledgements

MinTube exists because of [Litube](https://github.com/HydeYYHH/litube) by HydeYYHH and its contributors: all of the app's functionality comes from that project.

Litube, and therefore MinTube, is built on a [fork of MediaServiceCore](https://github.com/HydeYYHH/MediaServiceCore), the YouTube engine used by [SmartTube](https://github.com/yuliskov/SmartTube), and inspired by [NewTube](https://github.com/aleixrodriala/newtube). Our thanks to the maintainers and contributors of these projects for the work, code, and ideas that make this app possible.

Parts of the Cast implementation are adapted from [NewTube](https://github.com/aleixrodriala/newtube), with the original license and attribution retained. The app also builds on AndroidX Media3 and the wider open-source Android ecosystem, with support for SponsorBlock and DeArrow.

Fonts and icons: Be Vietnam Pro (SIL Open Font License 1.1), Roboto and Material Symbols (Apache License 2.0). Their licence texts ship in `app/src/main/assets/`.

---

<p align="center">
  <strong>MinTube</strong> · a fork of Litube 4.0.0 · Licensed under the <a href="LICENSE">GNU GPL v3.0</a>
</p>
