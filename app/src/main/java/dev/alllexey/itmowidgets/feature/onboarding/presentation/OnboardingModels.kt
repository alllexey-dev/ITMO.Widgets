package dev.alllexey.itmowidgets.feature.onboarding.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize

/** Declaration order is the order of the flow; `next` and `back` walk [OnboardingUiState.steps]. */
enum class OnboardingStep { COMPACT_WIDGET, FULL_WIDGET, QR_WIDGET, SERVICES, NOTIFICATIONS }

/** The widgets the flow can pin, one per step. */
enum class WidgetKind { SINGLE_LESSON, DAY_SCHEDULE, QR }

/** The appearance choices a widget step offers, in row order. */
enum class WidgetOption {
    COMPACT_NEXT_LESSON_EARLY,
    COMPACT_HIDE_TEACHER,
    FULL_HIDE_TEACHER,
    FULL_HIDE_PAST_LESSONS,
    FULL_SHOW_TOMORROW,
    QR_DYNAMIC_COLORS,
    QR_SPOILER;

    fun isEnabled(appearance: WidgetAppearance): Boolean = when (this) {
        COMPACT_NEXT_LESSON_EARLY -> appearance.schedule.compact.showNextLessonEarly
        COMPACT_HIDE_TEACHER -> appearance.schedule.compact.hideTeacher
        FULL_HIDE_TEACHER -> appearance.schedule.full.hideTeacher
        FULL_HIDE_PAST_LESSONS -> appearance.schedule.full.hidePastLessons
        FULL_SHOW_TOMORROW -> appearance.schedule.full.showTomorrowWhenTodayIsOver
        QR_DYNAMIC_COLORS -> appearance.qr.dynamicColors
        QR_SPOILER -> appearance.qr.spoilerEnabled
    }
}

val WidgetKind.options: List<WidgetOption>
    get() = when (this) {
        WidgetKind.SINGLE_LESSON -> listOf(WidgetOption.COMPACT_NEXT_LESSON_EARLY, WidgetOption.COMPACT_HIDE_TEACHER)
        WidgetKind.DAY_SCHEDULE -> listOf(
            WidgetOption.FULL_HIDE_TEACHER, WidgetOption.FULL_HIDE_PAST_LESSONS, WidgetOption.FULL_SHOW_TOMORROW
        )
        WidgetKind.QR -> listOf(WidgetOption.QR_DYNAMIC_COLORS, WidgetOption.QR_SPOILER)
    }

/** The text size of a schedule widget; the QR widget has none. */
fun WidgetKind.textSize(appearance: WidgetAppearance): WidgetTextSize? = when (this) {
    WidgetKind.SINGLE_LESSON -> appearance.schedule.compact.textSize
    WidgetKind.DAY_SCHEDULE -> appearance.schedule.full.textSize
    WidgetKind.QR -> null
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.COMPACT_WIDGET,
    val pinSupported: Boolean = true,
    val pinnedWidgets: Set<WidgetKind> = emptySet(),
    /** Null until the stored preferences answer; previews wait instead of showing defaults. */
    val appearance: WidgetAppearance? = null,
    val servicesEnabled: Boolean = false,
    val servicesBusy: Boolean = false,
    val notificationsGranted: Boolean = false,
    val notificationsAsked: Boolean = false,
    /** Null until the stored spoiler image answers; the row shows no value until then. */
    val customSpoiler: Boolean? = null,
    val spoilerBusy: Boolean = false,
    /** Grows with every stored image change so the QR preview knows to re-read it. */
    val spoilerRevision: Int = 0,
    val finished: Boolean = false
) {
    /** Notifications carry what services send; without the opt-in there is nothing to ask for. */
    val steps: List<OnboardingStep>
        get() = OnboardingStep.entries.filter { it != OnboardingStep.NOTIFICATIONS || servicesEnabled }

    val stepIndex: Int get() = steps.indexOf(step).coerceAtLeast(0)

    val isLastStep: Boolean get() = stepIndex == steps.lastIndex
}

sealed interface OnboardingEvent {
    data class RequestPinWidget(val kind: WidgetKind) : OnboardingEvent
    data object RequestNotificationPermission : OnboardingEvent
    data object OpenNotificationSettings : OnboardingEvent
    data object SpoilerImageFailed : OnboardingEvent
    data class ShowError(val error: AppError) : OnboardingEvent
}
