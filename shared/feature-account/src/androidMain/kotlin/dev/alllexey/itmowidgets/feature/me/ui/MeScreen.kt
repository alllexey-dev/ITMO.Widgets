package dev.alllexey.itmowidgets.feature.me.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.kit.GateCard
import dev.alllexey.itmowidgets.designsystem.kit.HeaderAction
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoRootScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.me.presentation.MeFriendsSummary
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_brand_github
import dev.alllexey.itmowidgets.shared.designsystem.ic_brand_telegram
import dev.alllexey.itmowidgets.shared.designsystem.ic_check_circle
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_code_blocks
import dev.alllexey.itmowidgets.shared.designsystem.ic_computer
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_language
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import dev.alllexey.itmowidgets.shared.designsystem.ic_logout
import dev.alllexey.itmowidgets.shared.designsystem.ic_notification
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.designsystem.ic_settings
import dev.alllexey.itmowidgets.shared.designsystem.ic_share
import dev.alllexey.itmowidgets.shared.designsystem.ic_widgets
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import org.jetbrains.compose.resources.painterResource

/** The project pages under the app rows; the host knows their addresses. */
enum class MeProjectLink { GITHUB, TELEGRAM }

/**
 * What the Профиль tab asks its host to do. Navigation, sharing and links are platform work; [onOpenProjectLink]
 * answers whether any app opened the page, and the route says so when none did. The connection, notification and
 * widget rows name no route themselves: the host maps them (the settings pages own them).
 */
data class MeActions(
    val onOpenFriends: () -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    /** `Подключение и данные`, also the gate's `Подключить` and the web sign-in row without the connection. */
    val onOpenConnection: () -> Unit = {},
    val onOpenNotifications: () -> Unit = {},
    val onOpenWidgets: () -> Unit = {},
    val onOpenWebLogin: () -> Unit = {},
    /** The official site in the app's web view. */
    val onOpenMyItmo: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenDebugTools: () -> Unit = {},
    /** Shares the profile under the [name] the header shows. */
    val onShareProfile: (name: String, isu: Int) -> Unit = { _, _ -> },
    val onOpenProjectLink: (MeProjectLink) -> Boolean = { true },
    val onSignOut: () -> Unit = {},
)

/** Tags for host and instrumented tests. */
object MeTestTags {
    const val ROOT = "main"
    const val PROFILE_NAME = "profile_name"
    const val PROFILE_GROUP = "profile_group"
    const val PROFILE_META = "profile_meta"
    const val SHARE = "profile_share_button"
    const val FRIENDS_ROW = "friends_row"
    const val PRIVACY_ROW = "privacy_row"
    const val GATE = "people_gate"
    const val CONNECTION_ROW = "connection_row"
    const val NOTIFICATIONS_ROW = "notifications_row"
    const val WIDGETS_ROW = "widgets_row"
    const val WEB_LOGIN_ROW = "web_login_row"
    const val MY_ITMO_ROW = "my_itmo_row"
    const val SETTINGS_ROW = "settings_row"
    const val DEBUG_TOOLS_ROW = "debug_tools_row"
    const val TELEGRAM = "telegram_row"
    const val GITHUB = "github_row"
    const val SIGN_OUT = "sign_out_row"
}

/**
 * The Профиль tab (DECISIONS.md, Профиль): the one-line root header with `Поделиться`, the identity card, then
 * groups of rows. Friends and requests are a counter here (their home is Друзья), the connection a status, the
 * notifications and widgets links; without the connection a gate stands in place of the people rows. The sign-out
 * asks first. [showDebugTools] adds the developer tools row (Android debug builds). Stateless; [MeRoute] feeds it.
 */
