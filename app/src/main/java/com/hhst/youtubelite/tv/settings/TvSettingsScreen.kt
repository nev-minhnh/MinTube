package com.hhst.youtubelite.tv.settings

import com.hhst.youtubelite.tv.design.tvFocusRestorer
import com.hhst.youtubelite.tv.design.tvAnchor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvFocusRequesters
import com.hhst.youtubelite.tv.design.TvListRow
import com.hhst.youtubelite.tv.design.TvOptionPanel
import com.hhst.youtubelite.tv.design.TvPanelOption
import com.hhst.youtubelite.tv.design.TvSidePanel
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.qr.TvQrImage
import com.hhst.youtubelite.ui.design.AppIcon

/** Which picker is open over the list. */
enum class TvSettingsPanel { Language, CaptionSize, CaptionBackground, Accounts, SignOut, UiScale }

/** Callbacks of [TvSettingsScreen]; the controller wires them to ExtensionManager, MediaSession and AppLocale. */
interface TvSettingsCallbacks {
    fun toggle(prefKey: String, enabled: Boolean)
    fun signIn()
    fun selectAccount(id: String)
    fun useDevice()
    fun signOut()
    fun language(index: Int)
    fun captionSize(index: Int)
    fun captionBackground(index: Int)
    /** Opens the Updates page. */
    fun openUpdates() = Unit
    /** Interface size picked from [TvSettingsChoices.uiScales]. */
    fun uiScale(index: Int) = Unit
    /** A caption picker is about to open (lazy player-style read). */
    fun captionsOpened() = Unit
}

/** Picker contents: resolved labels with the current choice marked. */
data class TvSettingsChoices(
    val languages: List<TvPanelOption> = emptyList(),
    val captionSizes: List<TvPanelOption> = emptyList(),
    val captionBackgrounds: List<TvPanelOption> = emptyList(),
    val uiScales: List<TvPanelOption> = emptyList(),
)

/**
 * TV Settings: one focusable list grouped by section. Toggles flip on OK; other rows open a
 * [TvOptionPanel] (a focus-trapping side panel). Focus returns to the row that opened a panel,
 * and moves to the first Account row when an account change removes the focused row.
 */
