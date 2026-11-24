package com.hhst.youtubelite.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAttemptTest {
    @Test fun recoveryBudgetsAreIndependentAndCannotBeGrantedTwice() {
        val attempt = PlaybackAttempt("video-a")

        assertTrue(attempt.tryTransportManifest())
        assertTrue(attempt.tryLightRefresh())
        assertEquals(0, attempt.fullRefreshes)
        assertTrue(attempt.tryFullRefresh())
        assertTrue(attempt.tryBackup())

        repeat(3) {
            assertFalse(attempt.tryTransportManifest())
            assertFalse(attempt.tryLightRefresh())
            assertFalse(attempt.tryFullRefresh())
            assertFalse(attempt.tryBackup())
        }
        assertEquals(1, attempt.lightRefreshes)
        assertEquals(1, attempt.fullRefreshes)
    }

    /** Owner report (v1.0.11): a 68-minute video failed on its second spaced-out MEDIA_SESSION_CHANGED. */
    @Test fun lightRefreshRenewsAfterHealthyPlaybackButNotForAnImmediateRepeat() {
        val attempt = PlaybackAttempt("video-a")
        assertTrue(attempt.tryLightRefresh(positionMs = 600_000, nowMs = 1_000_000))
        // Fails again straight after the refresh: no loop.
        assertFalse(attempt.tryLightRefresh(positionMs = 605_000, nowMs = 1_005_000))
        // Played on for minutes before the next route change: recover again.
        assertTrue(attempt.tryLightRefresh(positionMs = 1_713_287, nowMs = 2_200_000))
        assertEquals(1, attempt.lightRefreshes)
        // Progress alone (a seek forward) is not enough without wall time, and vice versa.
        assertFalse(attempt.tryLightRefresh(positionMs = 2_000_000, nowMs = 2_210_000))
        assertFalse(attempt.tryLightRefresh(positionMs = 1_720_000, nowMs = 3_000_000))
    }

    @Test fun backupIsOnlyAvailableAfterAFullRefreshAndStaysConsumedWithoutACandidate() {
        val attempt = PlaybackAttempt("video-a")
        assertFalse(attempt.tryBackup())
        assertFalse(attempt.backupAttempted)
        assertTrue(attempt.tryFullRefresh())

        assertTrue(attempt.tryBackup())
        // The engine may find no compatible candidate; that does not grant another lookup.
        assertTrue(attempt.backupAttempted)
        assertFalse(attempt.tryBackup())
    }

    @Test fun explicitRetryOrAnotherVideoStartsWithFreshBudgetsAndHistory() {
        val old = PlaybackAttempt("video-a")
        old.tryLightRefresh()
        old.tryFullRefresh()
        old.tryBackup()
        old.tryTransportManifest()
        old.recordRecovery(mapOf("reason" to "HTTP_403"))

        for (video in listOf("video-a", "video-b")) {
            val fresh = PlaybackAttempt(video)
            assertEquals(video, fresh.videoId)
            assertEquals(0, fresh.lightRefreshes)
            assertEquals(0, fresh.fullRefreshes)
            assertFalse(fresh.backupAttempted)
            assertFalse(fresh.transportManifestAttempted)
            assertTrue(fresh.history.isEmpty())
            assertNotEquals(old.diagnosticRoot.traceId, fresh.diagnosticRoot.traceId)
            assertTrue(fresh.tryLightRefresh())
            assertTrue(fresh.tryFullRefresh())
        }
        assertEquals(1, old.fullRefreshes)
        assertEquals(1, old.history.size)
    }

    @Test fun sourceGenerationsShareTheAttemptsDiagnosticRootAndConsumedBudget() {
        val attempt = PlaybackAttempt("video-a")
        val initial = attempt.diagnosticRoot.child().copy(generation = 1)
        assertTrue(attempt.tryFullRefresh())
        val recovery = attempt.diagnosticRoot.child().copy(generation = 2)

        assertEquals("video-a", initial.videoId)
        assertEquals(initial.videoId, recovery.videoId)
        assertEquals(initial.traceId, recovery.traceId)
        assertEquals(attempt.diagnosticRoot.operationId, recovery.parentOperationId)
        assertNotEquals(initial.operationId, recovery.operationId)
        assertFalse(attempt.tryFullRefresh())
    }

    @Test fun recoveryHistoryKeepsTheLatestSixEntriesAndStableSnapshots() {
        val attempt = PlaybackAttempt("video-a")
        val fields = mutableMapOf<String, Any?>("step" to 0)
        attempt.recordRecovery(fields)
        val earlier = attempt.history
        fields["step"] = "changed"
        for (step in 1..7) attempt.recordRecovery(mapOf("step" to step))

        assertEquals(listOf(mapOf("step" to 0)), earlier)
        assertEquals((2..7).toList(), attempt.history.map { it["step"] })
    }
}
