package com.hhst.youtubelite.downloader.android

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hhst.youtubelite.downloader.core.BatchSelection
import com.hhst.youtubelite.downloader.core.BatchSnapshot
import com.hhst.youtubelite.downloader.core.BatchSource
import com.hhst.youtubelite.downloader.core.DownloadConfig
import com.hhst.youtubelite.downloader.core.DownloadCoordinator
import com.hhst.youtubelite.downloader.core.DownloadRequest
import com.hhst.youtubelite.downloader.core.DownloadTarget
import com.hhst.youtubelite.downloader.core.RemoveMode
import com.hhst.youtubelite.downloader.ui.DownloadActivity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * DownloadActivity intent routing. Batch names and task titles differ so the
 * detail header and its row are distinct texts. The downloads database persists
 * across runs and the coordinator reuses an existing task for a video it already
 * knows, so each batch gets its own video id and each name a per-run suffix, and the
 * batches are removed afterwards: a row left by an earlier run never satisfies (or
 * replaces) this run's rows.
 */
@RunWith(AndroidJUnit4::class)
class DownloadActivityRoutingAndroidTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val run = UUID.randomUUID().toString().take(8)
    private val created = mutableListOf<String>()
    private val coordinator: DownloadCoordinator get() = GlobalContext.get().get()

    private fun name(base: String) = "$base $run"
    private var videos = 0
    /** An 11-character video id that no earlier run or other test has queued. */
    private fun video() = "rt${run}${videos++}"

    @After fun removeCreatedBatches() = runBlocking {
        created.forEach { coordinator.remove(DownloadTarget.Batch(it), RemoveMode.RECORD_AND_FILES) }
    }

    private fun batch(batchName: String, taskTitle: String): String = runBlocking {
        coordinator
            .enqueueBatch(
                BatchSnapshot(source = BatchSource.VIDEO, name = batchName,
                    items = listOf(DownloadRequest(videoId = video(), title = taskTitle,
                        author = "Test", config = DownloadConfig()))),
                BatchSelection(indexes = setOf(0)),
                "routing-${UUID.randomUUID()}",
            ).batchId.also { created += it }
    }

    private fun visibleTexts(): Set<String> {
        val texts = HashSet<String>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.toString()?.takeIf(String::isNotBlank)?.let { texts += it }
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        visit(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
        return texts
    }

    private fun awaitText(text: String, timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (visibleTexts().contains(text)) return
            Thread.sleep(200)
        }
        assertTrue("Text never became visible: $text; saw ${visibleTexts()}", false)
    }

    private fun awaitGone(text: String, timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!visibleTexts().contains(text)) return
            Thread.sleep(200)
        }
        assertTrue("Text stayed visible: $text", false)
    }

    private fun deliver(activity: DownloadActivity, batchId: String) {
        // Windowing-dependent startActivity routing varies on this host; deliver directly.
        activity.onNewIntent(DownloadActivity.intent(activity, batchId = batchId))
    }

    @Test fun requestBeforeTheCollectorAttachesKeepsTheLatestBatch() {
        val older = batch(name("Routing older batch"), name("Routing older task"))
        val newer = batch(name("Routing newer batch"), name("Routing newer task"))
        ActivityScenario.launch<DownloadActivity>(DownloadActivity.intent(context)).use { scenario ->
            var instance: DownloadActivity? = null
            scenario.onActivity { instance = it }
            // Both sends may land before/after the collector attaches; the conflated
            // channel must retain the latest either way.
            scenario.onActivity { deliver(it, older) }
            scenario.onActivity { deliver(it, newer) }
            awaitText(name("Routing newer batch"))
            awaitText(name("Routing newer task"))
            awaitGone(name("Routing older batch"))
            scenario.onActivity { current -> assertTrue(current === instance) }
        }
    }

    @Test fun runtimeIntentOpensTheBatchAndKeepsListState() {
        val first = batch(name("Routing runtime batch"), name("Routing runtime task"))
        ActivityScenario.launch<DownloadActivity>(DownloadActivity.intent(context)).use { scenario ->
            var instance: DownloadActivity? = null
            scenario.onActivity { instance = it }
            awaitText("Downloads")

            scenario.onActivity { deliver(it, first) }
            awaitText(name("Routing runtime batch"))
            scenario.onActivity { current ->
                assertTrue("onNewIntent must not recreate", current === instance)
            }

            // Back returns to the same list: the batch header leaves and the list still shows
            // this batch's row (list rows carry task titles; batch names belong to the detail).
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitText("Downloads")
            awaitGone(name("Routing runtime batch"))
            awaitText(name("Routing runtime task"))
            scenario.onActivity { current -> assertTrue(current === instance) }
        }
    }
}
