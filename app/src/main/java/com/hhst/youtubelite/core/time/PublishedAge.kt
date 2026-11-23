package com.hhst.youtubelite.core.time

import androidx.compose.runtime.Composable
import java.time.Instant

/**
 * How long ago something was published, read from the text YouTube sends ("5 days ago",
 * "Streamed 2 hours ago", "5 ngày trước", "Premiered 3 weeks ago") or from an ISO date ("2026-11-20").
 * One place for every video card and for the search upload-date filter.
 */
data class PublishedAge(val count: Int, val unit: Unit) {
    enum class Unit { Second, Minute, Hour, Day, Week, Month, Year }

    /** Upper estimate of the age in hours (a month is 31 days, a year 366), for filtering. */
    val hours: Long get() = TODO("feat/age-core")
}

object PublishedAges {
    /** Null when [raw] carries no age ("", "LIVE", "Scheduled for ..."). */
    fun parse(raw: String?, now: Instant = Instant.now()): PublishedAge? = null // feat/age-core
}

/** The age in the app's UI language ("5 days ago" / "5 ngày trước"); falls back to [raw] when it cannot be parsed. */
@Composable
fun publishedAgeText(raw: String?): String = raw.orEmpty() // feat/age-core
