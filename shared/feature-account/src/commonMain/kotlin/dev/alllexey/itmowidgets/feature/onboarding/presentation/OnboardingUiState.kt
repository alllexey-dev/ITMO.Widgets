package dev.alllexey.itmowidgets.feature.onboarding.presentation

import dev.alllexey.itmowidgets.core.result.AppError

/**
 * The two steps of the first run (owner, 2026-10-19), in order: who signed in with the permissions one by one, the
 * connection to ITMO.Widgets.
 */
enum class OnboardingStep { PERMISSIONS, CONNECTION }

/**
 * The signed-in person on the first step, from the ID token: the full name (family, given and middle), its initials
 * for the avatar, the ISU. The group is Backend's, which is off on a first run, so the card has none.
 */
data class OnboardingMe(val name: String, val initials: String, val pictureUrl: String?, val isu: Int?)

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.PERMISSIONS,
    /** Null until the session answers, or without a name in the token. */
    val me: OnboardingMe? = null,
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
