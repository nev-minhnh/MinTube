package com.hhst.youtubelite.core.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.R
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How long ago something was published, read from the text YouTube sends ("5 days ago",
 * "Streamed 2 hours ago", "5 ngày trước", "Premiered 3 weeks ago") or from an ISO date ("2026-11-20").
 * One place for every video card and for the search upload-date filter.
 */
data class PublishedAge(
    val count: Int,
    val unit: Unit,
    /** Publish day when the source carried a real date; shown as dd/MM/yyyy for [Unit.Date]. */
    val date: LocalDate? = null,
    /** Exact age in whole hours (rounded up) when the source carried a real date; null for YouTube's rounded text. */
    val exactHours: Long? = null,
) {
    /** [Today]: a date-only source dated today. [Date]: a real date older than 12 months. */
    enum class Unit { Second, Minute, Hour, Day, Week, Month, Year, Today, Date }

    /**
     * Age in hours for filtering: exact when the source had a date, otherwise an upper estimate of
     * YouTube's rounded text (a month is 31 days, a year 366), so a filter never drops a video too early.
     */
    val hours: Long get() = exactHours ?: when (unit) {
        Unit.Second, Unit.Minute, Unit.Today -> 1L
        Unit.Hour -> count.toLong()
        Unit.Day -> 24L * count
        Unit.Week -> 168L * count
        Unit.Month -> 31L * 24 * count
        Unit.Year, Unit.Date -> 366L * 24 * count
    }
}

object PublishedAges {
    private val relative = Regex(
        """(?:^|\s)(\d{1,9}|an?|one|một)\s+(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?|yrs?|giây|phút|giờ|tiếng|ngày|tuần|tháng|năm)\s+(?:ago|trước)\s*$""")
    private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val isoDateTime = Regex("""^\d{4}-\d{2}-\d{2}T.+$""", RegexOption.IGNORE_CASE)
    private val shortMonth = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
    private val longMonth = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)

    /** Null when [raw] carries no age ("", "LIVE", "Scheduled for ..."). */
    fun parse(raw: String?, now: Instant = Instant.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): PublishedAge? {
        val text = Normalizer.normalize(raw.orEmpty().trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)
        if (text.isEmpty()) return null
        relative.find(text)?.let { match ->
            val count = match.groupValues[1].toIntOrNull() ?: 1
            if (count <= 0) return null
            val unit = when (match.groupValues[2]) {
                "second", "seconds", "sec", "secs", "giây" -> PublishedAge.Unit.Second
                "minute", "minutes", "min", "mins", "phút" -> PublishedAge.Unit.Minute
                "hour", "hours", "hr", "hrs", "giờ", "tiếng" -> PublishedAge.Unit.Hour
                "day", "days", "ngày" -> PublishedAge.Unit.Day
                "week", "weeks", "tuần" -> PublishedAge.Unit.Week
                "month", "months", "tháng" -> PublishedAge.Unit.Month
                else -> PublishedAge.Unit.Year
            }
            return PublishedAge(count, unit)
        }
        val dateOnly = isoDate.matches(text) || !isoDateTime.matches(text)
        val published = absolute(text) ?: return null
        return if (dateOnly) fromDate(published.atZone(ZoneOffset.UTC).toLocalDate(), now, zone)
        else fromInstant(published, now, zone)
    }

    private fun absolute(text: String): Instant? = try {
        when {
            isoDate.matches(text) -> LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant()
            isoDateTime.matches(text) -> try { OffsetDateTime.parse(text.uppercase(Locale.ROOT)).toInstant() }
                catch (_: java.time.format.DateTimeParseException) {
                    java.time.LocalDateTime.parse(text.uppercase(Locale.ROOT)).toInstant(ZoneOffset.UTC)
                }
            else -> {
                val title = text.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
                val date = try { LocalDate.parse(title, longMonth) } catch (_: java.time.format.DateTimeParseException) {
                    LocalDate.parse(title, shortMonth)
                }
                date.atStartOfDay(ZoneOffset.UTC).toInstant()
            }
        }
    } catch (_: java.time.format.DateTimeParseException) { null }

    /** Older than this many days, a real date is shown as dd/MM/yyyy instead of an age. */
    private const val DATE_AFTER_DAYS = 365L

    /**
     * A date with no time of day: whole days in the viewer's calendar. Today has no hour count.
     * Days read in the largest unit they fill exactly (30 days = 1 month, 14 days = 2 weeks, 13 days stay days).
     */
    private fun fromDate(published: LocalDate, now: Instant, zone: java.time.ZoneId): PublishedAge? {
        val today = now.atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(published, today)
        if (days < 0) return null
        val hours = maxOf(1L, days * 24)
        if (days == 0L) return PublishedAge(0, PublishedAge.Unit.Today, published, hours)
        return ofDays(days, published, hours)
    }

    /**
     * A real timestamp: minutes under an hour; hours unless they make whole days (24 h = 1 day, 23 h and
     * 25 h stay hours); whole days then read in the largest unit they fill exactly.
     */
    private fun fromInstant(published: Instant, now: Instant, zone: java.time.ZoneId): PublishedAge? {
        val elapsed = Duration.between(published, now)
        if (elapsed.isNegative) return null
        val seconds = elapsed.seconds
        val day = published.atZone(zone).toLocalDate()
        val hours = maxOf(1L, (seconds + 3_599) / 3_600)
        return when {
            seconds < 60 -> PublishedAge(seconds.toInt(), PublishedAge.Unit.Second, day, hours)
            seconds < 3_600 -> PublishedAge((seconds / 60).toInt(), PublishedAge.Unit.Minute, day, hours)
            (seconds / 3_600) % 24 != 0L -> PublishedAge((seconds / 3_600).toInt(), PublishedAge.Unit.Hour, day, hours)
            else -> ofDays(seconds / 86_400, day, hours)
        }
    }

    private fun ofDays(days: Long, published: LocalDate, hours: Long): PublishedAge = when {
        days >= DATE_AFTER_DAYS -> PublishedAge(1, PublishedAge.Unit.Date, published, hours)
        days % 30 == 0L -> PublishedAge((days / 30).toInt(), PublishedAge.Unit.Month, published, hours)
        days % 7 == 0L -> PublishedAge((days / 7).toInt(), PublishedAge.Unit.Week, published, hours)
        else -> PublishedAge(days.toInt(), PublishedAge.Unit.Day, published, hours)
    }
}

/** The age in the app's UI language ("5 days ago" / "5 ngày trước"); falls back to [raw] when it cannot be parsed. */
@Composable
fun publishedAgeText(raw: String?): String {
    val age = remember(raw) { PublishedAges.parse(raw) }
    if (age == null) return raw.orEmpty()
    val plural = when (age.unit) {
        PublishedAge.Unit.Second -> return stringResource(R.string.published_just_now)
        PublishedAge.Unit.Today -> return stringResource(R.string.published_today)
        PublishedAge.Unit.Date -> return age.date?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: raw.orEmpty()
        PublishedAge.Unit.Minute -> R.plurals.published_age_minutes
        PublishedAge.Unit.Hour -> R.plurals.published_age_hours
        PublishedAge.Unit.Day -> R.plurals.published_age_days
        PublishedAge.Unit.Week -> R.plurals.published_age_weeks
        PublishedAge.Unit.Month -> R.plurals.published_age_months
        PublishedAge.Unit.Year -> R.plurals.published_age_years
    }
    return pluralStringResource(plural, age.count, age.count)
}
