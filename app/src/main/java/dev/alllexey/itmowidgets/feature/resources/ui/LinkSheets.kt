package dev.alllexey.itmowidgets.feature.resources.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import kotlin.math.roundToInt

/** The arguments every links sheet reads through its view model's `SavedStateHandle`. */
internal fun SubjectLinksArgs.toArguments(linkId: String? = null): Bundle = bundleOf(
    SubjectLinksArgs.SUBJECT_ID to subjectId,
    SubjectLinksArgs.SUBJECT_NAME to subjectName,
    SubjectLinksArgs.PERIOD_KEY to periodKey,
).apply { linkId?.let { putString(SubjectLinksArgs.LINK_ID, it) } }

internal fun ResourceScope.toArgs() = SubjectLinksArgs(subjectId, subjectName, periodKey)

/** Opens fully expanded at the height of its content, up to 90 % of the screen. */
internal fun BottomSheetDialogFragment.expandToContent() {
    val sheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
    sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
    BottomSheetBehavior.from(sheet).apply {
        maxHeight = (resources.displayMetrics.heightPixels * MAX_HEIGHT_FRACTION).roundToInt()
        state = BottomSheetBehavior.STATE_EXPANDED
        skipCollapsed = true
    }
}

private const val MAX_HEIGHT_FRACTION = 0.9f
