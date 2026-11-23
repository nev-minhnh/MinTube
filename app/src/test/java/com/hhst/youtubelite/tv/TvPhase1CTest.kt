package com.hhst.youtubelite.tv

import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager.Segment
import com.hhst.youtubelite.tv.account.TvSignInText
import com.hhst.youtubelite.tv.settings.TvAccountChoice
import com.hhst.youtubelite.tv.settings.TvAccountState
import com.hhst.youtubelite.tv.settings.TvSettingAction
import com.hhst.youtubelite.tv.settings.TvSettingRow
import com.hhst.youtubelite.tv.settings.TvSettingsCatalog
import com.hhst.youtubelite.tv.settings.TvSettingsSection
import com.hhst.youtubelite.tv.watch.TvSponsorSkipDetector
import com.hhst.youtubelite.tv.watch.TvWatchButton
import com.hhst.youtubelite.tv.watch.TvWatchButtons
import com.hhst.youtubelite.tv.watch.TvWatchDetails
import com.hhst.youtubelite.tv.watch.TvWatchExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSettingsCatalogTest {
    private fun rows(prefs: Map<String, Boolean> = emptyMap(), account: TvAccountState = TvAccountState()) =
        TvSettingsCatalog.rows(prefs, account, languageLabel = "English", version = "Version 1.0.0",
            updatesSummary = "MinTube TV is up to date", uiScale = "100%")

    @Test fun sectionsAppearOnceInOrder() {
        val sections = rows().map { it.section }.distinct()
        assertEquals(TvSettingsSection.entries.toList(), sections)
    }

    @Test fun updatesRowOpensThePageAndShowsTheLiveStatus() {
        val row = rows().single { it.key == TvSettingsCatalog.KEY_UPDATES } as TvSettingRow.Action
        assertEquals(TvSettingsSection.Updates, row.section)
        assertEquals(TvSettingAction.Updates, row.action)
        assertEquals("MinTube TV is up to date", row.summaryText)
        // Without a status the row is hidden (the page is not wired).
        assertFalse(TvSettingsCatalog.rows(emptyMap(), TvAccountState(), "English", "v").any { it.section == TvSettingsSection.Updates })
    }

    /** Owner request (tv-v1.0.7): Interface size sits right under Account so a too-large TV can still reach it. */
    @Test fun interfaceSizeIsTheDisplaySectionUnderAccount() {
        val row = rows().single { it.section == TvSettingsSection.Display } as TvSettingRow.Action
        assertEquals(TvSettingAction.UiScale, row.action)
        assertEquals("100%", row.value)
        assertEquals(listOf(TvSettingsSection.Account, TvSettingsSection.Display), TvSettingsSection.entries.take(2))
        assertFalse(TvSettingsCatalog.rows(emptyMap(), TvAccountState(), "English", "v").any { it.section == TvSettingsSection.Display })
    }

    @Test fun interfaceSizesAreTheFourOwnerSteps() {
        assertEquals(listOf("25%", "50%", "75%", "100%"),
            com.hhst.youtubelite.tv.design.TvUiScale.OPTIONS.map(com.hhst.youtubelite.tv.design.TvUiScale::label))
    }

    @Test fun listIndexCountsTheTitleAndSectionHeaders() {
        val rows = rows()
        // Item 0 is the title, item 1 the Account header, item 2 the first Account row.
        assertEquals(2, com.hhst.youtubelite.tv.settings.settingsListIndex(rows, rows.first().key))
        val updates = com.hhst.youtubelite.tv.settings.settingsListIndex(rows, TvSettingsCatalog.KEY_UPDATES)!!
        val headersBefore = rows.takeWhile { it.key != TvSettingsCatalog.KEY_UPDATES }.map { it.section }.distinct().size + 1
        assertEquals(1 + headersBefore + rows.indexOfFirst { it.key == TvSettingsCatalog.KEY_UPDATES }, updates)
        assertNull(com.hhst.youtubelite.tv.settings.settingsListIndex(rows, "missing"))
    }

    @Test fun keysAreUnique() {
        val keys = rows().map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun togglesUsePhoneKeysAndDefaults() {
        val toggles = rows().filterIsInstance<TvSettingRow.Toggle>().associateBy { it.prefKey }
        assertTrue(toggles.getValue(PreferenceKeys.CONTENT_LANGUAGE_ENGLISH).checked)
        assertTrue(toggles.getValue(PreferenceKeys.TRENDING_REGION_VIETNAM).checked)
        assertFalse(toggles.getValue(PreferenceKeys.DEARROW_TITLES).checked)
        TvSettingsCatalog.SPONSOR_KEYS.forEach { (key, _) -> assertTrue(key in toggles) }
        assertTrue(PreferenceKeys.ENABLE_AUTOPLAY in toggles)
        assertTrue(PreferenceKeys.PRIVATE_BROWSING in toggles)
    }

    @Test fun storedValuesWinOverDefaults() {
        val toggles = rows(mapOf(PreferenceKeys.CONTENT_LANGUAGE_ENGLISH to false))
            .filterIsInstance<TvSettingRow.Toggle>().associateBy { it.prefKey }
        assertFalse(toggles.getValue(PreferenceKeys.CONTENT_LANGUAGE_ENGLISH).checked)
    }

    @Test fun glowAndPhoneOnlySettingsAreHidden() {
        val keys = rows().filterIsInstance<TvSettingRow.Toggle>().map { it.prefKey }.toSet()
        TvSettingsCatalog.HIDDEN_ON_TV.forEach { assertFalse(it, it in keys) }
        assertFalse(PreferenceKeys.PLAYER_GLOW in keys)
    }

    @Test fun signedOutOffersSignInOnly() {
        val actions = rows().filterIsInstance<TvSettingRow.Action>().map { it.action }
        assertTrue(TvSettingAction.SignIn in actions)
        assertFalse(TvSettingAction.SignOut in actions)
        assertFalse(TvSettingAction.SwitchAccount in actions)
    }

    @Test fun signedInShowsIdentitySwitchAndSignOut() {
        val account = TvAccountState(hasAccount = true, signedIn = true, name = "Minh", email = "m@example.com",
            accounts = listOf(TvAccountChoice("a", "Minh", "m@example.com", true)))
        val all = rows(account = account)
        val identity = all.first { it.key == "account:identity" } as TvSettingRow.Info
        assertEquals("Minh", identity.title)
        assertEquals("m@example.com", identity.summary)
        val actions = all.filterIsInstance<TvSettingRow.Action>().map { it.action }
        assertTrue(TvSettingAction.SwitchAccount in actions)
        assertTrue(TvSettingAction.SignOut in actions)
        assertFalse(TvSettingAction.SignIn in actions)
        assertFalse(all.any { it.key == "account:on-device" })
    }

    @Test fun onDeviceModeIsShown() {
        val account = TvAccountState(hasAccount = true, signedIn = false, name = "Minh")
        assertTrue(rows(account = account).any { it.key == "account:on-device" })
    }

    @Test fun aboutCarriesVersionAndSourceLink() {
        val about = rows().filter { it.section == TvSettingsSection.About }
        assertEquals("Version 1.0.0", (about.first { it.key == "about:version" } as TvSettingRow.Info).title)
        assertEquals(TvSettingsCatalog.SOURCE_URL, about.filterIsInstance<TvSettingRow.Link>().single().url)
    }

    @Test fun captionScaleSnapsToNearestChoice() {
        assertEquals(1f, TvSettingsCatalog.nearestCaptionScale(1.1f))
        assertEquals(2f, TvSettingsCatalog.nearestCaptionScale(3f))
        assertEquals(0.75f, TvSettingsCatalog.nearestCaptionScale(0.5f))
    }
}

class TvSignInTextTest {
    @Test fun displayUrlDropsSchemeAndWww() {
        assertEquals("youtube.com/activate", TvSignInText.displayUrl("https://www.youtube.com/activate"))
        assertEquals("youtube.com/activate", TvSignInText.displayUrl("http://youtube.com/activate/"))
        assertEquals("google.com/device", TvSignInText.displayUrl("https://www.google.com/device"))
        assertEquals(TvSignInText.DEFAULT_URL, TvSignInText.displayUrl(" "))
    }
}

class TvSponsorSkipDetectorTest {
    private val segments = listOf(Segment(30_000, 60_000, "sponsor"), Segment(100_000, 100_000, "poi_highlight"))

    @Test fun jumpFromSegmentStartToItsEndIsASkip() {
        val detector = TvSponsorSkipDetector()
        assertNull(detector.onPosition(29_500, segments, 0))
        assertEquals("sponsor", detector.onPosition(60_100, segments, 500)?.category)
    }

    @Test fun ordinaryPlaybackIsNot() {
        val detector = TvSponsorSkipDetector()
        var now = 0L
        for (position in 28_000L..62_000L step 500) {
            assertNull(detector.onPosition(position, segments, now))
            now += 500
        }
    }

    @Test fun userSeeksAreNotReported() {
        val detector = TvSponsorSkipDetector()
        detector.onPosition(30_500, segments, 0)
        detector.onUserSeek(100)
        assertNull(detector.onPosition(60_000, segments, 600))
        // After the grace period a real skip reports again.
        detector.onPosition(30_100, segments, 5_000)
        assertEquals("sponsor", detector.onPosition(60_000, segments, 5_500)?.category)
    }

    @Test fun jumpElsewhereOrIntoASegmentIsNot() {
        val detector = TvSponsorSkipDetector()
        detector.onPosition(10_000, segments, 0)
        assertNull(detector.onPosition(45_000, segments, 500))
        detector.reset()
        detector.onPosition(31_000, segments, 0)
        assertNull(detector.onPosition(90_000, segments, 500))
    }
}

class TvWatchButtonsTest {
    @Test fun rowOrderAndVerify() {
        assertEquals(listOf(TvWatchButton.Info, TvWatchButton.Like, TvWatchButton.Dislike, TvWatchButton.Subscribe,
            TvWatchButton.WatchLater), TvWatchButtons.visible(TvWatchExtras()))
        assertEquals(TvWatchButton.Verify, TvWatchButtons.visible(TvWatchExtras(verification = true)).last())
    }

    @Test fun subscribeHiddenWithoutAChannel() {
        val extras = TvWatchExtras(details = TvWatchDetails(canSubscribe = false))
        assertFalse(TvWatchButton.Subscribe in TvWatchButtons.visible(extras))
    }

    @Test fun accountButtonsNeedSignIn() {
        assertFalse(TvWatchButtons.needsSignIn(TvWatchButton.Info))
        assertFalse(TvWatchButtons.needsSignIn(TvWatchButton.Verify))
        listOf(TvWatchButton.Like, TvWatchButton.Dislike, TvWatchButton.Subscribe, TvWatchButton.WatchLater)
            .forEach { assertTrue(TvWatchButtons.needsSignIn(it)) }
    }
}
