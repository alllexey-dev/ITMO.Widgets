package dev.alllexey.itmowidgets.feature.resources.reference

import android.os.Looper
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.SubjectLinksPreviewActivity
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.resources.reference.SubjectLinksReferenceFixtures.SCOPE
import dev.alllexey.itmowidgets.feature.resources.reference.SubjectLinksReferenceFixtures.fixture
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.ReportLinkDialogFragment
import java.time.Duration
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewRootImpl
import org.robolectric.util.ReflectionHelpers
import com.google.android.material.R as MaterialR

/**
 * XML references of the links surfaces under the names of their future CMP content: the actions sheet and the report
 * dialog (LX-3b ported the "Все ссылки" sheet, LX-3c the link editor), through [SubjectLinksPreviewActivity] over a
 * fresh [MemorySubjectLinksRepository] per launch. The harness launches the host without extras, so it opens the links
 * sheet; other surfaces are opened over it through the host's navigator on the first poll. Each port deletes its own
 * test here and its lines in `shared/feature-resources/screenshots/references.txt`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SubjectLinksReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = MODULE)

    /** The launch whose surface was already opened; every appearance launches a new host. */
    private var opened: SubjectLinksPreviewActivity? = null

    /** The launch whose actions sheet already sent "Пожаловаться"; a second tap would open a second dialog. */
    private var reported: SubjectLinksPreviewActivity? = null

    @After
    fun resetHost() {
        SubjectLinksPreviewActivity.repository = MemorySubjectLinksRepository()
    }

    @Test
    fun linkActionsSheet() {
        fun actions(name: String, linkId: String) = references.sheet(
            name,
            LinkActionsBottomSheet.TAG,
            configure = { it.snapshots.value = mapOf(SCOPE.key to fixture()) },
            open = { it.openLinkActions(ARGS, linkId) },
        ) { sheet -> sheet.findViewById<TextView>(R.id.title).text.isNotEmpty() }

        actions("LinkActionsSheetContent_others-sheet", "others-sheet")
        actions("LinkActionsSheetContent_own-shared", "own-scores")
        actions("LinkActionsSheetContent_own-private", "own-other")
        actions("LinkActionsSheetContent_own-rejected", "own-rejected")
    }

    /** Opened as the user gets there: "Пожаловаться" in the actions of another student's link, a reason picked. */
    @Test
    fun reportLinkDialog() = references.host(
        "ReportLinkDialog_reason",
        SubjectLinksPreviewActivity::class.java,
        appearance = { prepare(it) { repository -> repository.snapshots.value = mapOf(SCOPE.key to fixture()) } },
        ready = { activity ->
            openOnce(activity) { it.openLinkActions(ARGS, "materials-all") }
            if (reported !== activity) {
                activity.dialogOf(LinkActionsBottomSheet.TAG)?.findViewById<View>(R.id.action_report)?.takeIf { it.isShown }?.let {
                    reported = activity
                    it.performClick()
                }
            }
            val dialog = activity.dialogOf(ReportLinkDialogFragment.TAG) as? AlertDialog
            val reason = dialog?.findViewById<RadioButton>(R.id.reason_broken)?.takeIf { it.isShown }
            if (reason?.isChecked == false) reason.performClick()
            settledWhen(reason?.isChecked == true && dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        },
        view = { it.focusOn(checkNotNull(it.dialogOf(ReportLinkDialogFragment.TAG)?.window).decorView) },
    )

    /**
     * A reference of one sheet: [configure] fills the launch's repository, [open] shows the sheet over the links
     * sheet, [ready] runs on the sheet until its state is reached, and the sheet's own surface is captured.
     */
    private fun XmlReferenceCapture.sheet(
        name: String,
        tag: String,
        configure: (MemorySubjectLinksRepository) -> Unit,
        open: (SubjectLinksPreviewActivity) -> Unit,
        ready: (View) -> Boolean,
    ) = host(
        name,
        SubjectLinksPreviewActivity::class.java,
        appearance = { prepare(it, configure) },
        ready = { activity ->
            openOnce(activity, open)
            settledWhen(activity.sheetOf(tag)?.let(ready) == true)
        },
        view = { it.focusOn(checkNotNull(it.sheetOf(tag))) },
    )

    /** Runs before every launch (and once after the reference with the default appearance). */
    private fun prepare(appearance: PreviewAppearance, configure: (MemorySubjectLinksRepository) -> Unit) {
        SubjectLinksPreviewActivity.appearance = appearance
        SubjectLinksPreviewActivity.repository = MemorySubjectLinksRepository().apply { servicesEnabled = true }.also(configure)
    }

    private fun openOnce(activity: SubjectLinksPreviewActivity, open: (SubjectLinksPreviewActivity) -> Unit) {
        if (opened === activity) return
        opened = activity
        open(activity)
    }

    /** Once [reached], lets animations (chip scroll, ripples, fades) end before the capture. */
    private fun settledWhen(reached: Boolean): Boolean {
        if (reached) shadowOf(Looper.getMainLooper()).idleFor(SETTLE)
        return reached
    }

    private fun FragmentActivity.dialogOf(tag: String) =
        (supportFragmentManager.findFragmentByTag(tag) as? DialogFragment)?.dialog?.takeIf { it.isShowing }

    private fun FragmentActivity.sheetOf(tag: String): View? =
        dialogOf(tag)?.findViewById<View>(MaterialR.id.design_bottom_sheet)?.takeIf { it.isLaidOut }


    /**
     * Hands the window of [view] the focus the activity had, so Roborazzi's Espresso lookup, which searches the
     * focused root, finds a view inside a dialog; Robolectric keeps the focus on the activity when a dialog shows.
     * State drawables jump to their end: the radio button's animated check never advances under Robolectric.
     */
    private fun FragmentActivity.focusOn(view: View): View {
        window.decorView.windowFocus(false)
        view.rootView.windowFocus(true)
        shadowOf(Looper.getMainLooper()).idle()
        view.rootView.jumpDrawablesToCurrentState()
        return view
    }

    private fun View.windowFocus(focused: Boolean) =
        Shadow.extract<ShadowViewRootImpl>(ReflectionHelpers.callInstanceMethod<Any>(this, "getViewRootImpl"))
            .callWindowFocusChanged(focused)

    private companion object {
        const val MODULE = "feature-resources"
        val ARGS = SubjectLinksPreviewActivity.ARGS
        val SETTLE: Duration = Duration.ofSeconds(1)
    }
}
