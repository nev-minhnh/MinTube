package com.hhst.youtubelite.core.update

import com.hhst.youtubelite.extension.PrefStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DefaultUpdateCenterTest {
    private class FakePrefs : PrefStore {
        val map = mutableMapOf<String, Any>()
        override fun contains(key: String) = key in map
        override fun getBool(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun putBool(key: String, value: Boolean) { map[key] = value }
        override fun getLong(key: String, default: Long) = map[key] as? Long ?: default
        override fun putLong(key: String, value: Long) { map[key] = value }
        override fun getString(key: String, default: String) = map[key] as? String ?: default
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private class FakeScheduler : UpdateScheduler {
        val calls = mutableListOf<String>()
        override fun schedule() { calls += "schedule" }
        override fun cancel() { calls += "cancel" }
    }

    private class FakeSource(var latest: AppRelease? = null, var history: List<AppRelease> = emptyList(),
        var failure: Throwable? = null) : ReleaseSource {
        var historyChannel: UpdateChannel? = null
        override suspend fun latest(channel: UpdateChannel): AppRelease? { failure?.let { throw it }; return latest }
        override suspend fun history(channel: UpdateChannel, limit: Int): List<AppRelease> {
            historyChannel = channel; failure?.let { throw it }; return history.take(limit)
        }
        override suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File = File("x.apk")
    }

    private fun release(version: String, published: String = "") =
        AppRelease("v$version", version, "u", "a.apk", 1, "s", "notes", "p", published)

    private val prefs = FakePrefs()
    private val scheduler = FakeScheduler()
    private val source = FakeSource()
    private var clock = 1_000L

    private fun center(version: String = "1.0.9", debug: Boolean = false, channel: UpdateChannel = UpdateChannel.Phone) =
        DefaultUpdateCenter(source, PreferenceUpdateSettings(prefs), scheduler, channel, version, debug, now = { clock })

    @Test fun newerReleaseIsAvailableAndRecordsTheCheck() = runBlocking {
        source.latest = release("1.0.10")
        val center = center()
        assertNull(center.settings.lastSuccessfulCheck)
        val result = center.check()
        assertEquals(UpdateCheck.Available(release("1.0.10")), result)
        assertEquals(1_000L, center.settings.lastSuccessfulCheck)
    }

    @Test fun sameOrOlderReleaseIsUpToDate() = runBlocking {
        source.latest = release("1.0.9")
        assertEquals(UpdateCheck.UpToDate(release("1.0.9")), center().check())
        source.latest = release("1.0.8")
        assertTrue(center().check() is UpdateCheck.UpToDate)
    }

    @Test fun noPublishedPackageIsUpToDateWithoutLatest() = runBlocking {
        source.latest = null
        assertEquals(UpdateCheck.UpToDate(null), center().check())
    }

    @Test fun debugAndNonStableBuildsAreDevelopment() = runBlocking {
        source.latest = release("1.0.10")
        assertEquals(UpdateCheck.Development(release("1.0.10")), center(debug = true).check())
        assertTrue(center(version = "1.0.9-debug").check() is UpdateCheck.Development)
        assertTrue(center(version = "4.0.0-devx").check() is UpdateCheck.Development)
        assertEquals(CachedUpdateStatus.Development, center(debug = true).cachedStatus())
    }

    @Test fun failedCheckRecordsNothingAndThrows() = runBlocking {
        val center = center()
        source.latest = release("1.0.9")
        center.check()
        clock = 5_000L
        source.failure = java.io.IOException("offline")
        try { center.check(); fail("expected failure") } catch (_: java.io.IOException) { }
        assertEquals(1_000L, center.settings.lastSuccessfulCheck)
    }

    @Test fun cachedStatusFollowsTheLastCheck() = runBlocking {
        val center = center()
        assertEquals(CachedUpdateStatus.UpToDate, center.cachedStatus())
        source.latest = release("1.0.10")
        center.check()
        assertEquals(CachedUpdateStatus.Available, center.cachedStatus())
        assertEquals("1.0.10", center.cachedLatestVersion)
        source.latest = release("1.0.9")
        center.check()
        assertEquals(CachedUpdateStatus.UpToDate, center.cachedStatus())
    }

    @Test fun historyIsReadForTheCentersChannelAndLimited() = runBlocking {
        source.history = listOf(release("1.0.9", "2026-11-22T10:00:00Z"), release("1.0.8", "2026-11-20T10:00:00Z"),
            release("1.0.7"))
        val tv = center(channel = UpdateChannel.Tv)
        val list = tv.history(2)
        assertEquals(UpdateChannel.Tv, source.historyChannel)
        assertEquals(listOf("1.0.9", "1.0.8"), list.map { it.version })
        assertEquals("2026-11-22T10:00:00Z", list.first().publishedAt)
    }

    @Test fun rescheduleSchedulesWhenOnAndCancelsWhenOff() {
        val center = center()
        center.reschedule()
        center.settings.autoCheck = false
        center.reschedule()
        center.settings.autoCheck = true
        center.reschedule()
        assertEquals(listOf("schedule", "cancel", "schedule"), scheduler.calls)
    }

    @Test fun settingsDefaultOnAndPersist() {
        val first = PreferenceUpdateSettings(prefs)
        assertTrue(first.autoCheck); assertTrue(first.notify); assertNull(first.lastSuccessfulCheck)
        first.autoCheck = false; first.notify = false
        first.recordCheck(42L, "1.0.10")
        val second = PreferenceUpdateSettings(prefs)
        assertFalse(second.autoCheck); assertFalse(second.notify)
        assertEquals(42L, second.lastSuccessfulCheck)
        assertEquals("1.0.10", second.cachedLatest)
    }

    @Test fun notifiedVersionAndPermissionFlagPersist() {
        val settings = PreferenceUpdateSettings(prefs)
        assertEquals("", settings.notifiedVersion); assertFalse(settings.permissionAsked)
        settings.notifiedVersion = "1.0.10"; settings.permissionAsked = true
        assertEquals("1.0.10", PreferenceUpdateSettings(prefs).notifiedVersion)
        assertTrue(PreferenceUpdateSettings(prefs).permissionAsked)
    }
}
