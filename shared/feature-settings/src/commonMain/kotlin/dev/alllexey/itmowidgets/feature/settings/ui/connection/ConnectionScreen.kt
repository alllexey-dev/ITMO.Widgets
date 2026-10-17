package dev.alllexey.itmowidgets.feature.settings.ui.connection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoLoadingIndicator
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenu
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.components.services.ServicesConsentSheet
import dev.alllexey.itmowidgets.designsystem.components.services.StoredDataGroup
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SmallButton
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.BarsConnection
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.ConnectionPrivacy
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.ConnectionUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.PrivacyTopic
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_book_2
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_month
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_done
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_code_blocks
import dev.alllexey.itmowidgets.shared.designsystem.ic_delete
import dev.alllexey.itmowidgets.shared.designsystem.ic_devices
import dev.alllexey.itmowidgets.shared.designsystem.ic_grading
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code_scanner
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import dev.alllexey.itmowidgets.shared.designsystem.ic_visibility_off
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import org.jetbrains.compose.resources.painterResource

/** What «Подключение и данные» asks of its host. [onConnectedChange] gets `true` only after the consent sheet. */
data class ConnectionActions(
    val onBack: () -> Unit = {},
    val onConnectedChange: (Boolean) -> Unit = {},
    val onAudience: (PrivacyTopic, SharingVisibility) -> Unit = { _, _ -> },
    val onRetryPrivacy: () -> Unit = {},
    val onFriends: () -> Unit = {},
    val onQueue: () -> Unit = {},
    val onLinks: () -> Unit = {},
    val onSource: () -> Unit = {},
    val onBars: () -> Unit = {},
    val onPairDevice: () -> Unit = {},
    val onDevices: () -> Unit = {},
    val onDeleteAccount: () -> Unit = {},
)

/** Test tags of «Подключение и данные». */
object ConnectionTags {
    const val SCREEN = "connection"
    const val SWITCH = "connection_switch"
    const val AUDIENCES = "connection_audiences"
    const val BARS = "connection_bars"
    const val DEVICES = "connection_devices"
    const val DELETE = "connection_delete"
}

/**
 * «Подключение и данные» (DECISIONS.md, Screens), the one home of the connection to ITMO.Widgets: the status with its
 * switch, what the connection gives (rows lead to their sections while connected), what works without it, what the
 * server keeps, who sees what, BARS, the devices and the account deletion. Turning it on always goes through the
 * consent sheet; [consentShown] opens the screen with it (the sheet's own state is kept across recreation).
 */
@Composable
fun ConnectionScreen(
    state: ConnectionUiState,
    actions: ConnectionActions,
    modifier: Modifier = Modifier,
    consentShown: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
) {
    var consent by rememberSaveable { mutableStateOf(consentShown) }
    ItmoDetailScreen(
        title = text(SettingsStrings.connection_title),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier.testTag(ConnectionTags.SCREEN),
    ) { padding ->
        if (state.loaded) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .padding(top = ItmoTheme.spacing.related, bottom = ItmoTheme.spacing.group),
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            ) {
                StatusCard(state) { on -> if (on) consent = true else actions.onConnectedChange(false) }
                Gives(state, actions)
                Offline()
                SectionHeader(text(if (state.connected) SettingsStrings.connection_stored_on else SettingsStrings.connection_stored_off))
                StoredDataGroup {
                    ListRow(
                        text(SettingsStrings.connection_source),
                        icon = painterResource(Res.drawable.ic_code_blocks),
                        chevron = true,
                        onClick = actions.onSource,
                    )
                }
                ListGroupNote(text(CoreStrings.services_stored_token))
                Audiences(state.privacy, actions)
                Bars(state.bars, actions)
                Devices(state.connected, actions)
                ListGroup(Modifier.padding(top = ItmoTheme.spacing.group).testTag(ConnectionTags.DELETE)) {
                    item {
                        ListRow(
                            text(SettingsStrings.connection_delete),
                            subtitle = text(SettingsStrings.connection_delete_text),
                            icon = painterResource(Res.drawable.ic_delete),
                            iconTone = Tone.Error,
                            chevron = true,
                            danger = true,
                            onClick = actions.onDeleteAccount,
                        )
                    }
                }
                ListGroupNote(text(SettingsStrings.connection_delete_note))
            }
        }
    }
    if (consent) {
        ServicesConsentSheet(
            onConnect = {
                consent = false
                actions.onConnectedChange(true)
            },
            onDismiss = { consent = false },
        )
    }
}

