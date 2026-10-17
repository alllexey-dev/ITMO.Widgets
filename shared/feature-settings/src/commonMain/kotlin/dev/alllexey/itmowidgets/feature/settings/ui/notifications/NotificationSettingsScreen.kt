package dev.alllexey.itmowidgets.feature.settings.ui.notifications

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.kit.GateCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.MarkSource
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationPermission
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationPreview
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationPreviews
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationSettingsUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.PushKind
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.PushNotifications
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_devices
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_calendar
import dev.alllexey.itmowidgets.shared.designsystem.ic_notifications_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.designsystem.ic_person_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_school
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
    val onMarkSource: (MarkSource, Boolean) -> Unit = { _, _ -> },
    val onScheduleChanges: (Boolean) -> Unit = {},
    val onPush: (PushKind) -> Unit = {},
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
    const val MARKS = "notifications_marks"
    const val CHANGES = "notifications_changes"
    const val BACKGROUND = "notifications_background"
    const val PREVIEW = "notifications_preview"
    fun push(kind: PushKind) = "notifications_push_${kind.name.lowercase()}"
    fun source(source: MarkSource) = "notifications_marks_${source.name.lowercase()}"
}

/**
 * «Уведомления» (DECISIONS.md, Screens), the one home of notification settings: the gate while the system denies them
 * (the settings stay visible, dimmed), then each type with its switch and how it arrives, the pushes that the system
 * and the connection decide, the night rule, `Звук и вибрация` in the system and where everything that came stays.
 * [hintShown] is the background work hint, kept by the host across recreation.
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
                                    SettingsStrings.notifications_marks_text,
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
                                                .padding(start = PreviewInset)
                                                .testTag(NotificationSettingsTags.source(source.source)),
                                            subtitle = if (source.source == MarkSource.BARS) {
                                                text(SettingsStrings.notifications_marks_bars_text)
                                            } else {
                                                null
                                            },
                                            enabled = !denied,
                                        )
                                    }
                                }
                                Preview(NotificationPreviews.marks)
                            }
                        }
                    }
                    item {
                        Column(Modifier.testTag(NotificationSettingsTags.CHANGES)) {
                            TypeSwitch(
                                SettingsStrings.notifications_changes,
                                SettingsStrings.notifications_changes_text,
                                Res.drawable.ic_edit_calendar,
                                Tone.Warning,
                                state.scheduleChanges,
                                !denied,
                                actions.onScheduleChanges,
                            )
                            Preview(NotificationPreviews.scheduleChanges)
                        }
                    }
                    state.pushes.forEach { push -> item { PushType(push, actions) } }
                }
                ListGroupNote(text(SettingsStrings.notifications_push_note))
                ListGroupNote(text(SettingsStrings.notifications_night))
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
                        subtitle = text(SettingsStrings.notifications_sound_text),
                        icon = painterResource(Res.drawable.ic_volume_up),
                        chevron = true,
                        onClick = actions.onSystemSettings,
                    )
                }
            }
            ListGroupNote(text(SettingsStrings.notifications_bell_note))
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

/** A type with its own switch: the icon in its tone while on, neutral while off. */
@Composable
private fun TypeSwitch(
    title: String,
    subtitle: String,
    icon: DrawableResource,
    tone: Tone,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SwitchListRow(
        text(title),
        checked = checked,
        onCheckedChange = onChange,
        subtitle = text(subtitle),
        icon = painterResource(icon),
        iconTone = if (checked) tone else Tone.Neutral,
        enabled = enabled,
    )
}

/** A push: its system switch (Android) or the connection it needs, the way to change it, and how it arrives. */
@Composable
private fun PushType(push: PushNotifications, actions: NotificationSettingsActions) {
    val (title, subtitle, icon) = when (push.kind) {
        PushKind.SPORT -> Triple(SettingsStrings.notifications_sport, SettingsStrings.notifications_sport_text, Res.drawable.ic_sprint)
        PushKind.FRIENDS -> Triple(SettingsStrings.notifications_friends, SettingsStrings.notifications_friends_text, Res.drawable.ic_person_add)
        PushKind.DEVICES -> Triple(SettingsStrings.notifications_devices, SettingsStrings.notifications_devices_text, Res.drawable.ic_devices)
    }
    val tone = when (push.kind) {
        PushKind.SPORT -> Tone.Success
        PushKind.FRIENDS -> Tone.Tertiary
        PushKind.DEVICES -> Tone.Primary
    }
    val on = push.connected && push.systemEnabled != false
    Column(Modifier.testTag(NotificationSettingsTags.push(push.kind))) {
        ListRow(
            text(title),
            subtitle = text(if (push.connected) subtitle else SettingsStrings.notifications_push_connection),
            icon = painterResource(icon),
            iconTone = if (on) tone else Tone.Neutral,
            trailing = push.systemEnabled?.takeIf { push.connected }?.let {
                text(if (it) SettingsStrings.notifications_push_on else SettingsStrings.notifications_push_off)
            },
            chevron = true,
            onClick = { actions.onPush(push.kind) },
        )
        if (push.connected) Preview(push.preview)
    }
}

/** How the type arrives: a miniature of the system notification with the app's mark. */
@Composable
private fun Preview(preview: NotificationPreview) {
    val colors = ItmoTheme.colorScheme
    Row(
        Modifier
            .padding(start = PreviewInset, end = ItmoTheme.spacing.cardPadding, bottom = ItmoTheme.spacing.content)
            .fillMaxWidth()
            .clip(ItmoTheme.shapes.large)
            .background(colors.surfaceContainerHigh)
            .padding(ItmoTheme.spacing.content)
            .testTag(NotificationSettingsTags.PREVIEW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
    ) {
        Box(Modifier.size(MarkSize).clip(CircleShape).background(colors.primary), Alignment.Center) {
            Icon(painterResource(Res.drawable.ic_school), null, Modifier.size(MarkIconSize), tint = colors.onPrimary)
        }
        Column {
            Text(preview.title.asString(), style = ItmoTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            preview.text?.let {
                Text(
                    it.asString(),
                    color = colors.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun MarkSource.title(): String = when (this) {
    MarkSource.MY_ITMO -> SettingsStrings.notifications_marks_myitmo
    MarkSource.BARS -> SettingsStrings.notifications_marks_bars
    MarkSource.SHEETS -> SettingsStrings.notifications_marks_sheets
}

@Composable
private fun text(key: String): String = UiText.Res(key).asString()

/** Under the row's text, past its icon tile. */
private val PreviewInset = 70.dp
private val MarkSize = 24.dp
private val MarkIconSize = 14.dp
private const val DENIED_ALPHA = 0.8f
