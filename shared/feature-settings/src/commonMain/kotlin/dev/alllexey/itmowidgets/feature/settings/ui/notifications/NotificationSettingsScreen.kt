package dev.alllexey.itmowidgets.feature.settings.ui.notifications

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.kit.GateCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.MarkCheckSource
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationPermission
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationSettingsUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.PushKind
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.PushNotifications
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_devices
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_calendar
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_notifications_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.designsystem.ic_person_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import dev.alllexey.itmowidgets.shared.designsystem.ic_star
import dev.alllexey.itmowidgets.shared.designsystem.ic_volume_up
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** What «Уведомления» asks of its host; the switches go to the view model. */
data class NotificationSettingsActions(
    val onBack: () -> Unit = {},
    val onMarks: (Boolean) -> Unit = {},
    val onMarkSource: (MarkCheckSource, Boolean) -> Unit = { _, _ -> },
    val onScheduleChanges: (Boolean) -> Unit = {},
    val onPush: (PushKind, Boolean) -> Unit = { _, _ -> },
    val onConnection: () -> Unit = {},
    val onRequestPermission: () -> Unit = {},
    val onSystemSettings: () -> Unit = {},
    val onBackgroundWork: () -> Unit = {},
    /** The background work hint's `Разрешить`; `Не сейчас` and outside taps call [onDismissHint]. */
    val onAllowBackgroundWork: () -> Unit = {},
    val onDismissHint: () -> Unit = {},
)

/** Test tags of «Уведомления». */
object NotificationSettingsTags {
    const val SCREEN = "notifications"
    const val GATE = "notifications_gate"
    const val CHANNELS = "notifications_channels"
    const val MARKS = "notifications_marks"
    const val CHANGES = "notifications_changes"
    const val CONNECTION = "notifications_connection"
    const val BACKGROUND = "notifications_background"
    fun push(kind: PushKind) = "notifications_push_${kind.name.lowercase()}"
    fun source(source: MarkCheckSource) = "notifications_marks_${source.name.lowercase()}"
}

/**
 * «Уведомления» (DECISIONS.md, Screens), the one home of notification settings: one card on top while the system
 * keeps something from arriving (the permission, else a channel of a type that is on), then every type with its
 * switch, the background work row while it matters and `Звук и вибрация` in the system. While the permission is
 * denied the switches stay visible, dimmed. [hintShown] is the background work hint, kept by the host across
 * recreation.
 */
