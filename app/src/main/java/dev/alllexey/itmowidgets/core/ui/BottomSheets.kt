package dev.alllexey.itmowidgets.core.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.roundToInt

/** Opens fully expanded at the height of its content, up to 90 % of the screen. */
fun BottomSheetDialogFragment.expandToContent() {
    val sheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
    sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    BottomSheetBehavior.from(sheet).apply {
        maxHeight = (resources.displayMetrics.heightPixels * MAX_HEIGHT_FRACTION).roundToInt()
        state = BottomSheetBehavior.STATE_EXPANDED
        skipCollapsed = true
    }
}

private const val MAX_HEIGHT_FRACTION = 0.9f
