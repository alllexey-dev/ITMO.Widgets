package dev.alllexey.itmowidgets.core.ui

import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.core.view.updateLayoutParams
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import dev.alllexey.itmowidgets.R

/** Where a row stands in its connected group; the outer corners belong to the first and the last row. */
enum class GroupPosition {
    SINGLE, FIRST, MIDDLE, LAST;

    val isFirst: Boolean get() = this == SINGLE || this == FIRST
    val isLast: Boolean get() = this == SINGLE || this == LAST

    companion object {
        fun of(index: Int, size: Int): GroupPosition = when {
            size <= 1 -> SINGLE
            index == 0 -> FIRST
            index == size - 1 -> LAST
            else -> MIDDLE
        }
    }
}

/**
 * Draws one row of a connected group: the rows of a section share [surface], stand
 * `design_group_gap` apart and round 20 dp outside and 4 dp between rows. The ripple keeps the
 * row's shape; it shows only while the row is clickable, so informational rows use the same call.
 * [spaceBefore] (px) separates a first row from a group right above it when no heading stands between.
 */
fun View.bindGroupPosition(
    position: GroupPosition,
    @AttrRes surface: Int = com.google.android.material.R.attr.colorSurfaceContainerLow,
    spaceBefore: Int = 0,
) {
    val outer = resources.getDimension(R.dimen.design_group_radius_outer)
    val inner = resources.getDimension(R.dimen.design_group_radius_inner)
    val top = if (position.isFirst) outer else inner
    val bottom = if (position.isLast) outer else inner
    val shape = ShapeAppearanceModel.builder()
        .setTopLeftCornerSize(top).setTopRightCornerSize(top)
        .setBottomLeftCornerSize(bottom).setBottomRightCornerSize(bottom)
        .build()
    val fill = MaterialShapeDrawable(shape).apply { fillColor = ColorStateList.valueOf(MaterialColors.getColor(this@bindGroupPosition, surface)) }
    val ripple = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorControlHighlight)
    background = RippleDrawable(ColorStateList.valueOf(ripple), fill, MaterialShapeDrawable(shape))
    updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = if (position.isFirst) spaceBefore else resources.getDimensionPixelSize(R.dimen.design_group_gap)
    }
}
