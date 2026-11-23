MinTube TV 1.0.5 can download and install its own updates.

## Added

- **Check for updates.** In Settings, About, the new "Check for updates" button looks for a newer MinTube TV release. It reports "Checking…", "Up to date", or an error with a Retry button.
- **Update panel.** When a newer release exists, a panel shows "MinTube TV 1.0.4 → 1.0.5", the download size and the release notes (scroll them with Up and Down). "Download and install" has focus first; "Later" or Back closes the panel.
- **Download with progress.** A progress bar and percentage show while the file downloads; Cancel or Back stops it. The file's SHA-256 is checked before anything is installed, and a file that fails the check is deleted.

## One-time step

The first time you install an update, Android asks you to allow MinTube TV to install apps. MinTube TV opens that setting for you: turn on "Install unknown apps" for MinTube TV, then press Back to return and the install continues. If your TV has no such screen, open system Settings, Apps, Special app access, Install unknown apps, allow MinTube TV, then choose Retry in the panel.

Builds that are not signed release builds (development builds) show the latest version but never install it.

MinTube TV is part of MinTube, a fork of Litube 4.0.0 by HydeYYHH, licensed under GPL-3.0.
