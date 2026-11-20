package com.hhst.youtubelite.account

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hhst.youtubelite.R
import com.hhst.youtubelite.account.data.MediaSignInService
import com.hhst.youtubelite.account.domain.SignInAction
import com.hhst.youtubelite.account.domain.SignInUiState
import com.hhst.youtubelite.account.presentation.SignInScreen
import com.hhst.youtubelite.account.presentation.SignInViewModel
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.PreferencesAuthStore
import com.hhst.youtubelite.ui.design.setAppContent

class SignInActivity : com.hhst.youtubelite.ui.NativeActivity() {

    /** The page the user must open; exposed for instrumentation. */
    var currentUrl: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixtures = ContentRepositories.fixtures(intent)
        if (fixtures) currentUrl = FIXTURE_URL
        val model = androidx.lifecycle.ViewModelProvider(this,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == SignInViewModel::class.java)
                    val service = if (fixtures) object : com.hhst.youtubelite.account.data.SignInService {
                        override suspend fun loadCode() = com.liskovsoft.youtubeapi.videoinfo.V2.SignInCode(
                            "fixture", FIXTURE_CODE, FIXTURE_URL, Long.MAX_VALUE, 1000)
                        override suspend fun poll(code: com.liskovsoft.youtubeapi.videoinfo.V2.SignInCode):
                            com.liskovsoft.youtubeapi.videoinfo.V2.AccessToken = kotlinx.coroutines.awaitCancellation()
                        override suspend fun complete(token: com.liskovsoft.youtubeapi.videoinfo.V2.AccessToken) =
                            emptyList<com.liskovsoft.youtubeapi.videoinfo.V2.Account>()
                    } else MediaSignInService(
                        org.koin.java.KoinJavaComponent.getKoin().get<okhttp3.OkHttpClient>(), PreferencesAuthStore(this@SignInActivity))
                    return SignInViewModel(service) as T
                }
            })[SignInViewModel::class.java]
        setAppContent {
            val state by model.uiState.collectAsStateWithLifecycle()
            androidx.compose.runtime.SideEffect {
                currentUrl = (state as? SignInUiState.Ready)?.verificationUrl
            }
            LaunchedEffect(state) { if (state == SignInUiState.Success) finish() }
            BackHandler { finish() }
            SignInScreen(
                state = state,
                onBack = { finish() },
                onRetry = { model.onAction(SignInAction.Retry) },
                onOpenBrowser = { openBrowser(it) },
            )
        }
    }

    companion object {
        internal const val FIXTURE_CODE = "ABCD-EFGH"
        internal const val FIXTURE_URL = "https://www.youtube.com/activate"
    }

    private fun openBrowser(url: String) {
        val probe = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val browser = packageManager.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeUnless { it == packageName || it == "android" }
            ?: packageManager.queryIntentActivities(probe, 0).firstOrNull {
                it.activityInfo.packageName != packageName
            }?.activityInfo?.packageName
        val intent = browser?.let {
            Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(it)
        }
        if (intent == null || runCatching { startActivity(intent) }.isFailure) {
            android.widget.Toast.makeText(this, R.string.sign_in_no_browser,
                android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
