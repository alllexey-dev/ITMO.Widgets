package dev.alllexey.itmowidgets.feature.onboarding.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ChoiceDialog
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsChoiceRow
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsGroup
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsToggleRow
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetOption
import dev.alllexey.itmowidgets.feature.onboarding.presentation.options
import dev.alllexey.itmowidgets.feature.onboarding.presentation.textSize
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.core.settings_qr_custom_image_default
import dev.alllexey.itmowidgets.shared.core.settings_qr_custom_image_selected
import dev.alllexey.itmowidgets.shared.core.settings_qr_custom_image_title
import dev.alllexey.itmowidgets.shared.core.settings_qr_dynamic_colors_title
import dev.alllexey.itmowidgets.shared.core.settings_qr_spoiler_description
import dev.alllexey.itmowidgets.shared.core.settings_qr_spoiler_title
import dev.alllexey.itmowidgets.shared.core.settings_widget_hide_past_title
import dev.alllexey.itmowidgets.shared.core.settings_widget_hide_teacher_title
import dev.alllexey.itmowidgets.shared.core.settings_widget_next_early_description
import dev.alllexey.itmowidgets.shared.core.settings_widget_next_early_title
import dev.alllexey.itmowidgets.shared.core.settings_widget_text_size_extra_large
import dev.alllexey.itmowidgets.shared.core.settings_widget_text_size_large
import dev.alllexey.itmowidgets.shared.core.settings_widget_text_size_normal
import dev.alllexey.itmowidgets.shared.core.settings_widget_text_size_title
import dev.alllexey.itmowidgets.shared.core.settings_widget_tomorrow_description
import dev.alllexey.itmowidgets.shared.core.settings_widget_tomorrow_title
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_pin
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_compact_widget_title
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_full_widget_title
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_qr_widget_title
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_spoiler_image_replace
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_spoiler_image_reset
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_widget_add
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_widget_added
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_widget_manual_hint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * One widget: its real preview in a card, the choices that shape it as the settings screen's own rows (toggles, then
 * `Размер текста` for a schedule widget or `Изображение спойлера` for QR), and the pin to the launcher, or the
 * launcher hint where pinning is unsupported. Rows wait for the stored appearance instead of showing defaults.
 */
@Composable
internal fun OnboardingWidgetStep(
    kind: WidgetKind,
    state: OnboardingUiState,
    actions: OnboardingActions,
    widgetPreview: @Composable (WidgetKind, Modifier) -> Unit,
) {
    val appearance = state.appearance
    var choosingTextSize by rememberSaveable(kind) { mutableStateOf(false) }
    var choosingSpoilerImage by rememberSaveable(kind) { mutableStateOf(false) }
    val textSize = appearance?.let(kind::textSize)

    OnboardingPage(stringResource(kind.titleRes)) {
        OnboardingCard(Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.group)) {
            Box(Modifier.padding(ItmoTheme.spacing.cardPadding)) {
                widgetPreview(kind, Modifier.fillMaxWidth().testTag(OnboardingTestTags.PREVIEW))
            }
        }
        SettingsGroup(Modifier.padding(top = ItmoTheme.spacing.compact)) {
            kind.options.forEach { option ->
                row(option) {
                    SettingsToggleRow(
                        title = stringResource(option.titleRes),
                        checked = appearance?.let(option::isEnabled),
                        onCheckedChange = { actions.onOption(option, it) },
                        modifier = Modifier.testTag(OnboardingTestTags.option(option)),
                        description = option.descriptionRes?.let { stringResource(it) },
                    )
                }
            }
            if (kind == WidgetKind.QR) {
                row("spoiler_image") {
                    SpoilerImageRow(state) {
                        if (state.customSpoiler == true) choosingSpoilerImage = true else actions.onPickSpoilerImage()
                    }
                }
            } else {
                row("text_size") {
                    SettingsChoiceRow(
                        title = stringResource(CoreRes.string.settings_widget_text_size_title),
                        value = textSize?.let { stringResource(it.labelRes) },
                        onClick = { if (textSize != null) choosingTextSize = true },
                        modifier = Modifier.testTag(OnboardingTestTags.TEXT_SIZE),
                    )
                }
            }
        }
        PinAction(kind, state, actions)
    }

    if (choosingTextSize && textSize != null) {
        ChoiceDialog(
            title = stringResource(CoreRes.string.settings_widget_text_size_title),
            options = WidgetTextSize.entries.map { stringResource(it.labelRes) },
            selectedIndex = WidgetTextSize.entries.indexOf(textSize),
            onSelect = { index ->
                choosingTextSize = false
                actions.onTextSize(kind, WidgetTextSize.entries[index])
            },
            onDismiss = { choosingTextSize = false },
            dismissLabel = stringResource(CoreRes.string.common_cancel),
        )
    }
    if (choosingSpoilerImage) {
        ChoiceDialog(
            title = stringResource(CoreRes.string.settings_qr_custom_image_title),
            options = listOf(
                stringResource(Res.string.onboarding_spoiler_image_replace),
                stringResource(Res.string.onboarding_spoiler_image_reset),
            ),
            selectedIndex = null,
            onSelect = { index ->
                choosingSpoilerImage = false
                if (index == 0) actions.onPickSpoilerImage() else actions.onResetSpoilerImage()
            },
            onDismiss = { choosingSpoilerImage = false },
            dismissLabel = stringResource(CoreRes.string.common_cancel),
        )
    }
}

