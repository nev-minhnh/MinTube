package com.hhst.youtubelite.data.media

import com.liskovsoft.youtubeapi.videoinfo.V2.BrowsePage
import com.liskovsoft.youtubeapi.videoinfo.V2.HomeSection
import com.liskovsoft.youtubeapi.videoinfo.V2.MediaGroup
import com.liskovsoft.youtubeapi.videoinfo.V2.MediaItem
import com.liskovsoft.youtubeapi.videoinfo.V2.playerAnswer
import com.liskovsoft.youtubeapi.videoinfo.V2.watchPage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentLanguageReadsTest {
    private fun video(id: String) = MediaItem("video", id, id, "Author", "https://example.com/$id.jpg", "1:00",
        actions = emptyList())
    private fun shelf(title: String, id: String, key: String? = null) = MediaGroup(title, listOf(video(id)), key)

    /** Shelf titles as an hl=vi TV directory returns them, with a decoy shelf first. */
    private fun vietnamese(section: HomeSection): BrowsePage {
        val wanted = when (section) {
            HomeSection.GAMING -> "Video thịnh hành"
            HomeSection.SPORTS -> "Thịnh hành"
            HomeSection.NEWS -> "Tin tức hàng đầu"
            else -> error("Unexpected section")
        }
        return BrowsePage(emptyList(), listOf(shelf("Trực tiếp", "decoy"), shelf(wanted, "vi-${section.name}")), emptyList(), null)
    }

    @Test fun vietnameseDirectoryShelvesAreFoundWithoutAnEnglishRequest() = runTest {
        var englishCalls = 0
        val result = HomeDiscovery(englishRead = { _, _, _ -> englishCalls++; error("not needed") }) { section, _, _ ->
            vietnamese(section)
        }.load(1)
        assertEquals(listOf("vi-GAMING", "vi-SPORTS", "vi-NEWS"), result.items.map { it.id })
        assertEquals(0, englishCalls)
    }

    @Test fun anUnknownLanguageFallsBackToEnglishAndKeepsItForContinuations() = runTest {
        val localizedKeys = mutableListOf<String?>()
        val englishKeys = mutableListOf<String?>()
        val discovery = HomeDiscovery(englishRead = { section, key, _ ->
            englishKeys += key
            val title = when (section) {
                HomeSection.GAMING -> "Trending videos"
                HomeSection.SPORTS -> "Trending"
                else -> "Top news"
            }
            if (key == null) BrowsePage(emptyList(), listOf(shelf(title, "en-${section.name}", "en-next-${section.name}")), emptyList(), null)
            else BrowsePage(emptyList(), listOf(shelf("", "en2-${section.name}")), emptyList(), null)
        }) { section, key, _ ->
            localizedKeys += key
            BrowsePage(emptyList(), listOf(shelf("인기 급상승 동영상", "ko-${section.name}")), emptyList(), null)
        }
        val first = discovery.load(5)
        assertEquals(listOf("en-GAMING", "en-SPORTS", "en-NEWS"), first.items.map { it.id })
        assertNotNull(first.nextKey)
        val next = discovery.load(5, continuation = first.nextKey)
        assertEquals(listOf("en2-GAMING", "en2-SPORTS", "en2-NEWS"), next.items.map { it.id })
        assertEquals(listOf<String?>(null, null, null), localizedKeys)
        assertTrue(englishKeys.containsAll(listOf("en-next-GAMING", "en-next-SPORTS", "en-next-NEWS")))
    }

    @Test fun originalTextPrefersTheCreatorsTitleAndDescription() {
        val page = watchPage("""{"videoPrimaryInfoRenderer":{"title":{"simpleText":"Người Cuối Cùng Trụ Lại Sẽ Thắng 250.000 Đô"}},
            "videoSecondaryInfoRenderer":{"owner":{"videoOwnerRenderer":{"title":{"simpleText":"MrBeast"},
            "navigationEndpoint":{"browseEndpoint":{"browseId":"UCchannel"}}}}}}""", "q6fgVVmdpgY")
        val details = playerAnswer("""{"videoDetails":{"title":"${'$'}250,000 Hunger Games In Real Life",
            "shortDescription":"Original description","author":"MrBeast","channelId":"UCchannel"}}""").details
        assertEquals("Người Cuối Cùng Trụ Lại Sẽ Thắng 250.000 Đô", page.toAppWatchPage("q6fgVVmdpgY", details).video.title)
        val original = page.toAppWatchPage("q6fgVVmdpgY", details, originalText = true).video
        assertEquals("\$250,000 Hunger Games In Real Life", original.title)
        assertEquals("Original description", original.description)
    }
}
