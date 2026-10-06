package dev.alllexey.itmowidgets.feature.reviews

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import dev.alllexey.itmowidgets.feature.reviews.ui.ReportReviewDialogFragment
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorBottomSheet
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorPreviewActivity
import kotlinx.datetime.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Both reviews hosts obtain their ViewModels from Koin (LX-2c): the instance behind the host's `viewModel` property is
 * the one Koin keeps for the Fragment, and it reads the fixtures [ReviewEditorPreviewActivity] hands Koin through
 * `ReviewsDebugFixtures`, which no Hilt factory could reach. The editor keeps typed text across a recreation and,
 * through the Fragment's `SavedStateHandle`, across a restore from saved state.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ReviewsHostsKoinTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val stopKoin = StopKoinRule()

    @Before
    fun setUp() = reset()

    @After
    fun tearDown() = reset()

    @Test
    fun `the editor renders the Koin view model over the cached own review`() {
        ReviewEditorPreviewActivity.reviews = reviews(own())
        ReviewEditorPreviewActivity.lessons = AppResult.Success(TeacherLessons(setOf(7101L), listOf(SUBJECT)))
        val controller = launch(ReviewEditorPreviewActivity.SCREEN_EDITOR)
        try {
            val model = controller.get().host<ReviewEditorViewModel>(ReviewEditorBottomSheet.TAG)

            assertTrue(model.uiState.value.editing)
            assertEquals(REVIEW_TEXT, model.uiState.value.text)
            assertEquals(listOf(SUBJECT), model.uiState.value.suggestions)
            assertEquals(ReviewEditorPreviewActivity.TEACHER_NAME, model.teacherName)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `the report dialog sends through the Koin view model and the fixture repository`() {
        ReviewEditorPreviewActivity.reportResult = AppResult.Success(reviews(null))
        val controller = launch(ReviewEditorPreviewActivity.SCREEN_REPORT)
        try {
            val model = controller.get().host<ReportReviewViewModel>(ReportReviewDialogFragment.TAG)

            model.send(ReviewReportReason.SPAM, "  ")
            idle()

            assertEquals(listOf("${ReviewEditorPreviewActivity.REVIEW_ID}:SPAM:"), ReviewEditorPreviewActivity.reports.toList())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `the editor keeps typed text across a recreation`() {
        val controller = launch(ReviewEditorPreviewActivity.SCREEN_EDITOR)
        try {
            val before = controller.get().host<ReviewEditorViewModel>(ReviewEditorBottomSheet.TAG)
            controller.get().editorText().setText(TYPED)
            idle()

            controller.recreate()
            idle()

            val after = controller.get().host<ReviewEditorViewModel>(ReviewEditorBottomSheet.TAG)
            assertSame(before, after)
            assertEquals(TYPED, after.uiState.value.text)
            assertEquals(TYPED, controller.get().editorText().text.toString())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `the editor restores typed text from saved state into a new Koin view model`() {
        val first = launch(ReviewEditorPreviewActivity.SCREEN_EDITOR)
        val saved = Bundle()
        val before: ReviewEditorViewModel
        try {
            before = first.get().host(ReviewEditorBottomSheet.TAG)
            first.get().editorText().setText(TYPED)
            idle()
            first.pause().stop().saveInstanceState(saved)
        } finally {
            first.destroy()
        }

        val second = launch(ReviewEditorPreviewActivity.SCREEN_EDITOR, saved)
        try {
            val after = second.get().host<ReviewEditorViewModel>(ReviewEditorBottomSheet.TAG)
            assertNotSame(before, after)
            assertEquals(TYPED, after.uiState.value.text)
            assertTrue(after.hasChanges())
            assertEquals(TYPED, second.get().editorText().text.toString())
        } finally {
            second.pause().stop().destroy()
        }
    }

    private fun launch(screen: String, saved: Bundle? = null): ActivityController<ReviewEditorPreviewActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), ReviewEditorPreviewActivity::class.java)
            .putExtra(ReviewEditorPreviewActivity.EXTRA_SCREEN, screen)
        val controller = Robolectric.buildActivity(ReviewEditorPreviewActivity::class.java, intent)
        if (saved == null) controller.setup() else controller.setup(saved)
        idle()
        return controller
    }

    /** The host's own `viewModel` property, checked to be the instance Koin keeps for that Fragment. */
    private inline fun <reified VM : ViewModel> ReviewEditorPreviewActivity.host(tag: String): VM {
        val fragment = checkNotNull(supportFragmentManager.findFragmentByTag(tag)) { "no $tag" }
        val property = fragment.viewModelProperty() as VM
        assertSame(fragment.getViewModel<VM>(), property)
        return property
    }

    private fun Fragment.viewModelProperty(): Any? {
        val delegate = javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.get(this)
        return (delegate as Lazy<*>).value
    }

    private fun ReviewEditorPreviewActivity.editorText(): EditText {
        val sheet = checkNotNull(supportFragmentManager.findFragmentByTag(ReviewEditorBottomSheet.TAG)) { "no editor" }
        return sheet.requireView().findViewById(R.id.text)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun reset() {
        ReviewEditorPreviewActivity.appearance = PreviewAppearance()
        ReviewEditorPreviewActivity.reviews = null
        ReviewEditorPreviewActivity.lessons = AppResult.Success(TeacherLessons(emptySet(), emptyList()))
        ReviewEditorPreviewActivity.lessonsDelayMs = 0
        ReviewEditorPreviewActivity.saveResult = AppResult.Failure(AppError.Network)
        ReviewEditorPreviewActivity.saveDelayMs = 0
        ReviewEditorPreviewActivity.reportResult = AppResult.Failure(AppError.Network)
        ReviewEditorPreviewActivity.drafts.clear()
        ReviewEditorPreviewActivity.reports.clear()
    }

    private fun reviews(mine: OwnTeacherReview?) = TeacherReviews(ReviewEditorPreviewActivity.TEACHER_ISU, emptyList(), mine,
        canWrite = true, canVote = true, canReport = true, knownTeacher = true)

    private fun own() = OwnTeacherReview("own", SUBJECT, REVIEW_TEXT, anonymous = false,
        status = OwnReviewStatus.PUBLISHED, reviewNote = null, score = 2, verified = true,
        written = ReviewDate.Month(YearMonth(2026, 9)))

    private companion object {
        const val SUBJECT = "Математический анализ"
        const val REVIEW_TEXT = "Лекции понятные, на практике разбираем задачи из контрольных."
        const val TYPED = "Черновик отзыва, набранный до пересоздания экрана."
    }
}
