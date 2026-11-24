package com.hhst.youtubelite.core.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Owner request (v1.0.12): every view count reads in the same short form as the watch page. */
class ViewCountsTest {
    @Test fun parsesFullShortAndVietnameseForms() {
        val table = mapOf(
            "188,491 views" to 188_491L, "1 view" to 1L, "12 views" to 12L, "188K views" to 188_000L,
            "1.2M views" to 1_200_000L, "3B views" to 3_000_000_000L, "188 N lượt xem" to 188_000L,
            "1,2 Tr lượt xem" to 1_200_000L, "2 tỷ lượt xem" to 2_000_000_000L, "115.704 lượt xem" to 115_704L,
            "188491" to 188_491L, "No views" to 0L,
        )
        for ((raw, count) in table) assertEquals(raw, count, ViewCounts.parse(raw))
    }

    @Test fun leavesWhatIsNotAViewCount() {
        for (raw in listOf(null, "", "75K watching", "LIVE", "Premieres in 2 hours", "3:00")) assertNull(raw, ViewCounts.parse(raw))
    }

    @Test fun compactsInTheUiLanguage() {
        assertEquals("188K", ViewCounts.compact(188_491, Locale.ENGLISH))
        assertEquals("1.2M", ViewCounts.compact(1_234_567, Locale.ENGLISH))
        assertEquals("950", ViewCounts.compact(950, Locale.ENGLISH))
        assertEquals("2K", ViewCounts.compact(2_040, Locale.ENGLISH))
        val vi = Locale.forLanguageTag("vi")
        assertEquals("188 N", ViewCounts.compact(188_491, vi))
        assertEquals("1,2 Tr", ViewCounts.compact(1_234_567, vi))
        assertEquals("3 Tỷ", ViewCounts.compact(3_000_000_000, vi))
    }
}