@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    actions: NotificationSettingsActions,
    modifier: Modifier = Modifier,
    hintShown: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
) {
    ItmoDetailScreen(
        title = text(SettingsStrings.notifications_title),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier.testTag(NotificationSettingsTags.SCREEN),
        subtitle = state.subtitle?.asString(),
    ) { padding ->
        if (!state.loaded) return@ItmoDetailScreen
        val denied = state.permission == NotificationPermission.DENIED
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = ItmoTheme.spacing.screenMargin)
                .padding(top = ItmoTheme.spacing.related, bottom = ItmoTheme.spacing.group),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            if (denied) {
                GateCard(
                    title = text(SettingsStrings.notifications_gate_title),
                    reason = text(SettingsStrings.notifications_gate_text),
                    fix = text(SettingsStrings.notifications_gate_fix),
                    onFix = actions.onRequestPermission,
                    modifier = Modifier.testTag(NotificationSettingsTags.GATE),
                    icon = painterResource(Res.drawable.ic_notifications_off),
                )
            } else if (state.systemChannelsOff) {
                GateCard(
                    title = text(SettingsStrings.notifications_channels_title),
                    reason = text(SettingsStrings.notifications_channels_text),
                    fix = text(SettingsStrings.notifications_channels_fix),
                    onFix = actions.onSystemSettings,
                    modifier = Modifier.testTag(NotificationSettingsTags.CHANNELS),
                    icon = painterResource(Res.drawable.ic_notifications_off),
                )
            }
            Column(
                Modifier.alpha(if (denied) DENIED_ALPHA else 1f),
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            ) {
                SectionHeader(text(SettingsStrings.notifications_what))
                ListGroup {
                    state.marks?.let { marks ->
                        item {
                            Column(Modifier.testTag(NotificationSettingsTags.MARKS)) {
                                TypeSwitch(
                                    SettingsStrings.notifications_marks,
                                    Res.drawable.ic_star,
                                    Tone.Primary,
                                    marks.enabled,
                                    !denied,
                                    actions.onMarks,
                                )
                                if (marks.enabled) {
                                    marks.sources.forEach { source ->
                                        SwitchListRow(
                                            text(source.source.title()),
                                            checked = source.enabled,
                                            onCheckedChange = { actions.onMarkSource(source.source, it) },
                                            modifier = Modifier
                                                .padding(start = SourceInset)
                                                .testTag(NotificationSettingsTags.source(source.source)),
                                            enabled = !denied,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        TypeSwitch(
                            SettingsStrings.notifications_changes,
                            Res.drawable.ic_edit_calendar,
                            Tone.Warning,
                            state.scheduleChanges,
                            !denied,
                            actions.onScheduleChanges,
                            Modifier.testTag(NotificationSettingsTags.CHANGES),
                        )
                    }
                    if (!state.connected) {
                        item {
                            ListRow(
                                text(SettingsStrings.notifications_push_connection),
                                modifier = Modifier.testTag(NotificationSettingsTags.CONNECTION),
                                icon = painterResource(Res.drawable.ic_cloud_off),
                                iconTone = Tone.Neutral,
                                chevron = true,
                                onClick = actions.onConnection,
                            )
                        }
                    }
                    state.pushes.forEach { push ->
                        item { PushSwitch(push, enabled = !denied && state.connected, actions.onPush) }
                    }
                }
            }
            if (state.backgroundWorkRestricted) {
                ListGroup(Modifier.padding(top = ItmoTheme.spacing.compact).testTag(NotificationSettingsTags.BACKGROUND)) {
                    item {
                        ListRow(
                            text(SettingsStrings.settings_background_work_title),
                            subtitle = text(SettingsStrings.background_work_hint),
                            icon = painterResource(Res.drawable.ic_open_in_new),
                            onClick = actions.onBackgroundWork,
                        )
                    }
                }
            }
            ListGroup(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                item {
                    ListRow(
                        text(SettingsStrings.notifications_sound),
                        icon = painterResource(Res.drawable.ic_volume_up),
                        chevron = true,
                        onClick = actions.onSystemSettings,
                    )
                }
            }
        }
    }
    if (hintShown) {
        ConfirmDialog(
            title = text(SettingsStrings.settings_background_work_title),
            confirmLabel = text(SettingsStrings.background_work_allow),
            dismissLabel = text(SettingsStrings.background_work_later),
            onConfirm = actions.onAllowBackgroundWork,
            onDismiss = actions.onDismissHint,
            text = text(SettingsStrings.background_work_dialog_message),
        )
    }
}

/** A type with its switch: the icon in its tone while on, neutral while off. */
@Composable
private fun TypeSwitch(
    title: String,
    icon: DrawableResource,
    tone: Tone,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    SwitchListRow(
        text(title),
        checked = checked,
        onCheckedChange = onChange,
        modifier = modifier,
        subtitle = subtitle?.let { text(it) },
        icon = painterResource(icon),
        iconTone = if (checked && enabled) tone else Tone.Neutral,
        enabled = enabled,
    )
}

/** A push type: the app's switch; it waits for the connection, without which Backend sends nothing. */
@Composable
private fun PushSwitch(push: PushNotifications, enabled: Boolean, onChange: (PushKind, Boolean) -> Unit) {
    val (title, icon, tone) = when (push.kind) {
        PushKind.SPORT -> Triple(SettingsStrings.notifications_sport, Res.drawable.ic_sprint, Tone.Success)
        PushKind.FRIENDS -> Triple(SettingsStrings.notifications_friends, Res.drawable.ic_person_add, Tone.Tertiary)
        PushKind.DEVICES -> Triple(SettingsStrings.notifications_devices, Res.drawable.ic_devices, Tone.Primary)
    }
    TypeSwitch(
        title,
        icon,
        tone,
        push.enabled,
        enabled,
        { onChange(push.kind, it) },
        Modifier.testTag(NotificationSettingsTags.push(push.kind)),
        subtitle = SettingsStrings.notifications_devices_text.takeIf { push.kind == PushKind.DEVICES },
    )
}

private fun MarkCheckSource.title(): String = when (this) {
    MarkCheckSource.MY_ITMO -> SettingsStrings.notifications_marks_myitmo
    MarkCheckSource.BARS -> SettingsStrings.notifications_marks_bars
    MarkCheckSource.SHEETS -> SettingsStrings.notifications_marks_sheets
}

@Composable
private fun text(key: String): String = UiText.Res(key).asString()

/** Under the row's text, past its icon tile. */
private val SourceInset = 70.dp
private const val DENIED_ALPHA = 0.8f
