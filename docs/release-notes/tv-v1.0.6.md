MinTube TV 1.0.6 adds an Updates page to Settings, in the style of the MinMusic app.

## Added

- **Updates page.** Settings has a new "Updates" section. Its row shows the live status ("MinTube TV is up to date" or "Update available 1.0.6") and opens the Updates page. Back returns to the Settings list on the Updates row.
- **App update card.** The page shows the status, "MinTube TV version x", the time of the last successful update check and a "Check for updates" button, which has focus first. When a newer release exists its notes open in a panel (scroll them with Up and Down) with "Download and install", a progress bar and Cancel. "View update" on the card reopens that panel after Later.
- **Automatic checks.** "Automatically check for updates" and "Update notifications" are switches (press OK). MinTube TV looks for a new release about once a day in the background. Many TVs do not show app notifications, so the status on this page is the reliable place to see an update.
- **Changelogs.** A list of recent MinTube TV releases with the version, "Release notes", the date and how long ago it was published. OK opens that release's notes; a release newer than the installed one also offers "Download and install".

## Changed

- The "Check for updates" button moved from About to the Updates page. About still shows the version.
- Updates now run through the same update service as the phone app. The one-time "Install unknown apps" step and the SHA-256 check before installing are unchanged.

MinTube TV is part of MinTube, a fork of Litube 4.0.0 by HydeYYHH, licensed under GPL-3.0.
