package dev.alllexey.itmowidgets.feature.reviews

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import dev.alllexey.itmowidgets.feature.reviews.ui.ReportReviewDialogFragment
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorBottomSheet
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import com.google.android.material.R as MaterialR

/**
 * The two reviews hosts over Koin (LX-2c) with the Compose bodies of LX-4, in a plain host activity: the instance
 * behind the host's `viewModel` property is the one Koin keeps for the Fragment, and it reads the fake repository and
 * lessons this test hands Koin. The editor is a form sheet (no tap outside, no drag, resized for the keyboard), asks
 * before discarding changes on back, keeps typed text across a recreation and, through the Fragment's
 * `SavedStateHandle`, across a restore from saved state; a failed save keeps it with a snackbar, a saved review closes
 * it. The report dialog sends through its view model, keeps its form across a recreation and stays open on a failure.
 * What the bodies draw is covered by `ReviewEditorSheetTest` and `ReportReviewDialogTest` of `:shared:feature-reviews`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ReviewsHostsKoinTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val stopKoin = StopKoinRule()

    /** Drives the Compose bodies' frames and finds their nodes, the dialogs' windows included. */
    @get:Rule(order = 2)
    val compose = createEmptyComposeRule()

    private val reviews = FakeTeacherReviewsRepository()
    private val lessons = FakeTeacherLessonsGateway()

    @Before
    fun setUp() {
        val fixture = module {
            factory<TeacherReviewsRepository> { reviews }
            factory<TeacherLessonsGateway> { lessons }
        }
        KoinStarter.ensureStarted(ApplicationProvider.getApplicationContext<Context>())
            .loadModules(listOf(fixture), allowOverride = true)
    }

    @Test
    fun `the editor renders the Koin view model over the cached own review`() {
        reviews.cached = mapOf(TEACHER_ISU to reviews(own()))
        lessons.answer(AppResult.Success(TeacherLessons(setOf(7101L), listOf(SUBJECT))))
        val controller = launch { openEditor() }
        try {
            val model = controller.get().host<ReviewEditorViewModel>(ReviewEditorBottomSheet.TAG)

            assertTrue(model.uiState.value.editing)
            assertEquals(REVIEW_TEXT, model.uiState.value.text)
            assertEquals(listOf(SUBJECT), model.uiState.value.suggestions)
            assertEquals(TEACHER_NAME, model.teacherName)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `the editor is a form sheet that neither a tap outside nor a drag closes`() {
        val controller = launch { openEditor() }
        try {
            val sheet = controller.get().fragment<ReviewEditorBottomSheet>(ReviewEditorBottomSheet.TAG)
            val dialog = checkNotNull(sheet.dialog)
            val container = checkNotNull(dialog.findViewById<FrameLayout>(MaterialR.id.design_bottom_sheet))
            val behavior = BottomSheetBehavior.from(container)

            assertFalse(sheet.isCancelable)
            assertFalse(behavior.isDraggable)
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.state)
            @Suppress("DEPRECATION")
            assertEquals(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                checkNotNull(dialog.window).attributes.softInputMode and
                    WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `back without changes closes the editor and with changes asks first`() {
        val untouched = launch { openEditor() }
        try {
            back(untouched.get(), ReviewEditorBottomSheet.TAG)
            assertNull(untouched.get().supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG))
        } finally {
            untouched.pause().stop().destroy()
        }

        val changed = launch { openEditor() }
        try {
            val sheet = changed.get().fragment<ReviewEditorBottomSheet>(ReviewEditorBottomSheet.TAG)
            changed.get().host<ReviewEditorViewModel>(ReviewEditorBottomSheet.TAG).onTextChanged(TYPED)
            idle()

            back(changed.get(), ReviewEditorBottomSheet.TAG)

            assertSame(sheet, changed.get().supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG))
            compose.onNodeWithText("Не сохранять отзыв?").assertIsDisplayed()

            compose.onNodeWithText("Отмена").performClick()
            idle()
            compose.onNodeWithText("Не сохранять отзыв?").assertDoesNotExist()
            assertSame(sheet, changed.get().supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG))

            back(changed.get(), ReviewEditorBottomSheet.TAG)
            compose.onNodeWithText("Не сохранять").performClick()
            idle()
            assertNull(changed.get().supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG))
        } finally {
            changed.pause().stop().destroy()
        }
    }

    @Test
    fun `a failed save keeps the editor with a snackbar and a saved review closes it`() {
        reviews.saveResult = AppResult.Failure(AppError.Network)
        val controller = launch { openEditor() }
        try {
            val sheet = controller.get().fragment<ReviewEditorBottomSheet>(ReviewEditorBottomSheet.TAG)
            val model = controller.get().host<ReviewEditorViewModel>(ReviewEditorBottomSheet.TAG)
            model.onTextChanged(REVIEW_TEXT)
            model.save()
            idle()

            assertSame(sheet, controller.get().supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG))
            assertTrue(checkNotNull(sheet.dialog?.window).decorView.descendants().any { it is Snackbar.SnackbarLayout })

            reviews.saveResult = AppResult.Success(reviews(own()))
            model.save()
            idle()

            assertNull(controller.get().supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG))
            assertEquals(REVIEW_TEXT, reviews.lastDraft?.text)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `the editor keeps typed text across a recreation`() {
        val controller = launch { openEditor() }
        try {
            val before = controller.get().host<ReviewEditorViewModel>(ReviewEditorBottomSheet.TAG)
            before.onTextChanged(TYPED)
            idle()

            controller.recreate()
            idle()

            val after = controller.get().host<ReviewEditorViewModel>(ReviewEditorBottomSheet.TAG)
            assertSame(before, after)
            assertEquals(TYPED, after.uiState.value.text)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `the editor restores typed text from saved state into a new Koin view model`() {
        val first = launch { openEditor() }
        val saved = Bundle()
        val before: ReviewEditorViewModel
        try {
            before = first.get().host(ReviewEditorBottomSheet.TAG)
            before.onTextChanged(TYPED)
            idle()
            first.pause().stop().saveInstanceState(saved)
        } finally {
            first.destroy()
        }

        val second = launch(saved) {}
        try {
            val after = second.get().host<ReviewEditorViewModel>(ReviewEditorBottomSheet.TAG)
            assertNotSame(before, after)
            assertEquals(TYPED, after.uiState.value.text)
            assertTrue(after.hasChanges())
        } finally {
            second.pause().stop().destroy()
        }
    }

    @Test
    fun `the report dialog sends through the Koin view model and closes once accepted`() {
        reviews.reportResult = AppResult.Success(reviews(null))
        val controller = launch { openReport() }
        try {
            val model = controller.get().host<ReportReviewViewModel>(ReportReviewDialogFragment.TAG)

            model.send(ReviewReportReason.SPAM, "  ")
            idle()

            assertEquals(listOf("report:$TEACHER_ISU:$REVIEW_ID:SPAM"), reviews.actions)
            assertNull(reviews.lastComment)
            assertNull(controller.get().supportFragmentManager.findFragmentByTag(ReportReviewDialogFragment.TAG))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `a failed report keeps the dialog shown`() {
        reviews.reportResult = AppResult.Failure(AppError.Network)
        val controller = launch { openReport() }
        try {
            val fragment = controller.get().fragment<ReportReviewDialogFragment>(ReportReviewDialogFragment.TAG)
            compose.onNodeWithText("Жалоба на отзыв").assertIsDisplayed()

            controller.get().host<ReportReviewViewModel>(ReportReviewDialogFragment.TAG)
                .send(ReviewReportReason.WRONG_TEACHER, COMMENT)
            idle()

            val shown = controller.get().supportFragmentManager.findFragmentByTag(ReportReviewDialogFragment.TAG)
            assertSame(fragment, shown)
            compose.onNodeWithText("Нет связи. Проверьте интернет.").assertIsDisplayed()
            compose.onNodeWithText("Жалоба на отзыв").assertIsDisplayed()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `the report dialog keeps the chosen reason and comment across a recreation`() {
        val controller = launch { openReport() }
        try {
            compose.onNodeWithText("Спам").performClick()
            compose.onNode(hasSetTextAction()).performTextInput(COMMENT)
            idle()

            controller.recreate()
            idle()

            compose.onNodeWithText("Спам").assertIsSelected()
            compose.onNodeWithText(COMMENT).assertIsDisplayed()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun launch(
        saved: Bundle? = null,
        open: ReferenceHostActivity.() -> Unit,
    ): ActivityController<ReferenceHostActivity> {
        val controller = Robolectric.buildActivity(ReferenceHostActivity::class.java)
        if (saved == null) controller.setup() else controller.setup(saved)
        controller.get().open()
        idle()
        return controller
    }

    private fun ReferenceHostActivity.openEditor() =
        ReviewEditorBottomSheet.newInstance(ARGS).show(supportFragmentManager, ReviewEditorBottomSheet.TAG)

    private fun ReferenceHostActivity.openReport() =
        ReportReviewDialogFragment.newInstance(ARGS, REVIEW_ID)
            .show(supportFragmentManager, ReportReviewDialogFragment.TAG)

    private inline fun <reified F : Fragment> ReferenceHostActivity.fragment(tag: String): F =
        checkNotNull(supportFragmentManager.findFragmentByTag(tag) as? F) { "no $tag" }

    /** The host's own `viewModel` property, checked to be the instance Koin keeps for that Fragment. */
    private inline fun <reified VM : ViewModel> ReferenceHostActivity.host(tag: String): VM {
        val fragment = fragment<Fragment>(tag)
        val property = fragment.viewModelProperty() as VM
        assertSame(fragment.getViewModel<VM>(), property)
        return property
    }

    private fun Fragment.viewModelProperty(): Any? {
        val delegate = javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.get(this)
        return (delegate as Lazy<*>).value
    }

    private fun back(activity: ReferenceHostActivity, tag: String) {
        val dialog = checkNotNull(activity.fragment<DialogFragment>(tag).dialog) as ComponentDialog
        dialog.onBackPressedDispatcher.onBackPressed()
        idle()
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun reviews(mine: OwnTeacherReview?) = TeacherReviews(TEACHER_ISU, emptyList(), mine,
        canWrite = true, canVote = true, canReport = true, knownTeacher = true)

    private fun own() = OwnTeacherReview("own", SUBJECT, REVIEW_TEXT, anonymous = false,
        status = OwnReviewStatus.PUBLISHED, reviewNote = null, score = 2, verified = true,
        written = ReviewDate.Month(YearMonth(2026, 9)))

    private companion object {
        const val TEACHER_ISU = 100001
        const val TEACHER_NAME = "Константинопольская Александра Константиновна"
        const val REVIEW_ID = "0f8fad5b-d9cb-469f-a165-70867728950e"
        val ARGS = TeacherReviewArgs(TEACHER_ISU, TEACHER_NAME)
        const val SUBJECT = "Математический анализ"
        const val REVIEW_TEXT = "Лекции понятные, на практике разбираем задачи из контрольных."
        const val COMMENT = "Ведёт другой предмет"
        const val TYPED = "Черновик отзыва, набранный до пересоздания экрана."
    }
}
