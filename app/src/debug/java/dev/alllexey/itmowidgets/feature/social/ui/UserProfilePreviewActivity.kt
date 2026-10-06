package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.SummaryConfidence
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.NoOpAppNavigator
import dev.alllexey.itmowidgets.di.bridge.SocialDebugFixtures
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import kotlinx.datetime.YearMonth
import java.util.Collections
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.core.module.Module

/** The real profile Fragment with synthetic repositories; never reads a session or calls a service. */
@AndroidEntryPoint
class UserProfilePreviewActivity : AppCompatActivity(), AppNavigator by NoOpAppNavigator {
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

    private lateinit var socialFixture: Module

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate(): a restored UserProfileFragment obtains its ViewModel from Koin.
        socialFixture = SocialDebugFixtures.load(this, SocialDebugFixtures.Fakes(
            social = { PreviewSocial },
            people = { PreviewPeople },
            reviews = { PreviewReviews },
            currentUser = { PreviewCurrentUser },
        ))
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
                if (fragment !is UserProfileFragment) return
                // The instance the Fragment's `by viewModel()` returns later: same store, same key.
                val viewModel = fragment.getViewModel<UserProfileViewModel>()
                fragment.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiState.collect { states.add(it) }
                }
            }
        }, false)
        super.onCreate(savedInstanceState)
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        val frame = FrameLayout(this)
        val container = FrameLayout(this).apply { id = R.id.user_profile_test_container }
        frame.addView(container, FrameLayout.LayoutParams(
            if (appearance.widthDp > 0) (appearance.widthDp * resources.displayMetrics.density).toInt() else -1,
            -1, Gravity.CENTER_HORIZONTAL
        ))
        setContentView(frame)
        WindowCompat.getInsetsController(window, frame).apply {
            isAppearanceLightStatusBars = !appearance.dark
            isAppearanceLightNavigationBars = !appearance.dark
        }
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(
            R.id.user_profile_test_container,
            UserProfileFragment().apply { arguments = bundleOf(UserScreenArgs.ISU to ISU) },
            ROOT_TAG
        ).commitNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        SocialDebugFixtures.unload(this, socialFixture)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !appearance.dark
            isAppearanceLightNavigationBars = !appearance.dark
        }
    }

    override fun openScreen(screen: AppScreen, arguments: Bundle?) {
        openedScreens.add(screen to arguments?.let(::Bundle))
    }

    override fun openReviewEditor(args: TeacherReviewArgs) {
        openedEditors.add(args)
    }

    override fun openReviewReport(args: TeacherReviewArgs, reviewId: String) {
        openedReports.add(reviewId)
    }

    companion object {
        const val ROOT_TAG = "user-profile"
        const val ISU = 100001
        const val LONG_NAME = "Александра Константиновна Константинопольская"
        @Volatile var appearance = PreviewAppearance()
        @Volatile var person: AppResult<Person> = AppResult.Failure(AppError.NotFound)
        @Volatile var social: AppResult<UserProfile> = AppResult.Failure(AppError.NotFound)
        @Volatile var reviews: AppResult<TeacherReviews> = AppResult.Success(TeacherReviews(ISU, emptyList(), null, false, false, false, false))
        @Volatile var cachedPerson: Person? = null
        @Volatile var cachedSocial: UserProfile? = null
        @Volatile var cachedReviews: TeacherReviews? = null
        @Volatile var personDelayMs = 0L
        @Volatile var socialDelayMs = 0L
        @Volatile var reviewsDelayMs = 0L
        @Volatile var selfIsu = 0
        @Volatile var mutationDelayMs = 0L
        @Volatile var mutationError: AppError? = null
        const val OWN_REVIEW_ID = "own-review"
        val states: MutableList<UserProfileUiState> = Collections.synchronizedList(mutableListOf())
        val openedScreens: MutableList<Pair<AppScreen, Bundle?>> = Collections.synchronizedList(mutableListOf())
        val openedEditors: MutableList<TeacherReviewArgs> = Collections.synchronizedList(mutableListOf())
        val openedReports: MutableList<String> = Collections.synchronizedList(mutableListOf())

        /** A synthetic AI summary for [reviews]; the defaults fill every block of the card. */
        fun sampleSummary(
            reviewCount: Int = 12,
            level: TeacherLevel = TeacherLevel.POSITIVE,
            confidence: SummaryConfidence = SummaryConfidence.MEDIUM,
            description: String = "Студенты чаще всего отмечают понятные лекции и честные оценки. " +
                "Защита лабораторных строгая, к ней нужно готовиться заранее.",
            pros: List<String> = listOf("Понятно объясняет сложные темы", "Честно оценивает"),
            cons: List<String> = listOf("Строгая защита лабораторных"),
            tags: List<SummaryTag> = listOf(SummaryTag.MANY_LABS, SummaryTag.STRICT_DEFENSE, SummaryTag.CLEAR_REQUIREMENTS),
            scales: List<SummaryScale> = listOf(
                SummaryScale(SummaryScaleKind.EXPLAINS, SummaryScaleValue.HIGH, "Хвалят понятные лекции"),
                SummaryScale(SummaryScaleKind.ATTITUDE, SummaryScaleValue.MEDIUM, "Ровное отношение без поблажек"),
                SummaryScale(SummaryScaleKind.FAIRNESS, SummaryScaleValue.HIGH, "Оценки считают честными"),
                SummaryScale(SummaryScaleKind.STRICTNESS, SummaryScaleValue.HIGH, "Строго принимает лабораторные"),
                SummaryScale(SummaryScaleKind.WORKLOAD, SummaryScaleValue.NOT_ENOUGH_DATA, null),
            ),
        ) = TeacherSummary(reviewCount, description, pros, cons, tags, scales, level, confidence)
    }

    private object PreviewPeople : PersonRepository {
        override fun cachedPerson(isu: Int): Person? = UserProfilePreviewActivity.cachedPerson?.takeIf { it.isu == isu }

        override suspend fun person(isu: Int): AppResult<Person> {
            delay(personDelayMs)
            return UserProfilePreviewActivity.person.also { result ->
                when (result) {
                    is AppResult.Success -> UserProfilePreviewActivity.cachedPerson = result.value
                    is AppResult.Failure -> if (result.error == AppError.NotFound) UserProfilePreviewActivity.cachedPerson = null
                }
            }
        }
    }

    /** Reviews kept in [reviews]; mutations change that fixture and publish it as an update, like the real repository. */
    private object PreviewReviews : TeacherReviewsRepository {
        init { check(BuildConfig.DEBUG) }

        private val updates = MutableSharedFlow<TeacherReviews>(extraBufferCapacity = 8)

        override fun cachedReviews(isu: Int): TeacherReviews? = UserProfilePreviewActivity.cachedReviews?.takeIf { it.isu == isu }

        override suspend fun reviews(isu: Int): AppResult<TeacherReviews> {
            delay(reviewsDelayMs)
            return UserProfilePreviewActivity.reviews.also { result ->
                when (result) {
                    is AppResult.Success -> UserProfilePreviewActivity.cachedReviews = result.value
                    is AppResult.Failure -> if (result.error == AppError.CustomServicesDisabled) UserProfilePreviewActivity.cachedReviews = null
                }
            }
        }

        override fun observeUpdates(): Flow<TeacherReviews> = updates

        override suspend fun save(isu: Int, draft: TeacherReviewDraft) = mutate { current ->
            current.copy(mine = OwnTeacherReview(current.mine?.id ?: OWN_REVIEW_ID, draft.subject, draft.text, draft.anonymous,
                OwnReviewStatus.PENDING, null, 0, false, ReviewDate.Month(YearMonth(2026, 9))))
        }

        override suspend fun delete(isu: Int) = mutate { it.copy(mine = null) }

        override suspend fun vote(isu: Int, reviewId: String, value: Int) = mutate { current ->
            current.copy(reviews = current.reviews.map {
                if (it.id == reviewId) it.copy(score = it.score - it.myVote + value, myVote = value) else it
            })
        }

        override suspend fun report(isu: Int, reviewId: String, reason: ReviewReportReason, comment: String?) = mutate { current ->
            current.copy(reviews = current.reviews.map { review ->
                val origin = review.origin
                if (review.id == reviewId && origin is ReviewOrigin.Community) review.copy(origin = origin.copy(reportedByMe = true)) else review
            })
        }

        suspend fun publish(next: TeacherReviews) = updates.emit(next)

        private suspend fun mutate(change: (TeacherReviews) -> TeacherReviews): AppResult<TeacherReviews> {
            delay(mutationDelayMs)
            mutationError?.let { return AppResult.Failure(it) }
            val current = (UserProfilePreviewActivity.reviews as? AppResult.Success)?.value ?: return AppResult.Failure(AppError.NotFound)
            val next = change(current)
            UserProfilePreviewActivity.reviews = AppResult.Success(next)
            UserProfilePreviewActivity.cachedReviews = next
            updates.emit(next)
            return AppResult.Success(next)
        }
    }

    /** Publishes [next] as if an editor elsewhere had saved it. */
    fun publishReviews(next: TeacherReviews) {
        reviews = AppResult.Success(next)
        cachedReviews = next
        lifecycleScope.launch { PreviewReviews.publish(next) }
    }

    private object PreviewSocial : SocialRepository {
        override fun observeFriends() = flowOf<LoadState<List<UserProfile>>>(LoadState.Content(emptyList()))
        override fun observeRequests() = flowOf<LoadState<FriendRequests>>(LoadState.Content(FriendRequests.EMPTY))
        override fun observeCurrentUser() = flowOf<UserSummary?>(null)
        override val currentFriends: List<UserProfile> = emptyList()
        override fun cachedProfile(isu: Int): UserProfile? = cachedSocial?.takeIf { it.isu == isu }
        override suspend fun refresh() = Unit
        override suspend fun userFriends(isu: Int): AppResult<List<UserProfile>> = AppResult.Success(emptyList())
        override suspend fun lookup(isus: List<Int>): AppResult<List<UserProfile>> = AppResult.Success(emptyList())

        override suspend fun profile(isu: Int): AppResult<UserProfile> {
            delay(socialDelayMs)
            return social.also { result ->
                when (result) {
                    is AppResult.Success -> cachedSocial = result.value
                    is AppResult.Failure -> if (result.error in listOf(
                        AppError.NotFound, AppError.CustomServicesDisabled, AppError.Unauthorized, AppError.Forbidden
                    )) cachedSocial = null
                }
            }
        }

        override suspend fun sendRequest(isu: Int) = changeRelationship(isu, RelationshipState.OUTGOING)
        override suspend fun acceptRequest(isu: Int) = changeRelationship(isu, RelationshipState.FRIENDS)
        override suspend fun rejectRequest(isu: Int) = changeRelationship(isu, RelationshipState.NONE)
        override suspend fun cancelRequest(isu: Int) = changeRelationship(isu, RelationshipState.NONE)
        override suspend fun removeFriend(isu: Int) = changeRelationship(isu, RelationshipState.NONE)

        private fun changeRelationship(isu: Int, relationship: RelationshipState): AppResult<UserProfile> {
            val profile = ((social as? AppResult.Success)?.value ?: cachedSocial)?.takeIf { it.isu == isu }
                ?: return AppResult.Failure(AppError.NotFound)
            return AppResult.Success(profile.copy(relationship = relationship)).also {
                social = it
                cachedSocial = it.value
            }
        }
    }

    private object PreviewCurrentUser : CurrentUserProvider {
        override suspend fun getCurrentUser(): CurrentUser? = selfIsu.takeIf { it > 0 }?.let { CurrentUser(it, null, null) }
    }
}
