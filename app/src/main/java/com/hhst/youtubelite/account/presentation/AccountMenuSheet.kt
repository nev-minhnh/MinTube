package com.hhst.youtubelite.account.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.account.domain.AccountItem
import com.hhst.youtubelite.account.domain.AccountMenuAction
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.library.domain.AccountProfile
import com.hhst.youtubelite.ui.components.AppSheetHandle
import com.hhst.youtubelite.ui.components.PrivateBrowsingBadge
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MenuSheetItem
import com.hhst.youtubelite.ui.design.MenuSheetLayout
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.appDescription
import com.hhst.youtubelite.ui.design.appPaneTitle
import org.koin.core.context.GlobalContext

val accountMenuActions = AccountMenuAction.entries.filter { it != AccountMenuAction.ViewChannel }
val accountMenuItems = accountMenuActions.map { MenuSheetItem(null, it.label, tag = "account-action-${it.tag}") }

/**
 * The private-browsing preference, followed through the manager's change listener (ACC-07).
 * Read-only: nothing here writes the preference. False where no manager is registered (previews).
 */
@Composable
fun rememberPrivateBrowsing(): Boolean {
    val manager = remember { GlobalContext.getOrNull()?.getOrNull<ExtensionManager>() } ?: return false
    var active by remember(manager) { mutableStateOf(manager.isEnabled(PreferenceKeys.PRIVATE_BROWSING)) }
    DisposableEffect(manager) {
        val listener: (String) -> Unit = { key ->
            if (key == PreferenceKeys.PRIVATE_BROWSING || key == "*") active = manager.isEnabled(PreferenceKeys.PRIVATE_BROWSING)
        }
        manager.addOnChangedListener(listener)
        active = manager.isEnabled(PreferenceKeys.PRIVATE_BROWSING)
        onDispose { manager.removeOnChangedListener(listener) }
    }
    return active
}

/** Header of the account menu: the current account, opening its channel. */
@Composable
fun AccountMenuHeader(account: AccountProfile, onOpenChannel: () -> Unit) =
    AccountMenuHeader(account, rememberPrivateBrowsing(), onOpenChannel)

/** As above with the private-browsing flag supplied by the caller. */
@Composable
fun AccountMenuHeader(account: AccountProfile, privateBrowsing: Boolean, onOpenChannel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.ui_view_channel), onClick = onOpenChannel)
            .testTag("account-menu-header")
            .padding(horizontal = MenuSheetLayout.RowStart.dp, vertical = MinSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(account.avatar, 48)
        Spacer(Modifier.width(MinSpace.md))
        Column(Modifier.weight(1f)) {
            AppText(account.name, MinType.Subtitle, maxLines = 2)
            if (account.handle.isNotBlank()) AppText(account.handle, MinType.Body, color = DesignTokens.Secondary, maxLines = 1)
            if (privateBrowsing) PrivateBrowsingBadge(true, Modifier.padding(top = MinSpace.xs))
        }
        Spacer(Modifier.width(MinSpace.sm))
        AppIcon(AppIcon.ChevronRight, Modifier.size(20.dp), color = DesignTokens.Secondary)
    }
}

