package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.color

/** Stable status semantics for condition cards, deliberately independent of the wallpaper's primary colour. */
enum class ConditionTone(@param:ColorRes private val colorRes: Int) {
    ALLOWED(R.color.sport_condition_allowed),
    WAITING(R.color.sport_condition_waiting),
    WARNING(R.color.sport_condition_warning),
    BLOCKED(R.color.sport_condition_blocked);

    fun accent(context: Context): Int = ContextCompat.getColor(context, colorRes)

    fun container(context: Context): Int = ColorUtils.blendARGB(
        context.color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLowest),
        accent(context),
        0.12f
    )
}
