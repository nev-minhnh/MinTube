package com.hhst.youtubelite.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.hhst.youtubelite.R
import com.hhst.youtubelite.core.AppLocale
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.player.PlayerViewModel
import com.hhst.youtubelite.player.surface.SubtitleStyle
import com.hhst.youtubelite.tv.routes.TvSignInRoute
import com.hhst.youtubelite.tv.design.TvPanelOption
import com.hhst.youtubelite.ui.observeAccountChanges
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Wires TV Settings to the phone's settings sources, unchanged: preferences through
 * [ExtensionManager.setEnabled] (same keys and listeners as SettingsActivity/AdvancedActivity),
 * the account through [MediaSession] (as YouActivity's switcher and sign-out do), the language
 * through [AppLocale.apply], and caption style through [PlayerViewModel.onSubtitleStyle].
 * Owned by [com.hhst.youtubelite.tv.TvMainActivity]; [Content] is the Settings destination.
 */
class TvSettingsController(private val activity: ComponentActivity) {
    private val prefs = getKoin().get<ExtensionManager>()
    private var preferences by mutableStateOf(prefs.allPreferences())
    private var account by mutableStateOf(readAccount())
    /** Read lazily: resolving the player builds the engine, which Settings alone should not do. */
    private var captionStyle by mutableStateOf<SubtitleStyle?>(null)
    private val listener: (String) -> Unit = { preferences = prefs.allPreferences() }

    init {
        prefs.addOnChangedListener(listener)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = prefs.removeOnChangedListener(listener)
        })
        activity.observeAccountChanges { account = readAccount() }
    }

    fun onResume() {
        preferences = prefs.allPreferences()
        account = readAccount()
        if (captionStyle != null) captionStyle = player().uiState.value.subtitleStyle
    }

    private fun player(): PlayerViewModel = getKoin().get()

    private val callbacks = object : TvSettingsCallbacks {
        override fun toggle(prefKey: String, enabled: Boolean) = prefs.setEnabled(prefKey, enabled)

        override fun signIn() = activity.startActivity(TvSignInRoute.intent(activity))

        override fun selectAccount(id: String) {
            if (MediaSession.selectAccount(id)) account = readAccount()
        }

        override fun useDevice() {
            if (MediaSession.setAnonymousMode(true)) account = readAccount()
        }

        override fun signOut() {
            MediaSession.signOut()
            account = readAccount()
        }

        override fun language(index: Int) {
            val tag = AppLocale.OPTIONS.getOrNull(index)?.first ?: return
            if (tag == AppLocale.tag(activity)) return
            if (!AppLocale.apply(activity, tag)) activity.recreate()
        }

        override fun captionSize(index: Int) {
            val scale = TvSettingsCatalog.CAPTION_SCALES.getOrNull(index) ?: return
            val player = player()
            player.onSubtitleStyle(player.uiState.value.subtitleStyle.copy(textScale = scale))
            captionStyle = player.uiState.value.subtitleStyle
        }

        override fun captionBackground(index: Int) {
            val background = SubtitleStyle.Background.entries.getOrNull(index) ?: return
            val player = player()
            player.onSubtitleStyle(player.uiState.value.subtitleStyle.copy(background = background))
            captionStyle = player.uiState.value.subtitleStyle
        }

        override fun captionsOpened() {
            captionStyle = player().uiState.value.subtitleStyle
        }
    }

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        val currentTag = AppLocale.tag(activity)
        val systemLabel = stringResource(R.string.settings_language_system)
        val languages = AppLocale.OPTIONS.map { (tag, label) ->
            TvPanelOption(if (tag.isEmpty()) systemLabel else label, tag == currentTag)
        }
        val languageLabel = languages.firstOrNull { it.selected }?.label
            ?: java.util.Locale.forLanguageTag(currentTag).displayName
        val style = captionStyle
        val sizeLabels = TvSettingsCatalog.CAPTION_SCALES.map { "${(it * 100).toInt()}%" }
        val selectedScale = style?.let { TvSettingsCatalog.nearestCaptionScale(it.textScale) }
        val backgroundLabels = SubtitleStyle.Background.entries.map { background ->
            stringResource(when (background) {
                SubtitleStyle.Background.NONE -> R.string.subtitle_background_none
                SubtitleStyle.Background.SEMI -> R.string.subtitle_background_semi
                SubtitleStyle.Background.OPAQUE -> R.string.subtitle_background_opaque
            })
        }
        val version = remember {
            runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull().orEmpty()
        }
        val rows = TvSettingsCatalog.rows(
            preferences = preferences,
            account = account,
            languageLabel = languageLabel,
            version = stringResource(R.string.account_about_version, version),
            captionSize = selectedScale?.let { "${(it * 100).toInt()}%" },
            captionBackground = style?.let { backgroundLabels[it.background.ordinal] },
        )
        val choices = TvSettingsChoices(
            languages = languages,
            captionSizes = TvSettingsCatalog.CAPTION_SCALES.mapIndexed { index, scale ->
                TvPanelOption(sizeLabels[index], scale == selectedScale)
            },
            captionBackgrounds = backgroundLabels.mapIndexed { index, label ->
                TvPanelOption(label, style?.background?.ordinal == index)
            },
        )
        TvSettingsScreen(rows, account, choices, callbacks, modifier)
    }

    companion object {
        /** The stored account as Settings shows it; same identity rules as YouActivity's switcher. */
        fun readAccount(): TvAccountState {
            val snapshot = MediaSession.snapshot()
            val onDevice = MediaSession.anonymousMode()
            val accounts = MediaSession.accounts().filterNot { it.disabled }
            val stored = snapshot.signedIn || onDevice && MediaSession.signInClient() != null
            if (!stored) return TvAccountState()
            val selected = MediaSession.selectedAccount()
            return TvAccountState(
                hasAccount = true,
                signedIn = snapshot.signedIn,
                name = selected?.name.orEmpty(),
                email = selected?.email.orEmpty(),
                accounts = accounts.map {
                    TvAccountChoice(it.id, it.name.orEmpty(), it.email.orEmpty(), selected = it.id == selected?.id)
                },
            )
        }
    }
}
