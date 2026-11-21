package com.hhst.youtubelite.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.R
import com.hhst.youtubelite.core.AppLocale
import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.settings.domain.SettingsPage
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.theme.AppAppearance
import com.hhst.youtubelite.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    page: SettingsPage,
    preferences: Map<String, Boolean>,
    notificationsEnabled: Boolean,
    onNavigate: (SettingsPage) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onAbout: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAdvanced: (() -> Unit)? = null,
    onSelectTheme: (ThemeMode) -> Unit = {},
    onSelectLanguage: (String) -> Unit = {},
) {
    SettingsPageFrame(
        title = stringResource(when (page) {
            SettingsPage.Home -> R.string.settings_title
            SettingsPage.General -> R.string.settings_general
            SettingsPage.Playback -> R.string.settings_playback
            SettingsPage.Notifications -> R.string.settings_notifications
        }),
        onBack = onBack,
        screenTag = "settings-screen",
        topBarTag = "settings-topbar",
        pageTag = "settings-page-${page.argument}",
        modifier = modifier,
        privateBrowsing = preferences[PreferenceKeys.PRIVATE_BROWSING] == true,
        pageKey = page,
    ) {
        when (page) {
            SettingsPage.Home -> SettingsHome(onNavigate, onAbout, onAdvanced)
            SettingsPage.General -> GeneralSettings(preferences, onToggle, onSelectTheme, onSelectLanguage)
            SettingsPage.Playback -> PlaybackSettings(preferences, onToggle)
            SettingsPage.Notifications -> NotificationSettings(notificationsEnabled, onOpenNotificationSettings)
        }
    }
}

@Composable
private fun SettingsHome(onNavigate: (SettingsPage) -> Unit, onAbout: () -> Unit, onAdvanced: (() -> Unit)?) {
    SettingsGroup(stringResource(R.string.settings_group_preferences))
    SettingsRow(stringResource(R.string.settings_general), stringResource(R.string.settings_general_summary),
        tag = "settings-row-general", icon = AppIcon.Settings, chevron = true) {
        onNavigate(SettingsPage.General)
    }
    SettingsRow(stringResource(R.string.settings_playback), stringResource(R.string.settings_playback_summary),
        tag = "settings-row-playback", icon = AppIcon.Play, chevron = true) {
        onNavigate(SettingsPage.Playback)
    }
    SettingsRow(stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_summary),
        tag = "settings-row-notifications", icon = AppIcon.Bell, chevron = true) {
        onNavigate(SettingsPage.Notifications)
    }
    if (onAdvanced != null) SettingsRow(stringResource(R.string.settings_advanced),
        stringResource(R.string.settings_advanced_summary), tag = "settings-row-advanced",
        icon = AppIcon.Filter, chevron = true, onClick = onAdvanced)
    SettingsGroup(stringResource(R.string.settings_group_about))
    SettingsRow(stringResource(R.string.settings_about_title), stringResource(R.string.settings_about_summary),
        tag = "settings-row-about", icon = AppIcon.Info, chevron = true, onClick = onAbout)
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(when (mode) {
    ThemeMode.System -> R.string.settings_theme_system
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
})

@Composable
private fun GeneralSettings(
    preferences: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectLanguage: (String) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_appearance))
    var themeDialog by rememberSaveable { mutableStateOf(false) }
    var languageDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    SettingsRow(stringResource(R.string.settings_theme), value = themeLabel(AppAppearance.mode),
        tag = "settings-row-theme", onClick = { themeDialog = true })
    SettingsRow(stringResource(R.string.settings_language),
        value = AppLocale.OPTIONS.firstOrNull { it.first == AppLocale.tag(context) }?.second
            ?: java.util.Locale.forLanguageTag(AppLocale.tag(context)).displayName,
        tag = "settings-row-language", onClick = { languageDialog = true })
    SettingsToggle(stringResource(R.string.locale_translate_titles), stringResource(R.string.locale_translate_titles_summary),
        PreferenceKeys.CONTENT_LANGUAGE_ENGLISH, preferences, onToggle)
    SettingsToggle(stringResource(R.string.locale_trending_vietnam), stringResource(R.string.locale_trending_vietnam_summary),
        PreferenceKeys.TRENDING_REGION_VIETNAM, preferences, onToggle)
    SettingsGroup(stringResource(R.string.settings_group_feed))
    SettingsToggle(stringResource(R.string.settings_hide_shorts), stringResource(R.string.settings_hide_shorts_summary),
        PreferenceKeys.ENABLE_HIDE_SHORTS, preferences, onToggle)
    SettingsGroup(stringResource(R.string.settings_group_privacy))
    SettingsToggle(stringResource(R.string.settings_private_browsing), stringResource(R.string.settings_private_browsing_summary),
        PreferenceKeys.PRIVATE_BROWSING, preferences, onToggle)
    SettingsToggle(stringResource(R.string.settings_data_saver), stringResource(R.string.settings_data_saver_summary),
        PreferenceKeys.DATA_SAVER, preferences, onToggle)
    if (themeDialog) RadioDialog(
        title = stringResource(R.string.settings_theme),
        options = ThemeMode.entries.map { mode -> themeLabel(mode) to (mode == AppAppearance.mode) },
        tag = "theme-dialog",
        onSelect = { index -> onSelectTheme(ThemeMode.entries[index]); themeDialog = false },
        onDismiss = { themeDialog = false })
    if (languageDialog) RadioDialog(
        title = stringResource(R.string.settings_language),
        options = AppLocale.OPTIONS.map { (tag, label) ->
            label to (tag == AppLocale.tag(context))
        },
        tag = "language-dialog",
        onSelect = { index -> onSelectLanguage(AppLocale.OPTIONS[index].first); languageDialog = false },
        onDismiss = { languageDialog = false })
}

@Composable
private fun PlaybackSettings(preferences: Map<String, Boolean>, onToggle: (String, Boolean) -> Unit) {
    SettingsGroup(stringResource(R.string.settings_playback))
    SettingsToggle(stringResource(R.string.settings_background_play), stringResource(R.string.settings_background_play_summary),
        PreferenceKeys.ENABLE_BACKGROUND_PLAY, preferences, onToggle)
    SettingsToggle(stringResource(R.string.settings_pip), stringResource(R.string.settings_pip_summary),
        PreferenceKeys.ENABLE_PIP, preferences, onToggle)
    SettingsToggle(stringResource(R.string.in_app_mini_player), stringResource(R.string.account_mini_player_summary),
        PreferenceKeys.ENABLE_IN_APP_MINI_PLAYER, preferences, onToggle)
    SettingsToggle(stringResource(R.string.settings_remember_position), stringResource(R.string.settings_remember_position_summary),
        PreferenceKeys.REMEMBER_LAST_POSITION, preferences, onToggle)
    SettingsToggle(stringResource(R.string.glow_setting_title), stringResource(R.string.glow_setting_summary),
        PreferenceKeys.PLAYER_GLOW, preferences, onToggle)
}

@Composable
private fun NotificationSettings(notificationsEnabled: Boolean, onOpen: () -> Unit) {
    SettingsGroup(stringResource(R.string.settings_group_system))
    SettingsRow(stringResource(R.string.settings_notifications),
        value = stringResource(if (notificationsEnabled) R.string.settings_notifications_state_on
        else R.string.settings_notifications_state_off),
        tag = "settings-notifications-state", icon = AppIcon.Bell)
    SettingsRow(stringResource(R.string.settings_open_system_settings), stringResource(R.string.settings_open_system_settings_summary),
        tag = "settings-open-notification-settings", icon = AppIcon.OpenWith, onClick = onOpen)
}
