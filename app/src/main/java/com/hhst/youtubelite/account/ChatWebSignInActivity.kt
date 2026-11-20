package com.hhst.youtubelite.account

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.hhst.youtubelite.core.Constants
import com.hhst.youtubelite.core.WebViewTimerHandle
import com.hhst.youtubelite.core.WebViewTimerOccupancy
import com.hhst.youtubelite.core.WebViewTimerOwner
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.data.media.PreferencesAuthStore
import com.hhst.youtubelite.data.media.requireValue
import com.hhst.youtubelite.ui.design.MintColors
import com.hhst.youtubelite.watch.chat.ChatWebSessionStore
import com.liskovsoft.youtubeapi.videoinfo.V2.WebSession
import com.liskovsoft.youtubeapi.videoinfo.V2.loadActiveAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import org.koin.android.ext.android.inject

/** Opened only after a send attempt; cookies isolated from playback and normal login. */
class ChatWebSignInActivity : ComponentActivity() {
    private var web: WebView? = null
    private val timers: WebViewTimerOccupancy by inject()
    private var timerHandle: WebViewTimerHandle? = null
    private val native by lazy { MediaSession.snapshot() }
    private val expected by lazy { MediaSession.selectedAccount() }
    private lateinit var status: TextView
    private lateinit var verify: Button
    private var checking = false
    private val forPost by lazy { intent.getBooleanExtra(EXTRA_POST_AUTHORIZATION, false) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.hhst.youtubelite.ui.PhoneOrientation.lockOnPhone(this)
        if (!native.signedIn || expected == null) return finish()
        val colors = ChromeColors.of(this)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
        }
        // Text button at the start of a 56dp bar: the same place every other screen keeps "back".
        val close = Button(this, null, 0).apply {
            text = getString(if (forPost) com.hhst.youtubelite.R.string.post_auth_close else com.hhst.youtubelite.R.string.chat_auth_close)
            isAllCaps = false
            setTextColor(colors.accentText); textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            background = pill(colors.surface, dp(20), dp(8))
            minHeight = dp(56); minimumHeight = dp(56); minWidth = dp(48); minimumWidth = dp(48)
            setPadding(dp(20), 0, dp(20), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(8); marginEnd = dp(8) }
            setOnClickListener { finish() }
        }
        status = TextView(this).apply {
            setTextColor(colors.ink); textSize = 14f
            setLineSpacing(0f, 1.2f)
            setBackgroundColor(colors.panel)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = getString(if (forPost) com.hhst.youtubelite.R.string.post_auth_explanation else com.hhst.youtubelite.R.string.chat_auth_explanation, expected?.email ?: expected?.name.orEmpty())
        }
        verify = Button(this, null, 0).apply {
            text = getString(com.hhst.youtubelite.R.string.chat_auth_continue)
            isAllCaps = false
            setTextColor(android.content.res.ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(colors.secondary, colors.onAccent)))
            textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            background = pill(colors.accent, dp(24), dp(8), colors.surface)
            minHeight = dp(64); minimumHeight = dp(64)
            setPadding(dp(24), 0, dp(24), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(16); marginEnd = dp(16) }
            setOnClickListener { verifySession() }
        }
        root.addView(close); root.addView(status)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            status.setText(if (forPost) com.hhst.youtubelite.R.string.post_auth_webview_update else com.hhst.youtubelite.R.string.chat_auth_webview_update)
            return
        }
        val browser = WebView(this)
        WebViewCompat.setProfile(browser, PROFILE)
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = Constants.userAgent().replace("; wv", "").replace("Version/4.0 ", "")
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !allowed(request.url.toString())
            @Suppress("OverridingDeprecatedMember")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !allowed(url)
        }
        web = browser
        timers.attach(browser)
        timerHandle = timers.acquire(WebViewTimerOwner.BROWSER)
        root.addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(verify)
        if (savedInstanceState?.getBundle(WEB_STATE)?.let(browser::restoreState) == null) browser.loadUrl(SIGN_IN)
    }

    private fun verifySession() {
        val browser = web ?: return
        if (checking) return
        if (android.net.Uri.parse(browser.url.orEmpty()).host !in setOf("www.youtube.com", "m.youtube.com")) {
            status.setText(com.hhst.youtubelite.R.string.chat_auth_finish_signin)
            return
        }
        checking = true
        verify.isEnabled = false
        browser.evaluateJavascript("JSON.stringify({index:ytcfg.get('SESSION_INDEX')||0,delegate:ytcfg.get('DELEGATED_SESSION_ID')||'',visitor:ytcfg.get('VISITOR_DATA')||''})") { encoded ->
            lifecycleScope.launch {
                try {
                    val config = JSONObject(JSONTokener(encoded).nextValue() as? String ?: "{}")
                    val cookies = WebViewCompat.getProfile(browser).cookieManager.getCookie("https://www.youtube.com").orEmpty()
                    val session = WebSession(cookies, config.optInt("index", 0))
                    check(session.signedIn) { "chat_not_signed_in" }
                    val context = native.copy(accessToken = null, webSession = session,
                        onBehalfOfUser = config.optString("delegate").ifBlank { null },
                        visitor = config.optString("visitor").ifBlank { null }, visitorCookie = null)
                    val (owner, active) = withContext(Dispatchers.IO) {
                        loadActiveAccount(MediaSession.freshContext()).requireValue() to loadActiveAccount(context).requireValue()
                    }
                    if (!ChatWebSessionStore.sameAccount(owner, active) || native.onBehalfOfUser != context.onBehalfOfUser ||
                        native.onBehalfOfUser != null && active.pageId != native.onBehalfOfUser) {
                        status.setText(com.hhst.youtubelite.R.string.chat_auth_account_mismatch)
                        browser.loadUrl("https://www.youtube.com/channel_switcher")
                        return@launch
                    }
                    MediaSession.withUnchangedSession(native.generation) {
                        ChatWebSessionStore(PreferencesAuthStore(this@ChatWebSignInActivity))
                            .save(native, context.copy(onBehalfOfUser = active.pageId))
                    }
                    setResult(RESULT_OK)
                    finish()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    status.setText(if (forPost) com.hhst.youtubelite.R.string.post_auth_failed else com.hhst.youtubelite.R.string.chat_auth_failed)
                } finally {
                    checking = false
                    verify.isEnabled = true
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        web?.let { view -> outState.putBundle(WEB_STATE, Bundle().also { view.saveState(it) }) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        timerHandle?.release()
        web?.let { view -> view.stopLoading(); (view.parent as? ViewGroup)?.removeView(view); view.destroy() }
        web = null
        super.onDestroy()
    }

    /**
     * Pill background with a transparent vertical inset: the view stays [inset]*2 taller than the
     * visible pill, which keeps a 48dp touch target around a 40dp/48dp shape.
     */
    private fun pill(color: Int, radius: Int, inset: Int, disabled: Int = color): android.graphics.drawable.Drawable {
        fun shape(fill: Int) = android.graphics.drawable.InsetDrawable(
            android.graphics.drawable.GradientDrawable().apply { cornerRadius = radius.toFloat(); setColor(fill) }, 0, inset, 0, inset)
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(disabled))
            addState(intArrayOf(), shape(color))
        }
    }

    /** Mint palette for this View-based chrome, following the in-app theme choice. */
    private class ChromeColors(val background: Int, val panel: Int, val surface: Int, val ink: Int, val secondary: Int,
        val accent: Int, val onAccent: Int, val accentText: Int) {
        companion object {
            fun of(context: android.content.Context): ChromeColors {
                val night = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val dark = com.hhst.youtubelite.ui.theme.AppAppearance.dark ?: night
                return if (dark) ChromeColors(MintColors.DarkBackground.toInt(), MintColors.DarkPanel.toInt(),
                    MintColors.DarkSurface.toInt(), MintColors.DarkInk.toInt(), MintColors.DarkSecondary.toInt(),
                    MintColors.DarkAccent.toInt(), MintColors.DarkOnAccent.toInt(), MintColors.DarkAccentText.toInt())
                else ChromeColors(MintColors.LightBackground.toInt(), MintColors.LightPanel.toInt(),
                    MintColors.LightSurface.toInt(), MintColors.LightInk.toInt(), MintColors.LightSecondary.toInt(),
                    MintColors.LightAccent.toInt(), MintColors.LightOnAccent.toInt(), MintColors.LightAccentText.toInt())
            }
        }
    }

    companion object {
        const val EXTRA_POST_AUTHORIZATION = "post_authorization"
        private const val PROFILE = "live-chat-authorization"
        private const val WEB_STATE = "chat_sign_in_web_state"
        private const val SIGN_IN = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F"
        private fun allowed(url: String): Boolean {
            val uri = android.net.Uri.parse(url)
            return uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
                uri.host in setOf("accounts.google.com", "accounts.youtube.com", "www.youtube.com", "m.youtube.com", "consent.youtube.com", "consent.google.com")
        }
    }
}
