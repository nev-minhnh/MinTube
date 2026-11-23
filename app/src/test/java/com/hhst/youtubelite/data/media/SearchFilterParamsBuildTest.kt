package com.hhst.youtubelite.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Params MinTube sends when a signed-in search response offers no filter panel. */
class SearchFilterParamsBuildTest {
    @Test fun matchesYouTubesOwnUploadDateParams() {
        assertEquals("EgIIAg==", SearchFilterParams.build(2, null)) // Today
        assertEquals("EgIIAw==", SearchFilterParams.build(3, null)) // This week
        assertEquals("EgIIBA==", SearchFilterParams.build(4, null)) // This month
        assertEquals("EgIIBQ==", SearchFilterParams.build(5, null)) // This year
    }

    @Test fun combinesUploadDateAndTypeAndReadsBack() {
        val params = SearchFilterParams.build(3, 9)
        assertEquals(3L, SearchFilterParams.value(params, SearchFilterParams.UPLOAD_DATE))
        assertEquals(9L, SearchFilterParams.value(params, SearchFilterParams.TYPE))
        assertEquals(2L, SearchFilterParams.value(SearchFilterParams.build(null, 2), SearchFilterParams.TYPE))
    }

    @Test fun nothingSelectedSendsNoParams() = assertNull(SearchFilterParams.build(null, null))
}
