package dev.alllexey.itmowidgets.feature.sport.ui.common

import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLessonKind
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingObstacle
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingRestriction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportOccupancy
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportRegistrationStatus
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_capacity_card
import dev.alllexey.itmowidgets.shared.feature.sport.sport_card_queue
import dev.alllexey.itmowidgets.shared.feature.sport.sport_card_today
import dev.alllexey.itmowidgets.shared.feature.sport.sport_card_tomorrow
import dev.alllexey.itmowidgets.shared.feature.sport.sport_date_today
import dev.alllexey.itmowidgets.shared.feature.sport.sport_date_tomorrow
import dev.alllexey.itmowidgets.shared.feature.sport.sport_friends_count_label
import dev.alllexey.itmowidgets.shared.feature.sport.sport_kind_free_short
import dev.alllexey.itmowidgets.shared.feature.sport.sport_kind_intermediate_short
import dev.alllexey.itmowidgets.shared.feature.sport.sport_kind_team_short
import dev.alllexey.itmowidgets.shared.feature.sport.sport_kind_training_short
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_additional
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_debt
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_external
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_free_attendance
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_intermediate_section
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_open
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_standards
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_team_section
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_training_section
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lesson_unknown
import dev.alllexey.itmowidgets.shared.feature.sport.sport_registration_cancelled
import dev.alllexey.itmowidgets.shared.feature.sport.sport_registration_expired
import dev.alllexey.itmowidgets.shared.feature.sport.sport_registration_notified
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_credit
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_daily_limit
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_debt_only
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_denied
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_externat
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_health
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_selection
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_started
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_time_conflict
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_unknown
import dev.alllexey.itmowidgets.shared.feature.sport.sport_rule_weekly_limit
import dev.alllexey.itmowidgets.shared.feature.sport.sport_status_auto_sign_success
import dev.alllexey.itmowidgets.shared.feature.sport.sport_status_not_signed
import dev.alllexey.itmowidgets.shared.feature.sport.sport_status_sign_failed
import dev.alllexey.itmowidgets.shared.feature.sport.sport_status_signed
import kotlinx.datetime.format
import org.jetbrains.compose.resources.StringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.sport_registration_waiting

/*
 * The sport card and details texts in commonMain: the port of the View helpers in `:app`'s `SportCardPresentation`,
 * `OccupancyTone`, `SportBookingRestrictionText` and `SportLessonPresentation`, which stay for the remaining Views
 * until LP-5c and LP-6. Names differ from those helpers where the signatures would clash in the shared package.
 */

/**
 * Stable status semantics of sport conditions, independent of the wallpaper's primary colour; the Compose twin of
 * `:app`'s `ConditionTone`. The colours are `ItmoTheme.extendedColors.sportCondition*` ([accent] in SportCardParts).
 */
enum class SportConditionTone { ALLOWED, WAITING, WARNING, BLOCKED }

/** "18:30–20:00". */
fun SportSessionTiming.timeRangeText(): String =
    "${start.time.format(DateTexts.TIME)}–${end.time.format(DateTexts.TIME)}"

/** "Сегодня · 8 сентября", "Завтра · 9 сентября", otherwise "Чт, 10 сентября". */
fun SportSessionTiming.dateText(): UiText {
    val date = start.date.format(DateTexts.DAY_MONTH)
    return when {
        isToday -> UiText.Res(Res.string.sport_date_today, listOf(date))
        isTomorrow -> UiText.Res(Res.string.sport_date_tomorrow, listOf(date))
        else -> UiText.Dynamic(start.date.format(DateTexts.SHORT_WEEKDAY_DAY_MONTH).capitalizeFirst())
    }
}

/** Card date line: relative while the session is near, otherwise the weekday alone, "Четверг". */
fun SportSessionTiming.weekdayText(): UiText = when {
    isToday -> UiText.Res(Res.string.sport_card_today)
    isTomorrow -> UiText.Res(Res.string.sport_card_tomorrow)
    else -> UiText.Dynamic(start.date.format(DateTexts.WEEKDAY).capitalizeFirst())
}

/** "Пятница, 25 сентября 2026". */
fun SportSessionTiming.fullDate(): String = start.date.format(DateTexts.WEEKDAY_DAY_MONTH_YEAR).capitalizeFirst()

/** "вторник, 8 сентября, 18:30–20:00": absolute, because the recipient reads it on another day. */
fun SportSessionTiming.shareDate(): String = "${start.date.format(DateTexts.WEEKDAY_DAY_MONTH)}, ${timeRangeText()}"