@Composable
private fun AccountAvatar(asset: String, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(DesignTokens.Surface), contentAlignment = Alignment.Center) {
        if (asset.isBlank()) AppIcon(AppIcon.Person, Modifier.size((size / 2).dp), color = DesignTokens.Secondary)
        else Artwork(asset, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Account switcher: "On this device", every signed-in account, and "Add account". The caller
 * performs the switch; a row that is already selected does nothing.
 */
@Composable
fun AccountSwitchSheet(
    accounts: List<AccountItem>,
    onSelect: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit,
    deviceSelected: Boolean = false,
    onUseDevice: () -> Unit = {},
) {
    val privateBrowsing = rememberPrivateBrowsing()
    Box(Modifier.fillMaxSize().background(DesignTokens.MenuScrim).clickable { onDismiss() }
        .testTag("account-switch-scrim").appDescription(R.string.a11y_close_menu))
    com.hhst.youtubelite.ui.components.CoverNavigationRail(DesignTokens.MenuScrim, onDismiss = onDismiss)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(MenuSheetLayout.Corner.dp)
        Column(
            Modifier.padding(bottom = MenuSheetLayout.BottomInset.dp)
                .width(MenuSheetLayout.sheetWidth(maxWidth.value, maxHeight.value).dp)
                .heightIn(max = (maxHeight - MenuSheetLayout.BottomInset.dp - 16.dp).coerceAtLeast(0.dp))
                .padding(horizontal = if (maxWidth.value <= MenuSheetLayout.MaxWidth) MinSpace.sm else 0.dp)
                .shadow(8.dp, shape)
                .clip(shape)
                .background(DesignTokens.Panel)
                // Taps on the sheet body must not fall through to the scrim.
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .testTag("account-switch")
                .appPaneTitle(R.string.ui_switch_account),
        ) {
            AppSheetHandle()
            Row(Modifier.fillMaxWidth().padding(horizontal = MenuSheetLayout.RowStart.dp).padding(bottom = MinSpace.sm),
                verticalAlignment = Alignment.CenterVertically) {
                AppText(stringResource(R.string.ui_switch_account), MinType.Title,
                    Modifier.weight(1f).semantics { heading() })
                if (privateBrowsing) {
                    Spacer(Modifier.width(MinSpace.sm))
                    PrivateBrowsingBadge(true)
                }
            }
            SwitchRow(
                tag = "account-switch-device",
                selected = deviceSelected,
                title = stringResource(R.string.ui_on_this_device),
                detail = stringResource(R.string.ui_browse_anonymously_the_account_stays_signed_in),
                onClick = onUseDevice,
            ) {
                Box(Modifier.size(40.dp).background(DesignTokens.Surface, CircleShape), contentAlignment = Alignment.Center) {
                    AppIcon(AppIcon.Device, Modifier.size(20.dp))
                }
            }
            accounts.forEach { account ->
                SwitchRow(
                    tag = "account-switch-${account.id}",
                    selected = account.selected,
                    title = account.name,
                    detail = account.email,
                    onClick = { onSelect(account.id) },
                ) { AccountAvatar(account.photo, 40) }
            }
            Box(Modifier.padding(horizontal = MenuSheetLayout.RowStart.dp, vertical = MinSpace.sm).fillMaxWidth()
                .height(1.dp).background(DesignTokens.Border))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = onAddAccount)
                    .testTag("account-switch-add")
                    .padding(horizontal = MenuSheetLayout.RowStart.dp, vertical = MinSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(DesignTokens.AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                    AppIcon(AppIcon.Add, Modifier.size(20.dp), color = DesignTokens.AccentText)
                }
                Spacer(Modifier.width(MinSpace.md))
                AppText(stringResource(R.string.ui_add_account), MinType.Subtitle, Modifier.weight(1f))
            }
            Spacer(Modifier.height(MinSpace.sm))
        }
    }
}

@Composable
private fun SwitchRow(
    tag: String,
    selected: Boolean,
    title: String,
    detail: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .background(if (selected) DesignTokens.Ghost else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = !selected, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(tag)
            .padding(horizontal = MenuSheetLayout.RowStart.dp, vertical = MinSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(MinSpace.md))
        Column(Modifier.weight(1f)) {
            AppText(title, MinType.Subtitle, maxLines = 2)
            if (detail.isNotBlank()) AppText(detail, MinType.Caption, color = DesignTokens.Secondary, maxLines = 2)
        }
        if (selected) {
            Spacer(Modifier.width(MinSpace.sm))
            AppIcon(AppIcon.CheckCircle, Modifier.size(24.dp), color = DesignTokens.Accent)
        }
    }
}
