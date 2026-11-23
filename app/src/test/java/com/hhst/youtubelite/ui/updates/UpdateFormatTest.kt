package com.hhst.youtubelite.ui.updates

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateFormatTest {
    private val zone = ZoneId.of("UTC")
    private fun millis(s: String) = ZonedDateTime.parse(s).toInstant().toEpochMilli()

    @Test fun lastCheckTodayIsTimeOnly() {
        val text = UpdateFormat.lastCheck(millis("2026-11-22T22:21:00Z"), millis("2026-11-22T23:00:00Z"), zone, Locale.US)
        assertTrue(text, text.contains("10:21"))
        assertFalse(text, text.contains("2026"))
    }

    @Test fun lastCheckOtherDayHasDateAndTime() {
        val text = UpdateFormat.lastCheck(millis("2026-11-20T22:21:00Z"), millis("2026-11-22T23:00:00Z"), zone, Locale.US)
        assertTrue(text, text.contains("10:21"))
        assertTrue(text, text.contains("2026"))
    }

    @Test fun releaseDateTakesTheIsoDay() {
        assertEquals("2026-11-22", UpdateFormat.releaseDate("2026-11-22T21:00:00Z"))
        assertNull(UpdateFormat.releaseDate(""))
        assertNull(UpdateFormat.releaseDate("2026-13-45T00:00:00Z"))
        assertNull(UpdateFormat.releaseDate("yesterday"))
    }
}
