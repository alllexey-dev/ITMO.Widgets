package dev.alllexey.itmowidgets.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.charts.StepsIndicator
import dev.alllexey.itmowidgets.designsystem.gesture.tabSwipeBlocked
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingStep
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetKind
import dev.alllexey.itmowidgets.feature.onboarding.presentation.WidgetOption
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_done
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_next
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_skip
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_step_progress
import org.jetbrains.compose.resources.stringResource

/** What a test or a host looks up on the first-run flow. */
object OnboardingTestTags {
    const val STEPS = "onboarding_steps"
    const val PAGER = "onboarding_pager"
    const val SKIP = "onboarding_skip"
    const val NEXT = "onboarding_next"
    const val PREVIEW = "onboarding_widget_preview"
    const val PIN = "onboarding_pin"
    const val PIN_HINT = "onboarding_pin_hint"
    const val TEXT_SIZE = "onboarding_text_size"
    const val SPOILER_IMAGE = "onboarding_spoiler_image"
    const val SERVICES_ROW = "onboarding_services_row"
    const val SERVICES_SWITCH = "onboarding_services_switch"
    const val SERVICES_PROGRESS = "onboarding_services_progress"
    const val SERVICES_SOURCE = "onboarding_services_source"
    const val NOTIFICATIONS_STATUS = "onboarding_notifications_status"
    const val NOTIFICATIONS_BUTTON = "onboarding_notifications_button"

    fun option(option: WidgetOption): String = "onboarding_option_${option.name.lowercase()}"
}

/**
 * The callbacks of the first-run flow, one per [dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel]
 * entry point, plus the ones only a host can serve: the photo picker of the spoiler image and opening a link.
 */
data class OnboardingActions(
    val onNext: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onOption: (WidgetOption, Boolean) -> Unit = { _, _ -> },
    val onTextSize: (WidgetKind, WidgetTextSize) -> Unit = { _, _ -> },
    val onPickSpoilerImage: () -> Unit = {},
    val onResetSpoilerImage: () -> Unit = {},
    val onPinWidget: (WidgetKind) -> Unit = {},
    val onServicesEnabled: (Boolean) -> Unit = {},
    val onRequestNotifications: () -> Unit = {},
    val onOpenLink: (url: String) -> Unit = {},
)

/**
 * The first-run flow (`docs/features/onboarding.md`, Steps): the progress dots, one page per step of
 * [OnboardingUiState.steps] that only the footer moves, and the footer with `Пропустить` and `Далее`/`Готово`.
 * Stateless; the host owns the ViewModel, its events and back.
 *
 * [widgetPreview] draws the real widget of a [WidgetKind] in the preview card, filling the width it gets: the single
 * lesson at the widget's own height, the day list in its bounded 160 dp area (`WidgetPreviewFactory` on Android).
 * [notificationPermissionIsRuntime] is false where the system has no notification dialog (Android before 13), so the
 * notifications step offers the settings page from the start.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    actions: OnboardingActions,
    widgetPreview: @Composable (WidgetKind, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    notificationPermissionIsRuntime: Boolean = true,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val steps = state.steps
    val pager = rememberPagerState(initialPage = state.stepIndex) { steps.size }
    LaunchedEffect(pager, state.stepIndex) {
        if (pager.currentPage != state.stepIndex) pager.animateScrollToPage(state.stepIndex)
    }
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            StepsIndicator(
                count = steps.size,
                current = state.stepIndex,
                contentDescription = stringResource(Res.string.onboarding_step_progress, state.stepIndex + 1, steps.size),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = ItmoTheme.spacing.group, bottom = ItmoTheme.spacing.compact)
                    .testTag(OnboardingTestTags.STEPS),
            )
            HorizontalPager(
                state = pager,
                // The flow moves through its footer, never by a swipe past an unanswered step.
                modifier = Modifier.weight(1f).fillMaxWidth().tabSwipeBlocked().testTag(OnboardingTestTags.PAGER),
                userScrollEnabled = false,
                key = { page -> steps[page] },
            ) { page ->
                when (val step = steps[page]) {
                    OnboardingStep.COMPACT_WIDGET, OnboardingStep.FULL_WIDGET, OnboardingStep.QR_WIDGET ->
                        OnboardingWidgetStep(step.widgetKind, state, actions, widgetPreview)
                    OnboardingStep.SERVICES -> OnboardingServicesStep(state, actions)
                    OnboardingStep.NOTIFICATIONS ->
                        OnboardingNotificationsStep(state, actions, notificationPermissionIsRuntime)
                }
            }
            OnboardingFooter(state, actions)
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** `Пропустить` until the last step, then only `Готово`; the activity leaves the flow, so one tap is enough. */
@Composable
private fun OnboardingFooter(state: OnboardingUiState, actions: OnboardingActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = ItmoTheme.spacing.screenMargin,
                top = ItmoTheme.spacing.compact,
                end = ItmoTheme.spacing.screenMargin,
                bottom = ItmoTheme.spacing.group,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!state.isLastStep) {
            ProgressButton(
                label = stringResource(Res.string.onboarding_skip),
                onClick = actions.onSkip,
                modifier = Modifier.testTag(OnboardingTestTags.SKIP),
                style = ProgressButtonStyle.Text,
                enabled = !state.finished,
            )
        }
        Spacer(Modifier.weight(1f))
        ProgressButton(
            label = stringResource(if (state.isLastStep) Res.string.onboarding_done else Res.string.onboarding_next),
            onClick = actions.onNext,
            modifier = Modifier.widthIn(min = NextMinWidth).testTag(OnboardingTestTags.NEXT),
            enabled = !state.finished,
        )
    }
}

private val OnboardingStep.widgetKind: WidgetKind
    get() = when (this) {
        OnboardingStep.COMPACT_WIDGET -> WidgetKind.SINGLE_LESSON
        OnboardingStep.FULL_WIDGET -> WidgetKind.DAY_SCHEDULE
        OnboardingStep.QR_WIDGET -> WidgetKind.QR
        OnboardingStep.SERVICES, OnboardingStep.NOTIFICATIONS -> error("$this is not a widget step")
    }

/** `fragment_onboarding.xml`'s `next_button` `android:minWidth`. */
private val NextMinWidth = 128.dp
