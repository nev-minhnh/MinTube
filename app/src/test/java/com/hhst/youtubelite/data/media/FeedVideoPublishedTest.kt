package com.hhst.youtubelite.data.media

import com.liskovsoft.youtubeapi.videoinfo.V2.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedVideoPublishedTest {
    private fun item(views: String?, published: String?) = MediaItem("video", "id", "Title", "Author", "https://example.com/t.jpg", "1:00",
        viewCount = views, published = published, actions = emptyList())

    @Test fun publishTextTravelsRawAndSeparateFromViews() {
        val video = item("1.2M views", "Streamed 2 hours ago").feedVideo()
        assertEquals("1.2M views", video.metadata)
        assertEquals("Streamed 2 hours ago", video.published)
    }

    @Test fun missingPartsStayEmpty() {
        val video = item(null, null).feedVideo()
        assertEquals("", video.metadata)
        assertEquals("", video.published)
    }
}
