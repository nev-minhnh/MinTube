package com.hhst.youtubelite.ui

import com.hhst.youtubelite.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Element

/** QA pass 2 N2: device labels written by the data layer are shown in the app language. */
class LocalLabelTextTest {
    private val resources = File("src/main/res").takeIf(File::isDirectory) ?: File("app/src/main/res")

    private fun strings(locale: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "$locale/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test fun deviceLabelsFromTheDataLayerMapToStringResources() {
        assertEquals(R.string.ui_on_this_device, localLabelResource("On this device"))
        assertEquals(R.string.qa2_local_history_and_playlists, localLabelResource("Local history and playlists"))
    }

    @Test fun otherTextIsShownAsIs() {
        // Server-supplied section titles and owner names pass through untouched.
        assertNull(localLabelResource("Today"))
        assertNull(localLabelResource("on this device"))
        assertNull(localLabelResource(""))
    }

    @Test fun vietnameseTranslatesTheDeviceLabelsAndPlaylistOwner() {
        val english = strings("values")
        val vietnamese = strings("values-b+vi")
        assertEquals("On this device", english["ui_on_this_device"])
        assertEquals("Local history and playlists", english["qa2_local_history_and_playlists"])
        for (key in listOf("ui_on_this_device", "qa2_local_history_and_playlists", "ui_playlist_owner")) {
            assertNotEquals(key, english[key], vietnamese[key])
        }
    }
}
