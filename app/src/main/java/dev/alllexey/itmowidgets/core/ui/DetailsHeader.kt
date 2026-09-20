package dev.alllexey.itmowidgets.core.ui

import android.content.res.ColorStateList
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import dev.alllexey.itmowidgets.R
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
    val mapAvailable: Boolean
)

fun ViewDetailsHeaderBinding.bind(content: DetailsHeaderContent, onMap: () -> Unit) {
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
    teacherFact.bindFact(R.string.sport_details_teacher, content.teacher.orEmpty(), R.drawable.ic_person_rounded)
    locationFact.bindFact(R.string.sport_details_location, content.location.orEmpty(), R.drawable.ic_location_on_rounded)
    mapButton.isVisible = content.mapAvailable
    mapButton.setOnClickListener { onMap() }
    placeCard.isVisible = teacherFact.root.isVisible || locationFact.root.isVisible || mapButton.isVisible
}

/** The icon carries the category, so [title] only survives for screen readers. */
fun ItemSportDetailFactBinding.bindFact(@StringRes title: Int, value: String, @DrawableRes icon: Int) {
    root.isVisible = value.isNotBlank()
    factValue.text = value
    factValue.contentDescription = root.context.getString(R.string.sport_detail_fact_description, root.context.getString(title), value)
    factIcon.setImageResource(icon)
    alignRailIcon(factIcon, factValue)
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
