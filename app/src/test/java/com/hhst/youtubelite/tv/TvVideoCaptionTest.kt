package com.hhst.youtubelite.tv

import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.home.TvHomeRows
import org.junit.Assert.assertEquals
import org.junit.Test

class TvVideoCaptionTest {
    @Test fun captionJoinsOnlyNonEmptyParts() {
        assertEquals("Ch · 1K views · 5 days ago", TvVideo("a", "t", "Ch", "1K views", published = "5 days ago").subtitle)
        assertEquals("Ch · 5 days ago", TvVideo("a", "t", "Ch", published = "5 days ago").subtitle)
        assertEquals("1K views", TvVideo("a", "t", metadata = "1K views").subtitle)
        assertEquals("", TvVideo("a", "t").subtitle)
    }

    @Test fun publishedIsNotRepeatedWhenMetadataAlreadyHoldsIt() {
        assertEquals("Ch · 1K views · 5 days ago",
            TvVideo("a", "t", "Ch", "1K views · 5 days ago", published = "5 days ago").subtitle)
    }

    @Test fun homeVideoCarriesTheRawAge() {
        val item = FeedItem.Video("v", "T", "Au", "2K views", "", "1:00", "c", age = "2 hours ago")
        val video = TvHomeRows.video(item)
        assertEquals("2 hours ago", video.published)
        assertEquals("Au · 2K views · 2 hours ago", video.subtitle)
    }
}
