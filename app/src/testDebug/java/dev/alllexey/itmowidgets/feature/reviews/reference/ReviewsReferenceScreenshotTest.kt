package dev.alllexey.itmowidgets.feature.reviews.reference

import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.reviews.ui.ReportReviewDialogFragment
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorBottomSheet
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorPreviewActivity
import java.time.Duration
import kotlinx.datetime.YearMonth
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
 * XML references of the review surfaces under the names of their future CMP content: the review editor and the
 * report dialog, through [ReviewEditorPreviewActivity] over its synthetic fixtures, the cases of
 * `ReviewEditorVisualTest`. The harness launches the host without extras, so it opens the editor; the report dialog
 * is opened through the host's navigator on the first poll. Each port deletes its own test here and its lines in
 * `shared/feature-reviews/screenshots/references.txt`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ReviewsReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-reviews")

    /** The launch being polled, and the one whose step was already taken; every appearance launches a new host. */
    private var current: ReviewEditorPreviewActivity? = null
    private var acted: ReviewEditorPreviewActivity? = null

    @After
    fun resetHost() = prepare(PreviewAppearance()) {}

    @Test
    fun reviewEditorSheet() {
        editor("ReviewEditorSheetContent_new", { ReviewEditorPreviewActivity.lessons = SUGGESTIONS }) { sheet ->
            sheet.findViewById<ChipGroup>(R.id.suggestions).childCount == SUBJECTS.size
        }
        editor("ReviewEditorSheetContent_edit", { ReviewEditorPreviewActivity.reviews = reviews(own()) }) { sheet ->
            sheet.findViewById<EditText>(R.id.text).text.isNotEmpty()
        }
        editor("ReviewEditorSheetContent_too-short", {}) { sheet ->
            once {
                sheet.findViewById<EditText>(R.id.text).setText("а".repeat(TOO_SHORT))
                sheet.findViewById<View>(R.id.save_button).performClick()
            }
            sheet.findViewById<TextInputLayout>(R.id.text_layout).error != null
        }
        editor("ReviewEditorSheetContent_named", {}) { sheet ->
            once { sheet.findViewById<View>(R.id.anonymous).performClick() }
            sheet.findViewById<View>(R.id.anonymous_hint).isShown
        }
    }

    @Test
    fun reportReviewDialog() {
        report("ReportReviewDialog_reason") { dialog ->
            pick(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled
        }
        report("ReportReviewDialog_failed") { dialog ->
            if (pick(dialog)) dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            dialog.findViewById<TextInputLayout>(R.id.comment_layout)?.error != null
        }
    }

    private fun editor(name: String, configure: () -> Unit, ready: (View) -> Boolean) = references.host(
        name,
        ReviewEditorPreviewActivity::class.java,
        appearance = { prepare(it, configure) },
        ready = { activity ->
            current = activity
            settledWhen(activity.sheet()?.let(ready) == true)
        },
        view = { it.focusOn(checkNotNull(it.sheet())) },
    )

    /** The report dialog over the editor the host opens; a failed send keeps it open with the error. */
    private fun report(name: String, ready: (AlertDialog) -> Boolean) = references.host(
        name,
        ReviewEditorPreviewActivity::class.java,
        appearance = { prepare(it) { ReviewEditorPreviewActivity.reportResult = AppResult.Failure(AppError.Network) } },
        ready = { activity ->
            current = activity
            once { activity.openReviewReport(ReviewEditorPreviewActivity.ARGS, ReviewEditorPreviewActivity.REVIEW_ID) }
            settledWhen(activity.reportDialog()?.let(ready) == true)
        },
        view = { it.focusOn(checkNotNull(it.reportDialog()?.window).decorView) },
    )

    /** Picks "Не тот преподаватель" with a comment once; true when it picked. */
    private fun pick(dialog: AlertDialog): Boolean {
        val reason = dialog.findViewById<RadioButton>(R.id.reason_wrong_teacher) ?: return false
        if (reason.isChecked) return false
        reason.performClick()
        dialog.findViewById<EditText>(R.id.comment)?.setText("Ведёт другой предмет")
        return true
    }

    /** Runs [step] once per launch. */
    private fun once(step: () -> Unit) {
        if (acted === current) return
        acted = current
        step()
    }

    /** Runs before every launch (and once after the reference with the default appearance). */
    private fun prepare(appearance: PreviewAppearance, configure: () -> Unit) {
        ReviewEditorPreviewActivity.appearance = appearance
        ReviewEditorPreviewActivity.reviews = null
        ReviewEditorPreviewActivity.lessons = AppResult.Success(TeacherLessons(emptySet(), emptyList()))
        ReviewEditorPreviewActivity.lessonsDelayMs = 0
        ReviewEditorPreviewActivity.saveResult = AppResult.Failure(AppError.Network)
        ReviewEditorPreviewActivity.saveDelayMs = 0
        ReviewEditorPreviewActivity.reportResult = AppResult.Failure(AppError.Network)
        ReviewEditorPreviewActivity.drafts.clear()
        ReviewEditorPreviewActivity.reports.clear()
        configure()
    }

    /** Once [reached], lets animations (ripples, fades, the error's entrance) end before the capture. */
    private fun settledWhen(reached: Boolean): Boolean {
        if (reached) shadowOf(Looper.getMainLooper()).idleFor(SETTLE)
        return reached
    }

    private fun FragmentActivity.dialogOf(tag: String) =
        (supportFragmentManager.findFragmentByTag(tag) as? DialogFragment)?.dialog?.takeIf { it.isShowing }

    private fun FragmentActivity.sheet(): View? = dialogOf(ReviewEditorBottomSheet.TAG)
        ?.findViewById<View>(MaterialR.id.design_bottom_sheet)?.takeIf { it.isLaidOut }

    private fun FragmentActivity.reportDialog(): AlertDialog? = dialogOf(ReportReviewDialogFragment.TAG) as? AlertDialog

    private fun reviews(mine: OwnTeacherReview?) = TeacherReviews(ReviewEditorPreviewActivity.TEACHER_ISU, emptyList(), mine,
        canWrite = true, canVote = true, canReport = true, knownTeacher = true)

    private fun own() = OwnTeacherReview("own", "Математический анализ", REVIEW_TEXT, anonymous = false,
        status = OwnReviewStatus.PUBLISHED, reviewNote = null, score = 2, verified = true,
        written = ReviewDate.Month(YearMonth(2026, 9)))


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
        val SUBJECTS = listOf("Математический анализ", "Линейная алгебра", "Дискретная математика")
        val SUGGESTIONS: AppResult<TeacherLessons> = AppResult.Success(TeacherLessons(setOf(7101L, 7102L), SUBJECTS))
        const val REVIEW_TEXT = "Лекции понятные, на практике разбираем задачи из контрольных."
        const val TOO_SHORT = 29
        val SETTLE: Duration = Duration.ofSeconds(1)
    }
}
