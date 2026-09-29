package dev.alllexey.itmowidgets.feature.social.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.chip.Chip
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.core.ui.bindLevel
import dev.alllexey.itmowidgets.core.ui.tone
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemSummaryTagChipBinding
import dev.alllexey.itmowidgets.databinding.ItemTeacherSummaryBinding
import dev.alllexey.itmowidgets.databinding.ViewSummaryPointBinding
import dev.alllexey.itmowidgets.databinding.ViewSummaryScaleBinding

/**
 * The AI summary card. Texts are plain: nothing is parsed as markup or links. Empty blocks are hidden; every mutable
 * property is set here, so a recycled card never keeps the previous teacher's state.
 */
internal fun ItemTeacherSummaryBinding.bind(summary: TeacherSummary) {
    val context = root.context
    val title = context.resources.getQuantityString(R.plurals.teacher_summary_label, summary.reviewCount, summary.reviewCount)
    label.text = title
    header.contentDescription = "$title, ${context.getString(R.string.teacher_summary_ai_description)}"
    ViewCompat.setScreenReaderFocusable(header, true)

    levelRow.isVisible = summary.showsLevel
    val tone = summary.level.tone()
    levelDot.bindLevel(summary.level.takeIf { summary.showsLevel })
    level.setText(tone.label)
    levelRow.contentDescription = tone.description(context).takeIf { summary.showsLevel }
    ViewCompat.setScreenReaderFocusable(levelRow, summary.showsLevel)

    description.text = summary.description
    pros.bindPoints(summary.pros, R.drawable.ic_add, R.string.teacher_summary_pros)
    cons.bindPoints(summary.cons, R.drawable.ic_remove, R.string.teacher_summary_cons)
    // Without pros the cons start the block after the description.
    cons.updateLayoutParams<LinearLayout.LayoutParams> {
        topMargin = context.resources.getDimensionPixelSize(
            if (summary.pros.isEmpty()) R.dimen.design_spacing_content else R.dimen.design_spacing_related,
        )
    }

    tags.isVisible = summary.tags.isNotEmpty()
    while (tags.childCount > summary.tags.size) tags.removeViewAt(tags.childCount - 1)
    while (tags.childCount < summary.tags.size) {
        val chip = ItemSummaryTagChipBinding.inflate(LayoutInflater.from(context), tags, false).root
        // Chip makes itself clickable and focusable while inflating; a tag is only a label.
        chip.isClickable = false
        chip.isFocusable = false
        tags.addView(chip)
    }
    summary.tags.forEachIndexed { index, tag -> (tags.getChildAt(index) as Chip).setText(tag.label()) }

    val scales = summary.scales.associateBy(SummaryScale::kind)
    listOf(
        scaleExplains to SummaryScaleKind.EXPLAINS,
        scaleAttitude to SummaryScaleKind.ATTITUDE,
        scaleFairness to SummaryScaleKind.FAIRNESS,
        scaleStrictness to SummaryScaleKind.STRICTNESS,
        scaleWorkload to SummaryScaleKind.WORKLOAD,
    ).forEach { (view, kind) -> view.bind(kind, scales[kind]) }
}

/** One row per point; the block is read by TalkBack as «Плюсы: …». */
private fun LinearLayout.bindPoints(points: List<String>, @DrawableRes icon: Int, @StringRes title: Int) {
    isVisible = points.isNotEmpty()
    while (childCount > points.size) removeViewAt(childCount - 1)
    while (childCount < points.size) addView(ViewSummaryPointBinding.inflate(LayoutInflater.from(context), this, false).root)
    points.forEachIndexed { index, point ->
        val row = ViewSummaryPointBinding.bind(getChildAt(index))
        row.icon.setImageResource(icon)
        row.text.text = point
        // The icon box is one text line tall, so the sign stays centred on the first line at any font scale.
        row.icon.updateLayoutParams { height = row.text.lineHeight }
    }
    contentDescription = context.getString(R.string.teacher_summary_list, context.getString(title), points.joinToString("; "))
        .takeIf { points.isNotEmpty() }
    ViewCompat.setScreenReaderFocusable(this, points.isNotEmpty())
}

