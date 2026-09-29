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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
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
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import java.util.Collections
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * The real review editor or report dialog over an empty window, fed by synthetic fixtures; never reads a session
 * or calls a service. [EXTRA_SCREEN] picks [SCREEN_EDITOR] or [SCREEN_REPORT].
 */
@AndroidEntryPoint
class ReviewEditorPreviewActivity : AppCompatActivity(), AppNavigator {
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
        supportFragmentManager.registerFragmentLifecycleCallbacks(PreviewModels(), false)
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

    override fun openReviewEditor(args: TeacherReviewArgs) =
        ReviewEditorBottomSheet.newInstance(args).show(supportFragmentManager, ReviewEditorBottomSheet.TAG)

    override fun openReviewReport(args: TeacherReviewArgs, reviewId: String) =
        ReportReviewDialogFragment.newInstance(args, reviewId).show(supportFragmentManager, ReportReviewDialogFragment.TAG)

    override fun openScreen(screen: AppScreen, arguments: Bundle?) = Unit
    override fun openRoot(root: AppRoot) = Unit
    override fun dismissOverlays() = Unit
    override fun openLessonDetails(args: LessonDetailsArgs) = Unit
    override fun openPendingSportDetails(args: PendingSportDetailsArgs) = Unit
    override fun openSubjectLinks(args: SubjectLinksArgs) = Unit
    override fun openLinkEditor(args: SubjectLinksArgs, linkId: String?) = Unit
    override fun openLinkActions(args: SubjectLinksArgs, linkId: String) = Unit
    override fun openWebLogin() = Unit

    /** Hands both screens a view model over the fixtures before Hilt could create one, and narrows their window. */
    private class PreviewModels : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
            val arguments = fragment.arguments ?: return
            @Suppress("DEPRECATION")
            val handle = SavedStateHandle(arguments.keySet().associateWith { arguments.get(it) })
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                    ReviewEditorViewModel::class.java -> ReviewEditorViewModel(handle, PreviewReviews, PreviewLessons)
                    else -> ReportReviewViewModel(handle, PreviewReviews)
                } as T
            }
            when (fragment) {
                is ReviewEditorBottomSheet -> ViewModelProvider(fragment, factory)[ReviewEditorViewModel::class.java]
                is ReportReviewDialogFragment -> ViewModelProvider(fragment, factory)[ReportReviewViewModel::class.java]
            }
        }

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

    data class Appearance(val fontScale: Float = 1f, val dark: Boolean = false, val widthDp: Int = 0, val colorSeed: Int? = null)

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_EDITOR = "editor"
        const val SCREEN_REPORT = "report"
        const val TEACHER_ISU = 100001
        const val TEACHER_NAME = "Константинопольская Александра Константиновна"
        const val REVIEW_ID = "0f8fad5b-d9cb-469f-a165-70867728950e"
        val ARGS = TeacherReviewArgs(TEACHER_ISU, TEACHER_NAME)

        @Volatile var appearance = Appearance()
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
