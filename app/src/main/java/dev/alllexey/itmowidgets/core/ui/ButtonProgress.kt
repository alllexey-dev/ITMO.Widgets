package dev.alllexey.itmowidgets.core.ui

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable

/**
 * In-button progress: Material's extra-small indeterminate drawable in [color] takes the icon's place,
 * so the button keeps its size and the label stays for the screen reader. [idleIcon] returns when it stops.
 */
fun MaterialButton.showProgress(show: Boolean, @ColorInt color: Int, idleIcon: Drawable? = null) {
    icon = if (show) {
        val spec = CircularProgressIndicatorSpec(
            context, null, 0,
            com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall
        )
        spec.indicatorColors = intArrayOf(color)
        IndeterminateDrawable.createCircularDrawable(context, spec)
    } else idleIcon
}

/** Whether [showProgress] put its drawable in place; a second call would restart the animation. */
val MaterialButton.showsProgress: Boolean get() = icon is IndeterminateDrawable<*>