@Composable
private fun StatusCard(state: ConnectionUiState, onSwitch: (Boolean) -> Unit) {
    val on = state.connected
    ItmoCard(tone = if (on) Tone.Primary else Tone.Neutral, hero = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content), verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                painterResource(if (on) Res.drawable.ic_cloud_done else Res.drawable.ic_cloud_off),
                if (on) Tone.Success else Tone.Neutral,
                size = StatusTileSize,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text(if (on) SettingsStrings.connection_status_on else SettingsStrings.connection_status_off),
                    style = ItmoTheme.emphasizedTypography.headlineSmall,
                )
                Text(
                    text(if (on) SettingsStrings.connection_status_on_detail else SettingsStrings.connection_status_off_detail),
                    style = ItmoTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text(SettingsStrings.connection_status_text),
            Modifier.padding(top = ItmoTheme.spacing.compact),
            style = ItmoTheme.typography.bodyMedium,
        )
        SwitchListRow(
            text(SettingsStrings.connection_switch),
            checked = on,
            onCheckedChange = onSwitch,
            modifier = Modifier.testTag(ConnectionTags.SWITCH),
            enabled = state.changeable,
            inset = true,
        )
    }
}

@Composable
private fun Gives(state: ConnectionUiState, actions: ConnectionActions) {
    val on = state.connected
    val tone = if (on) Tone.Success else Tone.Neutral
    SectionHeader(text(SettingsStrings.connection_gives))
    ListGroup {
        item {
            ListRow(
                text(SettingsStrings.connection_friends),
                subtitle = state.friends?.asString() ?: text(SettingsStrings.connection_friends_text),
                icon = painterResource(Res.drawable.ic_group),
                iconTone = tone,
                badge = state.friendRequests.takeIf { it > 0 },
                chevron = on,
                onClick = actions.onFriends.takeIf { on },
            )
        }
        item {
            ListRow(
                text(SettingsStrings.connection_queue),
                subtitle = text(SettingsStrings.connection_queue_text),
                icon = painterResource(Res.drawable.ic_sprint),
                iconTone = tone,
                chevron = on,
                onClick = actions.onQueue.takeIf { on },
            )
        }
        item {
            ListRow(
                text(SettingsStrings.connection_links),
                subtitle = text(if (on) SettingsStrings.connection_links_on else SettingsStrings.connection_links_off),
                icon = painterResource(Res.drawable.ic_link),
                iconTone = tone,
                chevron = on,
                onClick = actions.onLinks.takeIf { on },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Offline() {
    SectionHeader(text(SettingsStrings.connection_offline))
    ItmoCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PillGap), verticalArrangement = Arrangement.spacedBy(PillGap)) {
            listOf(
                Res.drawable.ic_calendar_month to SettingsStrings.connection_offline_schedule,
                Res.drawable.ic_book_2 to SettingsStrings.connection_offline_recordbook,
                Res.drawable.ic_grading to SettingsStrings.connection_offline_bars,
                Res.drawable.ic_qr_code to SettingsStrings.connection_offline_pass,
                Res.drawable.ic_sprint to SettingsStrings.connection_offline_sport,
            ).forEach { (icon, label) -> StatusPill(text(label), icon = painterResource(icon)) }
        }
        Text(
            text(SettingsStrings.connection_offline_text),
            Modifier.padding(top = ItmoTheme.spacing.related),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Audiences(privacy: ConnectionPrivacy, actions: ConnectionActions) {
    SectionHeader(text(SettingsStrings.connection_audience))
    when (privacy) {
        ConnectionPrivacy.OnlyYou -> ListGroup {
            item {
                ListRow(
                    text(SettingsStrings.connection_only_you),
                    subtitle = text(SettingsStrings.connection_only_you_text),
                    icon = painterResource(Res.drawable.ic_visibility_off),
                )
            }
        }
        ConnectionPrivacy.Loading -> ItmoCard(Modifier.testTag(ConnectionTags.AUDIENCES)) {
            Box(Modifier.padding(ItmoTheme.spacing.related).align(Alignment.CenterHorizontally)) {
                ItmoLoadingIndicator(Modifier.size(LoadingSize))
            }
        }
        ConnectionPrivacy.Failed -> ListGroup(Modifier.testTag(ConnectionTags.AUDIENCES)) {
            item {
                ListRow(
                    text(SettingsStrings.connection_audience_failed),
                    accessory = { SmallButton(text(CoreStrings.common_retry), actions.onRetryPrivacy) },
                )
            }
        }
        is ConnectionPrivacy.Audiences -> {
            ListGroup(Modifier.testTag(ConnectionTags.AUDIENCES)) {
                item { AudienceRow(PrivacyTopic.SCHEDULE, privacy.schedule, !privacy.updating, actions) }
                item { AudienceRow(PrivacyTopic.SPORT, privacy.sport, !privacy.updating, actions) }
                item { AudienceRow(PrivacyTopic.FRIENDS, privacy.friends, !privacy.updating, actions) }
            }
            ListGroupNote(text(SettingsStrings.connection_audience_note))
        }
    }
}

/** One audience with its value; a tap opens the three choices as a menu under the row. */
@Composable
private fun AudienceRow(topic: PrivacyTopic, value: SharingVisibility, enabled: Boolean, actions: ConnectionActions) {
    var menu by remember { mutableStateOf(false) }
    val (icon, title) = when (topic) {
        PrivacyTopic.SCHEDULE -> Res.drawable.ic_calendar_month to SettingsStrings.connection_audience_schedule
        PrivacyTopic.SPORT -> Res.drawable.ic_sprint to SettingsStrings.connection_audience_sport
        PrivacyTopic.FRIENDS -> Res.drawable.ic_group to SettingsStrings.connection_audience_friends
    }
    Box {
        ListRow(
            text(title),
            Modifier.testTag("${ConnectionTags.AUDIENCES}_${topic.name.lowercase()}"),
            icon = painterResource(icon),
            trailing = value.label(),
            chevron = true,
            onClick = { if (enabled) menu = true },
        )
        ItmoMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            groups = listOf(SharingVisibility.entries.map { option -> ItmoMenuItem(option.label(), { actions.onAudience(topic, option) }) }),
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun SharingVisibility.label(): String = text(
    when (this) {
        SharingVisibility.ALL -> SettingsStrings.settings_privacy_all
        SharingVisibility.FRIENDS -> SettingsStrings.settings_privacy_friends
        SharingVisibility.NOBODY -> SettingsStrings.settings_privacy_nobody
    },
)

@Composable
private fun Bars(bars: BarsConnection, actions: ConnectionActions) {
    SectionHeader(text(SettingsStrings.connection_bars))
    val action = when (bars) {
        BarsConnection.CONNECTED -> null
        BarsConnection.EXPIRED -> SettingsStrings.connection_bars_sign_in
        BarsConnection.NOT_CONNECTED -> SettingsStrings.connection_bars_connect
    }
    ListGroup(Modifier.testTag(ConnectionTags.BARS)) {
        item {
            ListRow(
                text(SettingsStrings.connection_bars),
                subtitle = text(
                    when (bars) {
                        BarsConnection.CONNECTED -> SettingsStrings.connection_bars_connected
                        BarsConnection.EXPIRED -> SettingsStrings.connection_bars_expired
                        BarsConnection.NOT_CONNECTED -> SettingsStrings.connection_bars_off
                    },
                ),
                icon = painterResource(Res.drawable.ic_grading),
                iconTone = when (bars) {
                    BarsConnection.CONNECTED -> Tone.Success
                    BarsConnection.EXPIRED -> Tone.Warning
                    BarsConnection.NOT_CONNECTED -> Tone.Neutral
                },
                accessory = if (action != null) {
                    { SmallButton(text(action), actions.onBars) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun Devices(connected: Boolean, actions: ConnectionActions) {
    SectionHeader(text(SettingsStrings.connection_devices))
    ListGroup(Modifier.testTag(ConnectionTags.DEVICES)) {
        item {
            ListRow(
                text(SettingsStrings.connection_pair),
                subtitle = text(if (connected) SettingsStrings.connection_pair_text else SettingsStrings.connection_devices_off),
                icon = painterResource(Res.drawable.ic_qr_code_scanner),
                chevron = connected,
                onClick = actions.onPairDevice.takeIf { connected },
            )
        }
        item {
            ListRow(
                text(SettingsStrings.connection_devices_list),
                subtitle = text(if (connected) SettingsStrings.connection_devices_list_text else SettingsStrings.connection_devices_off),
                icon = painterResource(Res.drawable.ic_devices),
                chevron = connected,
                onClick = actions.onDevices.takeIf { connected },
            )
        }
    }
}

@Composable
private fun text(key: String): String = UiText.Res(key).asString()

private val StatusTileSize = 48.dp
private val PillGap = 6.dp
private val LoadingSize = 40.dp