@Composable
fun TvSettingsScreen(
    rows: List<TvSettingRow>,
    account: TvAccountState,
    choices: TvSettingsChoices,
    callbacks: TvSettingsCallbacks,
    modifier: Modifier = Modifier,
    /** Row key to focus when the screen first shows (back from the Updates page). */
    initialFocusKey: String? = null,
) {
    var panel by rememberSaveable { mutableStateOf<TvSettingsPanel?>(null) }
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var returnKey by rememberSaveable { mutableStateOf<String?>(null) }
    val requesters = remember { TvFocusRequesters() }
    val keys = rows.map { it.key }
    val latestKeys by rememberUpdatedState(keys)

    fun open(next: TvSettingsPanel, from: String) {
        returnKey = from
        panel = next
    }

    fun close() {
        panel = null
    }

    // Back from a panel (or a pick) lands on the row that opened it.
    LaunchedEffect(panel) {
        if (panel != null) return@LaunchedEffect
        val target = returnKey ?: return@LaunchedEffect
        withFrameNanos { }
        if (!requesters.request(target)) requesters.request(latestKeys.firstOrNull() ?: return@LaunchedEffect)
        returnKey = null
    }
    // An account change removed the focused row (Sign in → Switch account): stay in the Account section.
    LaunchedEffect(keys) {
        val focused = focusedKey ?: return@LaunchedEffect
        if (focused in keys || panel != null) return@LaunchedEffect
        withFrameNanos { }
        val fallback = rows.firstOrNull { it.section == TvSettingsSection.Account && it !is TvSettingRow.Info }?.key
            ?: keys.firstOrNull() ?: return@LaunchedEffect
        requesters.request(fallback)
    }

    val list = rememberLazyListState()

    // Back from a sub-page lands on the row that opened it; scroll it into the composed range first,
    // since a row below the screen has no focus target yet.
    LaunchedEffect(Unit) {
        val target = initialFocusKey ?: return@LaunchedEffect
        settingsListIndex(rows, target)?.let { list.scrollToItem(it) }
        repeat(10) {
            withFrameNanos { }
            if (requesters.request(target)) return@LaunchedEffect
        }
    }

    val first = keys.firstOrNull()
    Box(modifier.fillMaxSize().testTag(TAG_SETTINGS_SCREEN)) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().let { base ->
                if (first != null) base.tvFocusRestorer(requesters.of(first)) else base
            }.testTag(TAG_SETTINGS_LIST),
            contentPadding = PaddingValues(start = TvSpacing.ContentStart, end = TvSpacing.OverscanHorizontal,
                top = TvSpacing.OverscanVertical, bottom = TvSpacing.OverscanVertical + 48.dp),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        ) {
            item(key = "title") {
                Text(stringResource(R.string.tv_nav_settings), style = TvType.Display, color = TvColors.Ink,
                    modifier = Modifier.padding(bottom = TvSpacing.md))
            }
            var previous: TvSettingsSection? = null
            rows.forEach { row ->
                if (row.section != previous) {
                    val section = row.section
                    item(key = "section:${section.name}") {
                        Text(stringResource(section.title), style = TvType.Title, color = TvColors.Accent,
                            modifier = Modifier.padding(top = TvSpacing.xl, bottom = TvSpacing.xs).testTag("$TAG_SECTION-${section.name}"))
                    }
                    previous = section
                }
                item(key = row.key) {
                    val rowModifier = Modifier
                        .widthIn(max = 980.dp)
                        .tvAnchor(requesters.of(row.key))
                        .onFocusChanged { if (it.isFocused) focusedKey = row.key }
                        .testTag("$TAG_ROW-${row.key}")
                    SettingRow(row, rowModifier, callbacks, onOpen = { next -> open(next, row.key) })
                }
            }
        }
    }

    when (panel) {
        TvSettingsPanel.Language -> TvOptionPanel(stringResource(R.string.settings_language), choices.languages,
            onSelect = { index -> close(); callbacks.language(index) }, onDismiss = ::close, tag = TAG_PANEL_LANGUAGE)
        TvSettingsPanel.UiScale -> TvOptionPanel(stringResource(R.string.tv_settings_ui_scale), choices.uiScales,
            onSelect = { index -> close(); callbacks.uiScale(index) }, onDismiss = ::close, tag = TAG_PANEL_UI_SCALE)
        TvSettingsPanel.CaptionSize -> TvOptionPanel(stringResource(R.string.tv_settings_caption_size), choices.captionSizes,
            onSelect = { index -> close(); callbacks.captionSize(index) }, onDismiss = ::close, tag = TAG_PANEL_CAPTION_SIZE)
        TvSettingsPanel.CaptionBackground -> TvOptionPanel(stringResource(R.string.tv_settings_caption_background),
            choices.captionBackgrounds, onSelect = { index -> close(); callbacks.captionBackground(index) }, onDismiss = ::close,
            tag = TAG_PANEL_CAPTION_BACKGROUND)
        TvSettingsPanel.Accounts -> {
            val options = account.accounts.map { TvPanelOption(it.name.ifBlank { it.email }, it.selected && account.signedIn,
                it.email.takeIf { email -> email.isNotBlank() && it.name.isNotBlank() }) } +
                TvPanelOption(stringResource(R.string.ui_on_this_device), !account.signedIn,
                    stringResource(R.string.ui_browse_anonymously_the_account_stays_signed_in)) +
                TvPanelOption(stringResource(R.string.ui_add_account), false)
            TvOptionPanel(stringResource(R.string.ui_switch_account), options, onSelect = { index ->
                close()
                when {
                    index < account.accounts.size -> callbacks.selectAccount(account.accounts[index].id)
                    index == account.accounts.size -> callbacks.useDevice()
                    else -> callbacks.signIn()
                }
            }, onDismiss = ::close, tag = TAG_PANEL_ACCOUNTS)
        }
        TvSettingsPanel.SignOut -> SignOutPanel(onConfirm = { close(); callbacks.signOut() }, onDismiss = ::close)
        null -> Unit
    }
}