private fun ViewSummaryScaleBinding.bind(kind: SummaryScaleKind, scale: SummaryScale?) {
    val context = root.context
    val value = scale?.value ?: SummaryScaleValue.NOT_ENOUGH_DATA
    name.setText(kind.label())
    this.value.setText(value.label(kind))
    val known = value != SummaryScaleValue.NOT_ENOUGH_DATA
    this.value.setTextColor(if (known) context.color.onSurface else context.color.onSurfaceVariant)
    val reason = scale?.reason?.takeIf { known }
    this.reason.text = reason
    this.reason.isVisible = reason != null
    val line = context.getString(R.string.teacher_summary_scale_description, name.text, this.value.text)
    root.contentDescription = listOfNotNull(line, reason).joinToString(". ")
    ViewCompat.setScreenReaderFocusable(root, true)
    root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
}

@StringRes
private fun SummaryScaleKind.label(): Int = when (this) {
    SummaryScaleKind.EXPLAINS -> R.string.teacher_summary_scale_explains
    SummaryScaleKind.ATTITUDE -> R.string.teacher_summary_scale_attitude
    SummaryScaleKind.FAIRNESS -> R.string.teacher_summary_scale_fairness
    SummaryScaleKind.STRICTNESS -> R.string.teacher_summary_scale_strictness
    SummaryScaleKind.WORKLOAD -> R.string.teacher_summary_scale_workload
}

@StringRes
private fun SummaryScaleValue.label(kind: SummaryScaleKind): Int = when (this) {
    SummaryScaleValue.NOT_ENOUGH_DATA -> R.string.teacher_summary_value_not_enough_data
    SummaryScaleValue.LOW -> when (kind) {
        SummaryScaleKind.EXPLAINS -> R.string.teacher_summary_value_explains_low
        SummaryScaleKind.ATTITUDE -> R.string.teacher_summary_value_attitude_low
        else -> R.string.teacher_summary_value_low
    }
    SummaryScaleValue.MEDIUM -> when (kind) {
        SummaryScaleKind.EXPLAINS -> R.string.teacher_summary_value_explains_medium
        SummaryScaleKind.ATTITUDE -> R.string.teacher_summary_value_attitude_medium
        else -> R.string.teacher_summary_value_medium
    }
    SummaryScaleValue.HIGH -> when (kind) {
        SummaryScaleKind.EXPLAINS -> R.string.teacher_summary_value_explains_high
        SummaryScaleKind.ATTITUDE -> R.string.teacher_summary_value_attitude_high
        else -> R.string.teacher_summary_value_high
    }
}

@StringRes
private fun SummaryTag.label(): Int = when (this) {
    SummaryTag.AUTOMAT -> R.string.teacher_summary_tag_automat
    SummaryTag.MANY_LABS -> R.string.teacher_summary_tag_many_labs
    SummaryTag.HEAVY_HOMEWORK -> R.string.teacher_summary_tag_heavy_homework
    SummaryTag.FREQUENT_TESTS -> R.string.teacher_summary_tag_frequent_tests
    SummaryTag.STRICT_DEFENSE -> R.string.teacher_summary_tag_strict_defense
    SummaryTag.SOFT_DEFENSE -> R.string.teacher_summary_tag_soft_defense
    SummaryTag.HARD_EXAM -> R.string.teacher_summary_tag_hard_exam
    SummaryTag.EASY_EXAM -> R.string.teacher_summary_tag_easy_exam
    SummaryTag.ASKS_THEORY -> R.string.teacher_summary_tag_asks_theory
    SummaryTag.STRICT_DEADLINES -> R.string.teacher_summary_tag_strict_deadlines
    SummaryTag.FLEXIBLE_DEADLINES -> R.string.teacher_summary_tag_flexible_deadlines
    SummaryTag.ATTENDANCE_REQUIRED -> R.string.teacher_summary_tag_attendance_required
    SummaryTag.ATTENDANCE_OPTIONAL -> R.string.teacher_summary_tag_attendance_optional
    SummaryTag.BONUS_POINTS -> R.string.teacher_summary_tag_bonus_points
    SummaryTag.CLEAR_REQUIREMENTS -> R.string.teacher_summary_tag_clear_requirements
    SummaryTag.UNCLEAR_REQUIREMENTS -> R.string.teacher_summary_tag_unclear_requirements
    SummaryTag.INTERESTING_CLASSES -> R.string.teacher_summary_tag_interesting_classes
    SummaryTag.READS_SLIDES -> R.string.teacher_summary_tag_reads_slides
    SummaryTag.QUICK_REPLIES -> R.string.teacher_summary_tag_quick_replies
    SummaryTag.HARD_TO_REACH -> R.string.teacher_summary_tag_hard_to_reach
}
