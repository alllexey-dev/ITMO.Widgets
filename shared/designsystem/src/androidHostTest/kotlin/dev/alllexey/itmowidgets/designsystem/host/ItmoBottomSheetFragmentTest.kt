package dev.alllexey.itmowidgets.designsystem.host

import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.math.roundToInt

/** The Compose-body sheet host: height, dismissal policy, keyboard mode and state after recreation (SP-05a's checks). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItmoBottomSheetFragmentTest {

    @After
    fun reset() {
        ProbeSheet.bumpOnce = false
        ProbeSheet.observed = -1
        ProbeSheet.closeRequests = 0
    }

    @Test
    fun `a short body opens expanded at its own height`() {
        val (_, sheet) = show(ProbeSheet.newInstance(rows = 3))

        val body = sheet.fragment.requireView()
        assertTrue(body is ComposeView)
        assertEquals(body.height, sheet.view.height - sheet.view.paddingTop - sheet.view.paddingBottom)
        assertEquals(3 * ROW_PX, body.height)
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.behavior.state)
        assertTrue(sheet.behavior.skipCollapsed)
        assertTrue(sheet.view.height < sheet.maxHeight)
    }

    @Test
    fun `a tall body stops at 90 percent of the screen`() {
        val (_, sheet) = show(ProbeSheet.newInstance(rows = 60))

        assertEquals(sheet.maxHeight, sheet.view.height)
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.behavior.state)
    }

    @Test
    fun `a tall sheet takes 90 percent even with a short body`() {
        val (_, sheet) = show(ProbeSheet.newInstance(rows = 2, height = SheetHeight.Tall))

        assertEquals(sheet.maxHeight, sheet.view.height)
    }

    @Test
    fun `a free sheet can be dragged and cancelled`() {
        val (_, sheet) = show(ProbeSheet.newInstance(rows = 3))

        assertTrue(sheet.fragment.isCancelable)
        assertTrue(sheet.behavior.isDraggable)
        (sheet.fragment.requireDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        settle()

        assertFalse(sheet.fragment.isAdded)
        assertEquals(0, ProbeSheet.closeRequests)
    }

    @Test
    fun `a form is not dragged or cancelled and back asks the sheet`() {
        val (_, sheet) = show(ProbeSheet.newInstance(rows = 3, dismissal = SheetDismissal.Form, textInput = true))

        assertFalse(sheet.fragment.isCancelable)
        assertFalse(sheet.behavior.isDraggable)
        @Suppress("DEPRECATION")
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            sheet.fragment.requireDialog().window!!.attributes.softInputMode and
                WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
        )
        (sheet.fragment.requireDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        settle()

        assertEquals(1, ProbeSheet.closeRequests)
        assertTrue(sheet.fragment.isAdded)
        assertTrue(sheet.fragment.requireDialog().isShowing)
    }

    @Test
    fun `the body's saved state and the expanded sheet survive recreation`() {
        ProbeSheet.bumpOnce = true
        val (controller, _) = show(ProbeSheet.newInstance(rows = 3, dismissal = SheetDismissal.Form))
        assertEquals(BUMPED, ProbeSheet.observed)

        ProbeSheet.observed = -1
        controller.recreate()
        settle()

        val restored = controller.get().supportFragmentManager.findFragmentByTag(TAG) as ProbeSheet
        val sheet = SheetViews(restored)
        assertEquals(BUMPED, ProbeSheet.observed)
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.behavior.state)
        assertFalse(restored.isCancelable)
        assertFalse(sheet.behavior.isDraggable)
    }

    private fun show(fragment: ProbeSheet): Pair<ActivityController<SheetActivity>, SheetViews> {
        val controller = Robolectric.buildActivity(SheetActivity::class.java).setup()
        fragment.show(controller.get().supportFragmentManager, TAG)
        settle()
        return controller to SheetViews(fragment)
    }

    /** Runs pending work and a few frames, so Compose measures and the sheet lays out. */
    private fun settle() = repeat(SETTLE_ROUNDS) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS)) }

    private class SheetViews(val fragment: ProbeSheet) {
        val view: FrameLayout = fragment.requireDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet)
        val behavior: BottomSheetBehavior<View> = BottomSheetBehavior.from(view)
        val maxHeight = (fragment.resources.displayMetrics.heightPixels * 0.9f).roundToInt()
    }

    /** An AppCompat activity in the MDC theme the app's sheets run in. */
    class SheetActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
            super.onCreate(savedInstanceState)
        }
    }

    /** [rows] fixed rows in a scrolling column, and a saveable counter that the first sheet starts at [BUMPED]. */
    class ProbeSheet : ItmoBottomSheetFragment() {
        override val spec: SheetSpec
            get() = requireArguments().let {
                SheetSpec(
                    height = SheetHeight.valueOf(it.getString(HEIGHT)!!),
                    dismissal = SheetDismissal.valueOf(it.getString(DISMISSAL)!!),
                    textInput = it.getBoolean(TEXT_INPUT),
                )
            }

        @Composable
        override fun SheetContent() {
            // The initializer runs only without a restored value: the first sheet starts bumped, a recreated one
            // shows the bump only if the state came back.
            val counter by rememberSaveable {
                mutableIntStateOf(if (bumpOnce) BUMPED.also { bumpOnce = false } else 0)
            }
            SideEffect { observed = counter }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                repeat(requireArguments().getInt(ROWS)) { Box(Modifier.fillMaxWidth().height(ROW_DP.dp)) }
            }
        }

        override fun onCloseRequest() {
            closeRequests++
        }

        companion object {
            var bumpOnce = false
            var observed = -1
            var closeRequests = 0

            fun newInstance(
                rows: Int,
                height: SheetHeight = SheetHeight.FitContent,
                dismissal: SheetDismissal = SheetDismissal.Free,
                textInput: Boolean = false,
            ) = ProbeSheet().apply {
                arguments = bundleOf(ROWS to rows, HEIGHT to height.name, DISMISSAL to dismissal.name, TEXT_INPUT to textInput)
            }
        }
    }

    private companion object {
        const val TAG = "probe"
        const val ROWS = "rows"
        const val HEIGHT = "height"
        const val DISMISSAL = "dismissal"
        const val TEXT_INPUT = "textInput"
        const val ROW_DP = 48
        const val BUMPED = 7
        const val SETTLE_ROUNDS = 10
        const val FRAME_MS = 20L

        /** Robolectric's default screen is mdpi, so a dp is a pixel. */
        const val ROW_PX = ROW_DP
    }
}
