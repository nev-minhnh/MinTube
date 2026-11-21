package com.hhst.youtubelite.tv.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hhst.youtubelite.account.data.MediaSignInService
import com.hhst.youtubelite.account.data.SignInService
import com.hhst.youtubelite.account.domain.SignInAction
import com.hhst.youtubelite.account.domain.SignInUiState
import com.hhst.youtubelite.account.presentation.SignInViewModel
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.PreferencesAuthStore
import com.hhst.youtubelite.tv.TvActivity
import com.liskovsoft.youtubeapi.videoinfo.V2.AccessToken
import com.liskovsoft.youtubeapi.videoinfo.V2.Account
import com.liskovsoft.youtubeapi.videoinfo.V2.SignInCode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** Entry point other TV screens use to start sign-in. Keep the name: TV-B references it. */
object TvSignInRoute {
    fun intent(context: Context): Intent = Intent(context, TvSignInActivity::class.java)
}

/**
 * Full-screen device-code sign-in for the remote. Same flow as the phone's SignInActivity: the
 * unchanged [SignInViewModel] over [MediaSignInService] (fixture service under test intents).
 * The user enters the code on another device; this screen polls and closes itself on success.
 */
class TvSignInActivity : TvActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixtures = ContentRepositories.fixtures(intent)
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == SignInViewModel::class.java)
                val service = if (fixtures) FixtureSignInService else MediaSignInService(
                    org.koin.java.KoinJavaComponent.getKoin().get<okhttp3.OkHttpClient>(), PreferencesAuthStore(this@TvSignInActivity))
                return SignInViewModel(service) as T
            }
        })[SignInViewModel::class.java]
        setTvContent {
            val state by model.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state) {
                if (state == SignInUiState.Success) {
                    // Let "Signed in" register before the screen closes.
                    delay(SUCCESS_HOLD_MS)
                    setResult(RESULT_OK)
                    finish()
                }
            }
            BackHandler { finish() }
            TvSignInScreen(
                state = state,
                onRetry = { model.onAction(SignInAction.Retry) },
                onCancel = ::finish,
            )
        }
    }

    private object FixtureSignInService : SignInService {
        override suspend fun loadCode() = SignInCode("fixture", FIXTURE_CODE, FIXTURE_URL, Long.MAX_VALUE, 1000)
        override suspend fun poll(code: SignInCode): AccessToken = awaitCancellation()
        override suspend fun complete(token: AccessToken) = emptyList<Account>()
    }

    companion object {
        internal const val FIXTURE_CODE = "ABCD-EFGH"
        internal const val FIXTURE_URL = "https://www.youtube.com/activate"
        private const val SUCCESS_HOLD_MS = 800L
    }
}
