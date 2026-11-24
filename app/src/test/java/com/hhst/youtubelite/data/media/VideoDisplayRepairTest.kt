package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.ui.components.joinMeta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class VideoDisplayRepairTest {
    private val display = VideoDisplay("Channel", "UC1", "115,704 views", "2026-11-24T01:02:03Z", "3:21")

    @Test fun cachesSuccessfulReads() = runTest {
        val reads = AtomicInteger()
        val repair = CachedVideoDisplayRepair { reads.incrementAndGet(); display }
        assertEquals(display, repair.details("a"))
        assertEquals(display, repair.details("a"))
        assertEquals(1, reads.get())
    }

    @Test fun evictsTheLeastRecentlyUsedIdBeyondCapacity() = runTest {
        val reads = AtomicInteger()
        val repair = CachedVideoDisplayRepair(capacity = 2) { reads.incrementAndGet(); display }
        repair.details("a"); repair.details("b"); repair.details("a"); repair.details("c")
        assertEquals(3, reads.get())
        repair.details("a")
        assertEquals(3, reads.get())
        repair.details("b")
        assertEquals(4, reads.get())
    }

    @Test fun failureReturnsNullAndIsNotCached() = runTest {
        val reads = AtomicInteger()
        val repair = CachedVideoDisplayRepair { if (reads.incrementAndGet() == 1) throw java.io.IOException("x") else display }
        assertNull(repair.details("a"))
        assertEquals(display, repair.details("a"))
        assertNull(CachedVideoDisplayRepair { null }.details("z"))
    }

    @Test fun neverRunsMoreThanFourReadsAtOnce() = runTest {
        val running = AtomicInteger()
        var peak = 0
        val gate = CompletableDeferred<Unit>()
        val repair = CachedVideoDisplayRepair(maxConcurrent = 4) {
            peak = maxOf(peak, running.incrementAndGet())
            gate.await()
            running.decrementAndGet()
            display
        }
        val all = (1..10).map { async { repair.details("v$it") } }
        runCurrent()
        assertEquals(4, peak)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(10, all.awaitAll().size)
        assertEquals(4, peak)
    }

    @Test fun repairsOnlyIncompleteVideosThatAreNeitherLiveNorShorts() {
        val base = FeedItem.Video("v", "T", "A", "1 view", "c", "1:00", "UC", published = "5 days ago")
        assertFalse(base.needsDisplayRepair())
        assertTrue(base.copy(metadata = "").needsDisplayRepair())
        assertTrue(base.copy(published = "").needsDisplayRepair())
        assertFalse(base.copy(published = "", age = "Today").needsDisplayRepair())
        assertFalse(base.copy(metadata = "", live = true).needsDisplayRepair())
        assertFalse(base.copy(metadata = "", short = true).needsDisplayRepair())
    }

    @Test fun mergeNeverOverwritesFieldsThatAlreadyHaveValues() {
        val row = FeedItem.Video("v", "Title", "Mine", "9 views", "art", "", "", published = "")
        val merged = row.withDisplay(display)
        assertEquals("Mine", merged.author)
        assertEquals("9 views", merged.metadata)
        assertEquals("Title", merged.title)
        assertEquals("art", merged.artwork)
        assertEquals("2026-11-24T01:02:03Z", merged.published)
        assertEquals("UC1", merged.channelId)
        assertEquals("3:21", merged.duration)
    }

    @Test fun aLiveRowNeverReceivesADuration() {
        assertEquals("", FeedItem.Video("v", "T", "A", "", "c", "", "UC", live = true).withDisplay(display).duration)
    }

    @Test fun metaLineDropsMissingPartsWithoutStraySeparators() {
        assertEquals("1 view · 2 days ago", joinMeta("1 view", "2 days ago"))
        assertEquals("1 view", joinMeta("1 view", ""))
        assertEquals("2 days ago", joinMeta(null, "2 days ago"))
        assertEquals("", joinMeta("", null))
    }

    @Test fun repairerCancelsInFlightReadsOnReset() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repair = object : VideoDisplayRepair {
            override suspend fun details(videoId: String): VideoDisplay? { gate.await(); return display }
        }
        val repairer = FeedDisplayRepairer(repair, backgroundScope)
        val applied = mutableListOf<String>()
        repairer.repairIds(listOf("a", "b")) { id, _ -> applied += id }
        runCurrent()
        repairer.reset()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(applied.isEmpty())
        repairer.repairIds(listOf("a")) { id, _ -> applied += id }
        advanceUntilIdle()
        assertEquals(listOf("a"), applied)
    }

    @Test fun repairerDoesNotRetryAFailedIdUntilAccountChange() = runTest {
        val reads = AtomicInteger()
        val repair = object : VideoDisplayRepair {
            override suspend fun details(videoId: String): VideoDisplay? { reads.incrementAndGet(); return null }
        }
        val repairer = FeedDisplayRepairer(repair, backgroundScope)
        repairer.repairIds(listOf("a")) { _, _ -> }
        advanceUntilIdle()
        repairer.reset()
        repairer.repairIds(listOf("a")) { _, _ -> }
        advanceUntilIdle()
        assertEquals(1, reads.get())
        repairer.reset(forgetFailures = true)
        repairer.repairIds(listOf("a")) { _, _ -> }
        advanceUntilIdle()
        assertEquals(2, reads.get())
    }
}