fun SportRegistrationStatus.labelResource(): StringResource = when (this) {
    SportRegistrationStatus.SIGNED -> Res.string.sport_status_signed
    SportRegistrationStatus.AUTO_SIGNED -> Res.string.sport_status_auto_sign_success
    SportRegistrationStatus.WAITING -> CoreRes.string.sport_registration_waiting
    SportRegistrationStatus.NOTIFIED -> Res.string.sport_registration_notified
    SportRegistrationStatus.FAILED -> Res.string.sport_status_sign_failed
    SportRegistrationStatus.EXPIRED -> Res.string.sport_registration_expired
    SportRegistrationStatus.CANCELLED -> Res.string.sport_registration_cancelled
    SportRegistrationStatus.NOT_SIGNED -> Res.string.sport_status_not_signed
}

/** Statuses that carry no outcome yet stay neutral (null) rather than borrowing a semantic accent. */
fun SportRegistrationStatus.conditionTone(): SportConditionTone? = when (this) {
    SportRegistrationStatus.SIGNED, SportRegistrationStatus.AUTO_SIGNED -> SportConditionTone.ALLOWED
    SportRegistrationStatus.WAITING, SportRegistrationStatus.NOTIFIED -> SportConditionTone.WAITING
    SportRegistrationStatus.FAILED, SportRegistrationStatus.EXPIRED -> SportConditionTone.BLOCKED
    SportRegistrationStatus.CANCELLED, SportRegistrationStatus.NOT_SIGNED -> null
}

/** Full is blocked, at most a fifth of the places left is a warning, anything roomier only waits. */
fun SportOccupancy.tone(): SportConditionTone = when {
    available == 0 -> SportConditionTone.BLOCKED
    available.toDouble() / limit <= SCARCE_SHARE -> SportConditionTone.WARNING
    else -> SportConditionTone.WAITING
}

/** "Занято 13/20". */
fun SportOccupancy.cardText(): UiText = UiText.Res(Res.string.sport_capacity_card, listOf(occupied, limit))

/** "Автозапись · 3 из 12". */
fun sportQueuePositionText(position: Int, total: Int): UiText =
    UiText.Res(Res.string.sport_card_queue, listOf(position, total))

/** "Друзья · 2". */
fun sportFriendsCountText(count: Int): UiText = UiText.Res(Res.string.sport_friends_count_label, listOf(count))

fun SportBookingObstacle.titleResource(): StringResource = when (this) {
    SportBookingObstacle.TIME_CONFLICT -> Res.string.sport_rule_time_conflict
    SportBookingObstacle.DAILY_LIMIT -> Res.string.sport_rule_daily_limit
    SportBookingObstacle.WEEKLY_LIMIT -> Res.string.sport_rule_weekly_limit
    SportBookingObstacle.CREDIT -> Res.string.sport_rule_credit
    SportBookingObstacle.SELECTION -> Res.string.sport_rule_selection
    SportBookingObstacle.EXTERNAT -> Res.string.sport_rule_externat
    SportBookingObstacle.DEBT_ONLY -> Res.string.sport_rule_debt_only
    SportBookingObstacle.DENIED -> Res.string.sport_rule_denied
    SportBookingObstacle.HEALTH -> Res.string.sport_rule_health
    SportBookingObstacle.STARTED -> Res.string.sport_rule_started
    SportBookingObstacle.UNKNOWN -> Res.string.sport_rule_unknown
}

/** MyITMO's own words when it gave any, otherwise the obstacle's title. */
fun SportBookingRestriction.text(): UiText = detail?.let(UiText::Dynamic) ?: UiText.Res(kind.titleResource())

fun SportLessonKind.titleResource(): StringResource = when (this) {
    SportLessonKind.TRAINING_SECTION -> Res.string.sport_lesson_training_section
    SportLessonKind.INTERMEDIATE_SECTION -> Res.string.sport_lesson_intermediate_section
    SportLessonKind.TEAM_SECTION -> Res.string.sport_lesson_team_section
    SportLessonKind.OPEN -> Res.string.sport_lesson_open
    SportLessonKind.FREE_ATTENDANCE -> Res.string.sport_lesson_free_attendance
    SportLessonKind.DEBT -> Res.string.sport_lesson_debt
    SportLessonKind.STANDARDS -> Res.string.sport_lesson_standards
    SportLessonKind.EXTERNAL -> Res.string.sport_lesson_external
    SportLessonKind.ADDITIONAL -> Res.string.sport_lesson_additional
    SportLessonKind.UNKNOWN -> Res.string.sport_lesson_unknown
}

/** The kind chip's text; the full [titleResource] is its content description. */
fun SportLessonKind.compactTitleResource(): StringResource = when (this) {
    SportLessonKind.FREE_ATTENDANCE -> Res.string.sport_kind_free_short
    SportLessonKind.TRAINING_SECTION -> Res.string.sport_kind_training_short
    SportLessonKind.INTERMEDIATE_SECTION -> Res.string.sport_kind_intermediate_short
    SportLessonKind.TEAM_SECTION -> Res.string.sport_kind_team_short
    else -> titleResource()
}

/** Russian weekday and month names come in lower case; display texts start with a capital. */
private fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercaseChar() }

private const val SCARCE_SHARE = 0.2
