package com.hhst.youtubelite.tv.settings

import androidx.annotation.StringRes
import com.hhst.youtubelite.R
import com.hhst.youtubelite.extension.PreferenceKeys

/** Settings sections, in list order. */
enum class TvSettingsSection(@param:StringRes val title: Int) {
    Account(R.string.tv_settings_account),
    Display(R.string.tv_settings_display),
    Playback(R.string.settings_playback),
    Content(R.string.tv_settings_content),
    SponsorBlock(R.string.sponsorblock),
    DeArrow(R.string.dearrow),
    Language(R.string.settings_language),
    Updates(R.string.account_about_updates),
    About(R.string.about),
}

/** Who is signed in, as the Account section shows it. */
data class TvAccountState(
    /** An account is stored on this device (signed in, or browsing "On this device" with it). */
    val hasAccount: Boolean = false,
    /** Requests currently go out as the account (false in "On this device" mode). */
    val signedIn: Boolean = false,
    val name: String = "",
    val email: String = "",
    val accounts: List<TvAccountChoice> = emptyList(),
)

data class TvAccountChoice(val id: String, val name: String, val email: String, val selected: Boolean)

/** One row of the TV settings list. [key] is stable: it is the focus-memory and test-tag key. */
sealed interface TvSettingRow {
    val key: String
    val section: TvSettingsSection

    /** A boolean preference written through ExtensionManager with [prefKey]. */
    data class Toggle(
        override val section: TvSettingsSection,
        val prefKey: String,
        @param:StringRes val title: Int,
        @param:StringRes val summary: Int? = null,
        val checked: Boolean,
    ) : TvSettingRow {
        override val key: String get() = "pref:$prefKey"
    }

    /** Opens a picker panel or runs an action. */
    data class Action(
        override val section: TvSettingsSection,
        val action: TvSettingAction,
        @param:StringRes val title: Int,
        @param:StringRes val summary: Int? = null,
        /** Current value shown on the right (already resolved text). */
        val value: String? = null,
        /** Resolved summary text (live status); wins over [summary]. */
        val summaryText: String? = null,
    ) : TvSettingRow {
        override val key: String get() = "action:${action.name}"
    }

    /** Read-only text (signed-in identity, version, notices). Focusable so the list can scroll to it. */
    data class Info(
        override val section: TvSettingsSection,
        override val key: String,
        val title: String = "",
        val summary: String? = null,
        @param:StringRes val titleRes: Int? = null,
        @param:StringRes val summaryRes: Int? = null,
    ) : TvSettingRow

    /** The source link: shown as text plus a QR code. */
    data class Link(override val section: TvSettingsSection, override val key: String, @param:StringRes val title: Int, val url: String) :
        TvSettingRow
}

enum class TvSettingAction { SignIn, SwitchAccount, SignOut, CaptionSize, CaptionBackground, Language, Updates, UiScale }

/**
 * The TV settings list as plain data (unit-tested). The phone's keys and defaults apply; only
 * TV-meaningful settings appear: no glow, gestures, PiP, background play, mini player, downloads
 * or theme (TV is dark only).
 */
object TvSettingsCatalog {
    val SPONSOR_KEYS: List<Pair<String, Int>> = listOf(
        PreferenceKeys.SKIP_SPONSORS to R.string.skip_sponsors,
        PreferenceKeys.SKIP_SELF_PROMO to R.string.skip_sponsors_selfpromo,
        PreferenceKeys.SKIP_INTERACTION to R.string.skip_sponsors_interaction,
        PreferenceKeys.SKIP_INTRO to R.string.skip_sponsors_intro,
        PreferenceKeys.SKIP_OUTRO to R.string.skip_sponsors_outro,
        PreferenceKeys.SKIP_PREVIEW to R.string.skip_sponsors_preview,
        PreferenceKeys.SKIP_MUSIC_OFFTOPIC to R.string.skip_sponsors_music_offtopic,
        PreferenceKeys.SKIP_FILLER to R.string.skip_sponsors_filler,
        PreferenceKeys.SKIP_POI_HIGHLIGHT to R.string.skip_sponsors_highlight,
        PreferenceKeys.SPONSOR_COUNTDOWN to R.string.sponsor_countdown,
    )

    /** Keys the phone shows that the TV deliberately hides. */
    val HIDDEN_ON_TV: Set<String> = setOf(
        PreferenceKeys.PLAYER_GLOW, PreferenceKeys.ENABLE_PIP, PreferenceKeys.ENABLE_IN_APP_MINI_PLAYER,
        PreferenceKeys.ENABLE_BACKGROUND_PLAY, PreferenceKeys.REMEMBER_RESIZE_MODE, PreferenceKeys.DATA_SAVER,
    ) + PreferenceKeys.GESTURE_KEYS

    /** Focus key of the Settings row that opens the Updates page. */
    val KEY_UPDATES = "action:${TvSettingAction.Updates.name}"
    const val SOURCE_URL = "https://github.com/nev-minhnh/MinTube"

