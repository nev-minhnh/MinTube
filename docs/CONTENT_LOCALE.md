# Content language and region (hl / gl)

Every InnerTube request MinTube sends goes through MSC's `contextualQuery`, which writes
`context.client.hl = RequestContext.language` and `gl = RequestContext.region`. MSC
defaults are `en` / `US`. Search suggestions (`suggestqueries.google.com`) take `hl`/`gl` from the same context.

## Before this change (master d980e16)
| Where | hl | gl |
|---|---|---|
| `MediaSession.snapshot()` / `freshContext()` (all requests) | `en` (MSC default) | `US` (MSC default) |
| `MediaCatalog` discovery (signed-out Home, Popular/Gaming/Sports/News/Music charts) | forced `en` | `US` |
| `RequestContext()` in `SignInService` (account list only) | `en` | `US` |

Home feed, trending/discovery, Explore, search + filters, suggestions, watch/next + player,
comments, channel + tabs, playlists, subscriptions, history and Shorts therefore all used
`hl=en&gl=US`. Users saw English "1.5M views" / "5 days ago", US trending, and titles of
non-English videos translated to English when YouTube has a translation.

## How YouTube picks a title (probed 2026-11-21, WEB + TVHTML5 clients)
- List renderers (search, browse, shelves) and `/next` return the title in **`hl`**: the
  creator's own translation or YouTube's machine translation when one exists, else the
  original. `hl=en`: HTV news "60 Giây Sáng ..." became "60 Seconds Morning ..."
  (4 of 17 results). `hl=vi`: every MrBeast and Mark Rober title became Vietnamese.
- `/player` `videoDetails.title` and `shortDescription` are always the original, whatever `hl` is.
  YouTube Anti Translate and YouTube No Translation rely on this, and on oEmbed, which also returns the original.
- No request flag turns translation off. `hl=lo` (Lao, the extensions' trick) returns
  originals but makes every UI string Lao ("ຍອດເບິ່ງ"). Unsupported tags (`und`, `zxx`, empty, `vi-Latn`) return HTTP 400.
- So one list request cannot return original titles together with localized view counts and dates.

## Settings (Settings > General, under Language)
- **Translate titles to English** (`content_language_english`, default ON): ON is `hl=en`, as before.
  OFF is `hl` = the app language (otherwise the system locale), mapped to YouTube codes
  (`vi`, `zh-CN`/`zh-TW`, `iw`, `no`, ...), with English for languages YouTube lacks. OFF also
  makes the watch page prefer the player's original title and description.
- **Trending in Vietnam** (`trending_region_vietnam`, default ON): ON is `gl=VN`; OFF is the device country, else `US`.
- Both keys are in `PreferenceKeys.DEFAULTS`, so backup and restore include them.

**gl scope (chosen):** gl applies to all requests, not only trending. One session value keeps
Home, discovery, Explore, search and suggestions consistent, so trending and search never disagree.
gl does not affect account data.

## Data-layer changes
- `MediaSession.setContentLocale` overlays hl/gl on `snapshot()`, so it also applies to `freshContext()`
  and `anonymous()`. A change after the first value bumps the generation, as the anonymous-mode
  switch does. In-flight reads of the old locale abort, pagination cursors and generation-keyed
  caches stop matching, and Home and Explore reload on resume, so no rows mix locales.
  An account write still being confirmed during the switch is treated as after an identity
  change (same rule as anonymous mode).
- `ContentLocaleSync` re-resolves the locale on toggle, import or reset, and on every activity create and resume (app or system language changes).
- Home on-disk snapshots are keyed by `ContentLocale.scope(account)` (hl+gl). `SharedWatchReads` and
  the search filter panel already key by language and region.
- Discovery uses the session hl/gl (the forced `en` is gone).

## Label matching made language-independent
| Was (English label) | Now |
|---|---|
| Channel tabs "Videos"/"Shorts"/"Live"/"Playlists" (`MediaRepositories`), "Shorts" (`MediaLibraryRepositories`), "Posts"/"Community" (`MediaPostRepository`) | `ChannelTabKind` decodes the tab params: the WEB URL slug in field 2, or the TV selector under field 110.2 (videos 13, shorts 14, live 9, playlists 12). The English title is only a fallback. In `hl=vi`, "Videos" is "Video" and "Live" is "Video phát trực tiếp". |
| Search filters "Today"/"This week"/"Channel"/"Playlist"/"Shorts" | English label first, then the option whose params carry the date code (field 2.1) or type code (field 2.2). Non-English transitions take the existing cold path. |
| Discovery shelves "Top charts"/"Trending videos"/"Trending"/"Top news" | These shelves have no stable id, and shelf order differs between languages. MinTube matches the English and Vietnamese titles. For other languages, or if YouTube renames a shelf, that source falls back to one `hl=en` request and keeps English for its continuations. |

## Limits
- With OFF and `hl=vi`, lists show creator or YouTube translations into Vietnamese where they exist
  (for example MrBeast). Vietnamese and Korean videos keep their originals. Only the watch page is
  guaranteed original. Per-row originals would need one `/player` or oEmbed call per item; the extensions do this, and MinTube does not.
- `viewMagnitude` (sort by popularity on channel Shorts and Live) parses English suffixes.
  Vietnamese "1,5 Tr" sorts approximately.
- App-formatted fallbacks such as "N views" when the server omits a count stay English (existing behaviour).
