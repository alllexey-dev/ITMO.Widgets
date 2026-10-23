package dev.alllexey.itmowidgets.feature.settings.ui.connection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.services.StoredDataGroup
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SmallButton
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.ConnectionPrivacy
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.ConnectionUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.PrivacyTopic
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_month
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_done
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_code_blocks
import dev.alllexey.itmowidgets.shared.designsystem.ic_delete
import dev.alllexey.itmowidgets.shared.designsystem.ic_folder
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import org.jetbrains.compose.resources.painterResource

/** What «Сервер ITMO.Widgets» asks of its host. [onConnectedChange] gets `true` only after the consent sheet. */
data class ConnectionActions(
    val onBack: () -> Unit = {},
    val onConnectedChange: (Boolean) -> Unit = {},
    val onAudience: (PrivacyTopic, SharingVisibility) -> Unit = { _, _ -> },
    val onRetryPrivacy: () -> Unit = {},
    val onSource: () -> Unit = {},
    val onDeleteAccount: () -> Unit = {},
)

/** Test tags of «Сервер ITMO.Widgets». */
object ConnectionTags {
    const val SCREEN = "connection"
    const val SWITCH = "connection_switch"
    const val AUDIENCES = "connection_audiences"
    const val STORED = "connection_stored"
    const val STORED_SHEET = "connection_stored_sheet"
    const val DELETE = "connection_delete"
}

/**
 * «Сервер ITMO.Widgets» (DECISIONS.md, Screens), the one home of the connection to ITMO.Widgets: the status with its
 * switch, then while connected who sees what and the devices, before it what the connection gives; what the server
 * keeps opens in a sheet, and the account deletion comes last in both. Turning it on always goes through the consent
 * sheet; [consentShown] opens the screen with it (the sheets' own state is kept across recreation).
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
    var stored by rememberSaveable { mutableStateOf(false) }
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
                if (state.connected) Audiences(state.privacy, actions) else Gives()
                ListGroup(Modifier.padding(top = ItmoTheme.spacing.group)) {
                    item {
                        ListRow(
                            text(if (state.connected) SettingsStrings.connection_stored_on else SettingsStrings.connection_stored_off),
                            Modifier.testTag(ConnectionTags.STORED),
                            icon = painterResource(Res.drawable.ic_folder),
                            chevron = true,
                            onClick = { stored = true },
                        )
                    }
                }
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
    if (stored) StoredDataSheet(state.connected, actions.onSource, onDismiss = { stored = false })
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

/** What the connection gives, before it is on. */
@Composable
private fun Gives() {
    SectionHeader(text(SettingsStrings.connection_gives))
    ListGroup {
        item {
            ListRow(
                text(SettingsStrings.connection_friends),
                subtitle = text(SettingsStrings.connection_friends_text),
                icon = painterResource(Res.drawable.ic_group),
            )
        }
        item {
            ListRow(
                text(SettingsStrings.connection_queue),
                subtitle = text(SettingsStrings.connection_queue_text),
                icon = painterResource(Res.drawable.ic_sprint),
            )
        }
        item {
            ListRow(
                text(SettingsStrings.connection_links),
                subtitle = text(SettingsStrings.connection_links_off),
                icon = painterResource(Res.drawable.ic_link),
            )
        }
    }
}

@Composable
private fun Audiences(privacy: ConnectionPrivacy, actions: ConnectionActions) {
    SectionHeader(text(SettingsStrings.connection_audience))
    when (privacy) {
        // Only while not connected, when the page has no audiences.
        ConnectionPrivacy.OnlyYou -> Unit
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

/** What the server keeps (or will keep), the link to its code and the token note, in a sheet of their own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoredDataSheet(connected: Boolean, onSource: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = ItmoTheme.colorScheme.surfaceContainerLow,
    ) {
        StoredDataContent(connected, onSource, onDismiss)
    }
}

/** The stored data sheet's body. */
@Composable
internal fun StoredDataContent(connected: Boolean, onSource: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    SheetScaffold(
        title = text(if (connected) SettingsStrings.connection_stored_on else SettingsStrings.connection_stored_off),
        modifier = modifier.testTag(ConnectionTags.STORED_SHEET),
        close = SheetClose(text(CoreStrings.common_close), onDismiss),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ItmoTheme.spacing.section)
                .padding(bottom = ItmoTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            StoredDataGroup {
                ListRow(
                    text(SettingsStrings.connection_source),
                    icon = painterResource(Res.drawable.ic_code_blocks),
                    chevron = true,
                    onClick = onSource,
                )
            }
            ListGroupNote(text(CoreStrings.services_stored_token))
        }
    }
}

@Composable
private fun text(key: String): String = UiText.Res(key).asString()

private val StatusTileSize = 48.dp
private val LoadingSize = 40.dp