    fun rows(
        preferences: Map<String, Boolean>,
        account: TvAccountState,
        languageLabel: String,
        version: String,
        captionSize: String? = null,
        captionBackground: String? = null,
        /** Live status under the Updates row; null hides the row. */
        updatesSummary: String? = null,
        /** Current interface size ("100%"); null hides the Display section. */
        uiScale: String? = null,
    ): List<TvSettingRow> = buildList {
        fun toggle(section: TvSettingsSection, key: String, title: Int, summary: Int? = null) {
            add(TvSettingRow.Toggle(section, key, title, summary, preferences[key] ?: PreferenceKeys.DEFAULTS[key] ?: false))
        }
        val accountSection = TvSettingsSection.Account
        if (account.hasAccount) {
            add(TvSettingRow.Info(accountSection, "account:identity",
                title = account.name.ifBlank { account.email },
                summary = if (account.name.isNotBlank()) account.email.takeIf(String::isNotBlank) else null,
                titleRes = if (account.name.isBlank() && account.email.isBlank()) R.string.ui_signed_in else null))
            if (!account.signedIn) add(TvSettingRow.Info(accountSection, "account:on-device",
                titleRes = R.string.ui_on_this_device, summaryRes = R.string.ui_browse_anonymously_the_account_stays_signed_in))
            add(TvSettingRow.Action(accountSection, TvSettingAction.SwitchAccount, R.string.ui_switch_account))
            add(TvSettingRow.Action(accountSection, TvSettingAction.SignOut, R.string.ui_sign_out_2))
        } else {
            add(TvSettingRow.Info(accountSection, "account:identity", titleRes = R.string.tv_settings_signed_out))
            add(TvSettingRow.Action(accountSection, TvSettingAction.SignIn, R.string.ui_sign_in, R.string.tv_settings_sign_in_summary))
        }
        toggle(accountSection, PreferenceKeys.PRIVATE_BROWSING, R.string.settings_private_browsing, R.string.settings_private_browsing_summary)

        if (uiScale != null) add(TvSettingRow.Action(TvSettingsSection.Display, TvSettingAction.UiScale,
            R.string.tv_settings_ui_scale, R.string.tv_settings_ui_scale_summary, uiScale))

        val playback = TvSettingsSection.Playback
        toggle(playback, PreferenceKeys.REMEMBER_QUALITY, R.string.remember_quality)
        toggle(playback, PreferenceKeys.REMEMBER_PLAYBACK_SPEED, R.string.remember_playback_speed)
        toggle(playback, PreferenceKeys.ENABLE_AUTOPLAY, R.string.autoplay)
        toggle(playback, PreferenceKeys.REMEMBER_LAST_POSITION, R.string.settings_remember_position,
            R.string.settings_remember_position_summary)
        add(TvSettingRow.Action(playback, TvSettingAction.CaptionSize, R.string.tv_settings_caption_size,
            R.string.tv_settings_captions_summary, captionSize))
        add(TvSettingRow.Action(playback, TvSettingAction.CaptionBackground, R.string.tv_settings_caption_background,
            value = captionBackground))

        val content = TvSettingsSection.Content
        toggle(content, PreferenceKeys.CONTENT_LANGUAGE_ENGLISH, R.string.locale_translate_titles, R.string.locale_translate_titles_summary)
        toggle(content, PreferenceKeys.TRENDING_REGION_VIETNAM, R.string.locale_trending_vietnam, R.string.locale_trending_vietnam_summary)
        toggle(content, PreferenceKeys.ENABLE_HIDE_SHORTS, R.string.settings_hide_shorts, R.string.settings_hide_shorts_summary)

        SPONSOR_KEYS.forEach { (key, title) -> toggle(TvSettingsSection.SponsorBlock, key, title) }

        toggle(TvSettingsSection.DeArrow, PreferenceKeys.DEARROW_TITLES, R.string.dearrow_replace_titles, R.string.dearrow_titles_summary)
        toggle(TvSettingsSection.DeArrow, PreferenceKeys.DEARROW_THUMBNAILS, R.string.dearrow_replace_thumbnails,
            R.string.dearrow_thumbnails_summary)

        add(TvSettingRow.Action(TvSettingsSection.Language, TvSettingAction.Language, R.string.settings_language, value = languageLabel))

        if (updatesSummary != null) {
            add(TvSettingRow.Action(TvSettingsSection.Updates, TvSettingAction.Updates, R.string.account_about_updates,
                summaryText = updatesSummary))
        }

        val about = TvSettingsSection.About
        add(TvSettingRow.Info(about, "about:version", title = version))
        add(TvSettingRow.Info(about, "about:fork", titleRes = R.string.about_fork_notice, summaryRes = R.string.about_credits))
        add(TvSettingRow.Info(about, "about:license", titleRes = R.string.account_about_license,
            summaryRes = R.string.account_about_license_name))
        add(TvSettingRow.Link(about, "about:source", R.string.about_source, SOURCE_URL))
    }

    /** Caption text-size choices (scale factors of the phone's subtitle style). */
    val CAPTION_SCALES: List<Float> = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    /** The choice closest to [scale], so a phone-set custom size still marks one option. */
    fun nearestCaptionScale(scale: Float): Float = CAPTION_SCALES.minBy { kotlin.math.abs(it - scale) }
}
