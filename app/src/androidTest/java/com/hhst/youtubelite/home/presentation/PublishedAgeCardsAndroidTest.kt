package com.hhst.youtubelite.home.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hhst.youtubelite.channel.domain.ChannelVideo
import com.hhst.youtubelite.channel.presentation.ChannelVideoItem
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.playlist.domain.PlaylistEntry
import com.hhst.youtubelite.playlist.presentation.PlaylistVideoRow
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Every video card carries "views · age" in its metadata line. The age text itself comes from the
 * shared helper (and is localized), so these assertions keep to structure: the part count and the
 * leading parts, never the translated words.
 */
class PublishedAgeCardsAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun parts(prefix: String): List<String> {
        val node = compose.onNode(hasText(prefix, substring = true), useUnmergedTree = true).assertExists().fetchSemanticsNode()
        val text = node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
        return text.split(" · ")
    }

    @Test fun homeFeedCardShowsPublishedAfterViews() {
        compose.setContent {
            AppTheme { FeedCard(FeedItem.Video("v1", "Title", "Author", "1.2M views", "", "3:00", "ch", published = "5 days ago"), WindowClass.Compact, onAction = {}) }
        }
        // v1.0.11 layout: the channel has its own line; "views · age" is the meta line under it.
        compose.onNode(hasText("Author"), useUnmergedTree = true).assertExists()
        val parts = parts("1.2M views")
        assertEquals(2, parts.size)
        assertEquals("1.2M views", parts[0])
        assert(parts[1].isNotBlank())
    }

    @Test fun homeFeedCardWithoutPublishedLeavesNoOrphanSeparator() {
        compose.setContent {
            AppTheme { FeedCard(FeedItem.Video("v2", "Title", "Author", "1.2M views", "", "3:00", "ch"), WindowClass.Compact, onAction = {}) }
        }
        compose.onNode(hasText("Author"), useUnmergedTree = true).assertExists()
        assertEquals(listOf("1.2M views"), parts("1.2M views"))
    }

    @Test fun channelVideoRowShowsPublishedAfterViews() {
        compose.setContent {
            AppTheme { ChannelVideoItem(ChannelVideo("c1", "Title", "900 views", "2 hours ago", "1:00", ""), 360f, card = false, onAction = {}) }
        }
        val parts = parts("900 views")
        assertEquals(2, parts.size)
        assert(parts[1].isNotBlank())
    }

    @Test fun playlistRowShowsPublishedAfterViews() {
        compose.setContent {
            AppTheme { PlaylistVideoRow(PlaylistEntry("p1", "v", "Title", "Chan", "ch", "", "1:00", "7K views", "3 weeks ago"), onAction = {}) }
        }
        val parts = parts("7K views")
        assertEquals(2, parts.size)
        assert(parts[1].isNotBlank())
    }
}
