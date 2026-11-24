MinTube TV 1.0.10 keeps long videos playing through network changes.

## Fixed

- **Playback no longer stops with "MEDIA_SESSION_CHANGED" on long videos.** When the network route changes during playback (a VPN reconnecting, Wi-Fi roaming), MinTube TV refreshes the video's links and carries on. It could do that only once per video; it can now do it again after 30 seconds of normal playback, while a refresh that fails again straight away still stops after one try.

MinTube TV is part of MinTube, a fork of Litube 4.0.0 by HydeYYHH, licensed under GPL-3.0.