/**
 * `Стандартное` or `Своё изображение` once the stored image answers; dimmed and inert without the spoiler, before
 * that answer and while an image is being written, exactly like the settings row.
 */
@Composable
private fun SpoilerImageRow(state: OnboardingUiState, onClick: () -> Unit) {
    val configured = state.customSpoiler
    SettingsChoiceRow(
        title = stringResource(CoreRes.string.settings_qr_custom_image_title),
        value = configured?.let {
            stringResource(
                if (it) CoreRes.string.settings_qr_custom_image_selected else CoreRes.string.settings_qr_custom_image_default,
            )
        },
        onClick = onClick,
        modifier = Modifier.testTag(OnboardingTestTags.SPOILER_IMAGE),
        enabled = state.appearance?.qr?.spoilerEnabled == true && configured != null && !state.spoilerBusy,
    )
}

/** A confirmed pin only changes the label and the icon; nothing moves. */
@Composable
private fun PinAction(kind: WidgetKind, state: OnboardingUiState, actions: OnboardingActions) {
    if (state.pinSupported) {
        val pinned = kind in state.pinnedWidgets
        ProgressButton(
            label = stringResource(if (pinned) Res.string.onboarding_widget_added else Res.string.onboarding_widget_add),
            onClick = { actions.onPinWidget(kind) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ItmoTheme.spacing.group)
                .testTag(OnboardingTestTags.PIN),
            style = ProgressButtonStyle.Tonal,
            enabled = !pinned,
            icon = painterResource(if (pinned) KitRes.drawable.ic_check else KitRes.drawable.ic_pin),
        )
    } else {
        Text(
            stringResource(Res.string.onboarding_widget_manual_hint),
            Modifier
                .fillMaxWidth()
                .padding(top = ItmoTheme.spacing.group)
                .padding(horizontal = ItmoTheme.spacing.group)
                .testTag(OnboardingTestTags.PIN_HINT),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
        )
    }
}

private val WidgetKind.titleRes: StringResource
    get() = when (this) {
        WidgetKind.SINGLE_LESSON -> Res.string.onboarding_compact_widget_title
        WidgetKind.DAY_SCHEDULE -> Res.string.onboarding_full_widget_title
        WidgetKind.QR -> Res.string.onboarding_qr_widget_title
    }

private val WidgetOption.titleRes: StringResource
    get() = when (this) {
        WidgetOption.COMPACT_NEXT_LESSON_EARLY -> CoreRes.string.settings_widget_next_early_title
        WidgetOption.COMPACT_HIDE_TEACHER, WidgetOption.FULL_HIDE_TEACHER ->
            CoreRes.string.settings_widget_hide_teacher_title
        WidgetOption.FULL_HIDE_PAST_LESSONS -> CoreRes.string.settings_widget_hide_past_title
        WidgetOption.FULL_SHOW_TOMORROW -> CoreRes.string.settings_widget_tomorrow_title
        WidgetOption.QR_DYNAMIC_COLORS -> CoreRes.string.settings_qr_dynamic_colors_title
        WidgetOption.QR_SPOILER -> CoreRes.string.settings_qr_spoiler_title
    }

private val WidgetOption.descriptionRes: StringResource?
    get() = when (this) {
        WidgetOption.COMPACT_NEXT_LESSON_EARLY -> CoreRes.string.settings_widget_next_early_description
        WidgetOption.FULL_SHOW_TOMORROW -> CoreRes.string.settings_widget_tomorrow_description
        WidgetOption.QR_SPOILER -> CoreRes.string.settings_qr_spoiler_description
        else -> null
    }

private val WidgetTextSize.labelRes: StringResource
    get() = when (this) {
        WidgetTextSize.NORMAL -> CoreRes.string.settings_widget_text_size_normal
        WidgetTextSize.LARGE -> CoreRes.string.settings_widget_text_size_large
        WidgetTextSize.EXTRA_LARGE -> CoreRes.string.settings_widget_text_size_extra_large
    }
