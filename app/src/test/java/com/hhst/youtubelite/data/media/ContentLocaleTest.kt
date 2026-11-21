package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.core.ContentSnapshots
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

class ContentLocaleTest {
    @get:Rule val tmp = TemporaryFolder()

    @After fun tearDown() = MediaSession.initialize(MemoryAuthStore())

    @Test fun defaultsRequestEnglishContentFromVietnam() {
        val locale = ContentLocale.resolve(englishContent = true, vietnamRegion = true, appLanguageTag = "vi",
            system = Locale.forLanguageTag("ko-KR"))
        assertEquals(ContentLocale("en", "VN", originalText = false), locale)
    }

    @Test fun englishOffFollowsTheVietnameseAppLanguage() {
        val locale = ContentLocale.resolve(false, true, "vi", Locale.US)
        assertEquals("vi", locale.language)
        assertEquals("VN", locale.region)
        assertTrue(locale.originalText)
    }

    @Test fun englishOffWithoutAnAppLanguageFollowsTheSystemLocale() {
        assertEquals("vi", ContentLocale.resolve(false, true, "", Locale.forLanguageTag("vi-VN")).language)
    }

    @Test fun anUnmappedLanguageFallsBackToEnglish() {
        assertEquals("en", ContentLocale.resolve(false, true, "eo", Locale.US).language)
        assertEquals("en", ContentLocale.resolve(false, true, "", Locale.forLanguageTag("haw-US")).language)
    }

    @Test fun appLanguageTagsMapToYoutubeInterfaceCodes() {
        assertEquals("zh-CN", ContentLocale.youtubeLanguage(Locale.forLanguageTag("zh-Hans")))
        assertEquals("zh-TW", ContentLocale.youtubeLanguage(Locale.forLanguageTag("zh-Hant")))
        assertEquals("iw", ContentLocale.youtubeLanguage(Locale.forLanguageTag("he")))
        assertEquals("id", ContentLocale.youtubeLanguage(Locale.forLanguageTag("id")))
        assertEquals("no", ContentLocale.youtubeLanguage(Locale.forLanguageTag("nb")))
        assertEquals("fil", ContentLocale.youtubeLanguage(Locale.forLanguageTag("fil")))
        assertEquals("ko", ContentLocale.youtubeLanguage(Locale.forLanguageTag("ko")))
    }

    @Test fun vietnamOffUsesTheDeviceRegionThenUs() {
        assertEquals("JP", ContentLocale.resolve(true, false, "", Locale.JAPAN).region)
        assertEquals("US", ContentLocale.resolve(true, false, "", Locale.forLanguageTag("vi")).region)
        assertEquals("US", ContentLocale.resolve(true, false, "", Locale.forLanguageTag("es-419")).region)
    }

    @Test fun sessionCarriesTheLocaleAndAChangeStartsANewGeneration() {
        MediaSession.initialize(MemoryAuthStore())
        assertEquals("en", MediaSession.snapshot().language)
        val start = MediaSession.snapshot().generation
        assertFalse(MediaSession.setContentLocale(ContentLocale("en", "VN")))
        assertEquals("VN", MediaSession.withContentRegion(MediaSession.snapshot()).region)
        assertEquals("first value is not an identity change", start, MediaSession.snapshot().generation)
        assertFalse(MediaSession.setContentLocale(ContentLocale("en", "VN")))
        assertTrue(MediaSession.setContentLocale(ContentLocale("vi", "VN", originalText = true)))
        val changed = MediaSession.snapshot()
        assertEquals("vi", changed.language)
        assertEquals("VN", MediaSession.withContentRegion(changed).region)
        assertNotEquals(start, changed.generation)
        assertEquals(changed.generation, MediaSession.changes.value.generation)
        assertEquals("vi", MediaSession.snapshot().anonymous().language)
    }

    @Serializable private data class Row(val id: String)

    @Test fun snapshotKeysDifferByLanguageAndRegion() {
        val snapshots = ContentSnapshots(tmp.newFolder())
        val english = ContentLocale("en", "VN").scope("visitor")
        val vietnamese = ContentLocale("vi", "VN").scope("visitor")
        val us = ContentLocale("en", "US").scope("visitor")
        assertNotEquals(english, vietnamese)
        assertNotEquals(english, us)
        snapshots.write("home", english, "All", listOf(Row("english")), Row::class.java)
        assertNull(snapshots.read("home", vietnamese, "All", Row::class.java))
        assertNull(snapshots.read("home", us, "All", Row::class.java))
        assertEquals(listOf(Row("english")), snapshots.read("home", english, "All", Row::class.java))
    }
}
