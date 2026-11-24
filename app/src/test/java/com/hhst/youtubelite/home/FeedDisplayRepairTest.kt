package com.hhst.youtubelite.home

import androidx.lifecycle.SavedStateHandle
import com.hhst.youtubelite.data.media.VideoDisplay
import com.hhst.youtubelite.data.media.VideoDisplayRepair
import com.hhst.youtubelite.home.domain.*
import com.hhst.youtubelite.home.presentation.HomeViewModel
import com.hhst.youtubelite.subscriptions.data.SubscriptionsFilter
import com.hhst.youtubelite.subscriptions.data.SubscriptionsRepository
import com.hhst.youtubelite.subscriptions.data.SubscriptionsScenario
import com.hhst.youtubelite.subscriptions.domain.*
import com.hhst.youtubelite.subscriptions.presentation.SubscriptionsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedDisplayRepairTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private val full = FeedItem.Video("full", "Full", "A", "1 view", "c", "1:00", "UC", published = "5 days ago")
    private val bare = FeedItem.Video("bare", "Bare", "A", "", "c", "1:00", "UC")
    private val live = bare.copy(id = "live", live = true)
    private val short = bare.copy(id = "short", short = true)
    private val display = VideoDisplay("Other", "UC9", "10 views", "2026-11-24T01:00:00Z", "9:99")

    private class FakeRepair(val gate: CompletableDeferred<Unit>? = null) : VideoDisplayRepair {
        val asked = mutableListOf<String>()
        var result: VideoDisplay? = null
        override suspend fun details(videoId: String): VideoDisplay? { asked += videoId; gate?.await(); return result }
    }

    private val home = object : HomeRepository {
        override suspend fun loadHome(filter: String, continuation: String?) =
            HomePage(listOf(full, bare, live, short, FeedItem.Shorts("rail", listOf(bare.copy(id = "railed", short = true)))))
    }

    private fun HomeViewModel.videos() =
        (uiState.value.content as HomeContent.Feed).page.items.filterIsInstance<FeedItem.Video>().associateBy { it.id }

    @Test fun homePaintsFirstThenFillsOnlyTheIncompleteVideo() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repair = FakeRepair(gate).also { it.result = display }
        val vm = HomeViewModel(home, SavedStateHandle(), loadOnStart = true, displayRepair = repair)
        runCurrent()
        // The page is on screen while the repair read is still pending.
        assertEquals("", vm.videos().getValue("bare").metadata)
        assertEquals(listOf("bare"), repair.asked)
        gate.complete(Unit)
        advanceUntilIdle()
        val items = vm.videos()
        assertEquals("10 views", items.getValue("bare").metadata)
        assertEquals("2026-11-24T01:00:00Z", items.getValue("bare").published)
        assertEquals("A", items.getValue("bare").author)
        assertEquals(full, items.getValue("full"))
        assertEquals(live, items.getValue("live"))
        assertEquals(short, items.getValue("short"))
    }

    @Test fun homeRefreshCancelsPendingRepairsAndRepairsTheNewPage() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repair = FakeRepair(gate).also { it.result = display }
        val vm = HomeViewModel(home, SavedStateHandle(), loadOnStart = true, displayRepair = repair)
        runCurrent()
        vm.onAction(HomeAction.Refresh)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("10 views", vm.videos().getValue("bare").metadata)
    }

    @Test fun homeWithoutARepairSourceBehavesAsBefore() = runTest {
        val vm = HomeViewModel(home, SavedStateHandle(), loadOnStart = true)
        advanceUntilIdle()
        assertEquals("", vm.videos().getValue("bare").metadata)
    }

    @Test fun aFailedReadLeavesTheRowAndIsNotRetriedOnAppend() = runTest {
        val repair = FakeRepair()
        val vm = HomeViewModel(object : HomeRepository {
            override suspend fun loadHome(filter: String, continuation: String?) =
                if (continuation == null) HomePage(listOf(bare), nextKey = "n") else HomePage(listOf(bare.copy(title = "again")))
        }, SavedStateHandle(), loadOnStart = true, displayRepair = repair)
        advanceUntilIdle()
        vm.onAction(HomeAction.LoadNext)
        advanceUntilIdle()
        assertEquals(listOf("bare"), repair.asked)
        assertEquals("", vm.videos().getValue("bare").metadata)
    }

    @Test fun subscriptionsFillsIncompleteRowsInPlace() = runTest {
        val repository = object : SubscriptionsRepository {
            override suspend fun subscriptions(filter: SubscriptionsFilter, continuation: String?) =
                SubscriptionsPage(listOf(full, bare, live, short), emptyList())
        }
        val gate = CompletableDeferred<Unit>()
        val repair = FakeRepair(gate).also { it.result = display }
        val vm = SubscriptionsViewModel(repository, SavedStateHandle(), SubscriptionsScenario.Content, signedIn = true, displayRepair = repair)
        runCurrent()
        assertEquals("", (vm.uiState.value.content as SubscriptionsContent.Feed).page.items[1].metadata)
        gate.complete(Unit)
        advanceUntilIdle()
        val items = (vm.uiState.value.content as SubscriptionsContent.Feed).page.items.associateBy { it.id }
        assertEquals("10 views", items.getValue("bare").metadata)
        assertEquals(listOf("bare"), repair.asked)
        assertEquals(full, items.getValue("full"))
        assertEquals(live, items.getValue("live"))
        assertEquals(short, items.getValue("short"))
    }
}
