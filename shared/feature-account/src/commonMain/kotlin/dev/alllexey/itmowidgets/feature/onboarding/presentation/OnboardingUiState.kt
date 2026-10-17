package dev.alllexey.itmowidgets.feature.onboarding.presentation

import dev.alllexey.itmowidgets.core.lesson.LessonUi
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * The three steps of the first run (DECISIONS.md, Первый запуск), in order: what the app does with your own data, the
 * permissions one by one, the connection to ITMO.Widgets.
 */
enum class OnboardingStep { VALUE, PERMISSIONS, CONNECTION }

/** The signed-in person on the first step: the name ITMO.ID gives and its initials for the avatar. */
data class OnboardingMe(val name: String, val initials: String, val pictureUrl: String?)

/** The lesson the first step shows: [title] `Сейчас`, `Далее` or `Завтра`, over the lesson card. */
data class OnboardingLesson(val title: UiText, val lesson: LessonUi)

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.VALUE,
    /** Null until the session answers, or without a name in the token. */
    val me: OnboardingMe? = null,
    /** Today's lesson now or next, else tomorrow's first; null while the schedule has none or has not loaded. */
    val lesson: OnboardingLesson? = null,
    /** `30 из 100` of the current sport period; null until My ITMO answers. */
    val sport: UiText? = null,
    val notificationsGranted: Boolean = false,
    val notificationsAsked: Boolean = false,
    /** Null where the platform has no calendar sync. */
    val calendarSynced: Boolean? = null,
    val servicesEnabled: Boolean = false,
    val servicesBusy: Boolean = false,
    val finished: Boolean = false,
) {
    val stepIndex: Int get() = step.ordinal

    val stepCount: Int get() = OnboardingStep.entries.size
}

sealed interface OnboardingEvent {
    data object RequestNotificationPermission : OnboardingEvent
    data object OpenNotificationSettings : OnboardingEvent

    /** Asks for the calendar when needed, then reports back through `onCalendarAccessGranted`. */
    data object RequestCalendarAccess : OnboardingEvent

    /** The consent sheet; the connection turns on only from its `Подключить`. */
    data object ShowConsent : OnboardingEvent
    data class ShowError(val error: AppError) : OnboardingEvent
}
