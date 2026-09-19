package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R

/**
 * Progress dots: one per step, the current one stretched into a pill.
 *
 * Dots are drawn by this view, not by a tab strip: tabs pad their content and a
 * strip of empty tabs ends up as touching circles. The dots report progress only
 * and are not tappable; the footer walks the flow.
 */
class OnboardingStepsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val dotSize = (8 * density).toInt()
    private val activeWidth = (24 * density).toInt()
    private val gap = (8 * density).toInt()
    private val activeColor = MaterialColors.getColor(this, android.R.attr.colorPrimary)
    private val inactiveColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        // A width change of the current dot animates; adding and removing dots does not flash.
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            disableTransitionType(LayoutTransition.APPEARING)
            disableTransitionType(LayoutTransition.DISAPPEARING)
            disableTransitionType(LayoutTransition.CHANGE_APPEARING)
            disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun render(count: Int, current: Int) {
        while (childCount > count) removeViewAt(childCount - 1)
        while (childCount < count) {
            addView(View(context).apply {
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                background = GradientDrawable().apply { cornerRadius = dotSize / 2f }
            })
        }
        for (index in 0 until childCount) {
            val dot = getChildAt(index)
            val active = index == current
            (dot.background as GradientDrawable).setColor(if (active) activeColor else inactiveColor)
            dot.layoutParams = LayoutParams(if (active) activeWidth else dotSize, dotSize).apply {
                marginStart = if (index == 0) 0 else gap
            }
        }
        contentDescription = context.getString(R.string.onboarding_step_progress, current + 1, count)
    }
}