@Composable
fun MeScreen(
    state: MeUiState,
    showDebugTools: Boolean,
    actions: MeActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    val name = state.header.name.asString()
    ItmoRootScreen(
        title = text(CoreStrings.tab_profile),
        modifier = modifier,
        actions = {
            val isu = state.header.isu
            if (state.header.shareable && isu != null) {
                HeaderAction(
                    painterResource(Res.drawable.ic_share),
                    text(CoreStrings.share_action),
                    onClick = { actions.onShareProfile(name, isu) },
                    modifier = Modifier.testTag(MeTestTags.SHARE),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize().testTag(MeTestTags.ROOT),
                contentPadding = PaddingValues(
                    start = ItmoTheme.spacing.screenMargin,
                    end = ItmoTheme.spacing.screenMargin,
                    bottom = ItmoTheme.spacing.section,
                ),
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            ) {
                item(key = "header") { Header(state, name) }
                item(key = "people:title") { SectionHeader(text(AccountStrings.me_group_people)) }
                item(key = "people") { People(state, actions) }
                item(key = "widgets:title") { SectionHeader(text(AccountStrings.me_group_widgets)) }
                item(key = "widgets") { Service(state, actions) }
                item(key = "app:title") { SectionHeader(text(CoreStrings.me_group_app)) }
                item(key = "app") { App(showDebugTools, actions) }
                item(key = "sign-out") {
                    ListGroup(Modifier.padding(top = ItmoTheme.spacing.content)) {
                        item {
                            ListRow(
                                text(AccountStrings.me_sign_out),
                                Modifier.testTag(MeTestTags.SIGN_OUT),
                                icon = painterResource(Res.drawable.ic_logout),
                                danger = true,
                                onClick = { if (!state.signOutInProgress) confirmingSignOut = true },
                            )
                        }
                    }
                }
            }
            ItmoSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (confirmingSignOut) {
        ConfirmDialog(
            title = text(AccountStrings.me_sign_out_confirm_title),
            text = text(AccountStrings.me_sign_out_confirm_message),
            confirmLabel = text(AccountStrings.me_sign_out),
            dismissLabel = text(CoreStrings.common_cancel),
            onConfirm = {
                confirmingSignOut = false
                actions.onSignOut()
            },
            onDismiss = { confirmingSignOut = false },
            destructive = true,
        )
    }
}

/** Avatar, the full name, the study group and the ISU. */
@Composable
private fun Header(state: MeUiState, name: String) {
    val header = state.header
    ItmoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group)) {
            Avatar(name, header.pictureUrl, size = AvatarSize, initials = header.initials)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
                Text(
                    name,
                    Modifier.testTag(MeTestTags.PROFILE_NAME),
                    style = ItmoTheme.typography.titleLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                )
                header.group?.let { Quiet(it.asString(), MeTestTags.PROFILE_GROUP) }
                header.isu?.let { Quiet(text(AccountStrings.me_isu, it), MeTestTags.PROFILE_META) }
            }
        }
    }
}

