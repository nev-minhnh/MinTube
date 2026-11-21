package com.hhst.youtubelite.downloader.ui

import com.hhst.youtubelite.downloader.core.CompletionKind
import com.hhst.youtubelite.downloader.core.DownloadPhase
import com.hhst.youtubelite.downloader.core.DownloadStatus
import com.hhst.youtubelite.downloader.core.FileAvailability
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA pass 2 N4: a row stuck in any state (a ghost "Downloading" row left by an earlier run, a paused
 * or failed one, a missing file) can always be taken off the list from its row menu. Delete removes
 * the task whatever its status, so it must never depend on the row's phase.
 */
class DownloadRowRemovalTest {
    private fun item(phase: DownloadPhase, status: DownloadStatus, fileMissing: Boolean = false, skipped: Boolean = false) =
        DownloadItemUiState(
            taskId = "t", videoId = "abcdefghijk", title = "T", author = "A", thumbnailUrl = null,
            phase = phase, status = status, fileAvailability = FileAvailability.EXISTS,
            completion = CompletionKind.NONE, fullyDownloaded = false, fileMissing = fileMissing, skipped = skipped,
        )

    @Test fun everyRowStateOffersDelete() {
        for (phase in DownloadPhase.entries) for (status in DownloadStatus.entries) {
            for (missing in listOf(false, true)) for (skipped in listOf(false, true)) {
                val actions = DownloadPresentation.actionsFor(item(phase, status, missing, skipped))
                assertTrue("$phase/$status missing=$missing skipped=$skipped: $actions", DownloadRowAction.DELETE in actions)
            }
        }
    }

    @Test fun activeAndPausedRowsAlsoOfferCancel() {
        for (status in listOf(DownloadStatus.RUNNING, DownloadStatus.PAUSING, DownloadStatus.WAITING_SYSTEM,
            DownloadStatus.PAUSED, DownloadStatus.WAITING_NETWORK, DownloadStatus.QUEUED)) {
            val actions = DownloadPresentation.actionsFor(item(DownloadPhase.TRANSFER, status))
            assertTrue("$status: $actions", DownloadRowAction.CANCEL in actions)
        }
    }
}
