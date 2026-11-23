package com.hhst.youtubelite.core.time

import com.hhst.youtubelite.core.time.PublishedAge.Unit
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublishedAgeTest {
    private val now = Instant.parse("2026-11-23T12:00:00Z")
    private fun parse(raw: String?) = PublishedAges.parse(raw, now)

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

    @Test fun absoluteDates() {
        val table = mapOf(
            "2026-11-23T11:59:30Z" to PublishedAge(30, Unit.Second),
            "2026-11-23T11:00:00Z" to PublishedAge(1, Unit.Hour),
            "2026-11-23T11:30:00Z" to PublishedAge(30, Unit.Minute),
            "2026-11-23T18:00:00+07:00" to PublishedAge(1, Unit.Hour),
            "2026-11-18" to PublishedAge(5, Unit.Day),
            "2026-11-20T10:00:00Z" to PublishedAge(3, Unit.Day),
            "Nov 20, 2026" to PublishedAge(3, Unit.Day),
            "November 20, 2026" to PublishedAge(3, Unit.Day),
            "Nov 2, 2026" to PublishedAge(3, Unit.Week),
            "Sep 14, 2026" to PublishedAge(2, Unit.Month),
            "October 20, 2025" to PublishedAge(1, Unit.Year),
        )
        for ((raw, expected) in table) assertEquals(raw, expected, parse(raw))
    }

    @Test fun absoluteUnitBoundaries() {
        val at = { days: Long -> PublishedAges.parse(now.minusSeconds(days * 86_400).toString(), now) }
        assertEquals(PublishedAge(5, Unit.Day), at(5))
        assertEquals(PublishedAge(3, Unit.Week), at(21))
        assertEquals(PublishedAge(2, Unit.Month), at(70))
        assertEquals(PublishedAge(1, Unit.Year), at(400))
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
