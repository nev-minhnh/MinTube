package com.hhst.youtubelite.core.time

import com.hhst.youtubelite.core.time.PublishedAge.Unit
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublishedAgeTest {
    private val now = Instant.parse("2026-11-23T12:00:00Z")
    private fun parse(raw: String?) = PublishedAges.parse(raw, now, java.time.ZoneOffset.UTC)
    private fun shape(raw: String) = parse(raw)?.let { it.count to it.unit }

    @Test fun englishRelative() {
        val table = mapOf(
            "5 days ago" to PublishedAge(5, Unit.Day), "1 day ago" to PublishedAge(1, Unit.Day),
            "a day ago" to PublishedAge(1, Unit.Day), "an hour ago" to PublishedAge(1, Unit.Hour),
            "2 hours ago" to PublishedAge(2, Unit.Hour), "3 weeks ago" to PublishedAge(3, Unit.Week),
            "1 month ago" to PublishedAge(1, Unit.Month), "2 years ago" to PublishedAge(2, Unit.Year),
            "30 seconds ago" to PublishedAge(30, Unit.Second), "10 minutes ago" to PublishedAge(10, Unit.Minute),
            "1 minute ago" to PublishedAge(1, Unit.Minute), "5 DAYS AGO" to PublishedAge(5, Unit.Day),
            "Streamed 2 hours ago" to PublishedAge(2, Unit.Hour), "Premiered 3 weeks ago" to PublishedAge(3, Unit.Week),
            "Updated 1 day ago" to PublishedAge(1, Unit.Day), "streamed 4 months ago" to PublishedAge(4, Unit.Month),
            "  5 days ago " to PublishedAge(5, Unit.Day),
        )
        for ((raw, expected) in table) assertEquals(raw, expected, parse(raw))
    }

    @Test fun vietnameseRelative() {
        val table = mapOf(
            "5 ngày trước" to PublishedAge(5, Unit.Day), "2 giờ trước" to PublishedAge(2, Unit.Hour),
            "3 tuần trước" to PublishedAge(3, Unit.Week), "1 tháng trước" to PublishedAge(1, Unit.Month),
            "2 năm trước" to PublishedAge(2, Unit.Year), "30 giây trước" to PublishedAge(30, Unit.Second),
            "10 phút trước" to PublishedAge(10, Unit.Minute),
            "Đã phát trực tiếp 2 giờ trước" to PublishedAge(2, Unit.Hour),
            "Đã công chiếu 3 tuần trước" to PublishedAge(3, Unit.Week),
            "Đã cập nhật 1 ngày trước" to PublishedAge(1, Unit.Day),
        )
        for ((raw, expected) in table) assertEquals(raw, expected, parse(raw))
    }

    /** Owner rule: whole units only (24 h = 1 day, 23 h/25 h stay hours, 7 d = 1 week, 30 d = 1 month), dates after 12 months. */
    @Test fun absoluteDatesUseTheLargestExactUnit() {
        val table = mapOf(
            "2026-11-23T11:59:30Z" to (30 to Unit.Second),
            "2026-11-23T11:30:00Z" to (30 to Unit.Minute),
            "2026-11-23T11:00:00Z" to (1 to Unit.Hour),
            "2026-11-23T18:00:00+07:00" to (1 to Unit.Hour),
            "2026-11-22T13:00:00Z" to (23 to Unit.Hour),
            "2026-11-22T12:00:00Z" to (1 to Unit.Day),
            "2026-11-22T11:00:00Z" to (25 to Unit.Hour),
            "2026-11-20T10:00:00Z" to (74 to Unit.Hour),
            "2026-11-20T12:00:00Z" to (3 to Unit.Day),
            "2026-11-17T12:00:00Z" to (6 to Unit.Day),
            "2026-11-16T12:00:00Z" to (1 to Unit.Week),
            "2026-11-15T12:00:00Z" to (8 to Unit.Day),
            "2026-11-23" to (0 to Unit.Today),
            "2026-11-18" to (5 to Unit.Day),
            "Nov 20, 2026" to (3 to Unit.Day),
            "November 20, 2026" to (3 to Unit.Day),
            "Nov 10, 2026" to (13 to Unit.Day),
            "Nov 9, 2026" to (2 to Unit.Week),
            "Nov 8, 2026" to (15 to Unit.Day),
            "Nov 2, 2026" to (3 to Unit.Week),
            "Oct 24, 2026" to (1 to Unit.Month),
            "Oct 23, 2026" to (31 to Unit.Day),
            "Sep 24, 2026" to (2 to Unit.Month),
            "Sep 14, 2026" to (10 to Unit.Week),
            "Sep 13, 2026" to (71 to Unit.Day),
            "Nov 28, 2025" to (12 to Unit.Month),
            "Nov 23, 2025" to (1 to Unit.Date),
            "October 20, 2025" to (1 to Unit.Date),
        )
        for ((raw, expected) in table) assertEquals(raw, expected, shape(raw))
    }

    @Test fun datesOlderThanTwelveMonthsKeepTheirDay() {
        assertEquals(java.time.LocalDate.of(2025, 10, 20), parse("October 20, 2025")?.date)
        assertEquals(java.time.LocalDate.of(2024, 1, 5), parse("2024-01-05T08:00:00Z")?.date)
    }

    @Test fun realDatesFilterOnExactHours() {
        assertEquals(74L, parse("2026-11-20T10:00:00Z")?.hours)
        assertEquals(1L, parse("2026-11-23")?.hours)
        assertEquals(120L, parse("2026-11-18")?.hours)
        assertEquals(399L * 24, parse("October 20, 2025")?.hours)
    }

    @Test fun youTubesRoundedTextAlsoReadsInWholeUnits() {
        assertEquals(1 to Unit.Week, shape("7 days ago"))
        assertEquals(2 to Unit.Week, shape("14 days ago"))
        assertEquals(1 to Unit.Month, shape("30 days ago"))
        assertEquals(1 to Unit.Day, shape("24 hours ago"))
        assertEquals(2 to Unit.Day, shape("48 giờ trước"))
        assertEquals(6 to Unit.Day, shape("6 days ago"))
        assertEquals(13 to Unit.Day, shape("13 ngày trước"))
        assertEquals(23 to Unit.Hour, shape("23 hours ago"))
        assertEquals(168L, parse("7 days ago")?.hours)
    }

    @Test fun futureAndGarbage() {
        for (raw in listOf("2026-11-24T00:00:00Z", "2027-01-01", "Dec 31, 2026", null, "", "   ", "LIVE",
            "Scheduled for 11/25/26", "1.2M views", "Premieres in 2 hours", "5 days", "ago", "0 days ago", "not a date"))
            assertNull(raw, parse(raw))
    }

    @Test fun hoursAreUpperEstimates() {
        assertEquals(1L, PublishedAge(30, Unit.Second).hours)
        assertEquals(1L, PublishedAge(45, Unit.Minute).hours)
        assertEquals(7L, PublishedAge(7, Unit.Hour).hours)
        assertEquals(120L, PublishedAge(5, Unit.Day).hours)
        assertEquals(504L, PublishedAge(3, Unit.Week).hours)
        assertEquals(1488L, PublishedAge(2, Unit.Month).hours)
        assertEquals(8784L, PublishedAge(1, Unit.Year).hours)
    }
}
