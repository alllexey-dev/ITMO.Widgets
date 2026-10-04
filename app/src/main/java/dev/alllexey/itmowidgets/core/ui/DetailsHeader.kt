package dev.alllexey.itmowidgets.core.ui

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.databinding.ItemSportDetailFactBinding
import dev.alllexey.itmowidgets.databinding.ViewDetailsHeaderBinding
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What every details sheet says first; `null` rows disappear instead of reading as empty. */
data class DetailsHeaderContent(
    val title: String,
    val kind: String?,
    @param:ColorInt val typeColor: Int? = null,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val teacher: String?,
    val location: String?,
    val mapAvailable: Boolean,
    val flow: String? = null
)

fun ViewDetailsHeaderBinding.bind(content: DetailsHeaderContent, onTeacher: (() -> Unit)?, onMap: () -> Unit) {
    sectionName.text = content.title
    kind.text = content.kind
    kindRow.isVisible = !content.kind.isNullOrBlank()
    typeIndicator.isVisible = content.typeColor != null
    content.typeColor?.let { typeIndicator.imageTintList = ColorStateList.valueOf(it) }
    date.text = fullDateText(content.date)
    time.text = timeRangeText(content.start, content.end)
    val minutes = Duration.between(content.start, content.end).toMinutes().takeIf { it > 0 }
    duration.isVisible = minutes != null
    duration.text = minutes?.let { root.context.getString(R.string.sport_duration, it) }
    alignRailIcon(timeIcon, date)
    teacherFact.bindFact(R.string.sport_details_teacher, content.teacher.orEmpty(), R.drawable.ic_person)
    teacherFact.bindAction(onTeacher)
    bindTeacherLevel(null, reserve = false)
    flowFact.bindFact(R.string.schedule_lesson_details_flow, content.flow.orEmpty(), R.drawable.ic_group)
    locationFact.bindFact(R.string.sport_details_location, content.location.orEmpty(), R.drawable.ic_location_on)
    mapButton.isVisible = content.mapAvailable
    mapButton.setOnClickListener { onMap() }
    placeCard.isVisible = teacherFact.root.isVisible || flowFact.root.isVisible || locationFact.root.isVisible ||
        mapButton.isVisible
}

/**
 * The tone dot at the end of the teacher row, before the chevron. With [reserve] the row keeps the dot's place while
 * no [level] is known, so a late level does not move the name; a sheet reserves it only for a teacher with an ISU.
 */
fun ViewDetailsHeaderBinding.bindTeacherLevel(level: TeacherLevel?, reserve: Boolean) {
    val context = root.context
    teacherFact.factMark.bindLevel(level, reserve)
    alignRailIcon(teacherFact.factMark, teacherFact.factValue)
    val value = teacherFact.factValue.text.toString()
    val row = context.getString(R.string.sport_detail_fact_description, context.getString(R.string.sport_details_teacher), value)
    teacherFact.factValue.contentDescription = level?.let { "$row, ${it.tone().description(context).lowercase()}" } ?: row
}

/** The icon carries the category, so [title] only survives for screen readers. */
fun ItemSportDetailFactBinding.bindFact(@StringRes title: Int, value: String, @DrawableRes icon: Int) {
    root.isVisible = value.isNotBlank()
    factValue.text = value
    factValue.contentDescription = root.context.getString(R.string.sport_detail_fact_description, root.context.getString(title), value)
    factIcon.setImageResource(icon)
    alignRailIcon(factIcon, factValue)
    alignRailIcon(factTrailing, factValue)
}

fun ItemSportDetailFactBinding.bindAction(onClick: (() -> Unit)?) {
    val clickable = onClick != null
    root.setOnClickListener(if (onClick != null) { _ -> onClick() } else null)
    root.isClickable = clickable
    root.isFocusable = clickable
    if (clickable) {
        val background = TypedValue()
        root.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)
        root.setBackgroundResource(background.resourceId)
        ViewCompat.replaceAccessibilityAction(root, AccessibilityActionCompat.ACTION_CLICK,
            root.context.getString(R.string.teacher_open_profile), null)
    } else {
        root.background = null
        ViewCompat.removeAccessibilityAction(root, AccessibilityActionCompat.ACTION_CLICK.id)
    }
    val touchTarget = root.resources.getDimensionPixelSize(R.dimen.design_touch_target)
    val minimumPadding = root.resources.getDimensionPixelSize(R.dimen.design_spacing_compact)
    val verticalPadding = if (clickable) ((touchTarget - factValue.lineHeight) / 2).coerceAtLeast(minimumPadding) else minimumPadding
    root.minimumHeight = if (clickable) touchTarget else 0
    root.updatePadding(top = verticalPadding, bottom = verticalPadding)
    factTrailing.isVisible = clickable
}

/**
 * Centres a fixed-dp rail icon on the first line of its text. The line box grows with the font
 * scale while the icon does not, so a constant top margin only lines up at one scale.
 */
fun alignRailIcon(icon: ImageView, text: TextView) {
    icon.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = ((text.lineHeight - icon.layoutParams.height) / 2).coerceAtLeast(0)
    }
}

/** "Понедельник, 7 сентября 2026". */
fun fullDateText(date: LocalDate): String =
    date.format(FULL_DATE).replaceFirstChar { it.uppercase(RUSSIAN) }

/** "09:30–11:00". */
fun timeRangeText(start: LocalTime, end: LocalTime): String = "${start.format(TIME)}–${end.format(TIME)}"

private val RUSSIAN: Locale = Locale.forLanguageTag("ru")
private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", RUSSIAN)
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
