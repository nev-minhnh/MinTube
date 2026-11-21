package com.hhst.youtubelite.home

import androidx.lifecycle.SavedStateHandle
import com.hhst.youtubelite.data.media.ContentLocale
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.data.media.MemoryAuthStore
import com.hhst.youtubelite.home.data.FixtureHomeRepository
import com.hhst.youtubelite.home.domain.HomeAction
import com.hhst.youtubelite.home.domain.HomeContent
import com.hhst.youtubelite.home.domain.HomePage
import com.hhst.youtubelite.home.domain.HomeRepository
import com.hhst.youtubelite.home.presentation.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Returning to Home (Back from Watch, a tab switch, app resume) re-syncs the session on every
 * resume. Only a real identity change may replace the feed; everything else waits for a pull.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeReturnTest {
    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        MediaSession.initialize(MemoryAuthStore())
    }

    @After fun cleanup() {
        Dispatchers.resetMain()
        MediaSession.initialize(MemoryAuthStore())
    }

    private var loads = 0
    private var failNext = false

    private fun viewModel(): HomeViewModel = HomeViewModel(object : HomeRepository {
        override suspend fun loadHome(filter: String, continuation: String?): HomePage {
            loads++
            if (failNext) { failNext = false; throw java.io.IOException("offline") }
            return FixtureHomeRepository.samplePage()
        }
    }, SavedStateHandle(), loadOnStart = true)

    /** What HomeActivity.onResume does after ContentLocaleSync ran for the same resume. */
    private fun resume(vm: HomeViewModel) {
        val session = MediaSession.snapshot()
        vm.updateSession(session.signedIn, session.generation)
    }

    private fun HomeViewModel.page() = (uiState.value.content as HomeContent.Feed).page

    @Test fun returningWithTheSameIdentityKeepsTheFeedAndItsPosition() = runTest {
        MediaSession.setContentLocale(ContentLocale("en", "VN"))
        val vm = viewModel()
        resume(vm)
        advanceUntilIdle()
        assertEquals(1, loads)
        val shown = vm.page()
        val refreshes = vm.uiState.value.completedUserRefreshes
        repeat(3) {
            // ContentLocaleSync re-resolves the same hl/gl on every resume.
            MediaSession.setContentLocale(ContentLocale("en", "VN"))
            resume(vm)
            advanceUntilIdle()
        }
        assertEquals(1, loads)
        assertSame(shown, vm.page())
        assertEquals("no scroll reset without a refresh", refreshes, vm.uiState.value.completedUserRefreshes)
    }

    @Test fun anAccountChangeReloadsAndReturnsToTop() = runTest {
        val vm = viewModel()
        vm.updateSession(false, 1L)
        advanceUntilIdle()
        val refreshes = vm.uiState.value.completedUserRefreshes
        vm.updateSession(true, 2L)
        advanceUntilIdle()
        assertEquals(2, loads)
        assertEquals(refreshes + 1, vm.uiState.value.completedUserRefreshes)
    }

    @Test fun aContentLocaleChangeReloads() = runTest {
        MediaSession.setContentLocale(ContentLocale("en", "VN"))
        val vm = viewModel()
        resume(vm)
        advanceUntilIdle()
        assertTrue(MediaSession.setContentLocale(ContentLocale("vi", "VN", originalText = true)))
        resume(vm)
        advanceUntilIdle()
        assertEquals(2, loads)
        // The new locale is adopted: the next plain return is quiet again.
        resume(vm)
        advanceUntilIdle()
        assertEquals(2, loads)
    }

    @Test fun onlyASuccessfulManualRefreshReturnsToTop() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val shown = vm.page()
        val refreshes = vm.uiState.value.completedUserRefreshes
        failNext = true
        vm.onAction(HomeAction.Refresh)
        advanceUntilIdle()
        assertSame("a failed refresh keeps the rows", shown, vm.page())
        assertTrue((vm.uiState.value.content as HomeContent.Feed).refreshFailed)
        assertEquals(refreshes, vm.uiState.value.completedUserRefreshes)
        vm.onAction(HomeAction.Refresh)
        advanceUntilIdle()
        assertEquals(3, loads)
        assertEquals(refreshes + 1, vm.uiState.value.completedUserRefreshes)
    }

    @Test fun anEmptyOrFailedHomeStaysPutOnReturn() = runTest {
        val empty = HomeViewModel(object : HomeRepository {
            override suspend fun loadHome(filter: String, continuation: String?): HomePage { loads++; return HomePage(emptyList()) }
        }, SavedStateHandle(), loadOnStart = true)
        failNext = true
        val failed = viewModel()
        resume(empty); resume(failed)
        advanceUntilIdle()
        repeat(2) { resume(empty); resume(failed); advanceUntilIdle() }
        assertEquals(2, loads)
        assertTrue(empty.uiState.value.content is HomeContent.Empty)
        assertEquals(HomeContent.Failed, failed.uiState.value.content)
    }

    @Test fun anAnonymousModeToggleReloads() = runTest {
        val vm = viewModel()
        resume(vm)
        advanceUntilIdle()
        assertTrue(MediaSession.setAnonymousMode(true))
        resume(vm)
        advanceUntilIdle()
        assertEquals(2, loads)
        assertTrue(MediaSession.setAnonymousMode(false))
        resume(vm)
        advanceUntilIdle()
        assertEquals(3, loads)
    }
}
