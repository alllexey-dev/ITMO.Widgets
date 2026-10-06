package dev.alllexey.itmowidgets.feature.reviews.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.NoOpAppNavigator
import dev.alllexey.itmowidgets.di.bridge.ReviewsDebugFixtures
import java.util.Collections
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import org.koin.core.module.Module

/**
 * The real review editor or report dialog over an empty window, fed by synthetic fixtures through
 * [ReviewsDebugFixtures]; never reads a session or calls a service. [EXTRA_SCREEN] picks [SCREEN_EDITOR] or
 * [SCREEN_REPORT].
 */
@AndroidEntryPoint
class ReviewEditorPreviewActivity : AppCompatActivity(), AppNavigator by NoOpAppNavigator {
    private lateinit var reviewsFixture: Module

    override fun attachBaseContext(newBase: Context) {
        // AppCompat chooses its night configuration while attaching, before onCreate.
        delegate.localNightMode = if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = appearance.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        check(BuildConfig.DEBUG)
        // Before super.onCreate(): a restored editor or dialog obtains its ViewModel from Koin there.
        reviewsFixture = ReviewsDebugFixtures.load(this, { PreviewReviews }, { PreviewLessons })
        supportFragmentManager.registerFragmentLifecycleCallbacks(NarrowWindows(), false)
        super.onCreate(savedInstanceState)
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        setContentView(FrameLayout(this).apply { id = R.id.review_editor_test_container })
        if (savedInstanceState != null) return
        when (intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_EDITOR) {
            SCREEN_REPORT -> openReviewReport(ARGS, REVIEW_ID)
            else -> openReviewEditor(ARGS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ReviewsDebugFixtures.unload(this, reviewsFixture)
    }

    override fun openReviewEditor(args: TeacherReviewArgs) =
        ReviewEditorBottomSheet.newInstance(args).show(supportFragmentManager, ReviewEditorBottomSheet.TAG)

    override fun openReviewReport(args: TeacherReviewArgs, reviewId: String) =
        ReportReviewDialogFragment.newInstance(args, reviewId).show(supportFragmentManager, ReportReviewDialogFragment.TAG)

    /** Narrows both screens' windows to the appearance's width. */
    private class NarrowWindows : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, fragment: Fragment) {
            val width = appearance.widthDp.takeIf { it > 0 } ?: return
            val window = (fragment as? DialogFragment)?.dialog?.window ?: return
            window.setLayout((width * fragment.resources.displayMetrics.density).toInt(),
                if (fragment is ReviewEditorBottomSheet) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private object PreviewReviews : TeacherReviewsRepository {
        override fun cachedReviews(isu: Int): TeacherReviews? = reviews?.takeIf { it.isu == isu }
        override suspend fun reviews(isu: Int): AppResult<TeacherReviews> =
            reviews?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound)
        override fun observeUpdates(): Flow<TeacherReviews> = emptyFlow()

        override suspend fun save(isu: Int, draft: TeacherReviewDraft): AppResult<TeacherReviews> {
            delay(saveDelayMs)
            return saveResult.also { if (it is AppResult.Success) drafts.add(draft) }
        }

        override suspend fun delete(isu: Int): AppResult<TeacherReviews> = AppResult.Failure(AppError.NotFound)
        override suspend fun vote(isu: Int, reviewId: String, value: Int): AppResult<TeacherReviews> = AppResult.Failure(AppError.NotFound)

        override suspend fun report(isu: Int, reviewId: String, reason: ReviewReportReason, comment: String?): AppResult<TeacherReviews> {
            delay(saveDelayMs)
            return reportResult.also { if (it is AppResult.Success) reports.add("$reviewId:$reason:${comment.orEmpty()}") }
        }
    }

    private object PreviewLessons : TeacherLessonsGateway {
        override fun taughtBy(teacherIsu: Int): Flow<AppResult<TeacherLessons>> = flow {
            delay(lessonsDelayMs)
            emit(lessons)
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_EDITOR = "editor"
        const val SCREEN_REPORT = "report"
        const val TEACHER_ISU = 100001
        const val TEACHER_NAME = "Константинопольская Александра Константиновна"
        const val REVIEW_ID = "0f8fad5b-d9cb-469f-a165-70867728950e"
        val ARGS = TeacherReviewArgs(TEACHER_ISU, TEACHER_NAME)

        @Volatile var appearance = PreviewAppearance()
        @Volatile var reviews: TeacherReviews? = null
        @Volatile var lessons: AppResult<TeacherLessons> = AppResult.Success(TeacherLessons(emptySet(), emptyList()))
        @Volatile var lessonsDelayMs = 0L
        @Volatile var saveResult: AppResult<TeacherReviews> = AppResult.Failure(AppError.Network)
        @Volatile var saveDelayMs = 0L
        @Volatile var reportResult: AppResult<TeacherReviews> = AppResult.Failure(AppError.Network)
        val drafts: MutableList<TeacherReviewDraft> = Collections.synchronizedList(mutableListOf())
        val reports: MutableList<String> = Collections.synchronizedList(mutableListOf())
    }
}
