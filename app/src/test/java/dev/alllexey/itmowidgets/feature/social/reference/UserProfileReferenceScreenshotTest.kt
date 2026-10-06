package dev.alllexey.itmowidgets.feature.social.reference

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.SummaryConfidence
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileViewModel
import dev.alllexey.itmowidgets.feature.social.ui.UserProfileFragment
import dev.alllexey.itmowidgets.testkit.screenshot.CaptureSize
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's person profile under the names of LC-4b's `UserProfileScreen` previews, in
 * `shared/feature-social/screenshots/`: the Fragment over a [UserProfileViewModel] on the fixtures of
 * `UserProfilePreviewActivity` and `UserProfileVisualTest` (the teacher, the friend, the self page, a missing person,
 * the skeleton), with in-memory person, social and review repositories.
 *
 * The teacher page is captured in a window [TALL_HEIGHT_DP] high, so the AI summary under the facts is in the image;
 * its preview needs `@Preview(heightDp = 1800)`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class UserProfileReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    /** The Fragment's `by viewModel()` asks Koin, which the test application does not start. */
    @get:Rule
    val stopKoin = StopKoinRule()

    private val references = XmlReferenceCapture(shots, module = "feature-social")
    private val tallReferences =
        XmlReferenceCapture(shots, module = "feature-social", size = CaptureSize(heightDp = TALL_HEIGHT_DP))

    @Test
    fun teacherWithSummary() = profile("UserProfileScreen_teacher-summary", tallReferences) {
        person = AppResult.Success(teacher())
        reviews = AppResult.Success(
            TeacherReviews(ISU, teacherReviews(), null, false, false, false, false, summary()),
        )
    }

    @Test
    fun friend() = profile("UserProfileScreen_friend") { friendFixture() }

    @Test
    fun self() = profile("UserProfileScreen_self") {
        friendFixture()
        selfIsu = ISU
    }

    @Test
    fun notFound() = profile("UserProfileScreen_not-found", title = R.string.user_profile_not_found_title) {}

    /**
     * No part answers, so the skeleton stays. Its pulse is stopped (the duration scale of animations off) so two runs
     * capture the same frame.
     */
    @Test
    fun skeleton() {
        val scale = ValueAnimator.getDurationScale()
        setDurationScale(0f)
        try {
            profile("UserProfileScreen_skeleton", skeleton = true) { pending = true }
        } finally {
            setDurationScale(scale)
        }
    }

    /** `ValueAnimator.setDurationScale` is hidden; android-all has it. */
    private fun setDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    /** What the repositories answer for [ISU]; an unset part is absent, as for a person outside ITMO.Widgets. */
    private class Fixture {
        var person: AppResult<Person>? = null
        var social: UserProfile? = null
        var reviews: AppResult<TeacherReviews>? = null
        var selfIsu = 0

        /** Every part waits forever. */
        var pending = false

        fun friendFixture() {
            person = AppResult.Success(
                teacher().copy(
                    positions = emptyList(),
                    rooms = emptyList(),
                    education = listOf(PersonEducation("M3234", 2, "ФИТиП")),
                ),
            )
            social = UserProfile(
                UserSummary(ISU, BACKEND_NAME, null, listOf(UserGroup("M3234", 2, "ФИТиП")), UserSharing(true, true, true)),
                RelationshipState.FRIENDS,
            )
            reviews = AppResult.Success(TeacherReviews(ISU, emptyList(), null, false, false, false, false))
        }

        fun viewModel(): UserProfileViewModel {
            val wait: suspend () -> Unit = { if (pending) awaitCancellation() }
            val answer = person
            val people = object : PersonRepository {
                override fun cachedPerson(isu: Int): Person? = null

                override suspend fun person(isu: Int): AppResult<Person> {
                    wait()
                    return answer?.takeIf { isu == ISU } ?: AppResult.Failure(AppError.NotFound)
                }
            }
            val socialRepository = FakeSocialRepository().apply {
                social?.let { profiles = mapOf(ISU to it) }
                profileGate = wait
            }
            val reviewsRepository = FakeTeacherReviewsRepository().apply {
                reviews?.let { results = mapOf(ISU to it) }
                gate = wait
            }
            val self = selfIsu
            return UserProfileViewModel(
                SavedStateHandle(mapOf(UserScreenArgs.ISU to ISU)),
                socialRepository,
                people,
                reviewsRepository,
                object : CurrentUserProvider {
                    override suspend fun getCurrentUser() = self.takeIf { it > 0 }?.let { CurrentUser(it, null, null) }
                },
            )
        }
    }

    /**
     * Shows the profile once per launch over [fixture] and waits for the page, the state titled [title] or, for
     * [skeleton], the placeholder.
     */
    private fun profile(
        preview: String,
        capture: XmlReferenceCapture = references,
        @StringRes title: Int = 0,
        skeleton: Boolean = false,
        fixture: Fixture.() -> Unit,
    ) = capture.host(
        preview,
        ReferenceHostActivity::class.java,
        appearance = {},
        ready = ready@{ host ->
            val shown = host.supportFragmentManager.fragments.filterIsInstance<UserProfileFragment>().firstOrNull()
                ?: UserProfileFragment().also { fragment ->
                    fragment.arguments = bundleOf(UserScreenArgs.ISU to ISU)
                    host.supportFragmentManager.preset(fragment) { Fixture().apply(fixture).viewModel() }
                    host.show(fragment)
                }
            val view = shown.view ?: return@ready false
            when {
                skeleton -> view.findViewById<View>(R.id.loading).isShown
                title != 0 -> view.findViewById<TextView>(R.id.state_title).let {
                    it.isShown && it.text == host.getString(title)
                }
                else -> view.findViewById<RecyclerView>(R.id.profile_list).let {
                    it.isShown && it.childCount > 0 && !it.hasPendingAdapterUpdates() && !it.isAnimating
                }
            }
        },
        view = { it.content },
    )

    private companion object {
        const val ISU = 100001
        const val TALL_HEIGHT_DP = 1800
        const val LONG_NAME = "Александра Константиновна Константинопольская"
        const val BACKEND_NAME = "Соколов Артём Игоревич"
        const val LONG_DEPARTMENT =
            "Факультет информационных технологий и программирования, кафедра прикладной математики и теоретической информатики"
        const val UNTITLED_DEPARTMENT = "Институт международного развития и партнёрства"
        const val LONG_SUBJECT = "Математические методы моделирования сложных информационных систем"
        const val LONG_REVIEW = "Преподаватель последовательно объясняет сложные темы, разбирает примеры и отвечает на " +
            "вопросы студентов. На практических занятиях можно обсудить разные подходы к решению задачи и понять, " +
            "почему один из них лучше подходит."

        fun teacher() = Person(
            ISU,
            LONG_NAME,
            null,
            listOf(PersonPosition("Доцент", LONG_DEPARTMENT), PersonPosition(null, UNTITLED_DEPARTMENT)),
            listOf(PersonRoom("405", "Кронверкский проспект, 49")),
            emptyList(),
        )

        fun teacherReviews() = listOf(
            copiedReview("review-one", LONG_SUBJECT, ReviewDate.Month(YearMonth(2025, 1)), "Отзывы ПИ",
                "Понятно объясняет материал и подробно отвечает на вопросы."),
            copiedReview("review-two", null, ReviewDate.BeforeYear(2023), null, "На занятиях было интересно."),
            copiedReview("review-three", null, null, null, LONG_REVIEW),
        )

        fun copiedReview(id: String, subject: String?, written: ReviewDate?, source: String?, text: String) =
            TeacherReview(id, subject, written, text, score = 0, myVote = 0,
                origin = ReviewOrigin.Reviews(source, "https://example.org/reviews/$id"))

        /** `UserProfilePreviewActivity.sampleSummary()`: every block of the card filled. */
        fun summary() = TeacherSummary(
            reviewCount = 12,
            description = "Студенты чаще всего отмечают понятные лекции и честные оценки. " +
                "Защита лабораторных строгая, к ней нужно готовиться заранее.",
            pros = listOf("Понятно объясняет сложные темы", "Честно оценивает"),
            cons = listOf("Строгая защита лабораторных"),
            tags = listOf(SummaryTag.MANY_LABS, SummaryTag.STRICT_DEFENSE, SummaryTag.CLEAR_REQUIREMENTS),
            scales = listOf(
                SummaryScale(SummaryScaleKind.EXPLAINS, SummaryScaleValue.HIGH, "Хвалят понятные лекции"),
                SummaryScale(SummaryScaleKind.ATTITUDE, SummaryScaleValue.MEDIUM, "Ровное отношение без поблажек"),
                SummaryScale(SummaryScaleKind.FAIRNESS, SummaryScaleValue.HIGH, "Оценки считают честными"),
                SummaryScale(SummaryScaleKind.STRICTNESS, SummaryScaleValue.HIGH, "Строго принимает лабораторные"),
                SummaryScale(SummaryScaleKind.WORKLOAD, SummaryScaleValue.NOT_ENOUGH_DATA, null),
            ),
            level = TeacherLevel.POSITIVE,
            confidence = SummaryConfidence.MEDIUM,
        )
    }
}

/** Hands [fragment] the view model [create] makes before Koin could create one, under the key Koin reads. */
private fun FragmentManager.preset(fragment: Fragment, create: () -> ViewModel) =
    registerFragmentLifecycleCallbacks(
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentPreCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
                if (f !== fragment) return
                KoinStarter.ensureStarted(f.requireContext())
                val model = create()
                ViewModelProvider(
                    f,
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
                    },
                )[model.javaClass]
            }
        },
        false,
    )
