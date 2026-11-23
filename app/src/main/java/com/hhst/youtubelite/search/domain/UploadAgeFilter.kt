package com.hhst.youtubelite.search.domain

import com.hhst.youtubelite.core.time.PublishedAges
import java.time.Instant

/** Client-side upload-date limit in hours; null for Anytime. Matches PublishedAge.hours estimates. */
val SearchUploadDate.maxAgeHours: Long? get() = when (this) {
    SearchUploadDate.Anytime -> null
    SearchUploadDate.Today -> 24L
    SearchUploadDate.ThisWeek -> 7L * 24
    SearchUploadDate.ThisMonth -> 31L * 24
    SearchUploadDate.ThisYear -> 366L * 24
}

/** Upload age in whole days, rounded up, or null when the text carries no age. */
fun uploadedDaysOf(age: String, now: Instant = Instant.now()): Int? =
    PublishedAges.parse(age, now)?.let { ((it.hours + 23) / 24).toInt() }

/** Videos and shorts older than the limit are dropped; unparsable ages, channels and playlists stay. */
fun SearchPage.filteredByUpload(uploaded: SearchUploadDate, now: Instant = Instant.now()): SearchPage {
    val limit = uploaded.maxAgeHours ?: return this
    fun SearchResult.fresh() = PublishedAges.parse(age, now)?.let { it.hours <= limit } ?: true
    return copy(results = results.filter { it.fresh() }, shorts = shorts.filter { it.fresh() })
}

/**
 * Filters [first] and, while nothing is left to show but a continuation exists, follows it for at most
 * [maxExtraPages] more pages; the returned cursor is the last one fetched so paging continues from there.
 */
fun filterUploadPaging(first: SearchPage, uploaded: SearchUploadDate, now: Instant = Instant.now(),
    maxExtraPages: Int = 3, fetch: (String) -> SearchPage): SearchPage {
    if (uploaded.maxAgeHours == null) return first
    var page = first.filteredByUpload(uploaded, now)
    var extra = 0
    while (extra < maxExtraPages && page.isVideoless() && page.nextCursor != null) {
        val next = fetch(page.nextCursor!!).filteredByUpload(uploaded, now)
        page = next.copy(query = page.query)
        extra++
    }
    return page
}

private fun SearchPage.isVideoless() = results.isEmpty() && shorts.isEmpty() && playlists.isEmpty() && channels.isEmpty()
