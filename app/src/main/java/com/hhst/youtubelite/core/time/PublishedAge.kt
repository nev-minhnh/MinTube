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
data class PublishedAge(val count: Int, val unit: Unit) {
    enum class Unit { Second, Minute, Hour, Day, Week, Month, Year }

    /** Upper estimate of the age in hours (a month is 31 days, a year 366), for filtering. */
    val hours: Long get() = when (unit) {
        Unit.Second, Unit.Minute -> 1L
        Unit.Hour -> count.toLong()
        Unit.Day -> 24L * count
        Unit.Week -> 168L * count
        Unit.Month -> 31L * 24 * count
        Unit.Year -> 366L * 24 * count
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
    fun parse(raw: String?, now: Instant = Instant.now()): PublishedAge? {
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
        val published = absolute(text) ?: return null
        return fromDuration(Duration.between(published, now))
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

    private fun fromDuration(elapsed: Duration): PublishedAge? {
        if (elapsed.isNegative) return null
        val seconds = elapsed.seconds
        val days = seconds / 86_400
        return when {
            seconds < 60 -> PublishedAge(seconds.toInt(), PublishedAge.Unit.Second)
            seconds < 3_600 -> PublishedAge((seconds / 60).toInt(), PublishedAge.Unit.Minute)
            seconds < 86_400 -> PublishedAge((seconds / 3_600).toInt(), PublishedAge.Unit.Hour)
            days < 7 -> PublishedAge(days.toInt(), PublishedAge.Unit.Day)
            days < 30 -> PublishedAge((days / 7).toInt(), PublishedAge.Unit.Week)
            days < 365 -> PublishedAge((days / 30).toInt(), PublishedAge.Unit.Month)
            else -> PublishedAge((days / 365).toInt(), PublishedAge.Unit.Year)
        }
    }
}

/** The age in the app's UI language ("5 days ago" / "5 ngày trước"); falls back to [raw] when it cannot be parsed. */
@Composable
fun publishedAgeText(raw: String?): String {
    val age = remember(raw) { PublishedAges.parse(raw) }
    if (age == null) return raw.orEmpty()
    val plural = when (age.unit) {
        PublishedAge.Unit.Second -> return stringResource(R.string.published_just_now)
        PublishedAge.Unit.Minute -> R.plurals.published_age_minutes
        PublishedAge.Unit.Hour -> R.plurals.published_age_hours
        PublishedAge.Unit.Day -> R.plurals.published_age_days
        PublishedAge.Unit.Week -> R.plurals.published_age_weeks
        PublishedAge.Unit.Month -> R.plurals.published_age_months
        PublishedAge.Unit.Year -> R.plurals.published_age_years
    }
    return pluralStringResource(plural, age.count, age.count)
}
