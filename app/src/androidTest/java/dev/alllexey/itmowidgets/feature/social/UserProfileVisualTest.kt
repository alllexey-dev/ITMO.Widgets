package dev.alllexey.itmowidgets.feature.social

import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.R as MaterialR
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.ProfileItem
import dev.alllexey.itmowidgets.feature.social.ui.UserProfileAdapter
import dev.alllexey.itmowidgets.feature.social.ui.UserProfilePreviewActivity
import dev.alllexey.itmowidgets.feature.social.ui.UserProfilePreviewActivity.Companion.ISU
import dev.alllexey.itmowidgets.feature.social.ui.UserProfilePreviewActivity.Companion.LONG_NAME
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toUserProfile
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserProfileVisualTest {
    private val defaultPrimary = mutableMapOf<Boolean, Int>()

    @Test fun teacherHasAccessibleFactsAndCompleteReviews() = appearances { spec ->
        preview(spec, configure = {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews()))
        }) { scenario ->
            content(scenario)
            scenario.onActivity { activity ->
                val items = activity.items()
                assertEquals(3, items.count { it is ProfileItem.Review })
                assertTrue(items.none { it is ProfileItem.Sharing || it is ProfileItem.Relationship })
                assertNull(activity.findViewById<View>(R.id.friends_row))
                assertNull(activity.findViewById<View>(R.id.primary_action))
                assertEquals(LONG_NAME, activity.findViewById<TextView>(R.id.name).text.toString())
                assertTrue(activity.findViewById<View>(R.id.name).isAccessibilityHeading)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    activity.findViewById<View>(R.id.avatar).importantForAccessibility)
                val facts = activity.holder<ProfileItem.Facts>().findViewById<LinearLayout>(R.id.facts)
                val rows = facts.children.filter { it.findViewById<View>(R.id.fact_title) != null }.toList()
                assertEquals(3, rows.size)
                assertEquals("Должность: Доцент", rows[0].findViewById<TextView>(R.id.fact_title).contentDescription)
                assertTrue(rows[0].findViewById<View>(R.id.fact_subtitle).isShown)
                assertEquals(View.GONE, rows[1].findViewById<View>(R.id.fact_subtitle).visibility)
                assertEquals(1, facts.descendants().filterIsInstance<TextView>().count { it.text.toString() == UNTITLED_DEPARTMENT })
            }
            frame(scenario, "teacher-top-${spec.name}")
            scrollTo<ProfileItem.Section>(scenario)
            scenario.onActivity {
                val title = it.holder<ProfileItem.Section>().findViewById<TextView>(R.id.title)
                assertEquals("Отзывы", title.text.toString())
                assertTrue(title.isAccessibilityHeading)
            }
            teacherReviews().forEachIndexed { index, review -> assertReview(scenario, index, review) }
            frame(scenario, "teacher-bottom-${spec.name}")
        }
    }

    @Test fun friendKeepsRelationshipActionsAboveFacts() = appearances { spec ->
        preview(spec, ::friendFixture) { scenario ->
            content(scenario)
            scenario.onActivity { activity ->
                val button = activity.findViewById<TextView>(R.id.primary_action)
                assertEquals("Удалить из друзей", button.text.toString())
                for (id in listOf(R.id.friends_row, R.id.schedule_row, R.id.sport_row)) {
                    assertTrue(activity.findViewById<View>(id).isClickable)
                    assertTrue(activity.findViewById<View>(id).isEnabled)
                }
                assertTrue(activity.items().none { it is ProfileItem.Section })
                assertTrue(button.screenTop() < activity.holder<ProfileItem.Facts>().screenTop())
            }
            frame(scenario, "friend-${spec.name}")
        }
    }

    @Test fun selfProfileHasNoRelationshipButtons() = appearances { spec ->
        preview(spec, {
            friendFixture()
            UserProfilePreviewActivity.selfIsu = ISU
        }) { scenario ->
            content(scenario)
            scenario.onActivity {
                assertEquals("Это вы", it.findViewById<TextView>(R.id.relationship_status).text.toString())
                assertFalse(it.findViewById<View>(R.id.primary_action).isShown)
                assertFalse(it.findViewById<View>(R.id.secondary_action).isShown)
            }
            frame(scenario, "self-${spec.name}")
        }
    }

    @Test fun disabledBackendPublishesOnlyOneIdentityPage() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.social = AppResult.Failure(AppError.CustomServicesDisabled)
            UserProfilePreviewActivity.reviews = AppResult.Failure(AppError.CustomServicesDisabled)
        }) { scenario ->
            content(scenario)
            TestUi.settle(250)
            assertEquals(1, states().filterIsInstance<UserProfileUiState.Content>().size)
            scenario.onActivity { assertEquals(listOf(ProfileItem.Header::class, ProfileItem.Facts::class), it.items().map { item -> item::class }) }
            frame(scenario, "disabled-${spec.name}")
            TestUi.settle(250)
            assertEquals(1, states().filterIsInstance<UserProfileUiState.Content>().size)
        }
    }

    @Test fun backendFallbackDistinguishesMissingPersonFromFailure() = appearances { spec ->
        for (error in listOf(AppError.Network, AppError.NotFound)) {
            preview(spec, {
                UserProfilePreviewActivity.person = AppResult.Failure(error)
                UserProfilePreviewActivity.social = AppResult.Success(friend())
            }) { scenario ->
                content(scenario)
                scenario.onActivity {
                    assertEquals(BACKEND_NAME, it.findViewById<TextView>(R.id.name).text.toString())
                    assertEquals("M3234", it.holder<ProfileItem.Facts>().findViewById<TextView>(R.id.fact_title).text.toString())
                }
                if (error == AppError.Network) snackbar(scenario) else noSnackbar(scenario)
                frame(scenario, "backend-${if (error == AppError.Network) "error" else "missing"}-${spec.name}")
            }
        }
    }

    @Test fun personWithoutFactsHasOnlyAHeader() = appearances { spec ->
        preview(spec, { UserProfilePreviewActivity.person = AppResult.Success(teacher().copy(positions = emptyList(), rooms = emptyList())) }) { scenario ->
            content(scenario)
            scenario.onActivity {
                assertEquals(1, it.items().size)
                assertTrue(it.items().single() is ProfileItem.Header)
                assertNull(it.findViewById<View>(R.id.facts))
            }
            frame(scenario, "header-only-${spec.name}")
        }
    }

    @Test fun missingProfileHasNoDescriptionOrAction() = appearances { spec ->
        preview(spec) { scenario ->
            state(scenario, "Профиль не найден")
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.state_description).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.state_action).visibility)
            }
            frame(scenario, "not-found-${spec.name}")
        }
    }

    @Test fun fullErrorRetriesIntoThePersonPage() = appearances { spec ->
        preview(spec, { UserProfilePreviewActivity.person = AppResult.Failure(AppError.Network) }) { scenario ->
            state(scenario, "Не удалось загрузить")
            scenario.onActivity { assertEquals("Повторить", it.findViewById<TextView>(R.id.state_action).text.toString()) }
            frame(scenario, "error-${spec.name}")
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            scenario.onActivity { it.findViewById<View>(R.id.state_action).performClick() }
            content(scenario)
            frame(scenario, "retry-${spec.name}")
        }
    }

    @Test fun noReadyIdentityKeepsTheSkeletonBeyondFourSeconds() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.personDelayMs = 60_000
        }) { scenario ->
            loading(scenario)
            frame(scenario, "loading-first-${spec.name}")
            TestUi.settle(4_000)
            loading(scenario)
            assertTrue(states().none { it is UserProfileUiState.Content })
            frame(scenario, "loading-after-deadline-${spec.name}")
        }
    }

    @Test fun staggeredRepliesBeforeTheDeadlinePublishOneCompletePage() = appearances { spec ->
        preview(spec, {
            friendFixture()
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.cachedSocial = friend()
            UserProfilePreviewActivity.personDelayMs = 1_500
            UserProfilePreviewActivity.reviewsDelayMs = 2_000
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews()))
        }) { scenario ->
            loading(scenario)
            TestUi.settle(400)
            loading(scenario)
            assertTrue(states().none { it is UserProfileUiState.Content })
            frame(scenario, "staggered-loading-${spec.name}")
            content(scenario, attempts = 100)
            TestUi.settle(150)
            assertEquals(listOf(UserProfileUiState.Loading::class, UserProfileUiState.Content::class), states().map { it::class }.distinct())
            val page = states().filterIsInstance<UserProfileUiState.Content>().single()
            assertEquals(LONG_NAME, page.name)
            assertNotNull(page.social)
            assertEquals(3, page.reviews?.items?.size)
            frame(scenario, "staggered-content-${spec.name}")
        }
    }

    @Test fun reviewFailureKeepsTheIdentityAndOffersRetry() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Failure(AppError.Network)
        }) { scenario ->
            content(scenario)
            snackbar(scenario)
            scenario.onActivity { assertTrue(it.items().none { item -> item is ProfileItem.Review }) }
            frame(scenario, "reviews-error-${spec.name}")
        }
    }

    @Test fun fifteenReviewsRecycleEveryOptionalFieldAndRestoreScroll() = appearances { spec ->
        val reviews = (0 until 15).map { index ->
            copiedReview("review-$index", if (index % 2 == 0) "Предмет $index — $LONG_SUBJECT" else null,
                when (index % 3) { 0 -> ReviewDate.Month(YearMonth.of(2025, 1)); 1 -> ReviewDate.BeforeYear(2023); else -> null },
                if (index % 2 == 0) "Очень длинное название источника отзывов студентов университета ИТМО" else null,
                "https://example.org/reviews/$index", "Отзыв $index. " + LONG_REVIEW)
        }
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(reviews))
        }) { scenario ->
            content(scenario)
            reviews.forEachIndexed { index, review -> assertReview(scenario, index, review) }
            frame(scenario, "many-reviews-end-${spec.name}")
            reviews.indices.reversed().forEach { assertReview(scenario, it, reviews[it]) }
            frame(scenario, "many-reviews-back-${spec.name}")
            var before: Pair<Int, Int>? = null
            scenario.onActivity {
                val position = it.items().indexOfFirst { item -> item is ProfileItem.Review && item.review.id == "review-7" }
                (it.list().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, -23)
            }
            TestUi.settle(100)
            scenario.onActivity { before = it.scrollAnchor() }
            scenario.recreate()
            content(scenario)
            TestUi.eventually { scenario.onActivity { assertEquals(before, it.scrollAnchor()) } }
            frame(scenario, "many-reviews-recreated-${spec.name}")
        }
    }

    @Test fun failedPhotoShowsTheCurrentInitials() = appearances { spec ->
        preview(spec, { UserProfilePreviewActivity.person = AppResult.Success(teacher().copy(photoUrl = "https://invalid.test/p.jpg")) }) { scenario ->
            content(scenario)
            TestUi.eventually(attempts = 200) {
                scenario.onActivity {
                    val initials = it.findViewById<TextView>(R.id.avatar_text)
                    assertTrue(initials.isShown)
                    assertEquals("АК", initials.text.toString())
                    assertEquals(View.GONE, it.findViewById<View>(R.id.avatar_image).visibility)
                }
            }
            frame(scenario, "photo-fallback-${spec.name}")
        }
    }

    @Test fun recreatingTeacherPageDoesNotShowASkeleton() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews()))
        }) { scenario ->
            content(scenario)
            val previous = states().filterIsInstance<UserProfileUiState.Content>().last()
            val stateCount = states().size
            scenario.recreate()
            scenario.onActivity { assertFalse(it.findViewById<View>(R.id.loading).isShown) }
            content(scenario)
            assertTrue(states().drop(stateCount).none { it is UserProfileUiState.Loading })
            assertEquals(previous, states().last())
            frame(scenario, "teacher-recreated-${spec.name}")
        }
    }

    @Test fun lateReviewsAppendWithoutMovingTheNameOrFacts() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews()))
            UserProfilePreviewActivity.reviewsDelayMs = 6_000
        }) { scenario ->
            content(scenario, attempts = 200)
            var positions: Pair<Int, Int>? = null
            scenario.onActivity {
                assertTrue(it.items().none { item -> item is ProfileItem.Section })
                positions = it.identityPositions()
            }
            frame(scenario, "late-reviews-before-${spec.name}")
            TestUi.eventually(attempts = 200) {
                scenario.onActivity {
                    assertEquals(3, it.items().count { item -> item is ProfileItem.Review })
                    assertEquals(positions, it.identityPositions())
                }
            }
            noSnackbar(scenario)
            frame(scenario, "late-reviews-after-${spec.name}")
        }
    }

    @Test fun lateSocialBlockStaysDeferredUntilExplicitRetry() = appearances { spec ->
        preview(spec, {
            friendFixture()
            UserProfilePreviewActivity.socialDelayMs = 6_000
        }) { scenario ->
            content(scenario, attempts = 200)
            var top = 0
            scenario.onActivity {
                assertTrue(it.items().none { item -> item is ProfileItem.Sharing || item is ProfileItem.Relationship })
                top = it.holder<ProfileItem.Facts>().screenTop()
            }
            frame(scenario, "late-social-before-${spec.name}")
            snackbar(scenario, attempts = 200)
            scenario.onActivity {
                assertNull(it.findViewById<View>(R.id.friends_row))
                assertNull(it.findViewById<View>(R.id.primary_action))
                assertEquals(top, it.holder<ProfileItem.Facts>().screenTop())
            }
            frame(scenario, "late-social-deferred-${spec.name}")
            UserProfilePreviewActivity.socialDelayMs = 0
            scenario.onActivity { it.findViewById<View>(MaterialR.id.snackbar_action).performClick() }
            TestUi.eventually {
                scenario.onActivity {
                    assertTrue(it.findViewById<View>(R.id.friends_row)?.isShown == true)
                    assertEquals("Удалить из друзей", it.findViewById<TextView>(R.id.primary_action).text.toString())
                }
            }
            TestUi.eventually { scenario.onActivity { assertTrue(it.findViewById<View>(MaterialR.id.snackbar_text)?.isShown != true) } }
            frame(scenario, "late-social-retried-${spec.name}")
        }
    }

    private fun appearances(block: (Appearances.Spec) -> Unit) = Appearances.default.forEach(block)

    private fun preview(
        spec: Appearances.Spec,
        configure: () -> Unit = {},
        block: (ActivityScenario<UserProfilePreviewActivity>) -> Unit
    ) {
        reset()
        UserProfilePreviewActivity.appearance = spec.toUserProfile()
        configure()
        try {
            ActivityScenario.launch(UserProfilePreviewActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val config = activity.findViewById<View>(R.id.user_profile_test_container).resources.configuration
                    assertEquals("Effective font scale for ${spec.name}", spec.fontScale, config.fontScale, 0.001f)
                    assertEquals("Effective night mode for ${spec.name}",
                        if (spec.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
                        config.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                    val primary = activity.color.primary
                    if (spec.colorSeed == null) defaultPrimary[spec.dark] = primary
                    else assertNotEquals("The seeded palette must differ from the ordinary ${spec.name} theme",
                        checkNotNull(defaultPrimary[spec.dark]), primary)
                }
                block(scenario)
            }
        } finally {
            reset()
        }
    }

    private fun reset() {
        UserProfilePreviewActivity.appearance = UserProfilePreviewActivity.Appearance()
        UserProfilePreviewActivity.person = AppResult.Failure(AppError.NotFound)
        UserProfilePreviewActivity.social = AppResult.Failure(AppError.NotFound)
        UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(emptyList()))
        UserProfilePreviewActivity.cachedPerson = null
        UserProfilePreviewActivity.cachedSocial = null
        UserProfilePreviewActivity.cachedReviews = null
        UserProfilePreviewActivity.personDelayMs = 0
        UserProfilePreviewActivity.socialDelayMs = 0
        UserProfilePreviewActivity.reviewsDelayMs = 0
        UserProfilePreviewActivity.selfIsu = 0
        UserProfilePreviewActivity.states.clear()
        UserProfilePreviewActivity.openedScreens.clear()
    }

    private fun content(scenario: ActivityScenario<UserProfilePreviewActivity>, attempts: Int = 40) = TestUi.eventually(attempts = attempts) {
        scenario.onActivity {
            assertTrue(it.list().isShown)
            assertTrue(it.items().firstOrNull() is ProfileItem.Header)
            assertFalse(it.findViewById<View>(R.id.loading).isShown)
        }
    }

    private fun loading(scenario: ActivityScenario<UserProfilePreviewActivity>) = TestUi.eventually {
        scenario.onActivity {
            assertTrue(it.findViewById<View>(R.id.loading).isShown)
            assertFalse(it.list().isShown)
        }
    }

    private fun state(scenario: ActivityScenario<UserProfilePreviewActivity>, title: String) = TestUi.eventually {
        scenario.onActivity {
            assertTrue(it.findViewById<View>(R.id.state_container).isShown)
            assertEquals(title, it.findViewById<TextView>(R.id.state_title).text.toString())
            assertFalse(it.list().isShown)
        }
    }

    private fun snackbar(scenario: ActivityScenario<UserProfilePreviewActivity>, attempts: Int = 40) {
        TestUi.eventually(attempts = attempts) {
            scenario.onActivity {
                val text = it.findViewById<TextView>(MaterialR.id.snackbar_text)
                assertTrue(text?.isShown == true)
                assertEquals("Часть данных не загрузилась", text.text.toString())
            }
        }
        TestUi.settle(350)
        TestUi.eventually {
            scenario.onActivity {
                val text = it.findViewById<TextView>(MaterialR.id.snackbar_text)
                assertTrue(text.isShown)
                assertEquals(1f, text.alpha, 0.001f)
                val content = text.parent as View
                val container = content.parent as View
                assertEquals(1f, content.alpha, 0.001f)
                assertEquals(1f, container.alpha, 0.001f)
                assertEquals(0f, container.translationY, 0.001f)
            }
        }
    }

    private fun noSnackbar(scenario: ActivityScenario<UserProfilePreviewActivity>) {
        repeat(5) {
            TestUi.settle(50)
            scenario.onActivity { assertTrue(it.findViewById<TextView>(MaterialR.id.snackbar_text)?.isShown != true) }
        }
    }

    private fun frame(scenario: ActivityScenario<UserProfilePreviewActivity>, name: String) {
        TestUi.settle(if (Screenshots.enabled) 500 else 80)
        lateinit var activity: UserProfilePreviewActivity
        scenario.onActivity {
            activity = it
            val appearance = UserProfilePreviewActivity.appearance
            val container = it.findViewById<View>(R.id.user_profile_test_container)
            assertEquals(appearance.fontScale, container.resources.configuration.fontScale, 0.001f)
            assertEquals(if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
                container.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
            if (appearance.widthDp > 0) {
                assertEquals((appearance.widthDp * container.resources.displayMetrics.density).toInt(), container.width)
            }
            ViewChecks.assertTextFits(it.window.decorView)
            ViewChecks.assertTouchTargets(it.window.decorView)
        }
        TestUi.awaitFrameCommit(activity)
        Screenshots.capture("profile-screenshots", name, Screenshots.Location.FILES)
    }

    private inline fun <reified T : ProfileItem> scrollTo(scenario: ActivityScenario<UserProfilePreviewActivity>) {
        scenario.onActivity {
            val position = it.items().indexOfFirst { item -> item is T }
            assertTrue(position >= 0)
            (it.list().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
        }
        TestUi.settle(80)
    }

    private fun assertReview(scenario: ActivityScenario<UserProfilePreviewActivity>, index: Int, review: TeacherReview) {
        var position = 0
        scenario.onActivity {
            position = it.items().indexOfFirst { item -> item is ProfileItem.Review } + index
            (it.list().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
        }
        TestUi.settle(60)
        scenario.onActivity { activity ->
            val row = checkNotNull(activity.list().findViewHolderForAdapterPosition(position)).itemView
            val subject = row.findViewById<TextView>(R.id.subject)
            val date = row.findViewById<TextView>(R.id.date)
            val source = row.findViewById<TextView>(R.id.source)
            assertEquals(if (review.subject == null) View.GONE else View.VISIBLE, subject.visibility)
            assertEquals(review.subject.orEmpty(), subject.text.toString())
            assertEquals(if (review.written == null) View.GONE else View.VISIBLE, date.visibility)
            assertEquals(when (review.written) {
                is ReviewDate.Month -> "Январь 2025"
                is ReviewDate.BeforeYear -> "До 2023"
                null -> ""
            }, date.text.toString())
            val origin = review.origin as ReviewOrigin.Reviews
            assertEquals(origin.sourceTitle?.let { "Reviews · $it" } ?: "Reviews", source.text.toString())
            assertEquals(review.text, row.findViewById<TextView>(R.id.text).text.toString())
            assertTrue(source.height >= 48 * source.resources.displayMetrics.density)
            ViewChecks.assertTextFits(activity.window.decorView)
            ViewChecks.assertTouchTargets(activity.window.decorView)
        }
    }

    private fun UserProfilePreviewActivity.list(): RecyclerView = findViewById(R.id.profile_list)
    private fun UserProfilePreviewActivity.items(): List<ProfileItem> = (list().adapter as UserProfileAdapter).currentList
    private inline fun <reified T : ProfileItem> UserProfilePreviewActivity.holder(): View =
        checkNotNull(list().findViewHolderForAdapterPosition(items().indexOfFirst { it is T })).itemView
    private fun View.screenTop(): Int = IntArray(2).also(::getLocationOnScreen)[1]
    private fun UserProfilePreviewActivity.identityPositions(): Pair<Int, Int> =
        findViewById<View>(R.id.name).screenTop() to holder<ProfileItem.Facts>().screenTop()
    private fun UserProfilePreviewActivity.scrollAnchor(): Pair<Int, Int> {
        val manager = list().layoutManager as LinearLayoutManager
        val position = manager.findFirstVisibleItemPosition()
        return position to manager.getDecoratedTop(checkNotNull(manager.findViewByPosition(position)))
    }
    private fun states(): List<UserProfileUiState> = synchronized(UserProfilePreviewActivity.states) { UserProfilePreviewActivity.states.toList() }

    private fun friendFixture() {
        UserProfilePreviewActivity.person = AppResult.Success(teacher().copy(positions = emptyList(), rooms = emptyList(),
            education = listOf(PersonEducation("M3234", 2, "ФИТиП"))))
        UserProfilePreviewActivity.social = AppResult.Success(friend())
    }

    private fun friend() = UserProfile(UserSummary(ISU, BACKEND_NAME, null, listOf(UserGroup("M3234", 2, "ФИТиП")),
        UserSharing(schedule = true, sport = true, friends = true)), RelationshipState.FRIENDS)
    private fun teacher() = Person(ISU, LONG_NAME, null,
        listOf(PersonPosition("Доцент", LONG_DEPARTMENT), PersonPosition(null, UNTITLED_DEPARTMENT)),
        listOf(PersonRoom("405", "Кронверкский проспект, 49")), emptyList())
    private fun teacherReviews() = listOf(
        copiedReview("review-one", LONG_SUBJECT, ReviewDate.Month(YearMonth.of(2025, 1)), "Отзывы ПИ", "https://example.org/reviews/1", "Понятно объясняет материал и подробно отвечает на вопросы."),
        copiedReview("review-two", null, ReviewDate.BeforeYear(2023), null, "https://example.org/reviews/2", "На занятиях было интересно."),
        copiedReview("review-three", null, null, null, "https://example.org/reviews/3", LONG_REVIEW)
    )
    private fun copiedReview(id: String, subject: String?, written: ReviewDate?, sourceTitle: String?, sourceUrl: String, text: String) =
        TeacherReview(id, subject, written, text, score = 0, myVote = 0, origin = ReviewOrigin.Reviews(sourceTitle, sourceUrl))
    private fun reviewsOf(reviews: List<TeacherReview>) = TeacherReviews(ISU, reviews, mine = null, canWrite = false,
        canVote = false, canReport = false, knownTeacher = false)

    private companion object {
        const val BACKEND_NAME = "Соколов Артём Игоревич"
        const val LONG_DEPARTMENT = "Факультет информационных технологий и программирования, кафедра прикладной математики и теоретической информатики"
        const val UNTITLED_DEPARTMENT = "Институт международного развития и партнёрства"
        const val LONG_SUBJECT = "Математические методы моделирования сложных информационных систем"
        val LONG_REVIEW = "Преподаватель последовательно объясняет сложные темы, разбирает примеры и отвечает на вопросы студентов. " +
            "На практических занятиях можно обсудить разные подходы к решению задачи и понять, почему один из них лучше подходит.\n\n" +
            "Материалы помогают подготовиться к контрольной работе. Особенно полезны подробные комментарии к решениям: " +
            "после обсуждения становится понятно, какие шаги стоит проверить ещё раз и как избежать похожих ошибок в дальнейшем."
    }
}
