package com.hhst.youtubelite.data.media

import com.liskovsoft.youtubeapi.videoinfo.V2.BrowseTab
import com.liskovsoft.youtubeapi.videoinfo.V2.RequestContext
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Params recorded from live `hl=vi` / `hl=ko` channel responses (2026-11-21). */
class LocalizedTabDetectionTest {
    private val tvVideos = "8gYHEgVqAIABAg%3D%3D"
    private val tvShorts = "8gYHEgVyAIABAg%3D%3D"
    private val tvLive = "8gYHEgVKAIABAg%3D%3D"
    private val tvPlaylists = "8gYLEgliBAoCEGiAAQI%3D"
    private val tvPodcasts = "8gYHEgV6AIABAg%3D%3D"

    @Test fun tvTabsAreRecognizedByParamsWhateverTheirTitle() {
        assertTrue(ChannelTabKind.Videos.matches(BrowseTab("Video", "UCowner", tvVideos, false)))
        assertTrue(ChannelTabKind.Shorts.matches(BrowseTab("Shorts", "UCowner", tvShorts, false)))
        assertTrue(ChannelTabKind.Live.matches(BrowseTab("Video phát trực tiếp", "UCowner", tvLive, false)))
        assertTrue(ChannelTabKind.Playlists.matches(BrowseTab("Danh sách phát", "UCowner", tvPlaylists, false)))
        assertNull(ChannelTabKind.of(tvPodcasts))
        assertFalse(ChannelTabKind.Videos.matches(BrowseTab("Videos", "UCowner", tvShorts, false)))
    }

    @Test fun webTabsAreRecognizedByTheirUrlSlug() {
        assertEquals(ChannelTabKind.Videos, ChannelTabKind.of("EgZ2aWRlb3PyBgQKAjoA"))
        assertEquals(ChannelTabKind.Shorts, ChannelTabKind.of("EgZzaG9ydHPyBgUKA5oBAA%3D%3D"))
        assertEquals(ChannelTabKind.Live, ChannelTabKind.of("EgdzdHJlYW1z8gYECgJ6AA%3D%3D"))
        assertEquals(ChannelTabKind.Playlists, ChannelTabKind.of("EglwbGF5bGlzdHPyBgQKAkIA"))
        assertEquals(ChannelTabKind.Posts, ChannelTabKind.of("EgVwb3N0c_IGBAoCSgA%3D"))
        assertNull(ChannelTabKind.of("EghmZWF0dXJlZPIGBAoCMgA%3D"))
        assertTrue(ChannelTabKind.Videos.matches(BrowseTab("동영상", "UCowner", "EgZ2aWRlb3PyBgQKAjoA", false)))
    }

    @Test fun unrecognizedParamsFallBackToTheEnglishTitle() {
        assertTrue(ChannelTabKind.Shorts.matches(BrowseTab("Shorts", "UCowner", "shorts-command", false)))
        assertTrue(ChannelTabKind.Posts.matches(BrowseTab("Community", "UCowner", null, false)))
        assertFalse(ChannelTabKind.Videos.matches(BrowseTab("Video", "UCowner", null, false)))
    }

    @Test fun searchFilterOptionsAreRecognizedByParams() {
        // "Hôm nay" (Today) and "Kênh" (Channel) from an hl=vi search; the second is cumulative.
        assertEquals(2L, SearchFilterParams.value("EgIIAg%3D%3D", SearchFilterParams.UPLOAD_DATE))
        assertEquals(2L, SearchFilterParams.value("EgIQAg%3D%3D", SearchFilterParams.TYPE))
        assertNull(SearchFilterParams.value("EgIQAg%3D%3D", SearchFilterParams.UPLOAD_DATE))
        assertEquals(9L, SearchFilterParams.value("CAMSAhAJ", SearchFilterParams.TYPE))
        assertNull(SearchFilterParams.value("", SearchFilterParams.TYPE))
    }

    @Test fun vietnameseChannelStillFindsItsShortsAndLiveTabs() = runBlocking {
        val requests = mutableListOf<String>()
        val client = MediaClient(OkHttpClient.Builder().addInterceptor { chain ->
            val body = Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
            requests += Regex("\"(?:continuation|params)\"\\s*:\\s*\"([^\"]+)\"")
                .find(body)?.groupValues?.get(1) ?: "root"
            val answer = if (requests.last() == "root") navigation else page
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(answer.toResponseBody()).build()
        }.build(), { RequestContext(accessToken = "test-token", language = "vi", region = "VN") })
        val repository = MediaChannelRepository(client, MediaPostRepository(client))
        val shorts = repository.shorts("UCowner")
        assertEquals(listOf("video123456"), shorts.shorts.map { it.id })
        repository.live("UCowner")
        assertEquals(listOf("root", tvShorts, "root", tvLive), requests)
    }

    private fun tile(title: String, params: String) =
        """{"tileRenderer":{"style":"TILE_STYLE_YTLR_WORMHOLE_RECTANGULAR","header":{"tileHeaderRenderer":{"thumbnailOverlays":[{"tileMetadataRenderer":{"title":{"simpleText":"$title"}}}]}},"onSelectCommand":{"browseEndpoint":{"browseId":"UCowner","params":"$params"}}}}"""

    private val navigation = """{"contents":{"items":[${listOf(tile("Video", tvVideos), tile("Shorts", tvShorts),
        tile("Video phát trực tiếp", tvLive), tile("Danh sách phát", tvPlaylists)).joinToString(",")}]}}"""
    private val page = """{"contents":{"items":[{"tileRenderer":{"header":{"tileHeaderRenderer":{"thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/video123456/frame0.jpg"}]},"thumbnailOverlays":[{"tileMetadataRenderer":{"title":{"simpleText":"Tiêu đề gốc"}}}]}},"onSelectCommand":{"reelWatchEndpoint":{"videoId":"video123456"}}}}]}}"""
}
