# Watch from another country ("Xem từ quốc gia khác"): design

Status: design only, nothing implemented. Date: 2026-11-21. Base: MinTube 1.0.1 (`cf58314`).
Owner request: an in-app **Proxy** mode and an in-app **VPN** mode, each with a built-in list of free
servers grouped by country. The list updates itself, shows measured speed, and is sorted by speed
within each country. Non-technical users must be able to use it.

## 0. Invariants (non-negotiable)

1. **One exit per playback session.** googlevideo URLs are bound to the IP that fetched the player
   response. `player`/`next`/InnerTube calls, PoToken/BotGuard, media segments (DASH/HLS/progressive/live),
   downloads and the Cast relay must all leave through the same exit. If they don't, the media request gets HTTP 403.
2. **Changing the exit invalidates every stream URL.** On any exit change, bump an `exitEpoch` and treat
   cached player responses and `MediaRequestPlan`s from the old epoch as stale.
3. **`gl` is never rewritten** to the exit country (see `CONTENT_LOCALE.md`). The geo decision comes from the IP only.
4. **No silent direct fallback.** While the feature is ON and no exit works, report the failure and do
   not go direct. Going direct would 403 anyway, or play the geo-blocked error, and it would hide the problem.
5. **LAN traffic stays direct.** This covers DLNA/Cast control and `LocalStreamProxy` served to the TV.
6. **The bootstrap stays direct.** Fetching the server lists never goes through a candidate exit.

## 1. App networking map (verified in repo)

Every YouTube-facing stack is OkHttp 4.12.0. No `DefaultHttpDataSource` or `OkHttpDataSource` is used. Media3
1.10.1 reads through the app's own `YoutubeHttpDataSource` on an OkHttp `Call.Factory`. MSC (`HydeYYHH:MediaServiceCore v1.0.0`)
has no transport of its own: it calls the `com.liskovsoft.youtubeapi.videoinfo.V2.HttpClient` interface,
which `MediaClient` implements on the injected OkHttp client.

| # | Stack | Where it is built (file:line) | Today | Region routing |
|---|---|---|---|---|
| A | Shared client (Koin `single<OkHttpClient>`) | `di/AppModule.kt:112-118` | `DiagnosticNetwork.install(OkHttpClient.Builder())` | **PROXY** |
| A1 | MSC InnerTube (player/next/browse/search/sign-in TV flow) | `data/media/MediaClient.kt:44-56` (`newBuilder` of A) | inherits A | PROXY |
| A2 | Extraction host, session, oEmbed | `extractor/YoutubeExtractionHost.kt:155`, `YoutubeSessionProvider.kt:249`, `OEmbedTitleFetcher.kt:18` | inherit A | PROXY |
| A3 | Player media (all Media3 upstreams, live, HLS, manifests) | `player/datasource/PlayerDataSource.kt:110-170` and `YoutubeHttpDataSource.kt:497-509` (`sharedCallFactory` = `newBuilder` of A) | inherits A | PROXY |
| A4 | SponsorBlock, DeArrow, About/update check, sign-in service | `player/sponsor/SponsorBlockManager.kt:20`, `dearrow/DeArrowService.kt:44`, `ui/about/AboutActivity.kt:91`, `account/data/SignInService.kt:26` | inherit A | PROXY (simplest, no leak; small traffic) |
| A5 | Cast relay upstream (TV pulls from phone, phone pulls googlevideo) | `cast/LocalStreamProxy.kt:37`, `cast/CastController.kt:54-60` | inherits A | PROXY upstream. Keeps invariant 1 for casting |
| B | Download client (own Dispatcher and pool) | `downloader/net/YoutubeDownloadRequestAdapter.kt:91-104` (`DownloadHttpClients.create`), used at `di/AppModule.kt:156-176` | separate `OkHttpClient.Builder()` | **PROXY** (must be patched separately) |
| C | Thumbnails (Coil 3 is fed bytes only; `serviceLoaderEnabled(false)`) | `core/YoutubeThumbnail.kt:37-39` | separate builder | PROXY (`i.ytimg.com`; can be direct, but PROXY avoids leaking the watch list) |
| D | Gallery images | `gallery/GalleryImages.kt:16-17` | separate builder | PROXY |
| E | DLNA/Cast LAN control | `cast/LocalCastNetwork.kt:34-44` | `Proxy.NO_PROXY` and socket bound to the Wi-Fi `Network` | **DIRECT** (leave as is) |
| F | WebViews: account sign-in, traffic verification, hidden JS runtime (PoToken/BotGuard), EJS fallback | `account/ChatWebSignInActivity.kt:108`, `core/TrafficVerificationActivity.kt:62`, `extractor/JavascriptRuntime.kt:242`, `extractor/RemoteEjsRuntime.kt` (process `:youtube_ejs`) | Chromium network stack, ignores OkHttp | PROXY through `androidx.webkit.ProxyController` (per process) |
| G | `androidx.javascriptengine` sandbox (`AndroidChallengeSolver`) | `extractor/AndroidChallengeSolver.kt` | no network (JS only) | n/a |

