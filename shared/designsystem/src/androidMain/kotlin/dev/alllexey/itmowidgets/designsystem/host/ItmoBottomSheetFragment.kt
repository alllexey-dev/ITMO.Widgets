package dev.alllexey.itmowidgets.designsystem.host

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.roundToInt

/** How tall a Compose-body sheet opens. */
enum class SheetHeight {
    /** As tall as its content, up to 90 % of the screen (`core/ui/BottomSheets.kt`'s `expandToContent`). */
    FitContent,

    /** Always 90 % of the screen: the details sheets and the friend selector. */
    Tall,
}

/** Who may close a Compose-body sheet. */
enum class SheetDismissal(
    /** Back and a tap outside cancel the sheet. */
    val cancelable: Boolean,
    /** A downward drag hides the sheet. */
    val draggable: Boolean,
) {
    /** Drag, back and a tap outside close it. */
    Free(cancelable = true, draggable = true),

    /**
     * A form that must not lose input (`ReviewEditorBottomSheet`): no drag, no tap outside; back goes to
     * [ItmoBottomSheetFragment.onCloseRequest], which asks before discarding.
     */
    Form(cancelable = false, draggable = false),
}

/**
 * The behaviour of an [ItmoBottomSheetFragment]. [textInput] resizes the window for the keyboard
 * (`SOFT_INPUT_ADJUST_RESIZE`); MDC then lifts the sheet by the keyboard's height, so the body adds no `imePadding`
 * (SP-05a).
 */
data class SheetSpec(
    val height: SheetHeight = SheetHeight.FitContent,
    val dismissal: SheetDismissal = SheetDismissal.Free,
    val textInput: Boolean = false,
)

/**
 * A `BottomSheetDialogFragment` whose body is [SheetContent] (usually a `SheetScaffold`), hosted as SP-05a/b proved:
 * opened expanded without a collapsed step at the height [spec] asks for, drag-to-dismiss handed over by a scrolled
 * list through the nested scroll interop, the keyboard and the form mode applied programmatically. Subclasses keep
 * their class names, tags and `@AndroidEntryPoint`; the body stays stateless (state from the view model), so it
 * survives recreation like the View sheets did.
 */
abstract class ItmoBottomSheetFragment : BottomSheetDialogFragment() {

    /** Read in `onCreateDialog` and `onStart`; a constant per sheet class. */
    protected open val spec: SheetSpec get() = SheetSpec()

    /** The sheet's body, inside the kit theme. */
    @Composable
    protected abstract fun SheetContent()

    /**
     * Back in [SheetDismissal.Form], and the body's own close button in any mode: dismisses by default. A form
     * overrides it to ask before discarding changes.
     */
    open fun onCloseRequest() {
        dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = spec.dismissal.cancelable
        val dialog = super.onCreateDialog(savedInstanceState)
        if (!spec.dismissal.cancelable) {
            (dialog as ComponentDialog).onBackPressedDispatcher.addCallback(this) { onCloseRequest() }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            // Without the connection a drag that starts on a scrolled list goes to the sheet, not the list (SP-05a 2c').
            Box(Modifier.nestedScroll(rememberNestedScrollInteropConnection())) { SheetContent() }
        }

    override fun onStart() {
        super.onStart()
        val spec = spec
        if (spec.textInput) {
            @Suppress("DEPRECATION")
            dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val sheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val maxHeight = (resources.displayMetrics.heightPixels * MAX_HEIGHT_FRACTION).roundToInt()
        sheet.layoutParams = sheet.layoutParams.apply {
            height = when (spec.height) {
                SheetHeight.FitContent -> ViewGroup.LayoutParams.WRAP_CONTENT
                SheetHeight.Tall -> maxHeight
            }
        }
        BottomSheetBehavior.from(sheet).apply {
            this.maxHeight = maxHeight
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
            isDraggable = spec.dismissal.draggable
        }
    }

    private companion object {
        const val MAX_HEIGHT_FRACTION = 0.9f
    }
}
