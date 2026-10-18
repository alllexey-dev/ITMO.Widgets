package dev.alllexey.itmowidgets.feature.onboarding.ui.preview

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonState
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.LessonTimelines
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingLesson
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingMe
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingStep
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlinx.datetime.LocalTime

/** The first-run flow at the design lab's axes (`value`, `permissions`, `connection`), for both platforms' previews. */
object OnboardingSamples {
    /** [step] is a lab axis value; `permissions-granted` and `connected` add the answered states. */
    fun state(step: String): OnboardingUiState = when (step) {
        "permissions" -> base.copy(step = OnboardingStep.PERMISSIONS, notificationsGranted = true, notificationsAsked = true)
        "permissions-none" -> base.copy(step = OnboardingStep.PERMISSIONS)
        "connection" -> base.copy(step = OnboardingStep.CONNECTION)
        "connected" -> base.copy(step = OnboardingStep.CONNECTION, servicesEnabled = true)
        "value-empty" -> base.copy(lesson = null, sport = null)
        else -> base
    }

    private val lesson = DayLesson(
        start = LocalTime(11, 40),
        end = LocalTime(13, 10),
        subject = "Программирование",
        typeId = 2,
        teacher = "Ершов Н. А.",
        room = "1506",
        building = "Кронверкский пр., 49",
        online = false,
        target = LessonTarget.Lesson(
            LessonDetailsArgs(1, "2026-09-07", "Программирование", 2, "", "11:40", "13:10", "Ершов Н. А.", null, "1506", "Кронверкский пр., 49", null, null, null, null, null, null),
        ),
    )

    private val base = OnboardingUiState(
        step = OnboardingStep.VALUE,
        me = OnboardingMe("Александрова Мария", "АМ", null),
        lesson = OnboardingLesson(UiText.Res(AccountStrings.onboarding_value_now), LessonTimelines.lesson(lesson, LessonState.NOW)),
        sport = UiText.Res(AccountStrings.onboarding_value_sport_score, listOf(30)),
        calendarSynced = false,
    )
}
