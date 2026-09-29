package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel

/**
 * The tone of a teacher's reviews as a dot colour and words. Colours keep their meaning from red to green in every
 * palette and are only harmonized towards the wallpaper's primary colour; the words go to TalkBack with the dot.
 */
enum class TeacherLevelTone(@param:ColorRes private val colorRes: Int, @param:StringRes val label: Int) {
    VERY_NEGATIVE(R.color.teacher_level_very_negative, R.string.teacher_level_very_negative),
    NEGATIVE(R.color.teacher_level_negative, R.string.teacher_level_negative),
    MIXED(R.color.teacher_level_mixed, R.string.teacher_level_mixed),
    POSITIVE(R.color.teacher_level_positive, R.string.teacher_level_positive),
    VERY_POSITIVE(R.color.teacher_level_very_positive, R.string.teacher_level_very_positive);

    @ColorInt
    fun color(context: Context): Int = MaterialColors.harmonizeWithPrimary(context, ContextCompat.getColor(context, colorRes))

    /** «Тон отзывов: скорее положительные» for a row that carries the dot. */
    fun description(context: Context): String =
        context.getString(R.string.teacher_level_description, context.getString(label).lowercase())
}

fun TeacherLevel.tone(): TeacherLevelTone = when (this) {
    TeacherLevel.VERY_NEGATIVE -> TeacherLevelTone.VERY_NEGATIVE
    TeacherLevel.NEGATIVE -> TeacherLevelTone.NEGATIVE
    TeacherLevel.MIXED -> TeacherLevelTone.MIXED
    TeacherLevel.POSITIVE -> TeacherLevelTone.POSITIVE
    TeacherLevel.VERY_POSITIVE -> TeacherLevelTone.VERY_POSITIVE
}

/**
 * A decorative dot: tinted and visible with a [level]; without one it keeps its place when [reserve] is set, so a
 * late level does not move the row, and is gone otherwise. The row it sits in tells the tone to TalkBack.
 */
fun ImageView.bindLevel(level: TeacherLevel?, reserve: Boolean = false) {
    visibility = when {
        level != null -> View.VISIBLE
        reserve -> View.INVISIBLE
        else -> View.GONE
    }
    imageTintList = level?.let { ColorStateList.valueOf(it.tone().color(context)) }
    contentDescription = null
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}