**Injection point (to create).** The base builder is copy-pasted today as `DiagnosticNetwork.install(OkHttpClient.Builder())`
in 4 places (A, B, C, D). Add `net/NetworkStack.baseBuilder()` that returns that builder plus:
- `.proxySelector(ExitProxySelector)`: one process-wide selector that reads the current `Exit`.
  OkHttp asks the selector for every new route, so clients don't need rebuilding when the exit changes.
- `.proxyAuthenticator(ExitProxyAuthenticator)` for HTTP CONNECT credentials (custom proxies only).
- registration of the client's `ConnectionPool` in a `PoolRegistry`, so an exit switch can `evictAll()`.
  Pooled keep-alive sockets would otherwise keep using the old route. Clients derived with `newBuilder()` share their parent's pool,
  so 4 pools in total.

As a safety net, set `ProxySelector.setDefault(ExitProxySelector)` in `App.onCreate`. It must run **before**
`EjsRuntimeProcess.initialize` returns, because that early-return path runs in `:youtube_ejs`. That covers any future `HttpURLConnection`
and any library that builds an OkHttp client with defaults (OkHttp's builder defaults to `ProxySelector.getDefault()`).
`ExitProxySelector.select(uri)` returns `NO_PROXY` for loopback, RFC1918, link-local and `*.local`, which keeps E and A5's
TV-facing side direct. Otherwise it returns the current exit's `java.net.Proxy`. `connectFailed()` feeds `ExitManager`.

## 2. Proxy path

**Types accepted:** SOCKS5 and HTTP proxies that support `CONNECT` on port 443. SOCKS4 and SOCKS4a are dropped,
because Java's SOCKS client falls back to v4 and v4 resolves locally. Plain-HTTP-only proxies are dropped, because they can't `CONNECT`.
- **HTTP CONNECT:** `Proxy(Type.HTTP, InetSocketAddress(ip, port))`. OkHttp opens a tunnel with `CONNECT host:443`. The proxy
  resolves the hostname, so it is remote DNS by construction. The proxy endpoint comes from the list as an IP literal, so there is no lookup for it either.
- **SOCKS5:** `Proxy(Type.SOCKS, ...)`. For SOCKS routes OkHttp's `RouteSelector` passes
  `InetSocketAddress.createUnresolved(host, port)`, and the JDK `SocksSocketImpl` (libcore is OpenJDK-based since
  Android 7, and minSdk is 24) sends the domain name (ATYP 0x03). DNS is then resolved by the proxy. **Test:** with the
  feature ON, the device resolver must see no `*.googlevideo.com`/`youtubei` queries (check with `adb shell dumpsys dnsresolver` or a pcap).
- **Auth:** HTTP CONNECT uses `proxyAuthenticator` (Basic, answering 407). SOCKS5 user/pass has no OkHttp API. The only route is the
  global `java.net.Authenticator`, which is process-wide and leaky. Free lists are unauthenticated anyway, so SOCKS auth is only offered
  for a user-entered custom proxy, through `Authenticator.setDefault` scoped to that host and port.
  WebView `ProxyConfig` has no credential API. An authenticated proxy therefore cannot cover WebViews, and the app keeps WebViews on the
  VPN path or direct (see F below).
- **WebView (F):** `ProxyController.getInstance().setProxyOverride(ProxyConfig.Builder().addProxyRule("socks://ip:port")...)`
  needs `WebViewFeature.PROXY_OVERRIDE`. Schemes are `HTTP`, `HTTPS`, `SOCKS` ([ProxyConfig.Builder docs][wv]). Keep the
  implicit localhost and link-local bypass. The call must be made in the main process **and** in `:youtube_ejs`. If the feature is unsupported,
  PoToken and BotGuard minting would leave from the real IP. In that case, mark the exit "proxy-limited" and prefer VPN mode for the session.
  **Sign-in WebView:** keep it direct by default (owner decision D5). The OAuth TV flow over A1 goes through the proxy either way.
- **TLS end-to-end:** every YouTube and Google request is HTTPS, and the app trusts the system CAs only (no user CAs). A malicious proxy
  **can** see the destination hostname (SNI and CONNECT line), the IP, timing and byte counts. It can also drop, throttle or log traffic,
  and serve its own exit country. It **cannot** read or modify the content: video IDs, cookies, OAuth tokens, PoTokens and watch history
  all travel inside TLS. A forged certificate fails the handshake, and the app must show that as an exit failure, never as a prompt to "trust".
  The only plaintext traffic is the LAN DLNA traffic (E), which never touches the proxy.

## 3. VPN path

**Mechanism.** `MinTubeVpnService : VpnService` uses `Builder.addAllowedApplication(packageName)`, so only MinTube's UID is tunnelled.
Other apps use the network "as if the VPN wasn't running" ([VpnService.Builder][vpnb]). The process-wide
ProxySelector is set to NO_PROXY in this mode. Points to handle:
- **Only one VPN at a time.** "there can be only one active VPN interface at the same time" ([establish()][vpnb]).
  `VpnService.prepare()` takes over from the user's current VPN app, which matters to this owner. Before preparing, check
  `NetworkCapabilities.TRANSPORT_VPN` on the active network. If a VPN is already active, Auto mode stays on Proxy, and the UI explains that the external VPN already covers MinTube.
- **Loop avoidance.** The tunnel's own upstream socket belongs to the same UID, so it must be `protect()`ed. ics-openvpn already does this.
- **LAN and Cast.** `LocalCastNetwork` binds sockets to the Wi-Fi `Network`. That only works if the VPN calls `allowBypass()`, or if the
  routes exclude private ranges. Use the routes approach: `excludeRoute()` on API 33+, and on 24-32 add the complement of
  RFC1918, 169.254/16 and 224/4 as explicit `addRoute` entries.
- **DNS.** `addDnsServer` must point at a resolver reachable through the tunnel (for example the VPN's pushed DNS, or 8.8.8.8 routed in-tunnel), so lookups don't leak to the ISP.
- **Foreground notification** while the tunnel is up. Android 14+ requires `foregroundServiceType="systemExempted"`, which the docs list for VPN apps (verify on 16).

**Protocols from free sources.** VPN Gate servers speak SoftEther, L2TP/IPsec, OpenVPN and MS-SSTP ([VPN Gate][vg]).
The API ships a ready OpenVPN config per server. L2TP/IPsec has no app-level API (since Android 12 it isn't even an option in system
settings), and SSTP and SoftEther have no maintained embeddable Android clients. **OpenVPN is the only usable VPN Gate protocol.**
No free, public, country-selectable WireGuard source exists. Cloudflare WARP exits near the user, with no country choice. Proton Free
needs an account, its configs are personal, and it can't be redistributed.

**Libraries (licence text quoted from each repo).**

| Library | Licence (exact) | Fit |
|---|---|---|
| **ics-openvpn** (OpenVPN for Android) | `doc/LICENSE.txt`: "either version 2 of the License, or (at your option) any later version" = **GPLv2-or-later**. Author: "It is NOT about creating a library", but forks must publish source ([README][ics]) | Mature. Bundles native OpenVPN 2.6 and OpenSSL. Handles `protect()`, reconnects and VPN Gate `.ovpn` as-is |
| OpenVPN 2.x core (inside ics-openvpn) | `COPYING`: "distributed under the GPL license version 2" with OpenSSL, Apache-2.0 and LZO linking exceptions ([COPYING][ovpn2]). Source headers say v2 with no "or later" clause, so **GPLv2-only** | n/a (comes with ics-openvpn) |
| OpenVPN 3 Core | `LICENSE.md`: "GNU Affero General Public License version 3 **or** Mozilla Public License Version 2.0" ([LICENSE.md][ovpn3]) | C++ client library. No Android VpnService glue, so we would write the JNI, the TUN builder and the reconnect logic ourselves |
| wireguard-android / `com.wireguard.android:tunnel` | `COPYING`: "Apache License, Version 2.0" ([COPYING][wg]) | Excellent, but no free WireGuard server source exists, so it is useless here |
| **hev-socks5-tunnel** | "MIT" ([README][hev]) | tun2socks in C. Prebuilt AAR with 4 ABIs. Supports SOCKS5 user/pass and UDP relay |
| badvpn tun2socks | BSD-3-Clause ([COPYING][badvpn]) | Unmaintained since about 2019 |
| xjasonlyu/tun2socks | GPL-3.0 (Go) | Pulls in the Go runtime, about 8-10 MB per ABI |

**Licence line (owner decision taken):** ics-openvpn is GPLv2-or-later, and the OpenVPN 2 core it bundles is GPLv2-only. It ships as the VPN engine regardless.

**Two VPN engines, one service.**
- **VPN-over-proxy (tun2socks):** `VpnService` and hev-socks5-tunnel point at the *same* SOCKS5 exits as Proxy mode. It adds
  coverage for everything the proxy can't reach: WebView without `PROXY_OVERRIDE`, authenticated SOCKS5 for WebViews, and any future stack.
  It also provides a kill-switch by construction. It adds no new servers. Cost: about 0.3-0.6 MB per ABI (measure), plus userspace packet copying,
  roughly a few % CPU at 10 Mb/s.
- **OpenVPN (VPN Gate):** ics-openvpn's `main` module is vendored as a Gradle module. Integrate it through its `IOpenVPNAPIService`
  AIDL or by starting `OpenVPNService` with an in-memory `VpnProfile` built from `OpenVPN_ConfigData_Base64`, with allowed apps = MinTube.
  Cost: the first NDK build in this repo (CMake for openvpn, OpenSSL, lz4 and lzo), about 3-5 MB per ABI, and CI time.
  Mitigate with ABI splits (`arm64-v8a` and `armeabi-v7a` only). Battery: AES-GCM on ARMv8 crypto extensions is cheap, and the
  main extra cost is the keepalive radio wake-ups. Use `keepalive 25 120`, and tear the tunnel down when playback is idle for more than 10 min (D6).

## 4. Free server sources (sampled 2026-11-21)

| Source | Endpoint and format | Country / speed fields | Freshness | Licence / terms | Observed |
|---|---|---|---|---|---|
| **VPN Gate** | `https://www.vpngate.net/api/iphone/`. CSV after a `*vpn_servers` line: `HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64` | `CountryShort` and `Speed` (bit/s) are measured by VPN Gate, `Ping` (ms), and `Score` | live (volunteer servers come and go) | Academic experiment, University of Tsukuba. Connection logs are kept "for three or more months", including source IP and destination FQDNs ([abuse policy][vga]) | **27 servers: JP 20, KR 6, US 2.** Use HTTPS: plain HTTP returned a truncated list and is MITM-able |
| **proxifly/free-proxy-list** | `cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/countries/{CC}/data.json` and `/protocols/{socks5,http}/data.json`. Fields: `proxy, protocol, ip, port, https, anonymity, score, geolocation.country` | per-country files; no speed | "Every 5 minutes" | GPL-3.0 ([repo][pfx]) | about 12k entries claimed, including many "transparent" ones |
| **monosans/proxy-list** | `raw.githubusercontent.com/monosans/proxy-list/main/proxies.json`. Fields: `protocol, host, port, timeout, exit_ip, asn, geolocation.country.iso_code` | `timeout` (s) is measured, and files are "sorted fastest first" | hourly re-check | MIT. The GeoLite fields are CC BY-SA 4.0 and require MaxMind attribution in About ([repo][mono]) | **954 entries: http 508, socks4 369, socks5 77.** Countries: ID 257, CN 79, RU 51, IN 44, BR 42, US 40 |
| ProxyScrape | `api.proxyscrape.com/v4/free-proxy-list/get?request=display_proxies&protocol=socks5&country=jp&format=json`. Fields: `alive, anonymity, average_timeout, ip_data.countryCode, ip_data.hosting, last_seen` | yes, plus `hosting` and `proxy` flags | live | Commercial API. Free endpoint, but the ToS for in-app redistribution is unclear (D3) | JP socks5: 14 rows |

**Reality check (drives the UX).** Public proxy lists are mostly hijacked or misconfigured hosts and datacenter IPs. Typically more than 70%
of entries fail a real HTTPS request at any moment, and the survivors last minutes to hours. YouTube's anti-abuse targets exactly these
IPs ("Sign in to confirm you're not a bot", playability `LOGIN_REQUIRED`, or 403 on media). Expect **a few working exits per
country, at 0.5-5 Mb/s**. VPN Gate is more stable and faster (residential and university volunteers, some 100+ Mb/s), but almost
only JP/KR/US, and it logs. Consequences:
- The prober (section 5) is the product. Raw list size means nothing.
- The UI lists only countries with at least one verified exit, and says honestly when a country has none.
- A "Custom proxy / server" entry (the owner's own SOCKS5 or OpenVPN) is first-class. It is the one reliable route for this owner.
- Source selection: Phase 1 uses proxifly and monosans (SOCKS5 and HTTP CONNECT only). Phase 2 adds VPN Gate. ProxyScrape stays off until D3.
- Sources are pluggable (`ExitSource` interface), the source URLs are remote-overridable through a pinned JSON in the MinTube repo
  (D4), and the APK ships **no** server IPs, only source URLs.

## 5. Auto-update and speed measurement

**Data (Room, new `RegionDatabase`, or a new entity in an existing DB with a migration).** `ExitCandidate(id = "socks5://ip:port" | "ovpn:host",
kind {SOCKS5, HTTP_CONNECT, OPENVPN, CUSTOM}, country, source, sourceSpeed, firstSeen, lastListed, latencyMs, mbps, ytOk,
gvOk, lastProbeAt, consecutiveFails, bannedUntil, lastUsedOk)`. Keep the last 7 days of probe history, max 3,000 rows. OpenVPN configs are stored as gzipped blobs.

**Refresh.**
- `ExitRefreshWorker` (WorkManager `PeriodicWorkRequest`, 6 h; constraints: `UNMETERED` and `batteryNotLow`). It is enqueued only
  while the feature is ON, and cancelled when it is OFF. Steps: fetch the sources (direct client, 10 s timeout, 5 MB cap each), merge, run Stage 1 and Stage 2 on the top N.
- **On screen open:** if the last refresh is older than 10 min, refresh in the foreground with a progress chip. Cached data shows instantly.
- **On country pick:** run a deep probe of the top 3 in that country before connecting.

**Prober (`ExitProber`).** Each stage short-circuits. Every probe uses a throwaway OkHttp client with `proxy(p)`, no cookies and no auth.
1. **Reach** (all candidates of the selected countries, plus the top 40 per country by source score): TCP connect, SOCKS/CONNECT handshake and
   TLS to `www.youtube.com:443`, then `GET https://www.youtube.com/generate_204` and expect 204. Record the latency. Timeout 4 s.
2. **Throughput** (the 10 best by latency per country): `GET https://i.ytimg.com/vi/<fixed public id>/maxresdefault.jpg` with a 256 KB cap,
   or a Range request on a Google static asset. Compute Mb/s = bytes×8 / (t_last − t_firstByte). This is the Google edge, a good proxy for googlevideo.
3. **YouTube verify** (deep probe, the top 3 of the chosen country only, and always before first use): an InnerTube `player` request for a
   fixed public test video through the candidate. Require `playabilityStatus=OK`, then fetch 64 KB of one itag from googlevideo
   **through the same candidate**. This proves the whole chain: no bot wall, IP binding, and a country that is actually different (D7: optionally a
   region-locked canary video per country).
- **Concurrency:** 8 parallel probes on Wi-Fi and 3 on cellular, and never during active playback (playback sets a gate).
- **Data guard:** about 256 KB × 10 per country on Wi-Fi. On cellular, Stage 2 runs only for the picked country, and the background worker never runs on metered networks.
- **Battery guard:** no wakelocks, everything runs inside a Worker, and probing stops when `PowerManager.isPowerSaveMode`.
- **Sort within a country:** verified first, then by `mbps` descending, then by `latencyMs`. Penalty: `consecutiveFails ≥ 2` → `bannedUntil = now + 2h·2^k`.
- **Country summary:** best Mb/s and the number of verified exits. It is shown in the picker.

**Runtime health and failover (`ExitManager`, a state machine).**
`OFF → CONNECTING(country) → CONNECTED(exit, epoch) → DEGRADED → FAILOVER → CONNECTED | FAILED`.
- **Triggers:** `ProxySelector.connectFailed`. A failure-counting interceptor in `NetworkStack` (2 consecutive `IOException`s on YouTube hosts).
  A googlevideo 403 that persists after the existing `ForbiddenRecovery` and `PlaybackRecovery` re-resolve. A playability bot wall. Sustained throughput below the
  current format bitrate for 20 s, read from the `DiagnosticNetwork` traces.
- **Failover:** pick the next exit in the same country. Bump `exitEpoch`, `PoolRegistry.evictAll()`, reapply the WebView override, and reset
  the visitor (`MediaSession.rememberVisitor`), because visitor data minted in VN may carry geo. Invalidate player responses and `MediaRequestPlan`s from the old epoch
  (`YoutubeMediaRequests.isCurrent` gains an epoch check), then re-resolve and resume at the current position.
  In-flight downloads hit 403 and recover through the existing `ForbiddenRecovery` path.
- **Ceiling:** after 3 failovers in 5 min, or when the country is empty, move to `FAILED` and show the status and dialog. Invariant 4 applies.
- In **Auto** mode, `FAILED` on Proxy moves to VPN Gate if it lists the country (Phase 2), and only then reports the failure.

## 6. UX

Entry point: Settings › **Xem từ quốc gia khác**. It is a new row in `settings/presentation/SettingsScreen.kt` that opens `RegionAccessScreen`.
Everything defaults to **Tự động** (Automatic): pick the fastest server, and switch automatically when one fails.

```
┌──────────────────────────────────────────┐  ┌──────────────────────────────────────────┐
│ ←  Xem từ quốc gia khác                  │  │ ←  Chọn quốc gia            🔍           │
│──────────────────────────────────────────│  │──────────────────────────────────────────│
│ Bật                               [ ●  ] │  │ ✓ Tự động (nhanh nhất)                   │
│ ● Đang kết nối qua 🇯🇵 Nhật Bản · 12 Mb/s │  │──────────────────────────────────────────│
│   Máy chủ 2/5 · tự đổi khi lỗi           │  │ 🇯🇵 Nhật Bản        12 Mb/s   ●●●  5 mc   │
│──────────────────────────────────────────│  │ 🇺🇸 Hoa Kỳ           6 Mb/s   ●●○  3 mc   │
│ Quốc gia            🇯🇵 Nhật Bản       ›  │  │ 🇰🇷 Hàn Quốc         4 Mb/s   ●●○  2 mc   │
│ Chế độ   [Tự động] [Proxy] [VPN]         │  │ 🇸🇬 Singapore      1.2 Mb/s   ●○○  1 mc   │
│   Tự động: dùng proxy, cần thì dùng VPN  │  │──────────────────────────────────────────│
│──────────────────────────────────────────│  │ Chưa có máy chủ dùng được                │
│ Tự chọn máy chủ nhanh nhất        [ ● ]  │  │ 🇬🇧 Anh  🇩🇪 Đức  🇫🇷 Pháp   (đang thử…)   │
│ Danh sách cập nhật 4 phút trước   ↻      │  │──────────────────────────────────────────│
│ Máy chủ của tôi (nâng cao)            ›  │  │ Cập nhật 4 phút trước · 38 máy chủ ✓     │
│ ⓘ Về quyền riêng tư                   ›  │  │ 720p cần ~2.5 Mb/s, 1080p ~5 Mb/s        │
└──────────────────────────────────────────┘  └──────────────────────────────────────────┘
                Screen 1: main                           Screen 2: country picker

┌──────────────────────────────────────────┐  ┌──────────────────────────────────────────┐
│   Video này không xem được ở Việt Nam    │  │  Trước khi bật (hiện một lần)            │
│                                          │  │  • Máy chủ miễn phí do bên thứ ba vận    │
│   [ Thử xem qua quốc gia khác ]          │  │    hành. Họ thấy bạn xem YouTube lúc nào │
│     🇯🇵 Nhật Bản · 12 Mb/s   (Đổi)        │  │    và bao nhiêu, nhưng không thấy video  │
│   [ Thử lại ]        [ Chi tiết ]        │  │    hay tài khoản (đã mã hoá HTTPS).      │
│                                          │  │  • Máy chủ VPN Gate lưu nhật ký kết nối  │
│  (đang thử máy chủ 1/3 … ▓▓▓░░)          │  │    (IP của bạn) ít nhất 3 tháng.         │
└──────────────────────────────────────────┘  │  • Có thể chậm, chập chờn; YouTube có    │
   Screen 3: player error overlay              │    thể bắt xác minh hoặc chặn.           │
                                               │  • Tài khoản có thể bị Google hỏi xác    │
                                               │    minh khi đăng nhập từ nước khác.      │
                                               │  • Chế độ VPN thay thế VPN khác đang bật.│
                                               │          [ Hủy ]   [ Tôi hiểu, bật ]     │
                                               └──────────────────────────────────────────┘
```
- **Status line states:** `Tắt` · `Đang tìm máy chủ ở Nhật Bản…` · `Đang kết nối qua 🇯🇵 Nhật Bản · 12 Mb/s` ·
  `Đã chuyển sang máy chủ khác` (snackbar) · `Không kết nối được qua Nhật Bản. [Thử nước khác] [Tắt]`. Speed shows the
  live Mb/s from the `DiagnosticNetwork` read counters while playing, and the probe value otherwise.
- **Indicator:** a small flag badge on the player chrome while the feature is ON, so users understand why video is slower.
- **Player integration:** `ErrorOverlay` (`player/surface/PlayerControls.kt:600`) gets an optional `onTryRegion` action. It is shown when
  the failure classifies as `GEO_BLOCKED` (playability `UNPLAYABLE`/`CONTENT_CHECK_REQUIRED` with a region reason, or a live stream
  "not available in your country") or as a persistent googlevideo 403. One tap turns the feature ON with Auto, runs the deep probe of
  the best country (or of the first country in `availableCountries` from the microformat, when MSC exposes it), retries, and shows progress inline.
  The first time, the privacy sheet (screen 4) appears before connecting. Classification lives in `player/datasource/PlaybackLoadErrorPolicy.kt`
  and `PlaybackRecovery.kt`, surfaced through `PlayerSurface.kt:551`.
- **Advanced ("Máy chủ của tôi"):** add a SOCKS5/HTTP URL or import a `.ovpn` file. Custom servers always rank first.
- **Accessibility:** every row reads its flag as the country name, the speed dots carry text, and all strings are in `values-vi` and `values`.

## 7. Risks

Legal and ToS risk is accepted by the owner. The technical and privacy notes that must appear in the in-app warning are in screen 4 above.

## 8. Recommendation and phased plan

**Architecture.** One `ExitManager` owns a single `Exit` (proxy, VPN or custom) and an `exitEpoch`. All OkHttp stacks
get `NetworkStack.baseBuilder()`, which carries the shared `ExitProxySelector` and the pool registry. WebViews follow through `ProxyController`. VPN mode
uses the same Exit abstraction, with the selector returning NO_PROXY. `gl` is untouched. Media and API calls share the exit by construction.

**Phase 1: Proxy mode, auto-updating speed-sorted list, full routing (about 8-10 dev-days).**

| Area | Files |
|---|---|
| New `net/region/` package | `NetworkStack.kt` (baseBuilder + PoolRegistry), `ExitProxySelector.kt`, `ExitProxyAuthenticator.kt`, `ExitManager.kt` (state machine, epoch, failover), `ExitProber.kt`, `ExitRefreshWorker.kt`, `sources/{ExitSource,ProxiflySource,MonosansSource}.kt`, `data/{ExitCandidate,ExitDao,RegionDatabase}.kt`, `WebViewProxy.kt` |
| Builders to switch to `NetworkStack.baseBuilder()` | `di/AppModule.kt:113`, `downloader/net/YoutubeDownloadRequestAdapter.kt:97`, `core/YoutubeThumbnail.kt:37`, `gallery/GalleryImages.kt:16` |
| Keep direct, add a comment and a test | `cast/LocalCastNetwork.kt:34` |
| Init | `App.kt:33-36` (set the selector and the WebView override before the `EjsRuntimeProcess` early return), `extractor/RemoteEjsRuntime.kt` (override in `:youtube_ejs`) |
| Epoch invalidation | `extractor/YoutubeMediaRequests.kt` (`isCurrent`), `data/media/MediaSession` (visitor reset, scope the player-response cache key by epoch), `extractor/Extractor.kt` cache keys, `player/datasource/PlaybackRecovery.kt` (re-resolve on epoch change) |
| Failure classification and UI | `player/datasource/PlaybackLoadErrorPolicy.kt`, `player/surface/PlayerControls.kt:600`, `player/surface/PlayerSurface.kt:551`, `shorts/presentation/ShortPlaybackControls.kt:137`, `settings/presentation/{SettingsScreen,RegionAccessScreen}.kt`, `settings/domain/SettingsModels.kt`, `res/values{,-vi}/strings.xml` |
| Diagnostics | `diagnostics/DiagnosticNetwork.kt` (tag traces with exit id and epoch; never log the full proxy credentials) |

**Phase 2: VPN mode (about 8-12 dev-days, most of it the NDK and CI work).**
- **2a (about 3 days):** `MinTubeVpnService` with `addAllowedApplication(self)`, the private-range route complement, and hev-socks5-tunnel (MIT, AAR)
  over the Phase 1 SOCKS5 exits. Turn it on automatically when `PROXY_OVERRIDE` is missing or for custom authenticated SOCKS. This is the lightest step and gives a kill-switch.
- **2b (about 6-9 days):** VPN Gate source plus **ics-openvpn** vendored as a module (GPLv2-or-later; its bundled OpenVPN 2 core is GPLv2-only) for
  OpenVPN servers. Add `.ovpn` import for custom servers, ABI splits, and an NDK step in `ci/`. Manifest additions: the service with
  `BIND_VPN_SERVICE`, the `android.net.VpnService` intent filter, and the foreground type.
- Auto mode: Proxy first, then VPN Gate in the same country, then report the failure.

**Test plan.**
- **Unit (JVM):**
  - source parsers, fed real samples saved as fixtures (VPN Gate CSV with the `*`/`#` lines and the base64 column; proxifly and monosans JSON);
  - sort and penalty logic;
  - the `ExitManager` state machine with a fake clock (failover, ceiling, no direct fallback);
  - `ExitProxySelector` (LAN, loopback and `*.local` give NO_PROXY; everything else the exit);
  - SOCKS remote DNS (MockWebServer behind a tiny in-test SOCKS5 server, asserting a DOMAINNAME ATYP and no local `Dns` calls);
  - HTTP CONNECT 407 handling;
  - epoch invalidation (`isCurrent` is false after a bump; the player cache misses).
- **Integration:**
  - every client built by DI has the selector; a reflection test walks the Koin graph and every `OkHttpClient` has `proxySelector === ExitProxySelector`, or it is the documented LAN client;
  - `PoolRegistry.evictAll` drops the idle connections.
- **Emulator (androidTest):**
  - a local SOCKS5 server on the host (`10.0.2.2`), with the feature ON:
    - home, search, watch, live, download and thumbnail all pass through the proxy (proxy access log), with no direct sockets (`/proc/net/tcp` owned by the app UID shows only the proxy);
    - kill the proxy mid-playback, and the app fails over to a second local proxy and resumes at the same position;
    - disable all proxies, and the status reads FAILED with no direct requests;
  - Cast to a DLNA emulator stays on the LAN;
  - 2a: the VPN tunnel up and only the app UID routed (`dumpsys connectivity`).
- **Manual (owner, in VN):** a known geo-blocked live stream and VOD play through JP/US exits; a signed-in account keeps working; battery and data use over 1 h of playback on Proxy vs VPN Gate.

**Decisions the owner must make.**
- **D1** Ship Phase 2b (adds about 3-5 MB per ABI and the first NDK build), or stop at Phase 1 + 2a.
- **D2** Default mode: Auto (recommended) vs Proxy.
- **D3** Include ProxyScrape (unclear redistribution ToS).
- **D4** Host a remote-overridable `region-sources.json` in the MinTube GitHub repo (source URLs only, no IPs).
- **D5** Sign-in WebView direct (recommended) or through the exit.
- **D6** VPN idle teardown time (10 min proposed).
- **D7** Per-country canary videos for exit verification.
- **D8** Allow probing on cellular for the picked country (proposed: yes, capped at 3 MB per refresh).

[wv]: https://developer.android.com/reference/androidx/webkit/ProxyConfig.Builder
[vpnb]: https://developer.android.com/reference/android/net/VpnService.Builder
[vg]: https://www.vpngate.net/en/about_overview.aspx
[vga]: https://www.vpngate.net/en/about_abuse.aspx
[ics]: https://github.com/schwabe/ics-openvpn/blob/master/README.md
[ovpn2]: https://github.com/OpenVPN/openvpn/blob/master/COPYING
[ovpn3]: https://github.com/OpenVPN/openvpn3/blob/master/LICENSE.md
[wg]: https://github.com/WireGuard/wireguard-android/blob/master/COPYING
[hev]: https://github.com/heiher/hev-socks5-tunnel
[badvpn]: https://github.com/ambrop72/badvpn/blob/master/COPYING
[pfx]: https://github.com/proxifly/free-proxy-list
[mono]: https://github.com/monosans/proxy-list