@Composable
private fun Quiet(value: String, tag: String) {
    Text(value, Modifier.testTag(tag), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
}

/** Friends with the requests as a badge, and privacy; without the connection the gate in their place. */
@Composable
private fun People(state: MeUiState, actions: MeActions) {
    if (state.friends == MeFriendsSummary.Disabled) {
        GateCard(
            title = text(AccountStrings.me_gate_title),
            reason = text(AccountStrings.me_gate_reason),
            fix = text(AccountStrings.me_gate_fix),
            onFix = actions.onOpenConnection,
            modifier = Modifier.testTag(MeTestTags.GATE),
            icon = painterResource(Res.drawable.ic_group),
        )
        return
    }
    val requests = state.incomingRequests
    val badge = text(AccountStrings.me_requests_badge_accessibility, requests)
    ListGroup {
        item {
            ListRow(
                text(AccountStrings.me_friends_title),
                Modifier.testTag(MeTestTags.FRIENDS_ROW).semantics { if (requests > 0) contentDescription = badge },
                subtitle = state.friendsSubtitle?.asString(),
                icon = painterResource(Res.drawable.ic_group),
                iconTone = Tone.Primary,
                badge = requests,
                chevron = true,
                onClick = actions.onOpenFriends,
            )
        }
        item {
            ListRow(
                text(AccountStrings.me_privacy_title),
                Modifier.testTag(MeTestTags.PRIVACY_ROW),
                subtitle = text(AccountStrings.me_privacy_description),
                icon = painterResource(Res.drawable.ic_lock),
                iconTone = Tone.Tertiary,
                chevron = true,
                onClick = actions.onOpenPrivacy,
            )
        }
    }
}

/** The connection as a status, the notification and widget pages as links, the web sign-in. */
@Composable
private fun Service(state: MeUiState, actions: MeActions) {
    ListGroup {
        item {
            ListRow(
                text(AccountStrings.me_connection_title),
                Modifier.testTag(MeTestTags.CONNECTION_ROW),
                subtitle = state.connectionStatus.asString(),
                icon = painterResource(if (state.connected) Res.drawable.ic_check_circle else Res.drawable.ic_cloud_off),
                iconTone = if (state.connected) Tone.Success else Tone.Neutral,
                chevron = true,
                onClick = actions.onOpenConnection,
            )
        }
        item {
            ListRow(
                text(AccountStrings.me_notifications_title),
                Modifier.testTag(MeTestTags.NOTIFICATIONS_ROW),
                icon = painterResource(Res.drawable.ic_notification),
                iconTone = Tone.Warning,
                chevron = true,
                onClick = actions.onOpenNotifications,
            )
        }
        item {
            ListRow(
                text(AccountStrings.me_widgets_title),
                Modifier.testTag(MeTestTags.WIDGETS_ROW),
                icon = painterResource(Res.drawable.ic_widgets),
                iconTone = Tone.Primary,
                chevron = true,
                onClick = actions.onOpenWidgets,
            )
        }
        item {
            ListRow(
                text(AccountStrings.me_web_login_title),
                Modifier.testTag(MeTestTags.WEB_LOGIN_ROW),
                subtitle = state.webLoginSubtitle.asString(),
                icon = painterResource(Res.drawable.ic_computer),
                iconTone = Tone.Neutral,
                chevron = true,
                onClick = if (state.connected) actions.onOpenWebLogin else actions.onOpenConnection,
            )
        }
    }
}

@Composable
private fun App(showDebugTools: Boolean, actions: MeActions) {
    ListGroup {
        item {
            ExternalRow(
                text(AccountStrings.me_my_itmo),
                text(AccountStrings.me_my_itmo_subtitle),
                painterResource(Res.drawable.ic_language),
                MeTestTags.MY_ITMO_ROW,
                actions.onOpenMyItmo,
            )
        }
        item {
            ListRow(
                text(CoreStrings.settings_title),
                Modifier.testTag(MeTestTags.SETTINGS_ROW),
                icon = painterResource(Res.drawable.ic_settings),
                iconTone = Tone.Neutral,
                chevron = true,
                onClick = actions.onOpenSettings,
            )
        }
        if (showDebugTools) {
            item {
                ListRow(
                    text(CoreStrings.debug_tools_title),
                    Modifier.testTag(MeTestTags.DEBUG_TOOLS_ROW),
                    icon = painterResource(Res.drawable.ic_code_blocks),
                    iconTone = Tone.Neutral,
                    chevron = true,
                    onClick = actions.onOpenDebugTools,
                )
            }
        }
        item {
            ExternalRow(
                text(AccountStrings.me_telegram),
                text(AccountStrings.me_telegram_subtitle),
                painterResource(Res.drawable.ic_brand_telegram),
                MeTestTags.TELEGRAM,
            ) { actions.onOpenProjectLink(MeProjectLink.TELEGRAM) }
        }
        item {
            ExternalRow(
                text(AccountStrings.me_github),
                text(AccountStrings.me_github_subtitle),
                painterResource(Res.drawable.ic_brand_github),
                MeTestTags.GITHUB,
            ) { actions.onOpenProjectLink(MeProjectLink.GITHUB) }
        }
    }
}

/** A row that leaves the app: an arrow out instead of the chevron. */
@Composable
private fun ExternalRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    tag: String,
    onClick: () -> Unit,
) {
    ListRow(
        title,
        Modifier.testTag(tag),
        subtitle = subtitle,
        icon = icon,
        iconTone = Tone.Neutral,
        accessory = {
            Icon(painterResource(Res.drawable.ic_open_in_new), null, tint = ItmoTheme.colorScheme.onSurfaceVariant)
        },
        onClick = onClick,
    )
}

@Composable
private fun text(key: String, vararg arguments: Any): String = UiText.Res(key, arguments.toList()).asString()

/** The identity card's avatar. */
private val AvatarSize = 64.dp
