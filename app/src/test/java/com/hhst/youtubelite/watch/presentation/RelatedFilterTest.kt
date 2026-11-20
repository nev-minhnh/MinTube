package com.hhst.youtubelite.watch.presentation

import com.hhst.youtubelite.watch.data.FixtureWatchRepository
import com.hhst.youtubelite.watch.domain.WatchPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chip row filters only the loaded related list; there are no server chips to fake. */
class RelatedFilterTest {
    private fun page(vararg channels: String): WatchPage {
        val base = FixtureWatchRepository.samplePage("bunny")
        val template = base.related.first()
        return base.copy(
            video = base.video.copy(channel = "Blender", channelId = "UC-blender"),
            related = channels.mapIndexed { index, channel -> template.copy(id = "item-$index", channelId = channel) },
        )
    }

    @Test fun allIsTheDefaultAndKeepsEveryLoadedItem() {
        val filter = RelatedFilter.of(page("UC-blender", "UC-other", "UC-blender"), fromChannelSelected = false)
        assertTrue(filter.canFilter)
        assertFalse(filter.fromChannel)
        assertEquals(listOf("item-0", "item-1", "item-2"), filter.visible.map { it.id })
        assertEquals("Blender", filter.channel)
    }

    @Test fun fromChannelKeepsOnlyTheCurrentChannelsLoadedItems() {
        val filter = RelatedFilter.of(page("UC-blender", "UC-other", "UC-blender"), fromChannelSelected = true)
        assertTrue(filter.fromChannel)
        assertEquals(listOf("item-0", "item-2"), filter.visible.map { it.id })
    }

    @Test fun fromChannelIsOfferedOnlyWhenALoadedItemMatches() {
        val none = RelatedFilter.of(page("UC-other", "UC-third"), fromChannelSelected = true)
        assertFalse(none.canFilter)
        // A stale selection falls back to All instead of showing an empty list.
        assertFalse(none.fromChannel)
        assertEquals(2, none.visible.size)
        val blankChannel = page("", "").let { it.copy(video = it.video.copy(channelId = "")) }
        assertFalse(RelatedFilter.of(blankChannel, fromChannelSelected = true).canFilter)
    }
}
