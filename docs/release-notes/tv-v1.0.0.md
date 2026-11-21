MinTube TV 1.0.0 is the first release of MinTube for Android TV and Google TV: a dedicated 10-foot interface driven entirely by the remote, on the same player, account and data code as the MinTube phone app.

It is a separate app (`com.nevminhnh.mintube.tv`), signed with the same key as MinTube, so it installs next to the phone app and never replaces it.

## What is in it

- **Home.** Rows of video cards by Home section (Recommended, or Trending / Trending in Vietnam when signed out, then Music, Gaming, News, Sports and the other Home chips), plus a Shorts row. Rows page in as you move right and down. Hold OK on a card to open its channel.
- **Search.** A search field for the TV keyboard, voice search when the TV has a speech recognizer, live suggestions, recent searches (hold OK to remove one), a filter row and a results grid of videos, channels and playlists.
- **Watch.** Full-screen playback with a remote overlay: OK plays or pauses, Left/Right seek 10 s (hold to go faster), Up opens quality, speed, captions, audio track and chapters, Down shows "Up next". The seek bar shows SponsorBlock segments, and skipped segments show a short toast. An Info panel holds the description; Like, Dislike, Subscribe and Watch later sit in the controls row. Autoplay counts down over the last 10 seconds; during a playlist it announces the playlist's next video.
- **Channels and playlists.** Channel pages with Videos, Shorts, Live and Playlists tabs. Playlist pages with Play all and Shuffle, which play the whole playlist in order.
- **Subscriptions.** A strip of your channels above the subscriptions feed.
- **Library.** History, Watch later, Playlists, Liked and Queue rows, each with a View all page. In History, hold OK on a video to remove it, and pause or resume watch history.
- **Shorts.** Full-screen vertical Shorts: Up/Down change the Short, OK pauses, Left/Right seek 5 s, hold OK to like.
- **Sign in on TV.** A device-code page with a large code, the address to visit and a QR code to scan with your phone. Account switching and sign-out are in Settings.
- **Settings.** Account, playback, content (Translate titles to English, Trending in Vietnam), SponsorBlock, DeArrow, captions, language and About.
- **Traffic checks.** When YouTube asks for a verification, a remote-friendly page explains it and opens the check.
- **Signal design for 10 feet.** Dark only, cyan focus with a glow on the focused card, overscan-safe margins, and focus that is never lost and comes back where you left it when you press Back.
- **Languages.** TV-only text is in English and Vietnamese; in other app languages it shows in English, while shared text keeps its existing translations.

## Install

Download `MinTube-TV-tv-v1.0.0.apk` from this release and sideload it: copy it to the TV with a USB drive or the Downloader app, then allow installs from that source when Android TV asks. See the "MinTube TV" section of the README for step-by-step instructions and a remote cheat sheet.

## Limitations

- Sideload only; there is no Play Store listing.
- No downloads, picture-in-picture, mini player, comments or live chat on TV.
- Voice search needs a speech recognizer on the TV (Google TV and most Android TV devices have one).
- Hold OK on a Home card opens its channel only when the feed provides the channel; some signed-out feeds do not.
- Casting to the TV from the YouTube phone app is not supported; open videos in MinTube TV itself, or share a YouTube link to it.

MinTube is a fork of Litube 4.0.0 by HydeYYHH, licensed under GPL-3.0. MinTube is not affiliated with YouTube or Google.
