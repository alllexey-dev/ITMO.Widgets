package dev.alllexey.itmowidgets.feature.social.ui.reviews

import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_scale_attitude
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_scale_explains
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_scale_fairness
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_scale_strictness
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_scale_workload
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_asks_theory
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_attendance_optional
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_attendance_required
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_automat
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_bonus_points
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_clear_requirements
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_easy_exam
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_flexible_deadlines
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_frequent_tests
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_hard_exam
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_hard_to_reach
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_heavy_homework
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_interesting_classes
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_many_labs
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_quick_replies
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_reads_slides
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_soft_defense
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_strict_deadlines
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_strict_defense
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_tag_unclear_requirements
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_value_attitude_high
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_value_attitude_low
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_value_attitude_medium
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_value_explains_high
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_value_explains_low
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_value_explains_medium
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_value_high
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_value_low
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_value_medium
import dev.alllexey.itmowidgets.shared.feature.social.teacher_summary_value_not_enough_data
import org.jetbrains.compose.resources.StringResource

internal fun SummaryScaleKind.label(): StringResource = when (this) {
    SummaryScaleKind.EXPLAINS -> Res.string.teacher_summary_scale_explains
    SummaryScaleKind.ATTITUDE -> Res.string.teacher_summary_scale_attitude
    SummaryScaleKind.FAIRNESS -> Res.string.teacher_summary_scale_fairness
    SummaryScaleKind.STRICTNESS -> Res.string.teacher_summary_scale_strictness
    SummaryScaleKind.WORKLOAD -> Res.string.teacher_summary_scale_workload
}

/** `Объясняет` and `Отношение к студентам` have their own words; the other scales are низкая, средняя, высокая. */
internal fun SummaryScaleValue.label(kind: SummaryScaleKind): StringResource = when (this) {
    SummaryScaleValue.NOT_ENOUGH_DATA -> Res.string.teacher_summary_value_not_enough_data
    SummaryScaleValue.LOW -> when (kind) {
        SummaryScaleKind.EXPLAINS -> Res.string.teacher_summary_value_explains_low
        SummaryScaleKind.ATTITUDE -> Res.string.teacher_summary_value_attitude_low
        else -> Res.string.teacher_summary_value_low
    }
    SummaryScaleValue.MEDIUM -> when (kind) {
        SummaryScaleKind.EXPLAINS -> Res.string.teacher_summary_value_explains_medium
        SummaryScaleKind.ATTITUDE -> Res.string.teacher_summary_value_attitude_medium
        else -> Res.string.teacher_summary_value_medium
    }
    SummaryScaleValue.HIGH -> when (kind) {
        SummaryScaleKind.EXPLAINS -> Res.string.teacher_summary_value_explains_high
        SummaryScaleKind.ATTITUDE -> Res.string.teacher_summary_value_attitude_high
        else -> Res.string.teacher_summary_value_high
    }
}

internal fun SummaryTag.label(): StringResource = when (this) {
    SummaryTag.AUTOMAT -> Res.string.teacher_summary_tag_automat
    SummaryTag.MANY_LABS -> Res.string.teacher_summary_tag_many_labs
    SummaryTag.HEAVY_HOMEWORK -> Res.string.teacher_summary_tag_heavy_homework
    SummaryTag.FREQUENT_TESTS -> Res.string.teacher_summary_tag_frequent_tests
    SummaryTag.STRICT_DEFENSE -> Res.string.teacher_summary_tag_strict_defense
    SummaryTag.SOFT_DEFENSE -> Res.string.teacher_summary_tag_soft_defense
    SummaryTag.HARD_EXAM -> Res.string.teacher_summary_tag_hard_exam
    SummaryTag.EASY_EXAM -> Res.string.teacher_summary_tag_easy_exam
    SummaryTag.ASKS_THEORY -> Res.string.teacher_summary_tag_asks_theory
    SummaryTag.STRICT_DEADLINES -> Res.string.teacher_summary_tag_strict_deadlines
    SummaryTag.FLEXIBLE_DEADLINES -> Res.string.teacher_summary_tag_flexible_deadlines
    SummaryTag.ATTENDANCE_REQUIRED -> Res.string.teacher_summary_tag_attendance_required
    SummaryTag.ATTENDANCE_OPTIONAL -> Res.string.teacher_summary_tag_attendance_optional
    SummaryTag.BONUS_POINTS -> Res.string.teacher_summary_tag_bonus_points
    SummaryTag.CLEAR_REQUIREMENTS -> Res.string.teacher_summary_tag_clear_requirements
    SummaryTag.UNCLEAR_REQUIREMENTS -> Res.string.teacher_summary_tag_unclear_requirements
    SummaryTag.INTERESTING_CLASSES -> Res.string.teacher_summary_tag_interesting_classes
    SummaryTag.READS_SLIDES -> Res.string.teacher_summary_tag_reads_slides
    SummaryTag.QUICK_REPLIES -> Res.string.teacher_summary_tag_quick_replies
    SummaryTag.HARD_TO_REACH -> Res.string.teacher_summary_tag_hard_to_reach
}