@Composable
private fun SettingRow(
    row: TvSettingRow,
    modifier: Modifier,
    callbacks: TvSettingsCallbacks,
    onOpen: (TvSettingsPanel) -> Unit,
) {
    when (row) {
        is TvSettingRow.Toggle -> TvListRow(
            title = stringResource(row.title),
            summary = row.summary?.let { stringResource(it) },
            checked = row.checked,
            onClick = { callbacks.toggle(row.prefKey, !row.checked) },
            modifier = modifier,
        )
        is TvSettingRow.Action -> TvListRow(
            title = stringResource(row.title),
            summary = row.summaryText ?: row.summary?.let { stringResource(it) },
            value = row.value,
            icon = when (row.action) {
                TvSettingAction.SignIn -> AppIcon.Person
                TvSettingAction.SwitchAccount -> AppIcon.Person
                TvSettingAction.SignOut -> AppIcon.Close
                TvSettingAction.Language -> AppIcon.Globe
                TvSettingAction.Updates -> AppIcon.Refresh
                TvSettingAction.CaptionSize, TvSettingAction.CaptionBackground, TvSettingAction.UiScale -> null
            },
            onClick = {
                when (row.action) {
                    TvSettingAction.SignIn -> callbacks.signIn()
                    TvSettingAction.SwitchAccount -> onOpen(TvSettingsPanel.Accounts)
                    TvSettingAction.SignOut -> onOpen(TvSettingsPanel.SignOut)
                    TvSettingAction.Language -> onOpen(TvSettingsPanel.Language)
                    TvSettingAction.Updates -> callbacks.openUpdates()
                    TvSettingAction.UiScale -> onOpen(TvSettingsPanel.UiScale)
                    TvSettingAction.CaptionSize -> {
                        callbacks.captionsOpened()
                        onOpen(TvSettingsPanel.CaptionSize)
                    }
                    TvSettingAction.CaptionBackground -> {
                        callbacks.captionsOpened()
                        onOpen(TvSettingsPanel.CaptionBackground)
                    }
                }
            },
            modifier = modifier,
        )
        is TvSettingRow.Info -> TvListRow(
            title = row.titleRes?.let { stringResource(it) } ?: row.title,
            summary = row.summaryRes?.let { stringResource(it) } ?: row.summary,
            icon = if (row.key == "account:identity") AppIcon.Person else null,
            onClick = {},
            modifier = modifier,
        )
        is TvSettingRow.Link -> TvListRow(
            title = stringResource(row.title),
            summary = row.url,
            icon = AppIcon.Link,
            onClick = {},
            modifier = modifier,
            trailing = {
                TvQrImage(row.url, stringResource(R.string.tv_qr_description, row.url), size = 132.dp)
            },
        )
    }
}

@Composable
private fun SignOutPanel(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TvSidePanel(stringResource(R.string.ui_sign_out), onDismiss, Modifier.testTag(TAG_PANEL_SIGN_OUT)) {
        Text(stringResource(R.string.ui_this_removes_the_account_from_this_device_downloads_and_settings_are_k),
            style = TvType.Body, color = TvColors.Secondary)
        val cancel = remember { androidx.compose.ui.focus.FocusRequester() }
        Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            // Cancel takes focus: signing out by accident costs a full device-code sign-in.
            TvButton(stringResource(R.string.cancel), onDismiss, Modifier.focusRequester(cancel).testTag("$TAG_PANEL_SIGN_OUT-cancel"))
            TvButton(stringResource(R.string.ui_sign_out_2), onConfirm, Modifier.testTag("$TAG_PANEL_SIGN_OUT-confirm"),
                primary = true)
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { cancel.requestFocus() }
        }
    }
}

const val TAG_SETTINGS_SCREEN = "tv-settings"
const val TAG_SETTINGS_LIST = "tv-settings-list"
const val TAG_SECTION = "tv-settings-section"
const val TAG_ROW = "tv-settings-row"
const val TAG_PANEL_LANGUAGE = "tv-settings-language"
const val TAG_PANEL_CAPTION_SIZE = "tv-settings-caption-size"
const val TAG_PANEL_UI_SCALE = "tv-settings-ui-scale"
const val TAG_PANEL_CAPTION_BACKGROUND = "tv-settings-caption-background"
const val TAG_PANEL_ACCOUNTS = "tv-settings-accounts"
const val TAG_PANEL_SIGN_OUT = "tv-settings-sign-out"

/** Lazy-list index of [key]: the title, then a header before each section's first row. */
internal fun settingsListIndex(rows: List<TvSettingRow>, key: String): Int? {
    var index = 1
    var previous: TvSettingsSection? = null
    for (row in rows) {
        if (row.section != previous) { index++; previous = row.section }
        if (row.key == key) return index
        index++
    }
    return null
}
