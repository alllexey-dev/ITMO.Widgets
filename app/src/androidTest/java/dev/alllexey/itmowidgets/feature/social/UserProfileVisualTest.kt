package dev.alllexey.itmowidgets.feature.social

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.R as MaterialR
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.SummaryConfidence
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.core.ui.AvatarView
import dev.alllexey.itmowidgets.core.ui.GroupPosition
import dev.alllexey.itmowidgets.core.ui.TeacherLevelTone
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.tone
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.shortPersonName
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonEducation
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonPosition
import dev.alllexey.itmowidgets.feature.social.domain.model.PersonRoom
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFactKind
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import dev.alllexey.itmowidgets.feature.social.ui.ProfileItem
import dev.alllexey.itmowidgets.feature.social.ui.UserProfileAdapter
import dev.alllexey.itmowidgets.feature.social.ui.UserProfilePreviewActivity
import dev.alllexey.itmowidgets.feature.social.ui.UserProfilePreviewActivity.Companion.ISU
import dev.alllexey.itmowidgets.feature.social.ui.UserProfilePreviewActivity.Companion.LONG_NAME
import dev.alllexey.itmowidgets.feature.social.ui.UserProfilePreviewActivity.Companion.sampleSummary
import dev.alllexey.itmowidgets.feature.social.ui.text
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.toUserProfile
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import java.io.File
import kotlinx.datetime.YearMonth
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserProfileVisualTest {
    private val defaultPrimary = mutableMapOf<Boolean, Int>()

    @Test fun teacherHasAHeroGroupedFactsAndCompleteReviews() = appearances { spec ->
        preview(spec, configure = {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews()))
        }) { scenario ->
            content(scenario)
            scenario.onActivity { activity ->
                val items = activity.items()
                assertEquals(3, items.count { it is ProfileItem.Review })
                assertTrue(items.none { it is ProfileItem.Sharing })
                assertNull(activity.findViewById<View>(R.id.friends_row))
                assertFalse(activity.findViewById<View>(R.id.primary_action).isShown)
                assertFalse(activity.findViewById<View>(R.id.badge).isShown)
                assertEquals(LONG_NAME, activity.findViewById<TextView>(R.id.name).text.toString())
                // One short line: the role without its department, which stays under «Должности».
                assertEquals("Доцент", activity.findViewById<TextView>(R.id.headline).text.toString())
                assertTrue(activity.findViewById<View>(R.id.name).isAccessibilityHeading)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    activity.findViewById<View>(R.id.avatar).importantForAccessibility)
                assertIsu(activity)
                assertEquals(listOf(ProfileFactKind.POSITION, ProfileFactKind.ROOM), activity.items().filterIsInstance<ProfileItem.Facts>().map { it.kind })
                val positions = activity.factsHolder(ProfileFactKind.POSITION)
                assertEquals("Должности", positions.findViewById<TextView>(R.id.heading).text.toString())
                val rows = positions.findViewById<LinearLayout>(R.id.facts).children.toList()
                assertEquals(2, rows.size)
                assertGroup(rows)
                assertEquals("Должность: Доцент", rows[0].findViewById<TextView>(R.id.fact_title).contentDescription)
                assertEquals(LONG_DEPARTMENT, rows[0].findViewById<TextView>(R.id.fact_subtitle).text.toString())
                assertEquals(View.GONE, rows[1].findViewById<View>(R.id.fact_subtitle).visibility)
                assertEquals(1, positions.descendants().filterIsInstance<TextView>().count { it.text.toString() == UNTITLED_DEPARTMENT })
                val rooms = activity.factsHolder(ProfileFactKind.ROOM)
                assertEquals("Где найти", rooms.findViewById<TextView>(R.id.heading).text.toString())
                assertEquals("Кронверкский проспект, 49", rooms.findViewById<TextView>(R.id.fact_subtitle).text.toString())
                assertTrue(activity.window.decorView.descendants().filterIsInstance<TextView>().none { "·" in it.text })
            }
            frame(scenario, "teacher-top-${spec.name}")
            scrollTo<ProfileItem.Section>(scenario)
            scenario.onActivity {
                val title = it.holder<ProfileItem.Section>().findViewById<TextView>(R.id.title)
                assertEquals("Отзывы", title.text.toString())
                assertEquals("3", it.holder<ProfileItem.Section>().findViewById<TextView>(R.id.count).text.toString())
                assertEquals("Отзывы, 3", title.contentDescription)
                assertTrue(title.isAccessibilityHeading)
                assertFalse(it.holder<ProfileItem.Section>().findViewById<View>(R.id.action).isShown)
            }
            teacherReviews().forEach { review -> assertReview(scenario, review) }
            frame(scenario, "teacher-bottom-${spec.name}")
        }
    }

    @Test fun friendHasABadgeAndTheWidgetsGroupAboveStudy() = appearances { spec ->
        preview(spec, ::friendFixture) { scenario ->
            content(scenario)
            scenario.onActivity { activity ->
                assertEquals("M3234, 2 курс", activity.findViewById<TextView>(R.id.headline).text.toString())
                assertEquals("в друзьях", activity.findViewById<TextView>(R.id.badge).text.toString())
                assertFalse(activity.findViewById<View>(R.id.actions).isShown)
                assertIsu(activity)
                val sharing = activity.holder<ProfileItem.Sharing>()
                assertEquals("ITMO.Widgets", sharing.findViewById<TextView>(R.id.heading).text.toString())
                val rows = listOf(R.id.friends_row, R.id.schedule_row, R.id.sport_row).map { activity.findViewById<View>(it) }
                rows.forEach {
                    assertTrue(it.isClickable)
                    assertTrue(it.isEnabled)
                }
                assertGroup(rows)
                val remove = activity.findViewById<TextView>(R.id.remove_friend)
                assertEquals("Удалить из друзей", remove.text.toString())
                assertTrue(remove.isShown)
                assertFalse(activity.findViewById<View>(R.id.hidden_hint).isShown)
                val items = activity.items()
                assertTrue(items.indexOfFirst { it is ProfileItem.Sharing } <
                    items.indexOfFirst { it is ProfileItem.Facts && it.kind == ProfileFactKind.EDUCATION })
                assertEquals("2 курс, ФИТиП", activity.factsHolder(ProfileFactKind.EDUCATION)
                    .findViewById<TextView>(R.id.fact_subtitle).text.toString())
                assertTrue(items.none { it is ProfileItem.Section })
            }
            frame(scenario, "friend-${spec.name}")
        }
    }

    @Test fun isuNumberCopiesOnATap() {
        preview(Appearances.light, { UserProfilePreviewActivity.person = AppResult.Success(teacher()) }) { scenario ->
            content(scenario)
            scenario.onActivity { assertIsu(it, copy = true) }
        }
    }

    @Test fun everyFriendshipStateHasItsLineAndButtons() = appearances { spec ->
        val cases = listOf(
            RelationshipState.NONE to (null to listOf("Добавить в друзья")),
            RelationshipState.OUTGOING to ("Заявка отправлена" to listOf("Отменить заявку")),
            RelationshipState.INCOMING to ("Хочет добавить вас" to listOf("Принять заявку", "Отклонить")),
            RelationshipState.BLOCKED to (null to emptyList()),
        )
        for ((relationship, expected) in cases) {
            preview(spec, {
                friendFixture()
                UserProfilePreviewActivity.social = AppResult.Success(friend().copy(relationship = relationship,
                    user = friend().user.copy(sharing = UserSharing(schedule = false, sport = false, friends = false))))
            }) { scenario ->
                content(scenario)
                scenario.onActivity { activity ->
                    val status = activity.findViewById<TextView>(R.id.relationship_status)
                    assertEquals(expected.first, status.text?.toString()?.takeIf { status.isShown })
                    val shown = listOf(R.id.primary_action, R.id.secondary_action).map { activity.findViewById<TextView>(it) }
                        .filter { it.isShown }
                    assertEquals(relationship.name, expected.second, shown.map { it.text.toString() })
                    // Two labels that do not fit side by side stack instead of breaking inside a word.
                    shown.forEach { assertEquals(it.text.toString(), 1, it.lineCount) }
                    assertFalse(activity.findViewById<View>(R.id.badge).isShown)
                    assertFalse(activity.findViewById<View>(R.id.remove_friend).isShown)
                    // Closed entries keep their surface, say why and are no targets.
                    assertFalse(activity.findViewById<View>(R.id.schedule_row).isClickable)
                    assertTrue(activity.findViewById<View>(R.id.hidden_hint).isShown)
                }
                frame(scenario, "friendship-${relationship.name.lowercase()}-${spec.name}")
            }
        }
    }

    @Test fun selfProfileHasNoRelationshipButtons() = appearances { spec ->
        preview(spec, {
            friendFixture()
            UserProfilePreviewActivity.selfIsu = ISU
        }) { scenario ->
            content(scenario)
            scenario.onActivity {
                assertEquals("это вы", it.findViewById<TextView>(R.id.badge).text.toString())
                assertFalse(it.findViewById<View>(R.id.relationship_status).isShown)
                assertFalse(it.findViewById<View>(R.id.primary_action).isShown)
                assertFalse(it.findViewById<View>(R.id.secondary_action).isShown)
                assertFalse(it.findViewById<View>(R.id.remove_friend).isShown)
            }
            frame(scenario, "self-${spec.name}")
        }
    }

    @Test fun shareButtonOnlyWithAPage() {
        val spec = Appearances.light
        listOf<Pair<String, () -> Unit>>(
            "other" to { UserProfilePreviewActivity.person = AppResult.Success(teacher()) },
            "self" to {
                friendFixture()
                UserProfilePreviewActivity.selfIsu = ISU
            }
        ).forEach { (name, configure) ->
            preview(spec, configure) { scenario ->
                content(scenario)
                scenario.onActivity { assertShareButton(it, shown = true) }
                frame(scenario, "share-$name-${spec.name}")
            }
        }
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.personDelayMs = 60_000
        }) { scenario ->
            loading(scenario)
            scenario.onActivity { assertShareButton(it, shown = false) }
        }
        preview(spec, { UserProfilePreviewActivity.person = AppResult.Failure(AppError.Network) }) { scenario ->
            state(scenario, "Не удалось загрузить")
            scenario.onActivity { assertShareButton(it, shown = false) }
        }
        preview(spec) { scenario ->
            state(scenario, "Профиль не найден")
            scenario.onActivity { assertShareButton(it, shown = false) }
        }
    }

    private fun assertShareButton(activity: UserProfilePreviewActivity, shown: Boolean) {
        val share = activity.findViewById<View>(R.id.share_button)
        assertEquals(shown, share.isShown)
        if (!shown) return
        val target = 48 * activity.resources.displayMetrics.density - 1
        assertTrue("Share ${share.width}x${share.height}", share.width >= target && share.height >= target)
        assertEquals("Поделиться", share.contentDescription)
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
            scenario.onActivity {
                assertEquals(listOf(ProfileItem.Header::class, ProfileItem.Facts::class, ProfileItem.Facts::class),
                    it.items().map { item -> item::class })
            }
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
                    assertEquals("M3234, 2 курс", it.findViewById<TextView>(R.id.headline).text.toString())
                    assertEquals("M3234", it.holder<ProfileItem.Facts>().findViewById<TextView>(R.id.fact_title).text.toString())
                }
                if (error == AppError.Network) snackbar(scenario) else noSnackbar(scenario)
                frame(scenario, "backend-${if (error == AppError.Network) "error" else "missing"}-${spec.name}")
            }
        }
    }

    @Test fun personWithoutFactsHasOnlyTheHeroWithTheIsu() = appearances { spec ->
        preview(spec, { UserProfilePreviewActivity.person = AppResult.Success(teacher().copy(positions = emptyList(), rooms = emptyList())) }) { scenario ->
            content(scenario)
            scenario.onActivity {
                assertEquals(listOf(ProfileItem.Header::class), it.items().map { item -> item::class })
                assertEquals(View.GONE, it.findViewById<View>(R.id.headline).visibility)
                assertIsu(it)
            }
            frame(scenario, "isu-only-${spec.name}")
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
            // Both replies come before the 3 s deadline but late enough for a cold, dark launch to show the skeleton.
            UserProfilePreviewActivity.personDelayMs = 2_000
            UserProfilePreviewActivity.reviewsDelayMs = 2_400
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews()))
        }) { scenario ->
            loading(scenario)
            TestUi.settle(300)
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

    @Test fun twentyMixedReviewsRecycleEveryOptionalFieldAndRestoreScroll() = appearances { spec ->
        val reviews = (0 until 20).map { index ->
            val subject = if (index % 2 == 0) "Предмет $index — $LONG_SUBJECT" else null
            val written = when (index % 3) { 0 -> ReviewDate.Month(YearMonth(2025, 1)); 1 -> ReviewDate.BeforeYear(2023); else -> null }
            when (index % 4) {
                0 -> copiedReview("review-$index", subject, written,
                    if (index % 8 == 0) "Очень длинное название источника отзывов студентов университета ИТМО" else null,
                    "https://example.org/reviews/$index", "Отзыв $index. " + LONG_REVIEW)
                1 -> communityReview("review-$index", subject, written, "Отзыв $index. На занятиях было интересно.", author = AUTHOR,
                    verified = index % 8 == 1, score = index)
                2 -> communityReview("review-$index", subject, written, "Отзыв $index. $LONG_REVIEW", verified = true, reportedByMe = true)
                else -> communityReview("review-$index", subject, written, "Отзыв $index. Всё понятно.", score = -1, myVote = -1)
            }
        }
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(reviews, canVote = true, canReport = true))
        }) { scenario ->
            content(scenario)
            reviews.forEach { assertReview(scenario, it, canVote = true, canReport = true) }
            frame(scenario, "many-reviews-end-${spec.name}")
            reviews.reversed().forEach { assertReview(scenario, it, canVote = true, canReport = true) }
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

    @Test fun mixedReviewsShowTheOwnReviewFirstAndEachOrigin() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews())
        }) { scenario ->
            content(scenario)
            scenario.onActivity { activity ->
                val reviews = activity.items().dropWhile { it !is ProfileItem.Section }
                val section = reviews.first() as ProfileItem.Section
                assertEquals(4, section.count)
                assertEquals(null, section.actionRes)
                assertTrue(reviews[1] is ProfileItem.OwnReview)
                assertEquals(listOf("named", "anonymous", "copy"), reviews.drop(2).map { (it as ProfileItem.Review).review.id })
            }
            frame(scenario, "mixed-top-${spec.name}")
            scrollTo<ProfileItem.Section>(scenario)
            scenario.onActivity { activity ->
                assertEquals("4", activity.holder<ProfileItem.Section>().findViewById<TextView>(R.id.count).text.toString())
                val own = activity.holder<ProfileItem.OwnReview>()
                assertEquals("мой", own.findViewById<TextView>(R.id.own_badge).text.toString())
                assertEquals("На проверке", own.findViewById<TextView>(R.id.status).text.toString())
                assertEquals("Математический анализ, анонимно", own.findViewById<TextView>(R.id.meta).text.toString())
                assertFalse(own.findViewById<View>(R.id.vote_up).isShown)
                assertFalse(own.findViewById<View>(R.id.score).isShown)
                assertFalse(own.findViewById<View>(R.id.verified).isShown)
                assertTrue(own.findViewById<View>(R.id.more).isShown)
                assertEquals("Действия с отзывом", own.findViewById<View>(R.id.more).contentDescription)
            }
            frame(scenario, "mixed-reviews-${spec.name}")
            mixedReviews().reviews.forEach { assertReview(scenario, it, canVote = true, canReport = true) }
            frame(scenario, "mixed-bottom-${spec.name}")
        }
    }

    @Test fun ownReviewIsAGroupOfItsOwnAboveTheOthers() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews())
        }) { scenario ->
            content(scenario)
            scrollTo<ProfileItem.Section>(scenario)
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                val items = activity.items()
                assertEquals(GroupPosition.SINGLE, items.filterIsInstance<ProfileItem.OwnReview>().single().position)
                assertEquals(listOf(GroupPosition.FIRST, GroupPosition.MIDDLE, GroupPosition.LAST),
                    items.filterIsInstance<ProfileItem.Review>().map { it.position })
                val own = activity.holder<ProfileItem.OwnReview>()
                val corners = ((own.background as android.graphics.drawable.RippleDrawable).getDrawable(0)
                    as com.google.android.material.shape.MaterialShapeDrawable).shapeAppearanceModel
                val bounds = android.graphics.RectF(0f, 0f, 100f, 100f)
                assertEquals(20 * density, corners.topLeftCornerSize.getCornerSize(bounds), 0.5f)
                assertEquals(20 * density, corners.bottomLeftCornerSize.getCornerSize(bounds), 0.5f)
                val next = activity.reviewRow("named")
                assertEquals(activity.resources.getDimensionPixelSize(R.dimen.design_spacing_group), next.screenTop() - own.screenTop() - own.height)
                // A pending review ends with its text and the content padding, not glued to the edge.
                assertFalse(own.findViewById<View>(R.id.footer).isShown)
                val text = own.findViewById<View>(R.id.text)
                assertEquals(activity.resources.getDimensionPixelSize(R.dimen.design_card_padding),
                    own.screenTop() + own.height - text.screenTop() - text.height)
            }
            frame(scenario, "own-group-${spec.name}")
        }
    }

    @Test fun footerStacksWhoWroteItOverTheVerificationWithTheVotesCentredBeside() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews(ownReview(OwnReviewStatus.PUBLISHED, verified = true)))
        }) { scenario ->
            content(scenario)
            for ((id, origin) in listOf("named" to R.id.author, "anonymous" to R.id.anonymous, "copy" to R.id.source)) {
                showReview(scenario, id)
                scenario.onActivity { activity ->
                    val density = activity.resources.displayMetrics.density
                    val row = activity.reviewRow(id)
                    // The top line is the caption alone; who wrote it opens the footer, its verification under it.
                    val who = row.findViewById<View>(origin)
                    assertTrue(id, who.isShown)
                    assertTrue(id, row.findViewById<View>(R.id.meta).screenTop() < row.findViewById<View>(R.id.text).screenTop())
                    assertTrue(id, who.screenTop() > row.findViewById<View>(R.id.text).screenTop())
                    // Its first line stays under the review text even when it wraps at a large font scale.
                    val text = row.findViewById<View>(R.id.text)
                    val firstLine = Rect().also { (who as TextView).getLineBounds(0, it) }
                    assertTrue(id, who.screenTop() + firstLine.top >= text.screenTop() + text.height)
                    val verification = listOf(R.id.verified, R.id.unverified).map { row.findViewById<View>(it) }.singleOrNull { it.isShown }
                    if (verification != null) {
                        assertTrue(id, verification.screenTop() > who.screenTop())
                        assertTrue(id, verification.screenLeft() <= who.screenLeft() + who.paddingLeft + density)
                    }
                    val votes = row.findViewById<View>(R.id.votes)
                    val credit = row.findViewById<View>(R.id.credit)
                    assertTrue(id, credit.screenLeft() + credit.width <= votes.screenLeft())
                    assertEquals(id, (credit.screenTop() + credit.height / 2).toFloat(), (votes.screenTop() + votes.height / 2).toFloat(), 2 * density)
                    assertClearOfTheBottom(row, verification ?: who)
                    ViewChecks.assertTextFits(activity.window.decorView, ellipsizable = ::isReviewMeta)
                    ViewChecks.assertTouchTargets(activity.window.decorView)
                }
                frame(scenario, "footer-$id-${spec.name}")
            }
            scrollTo<ProfileItem.OwnReview>(scenario)
            scenario.onActivity { activity ->
                val own = activity.holder<ProfileItem.OwnReview>()
                assertEquals("Вёл у вас", own.findViewById<TextView>(R.id.verified_text).text.toString())
                assertTrue(own.findViewById<View>(R.id.score).isShown)
                assertClearOfTheBottom(own, own.findViewById(R.id.verified))
            }
            frame(scenario, "footer-own-${spec.name}")
        }
    }

    /** Nothing ends flush with a row's edge: at least 12 dp under the last line. */
    private fun assertClearOfTheBottom(row: View, last: View) {
        val gap = row.screenTop() + row.height - (last.screenTop() + last.height - last.paddingBottom)
        assertTrue("Gap $gap under the last line", gap >= row.resources.getDimensionPixelSize(R.dimen.design_spacing_content) - 1)
    }

    @Test fun writeIsOfferedOnlyForTeachersWithoutAnOwnReview() = appearances { spec ->
        val lecturer = teacher()
        val student = teacher().copy(positions = emptyList())
        val cases = listOf(
            Triple("known", student to reviewsOf(emptyList(), canWrite = true, knownTeacher = true), true),
            Triple("position", lecturer to reviewsOf(emptyList(), canWrite = true), true),
            Triple("student", student to reviewsOf(emptyList(), canWrite = true), null),
            Triple("own", lecturer to reviewsOf(emptyList(), canWrite = true, knownTeacher = true, mine = ownReview(OwnReviewStatus.PUBLISHED)), false),
            Triple("closed", lecturer to reviewsOf(teacherReviews().take(1), canWrite = false, knownTeacher = true), false),
        )
        for ((name, fixture, write) in cases) {
            preview(spec, {
                UserProfilePreviewActivity.person = AppResult.Success(fixture.first)
                UserProfilePreviewActivity.reviews = AppResult.Success(fixture.second)
            }) { scenario ->
                content(scenario)
                // The taller hero and the fact groups can push the heading below the fold on a narrow screen.
                if (write != null) scrollTo<ProfileItem.Section>(scenario)
                scenario.onActivity { activity ->
                    val section = activity.items().filterIsInstance<ProfileItem.Section>().singleOrNull()
                    if (write == null) assertNull(name, section)
                    else {
                        assertEquals(name, write, section?.actionRes != null)
                        val action = activity.holder<ProfileItem.Section>().findViewById<TextView>(R.id.action)
                        assertEquals(name, write, action.isShown)
                        if (write) {
                            assertEquals("Написать", action.text.toString())
                            assertTrue(action.height >= activity.resources.getDimensionPixelSize(R.dimen.design_touch_target))
                            // «Написать» stands at the end of the heading row on the title's baseline.
                            val title = activity.holder<ProfileItem.Section>().findViewById<TextView>(R.id.title)
                            assertEquals((title.screenTop() + title.baseline).toFloat(), (action.screenTop() + action.baseline).toFloat(),
                                activity.resources.displayMetrics.density)
                            val section = activity.holder<ProfileItem.Section>()
                            assertEquals(section.screenLeft() + section.width, action.screenLeft() + action.width)
                        }
                    }
                }
                if (name == "known") {
                    frame(scenario, "write-${spec.name}")
                    scenario.onActivity { it.holder<ProfileItem.Section>().findViewById<View>(R.id.action).performClick() }
                    assertEquals(listOf(TeacherReviewArgs(ISU, LONG_NAME)), UserProfilePreviewActivity.openedEditors.toList())
                }
            }
        }
    }

    @Test fun arrowsVoteTakeTheVoteBackAndWaitForTheAnswer() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews())
        }) { scenario ->
            content(scenario)
            fun row(activity: UserProfilePreviewActivity) = activity.reviewRow("anonymous")
            fun assertVote(score: Int, vote: Int) = TestUi.eventually {
                scenario.onActivity { activity ->
                    val row = row(activity)
                    assertEquals(score.toString().replace('-', '−'), row.findViewById<TextView>(R.id.score).text.toString())
                    assertEquals("Рейтинг $score", row.findViewById<TextView>(R.id.score).contentDescription)
                    assertEquals(vote > 0, row.findViewById<View>(R.id.vote_up).isSelected)
                    assertEquals(vote < 0, row.findViewById<View>(R.id.vote_down).isSelected)
                    assertTrue(row.findViewById<View>(R.id.vote_up).isEnabled)
                }
            }
            showReview(scenario, "anonymous")
            assertVote(0, 0)
            scenario.onActivity { row(it).findViewById<View>(R.id.vote_up).performClick() }
            assertVote(1, 1)
            frame(scenario, "vote-up-${spec.name}")
            scenario.onActivity { row(it).findViewById<View>(R.id.vote_up).performClick() }
            assertVote(0, 0)

            UserProfilePreviewActivity.mutationDelayMs = 2_000
            scenario.onActivity { row(it).findViewById<View>(R.id.vote_down).performClick() }
            TestUi.eventually {
                scenario.onActivity {
                    assertFalse(row(it).findViewById<View>(R.id.vote_up).isEnabled)
                    assertFalse(row(it).findViewById<View>(R.id.vote_down).isEnabled)
                }
            }
            assertVote(-1, -1)

            UserProfilePreviewActivity.mutationDelayMs = 0
            UserProfilePreviewActivity.mutationError = AppError.Restricted
            scenario.onActivity { row(it).findViewById<View>(R.id.vote_up).performClick() }
            snackbar(scenario, text = "Действие ограничено модерацией")
            assertVote(-1, -1)
        }
    }

    @Test fun overflowReportsOnlyOthersReviewsOnce() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews())
        }) { scenario ->
            content(scenario)
            showReview(scenario, "copy")
            scenario.onActivity { assertFalse(it.reviewRow("copy").findViewById<View>(R.id.more).isShown) }
            showReview(scenario, "named")
            scenario.onActivity { it.reviewRow("named").findViewById<View>(R.id.more).performClick() }
            TestUi.settle(400)
            Screenshots.capture("profile-screenshots", "report-menu-${spec.name}", Screenshots.Location.FILES)
            onView(withText("Пожаловаться")).inRoot(isPlatformPopup()).perform(click())
            TestUi.eventually { assertEquals(listOf("named"), UserProfilePreviewActivity.openedReports.toList()) }
            scenario.onActivity { activity ->
                val reported = mixedReviews().let { fixture ->
                    fixture.copy(reviews = fixture.reviews.map {
                        if (it.id == "named") it.copy(origin = (it.origin as ReviewOrigin.Community).copy(reportedByMe = true)) else it
                    })
                }
                activity.publishReviews(reported)
            }
            TestUi.eventually { scenario.onActivity { assertFalse(it.reviewRow("named").findViewById<View>(R.id.more).isShown) } }
            frame(scenario, "reported-${spec.name}")
        }
    }

    @Test fun ownReviewStatesShowTheirPillReasonAndScore() = appearances { spec ->
        val cases = listOf(
            ownReview(OwnReviewStatus.PENDING),
            ownReview(OwnReviewStatus.REJECTED, note = LONG_REASON),
            ownReview(OwnReviewStatus.HIDDEN),
            ownReview(OwnReviewStatus.PUBLISHED, verified = true).copy(anonymous = false, score = 3),
            ownReview(OwnReviewStatus.PUBLISHED),
        )
        for (mine in cases) {
            preview(spec, {
                UserProfilePreviewActivity.person = AppResult.Success(teacher())
                UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews(mine))
            }) { scenario ->
                content(scenario)
                scrollTo<ProfileItem.Section>(scenario)
                scenario.onActivity { activity ->
                    val own = activity.holder<ProfileItem.OwnReview>()
                    val status = own.findViewById<TextView>(R.id.status)
                    val reason = own.findViewById<TextView>(R.id.reason)
                    val published = mine.status == OwnReviewStatus.PUBLISHED
                    assertEquals(when (mine.status) {
                        OwnReviewStatus.PENDING -> "На проверке"
                        OwnReviewStatus.REJECTED -> "Отклонён"
                        OwnReviewStatus.HIDDEN -> "Скрыт"
                        OwnReviewStatus.PUBLISHED -> ""
                    }, if (status.isShown) status.text.toString() else "")
                    assertEquals(if (mine.status == OwnReviewStatus.REJECTED) "Причина: $LONG_REASON" else null,
                        reason.text?.toString()?.takeIf { reason.isShown })
                    assertEquals(published && mine.verified, own.findViewById<View>(R.id.verified).isShown)
                    if (published && mine.verified) assertEquals("Вёл у вас", own.findViewById<TextView>(R.id.verified_text).text.toString())
                    assertEquals(published && !mine.verified, own.findViewById<View>(R.id.unverified).isShown)
                    assertEquals(published, own.findViewById<View>(R.id.score).isShown)
                    if (published) assertEquals("Рейтинг ${mine.score}", own.findViewById<View>(R.id.score).contentDescription)
                    assertEquals("Математический анализ, ${if (mine.anonymous) "анонимно" else "с вашим именем"}",
                        own.findViewById<TextView>(R.id.meta).text.toString())
                    assertTrue(own.findViewById<View>(R.id.own_badge).isShown)
                    assertFalse(own.findViewById<View>(R.id.vote_up).isShown)
                    val last = if (published) listOf(R.id.verified, R.id.unverified).map { own.findViewById<View>(it) }.single { it.isShown }
                        else own.findViewById(R.id.text)
                    assertClearOfTheBottom(own, last)
                }
                frame(scenario, "own-${mine.status.name.lowercase()}${if (mine.verified) "-verified" else ""}-${spec.name}")
            }
        }
    }

    @Test fun deletingTheOwnReviewAsksAndBringsBackWrite() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews(ownReview(OwnReviewStatus.PUBLISHED)))
        }) { scenario ->
            content(scenario)
            scrollTo<ProfileItem.Section>(scenario)
            scenario.onActivity { it.holder<ProfileItem.OwnReview>().findViewById<View>(R.id.more).performClick() }
            TestUi.settle(400)
            Screenshots.capture("profile-screenshots", "own-menu-${spec.name}", Screenshots.Location.FILES)
            onView(withText("Удалить")).inRoot(isPlatformPopup()).perform(click())
            onView(withText("Удалить отзыв?")).inRoot(isDialog()).check(matches(isDisplayed()))
            Screenshots.capture("profile-screenshots", "delete-confirm-${spec.name}", Screenshots.Location.FILES) { TestUi.settle(400) }
            onView(withText("Удалить")).inRoot(isDialog()).perform(click())
            TestUi.eventually {
                scenario.onActivity { activity ->
                    assertTrue(activity.items().none { it is ProfileItem.OwnReview })
                    assertEquals("Написать", activity.holder<ProfileItem.Section>().findViewById<TextView>(R.id.action).text.toString())
                    assertTrue(activity.holder<ProfileItem.Section>().findViewById<View>(R.id.action).isShown)
                }
            }
            frame(scenario, "deleted-${spec.name}")
        }
    }

    @Test fun authorNameOpensTheAuthorProfile() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews())
        }) { scenario ->
            content(scenario)
            showReview(scenario, "named")
            scenario.onActivity { it.reviewRow("named").findViewById<View>(R.id.author).performClick() }
            val opened = UserProfilePreviewActivity.openedScreens.single()
            assertEquals(AppScreen.USER_PROFILE, opened.first)
            assertEquals(AUTHOR.isu, opened.second?.getInt(UserScreenArgs.ISU))
        }
    }

    @Test fun savedReviewAppearsFirstWithoutMovingTheHeader() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews(mine = null))
        }) { scenario ->
            content(scenario)
            var positions: Pair<Int, Int>? = null
            scenario.onActivity {
                assertTrue(it.items().none { item -> item is ProfileItem.OwnReview })
                positions = it.identityPositions()
                it.publishReviews(mixedReviews(ownReview(OwnReviewStatus.PENDING)))
            }
            TestUi.eventually {
                scenario.onActivity { activity ->
                    val reviews = activity.items().dropWhile { it !is ProfileItem.Section }
                    assertTrue(reviews[1] is ProfileItem.OwnReview)
                    assertEquals(positions, activity.identityPositions())
                }
            }
            noSnackbar(scenario)
            frame(scenario, "saved-${spec.name}")
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

    @Test fun loadedPhotoReplacesTheInitialsAndARecycledAvatarShowsTheLatestUser() = appearances { spec ->
        val photo = localPhoto()
        preview(spec, { UserProfilePreviewActivity.person = AppResult.Success(teacher().copy(photoUrl = photo)) }) { scenario ->
            content(scenario)
            assertPhoto(scenario)
            frame(scenario, "photo-loaded-${spec.name}")

            // A failing request superseded by a loading one must not bring the initials back.
            scenario.onActivity {
                val avatar = it.findViewById<AvatarView>(R.id.avatar)
                avatar.setUser("Другой Человек", "https://invalid.test/p.jpg")
                avatar.setUser(LONG_NAME, photo)
            }
            TestUi.settle(300)
            assertPhoto(scenario)

            scenario.onActivity { it.findViewById<AvatarView>(R.id.avatar).setUser("Другой Человек", "https://invalid.test/p.jpg") }
            assertInitials(scenario, "ДЧ")
            scenario.onActivity { it.findViewById<AvatarView>(R.id.avatar).setUser("Без Фото", null) }
            assertInitials(scenario, "БФ")
            scenario.onActivity { assertNull(it.findViewById<ImageView>(R.id.avatar_image).drawable) }
        }
    }

    private fun localPhoto(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "avatar-photo.png")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(0x3F, 0x51, 0xB5)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file).toString()
    }

    private fun assertPhoto(scenario: ActivityScenario<UserProfilePreviewActivity>) = TestUi.eventually(attempts = 200) {
        scenario.onActivity {
            val image = it.findViewById<ImageView>(R.id.avatar_image)
            assertTrue(image.isShown)
            assertNotNull(image.drawable)
            assertEquals(View.GONE, it.findViewById<View>(R.id.avatar_text).visibility)
        }
    }

    private fun assertInitials(scenario: ActivityScenario<UserProfilePreviewActivity>, initials: String) =
        TestUi.eventually(attempts = 200) {
            scenario.onActivity {
                val text = it.findViewById<TextView>(R.id.avatar_text)
                assertTrue(text.isShown)
                assertEquals(initials, text.text.toString())
                assertEquals(View.GONE, it.findViewById<View>(R.id.avatar_image).visibility)
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
                assertTrue(it.items().none { item -> item is ProfileItem.Sharing })
                top = it.holder<ProfileItem.Facts>().screenTop()
            }
            frame(scenario, "late-social-before-${spec.name}")
            snackbar(scenario, attempts = 200)
            scenario.onActivity {
                assertNull(it.findViewById<View>(R.id.friends_row))
                assertFalse(it.findViewById<View>(R.id.badge).isShown)
                assertEquals(top, it.holder<ProfileItem.Facts>().screenTop())
            }
            frame(scenario, "late-social-deferred-${spec.name}")
            UserProfilePreviewActivity.socialDelayMs = 0
            scenario.onActivity { it.findViewById<View>(MaterialR.id.snackbar_action).performClick() }
            TestUi.eventually {
                scenario.onActivity {
                    assertTrue(it.findViewById<View>(R.id.friends_row)?.isShown == true)
                    assertEquals("в друзьях", it.findViewById<TextView>(R.id.badge).text.toString())
                }
            }
            TestUi.eventually { scenario.onActivity { assertTrue(it.findViewById<View>(MaterialR.id.snackbar_text)?.isShown != true) } }
            frame(scenario, "late-social-retried-${spec.name}")
        }
    }

    @Test fun summaryComesFirstWithLabelLevelAndScales() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews().copy(summary = sampleSummary()))
        }) { scenario ->
            content(scenario)
            scenario.onActivity { activity ->
                val reviews = activity.items().dropWhile { it !is ProfileItem.Section }
                assertEquals(4, (reviews[0] as ProfileItem.Section).count)
                assertTrue(reviews[1] is ProfileItem.Summary)
                assertTrue(reviews[2] is ProfileItem.OwnReview)
            }
            scrollTo<ProfileItem.Section>(scenario)
            scenario.onActivity { activity ->
                val card = activity.holder<ProfileItem.Summary>()
                assertEquals("Сводка по 12 отзывам", card.text(R.id.label))
                assertEquals("ИИ", card.text(R.id.ai))
                assertEquals("Сводка по 12 отзывам, составлена ИИ", card.findViewById<View>(R.id.header).contentDescription)
                assertTrue(card.findViewById<View>(R.id.level_row).isShown)
                assertTrue(card.findViewById<View>(R.id.level_dot).isShown)
                assertEquals("Скорее положительные", card.text(R.id.level))
                assertEquals("Тон отзывов: скорее положительные", card.findViewById<View>(R.id.level_row).contentDescription)
                assertEquals(sampleSummary().description, card.text(R.id.description))
                assertEquals(listOf("Понятно объясняет сложные темы", "Честно оценивает"), card.points(R.id.pros))
                assertEquals(listOf("Строгая защита лабораторных"), card.points(R.id.cons))
                assertEquals("Плюсы: Понятно объясняет сложные темы; Честно оценивает", card.findViewById<View>(R.id.pros).contentDescription)
                assertEquals(listOf("Много лаб", "Строгий на защите", "Чёткие требования"), card.tags())
                assertTrue(card.findViewById<ViewGroup>(R.id.tags).children.all { !it.isClickable && !it.isFocusable })
                card.assertScalesFolded(expanded = false)
                val gap = card.screenTop() - activity.holder<ProfileItem.Section>().let { it.screenTop() + it.height }
                assertEquals(0, gap)
            }
            frame(scenario, "summary-${spec.name}")
            scenario.onActivity { activity ->
                val own = activity.items().indexOfFirst { it is ProfileItem.OwnReview }
                (activity.list().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(own, activity.list().height / 2)
            }
            TestUi.settle(80)
            scenario.onActivity { activity ->
                val card = activity.holder<ProfileItem.Summary>()
                val next = activity.holder<ProfileItem.OwnReview>().screenTop() - (card.screenTop() + card.height)
                assertEquals(card.resources.getDimensionPixelSize(R.dimen.design_spacing_compact), next)
            }
            frame(scenario, "summary-end-${spec.name}")
        }
    }

    @Test fun summaryScalesFoldBehindATextButton() {
        val specs = if (Appearances.fullMatrix) Appearances.all else Appearances.all.take(2)
        specs.forEach { spec ->
            preview(spec, {
                UserProfilePreviewActivity.person = AppResult.Success(teacher())
                UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews().copy(summary = sampleSummary()))
            }) { scenario ->
                content(scenario)
                scrollTo<ProfileItem.Section>(scenario)
                var sectionTop = 0
                var cardTop = 0
                var collapsedHeight = 0
                var descriptionTop = 0
                scenario.onActivity { activity ->
                    val card = activity.holder<ProfileItem.Summary>()
                    card.assertScalesFolded(expanded = false)
                    assertTrue(card.findViewById<View>(R.id.level_row).isShown)
                    assertTrue(card.findViewById<View>(R.id.description).isShown)
                    assertTrue(card.findViewById<View>(R.id.pros).isShown)
                    assertTrue(card.findViewById<View>(R.id.cons).isShown)
                    assertTrue(card.findViewById<View>(R.id.tags).isShown)
                    sectionTop = activity.holder<ProfileItem.Section>().screenTop()
                    cardTop = card.screenTop()
                    collapsedHeight = card.height
                    descriptionTop = card.findViewById<View>(R.id.description).screenTop()
                }
                frame(scenario, "summary-collapsed-${spec.name}")

                clickScalesToggle(scenario)
                TestUi.eventually {
                    scenario.onActivity { activity ->
                        val card = activity.holder<ProfileItem.Summary>()
                        card.assertScalesFolded(expanded = true)
                        assertEquals(5, card.scales().count { it.isShown })
                        assertEquals(
                            listOf("Объясняет" to "хорошо", "Отношение к студентам" to "нейтральное", "Справедливость оценок" to "высокая",
                                "Строгость" to "высокая", "Нагрузка" to "мало данных"),
                            card.scales().map { it.text(R.id.name) to it.text(R.id.value) },
                        )
                        assertEquals(listOf(true, true, true, true, false), card.scales().map { it.findViewById<View>(R.id.reason).isShown })
                        assertEquals("Объясняет: хорошо. Хвалят понятные лекции", card.scales().first().contentDescription)
                        // Only the card grows: nothing above it and none of its own blocks move.
                        assertEquals(sectionTop, activity.holder<ProfileItem.Section>().screenTop())
                        assertEquals(cardTop, card.screenTop())
                        assertEquals(descriptionTop, card.findViewById<View>(R.id.description).screenTop())
                        assertTrue(card.height > collapsedHeight)
                    }
                }
                frame(scenario, "summary-expanded-${spec.name}")

                scenario.recreate()
                content(scenario)
                scrollTo<ProfileItem.Section>(scenario)
                scenario.onActivity { activity ->
                    assertTrue((activity.items().single { it is ProfileItem.Summary } as ProfileItem.Summary).expanded)
                    activity.holder<ProfileItem.Summary>().assertScalesFolded(expanded = true)
                }

                clickScalesToggle(scenario)
                TestUi.eventually {
                    scenario.onActivity { activity ->
                        val card = activity.holder<ProfileItem.Summary>()
                        card.assertScalesFolded(expanded = false)
                        assertEquals(collapsedHeight, card.height)
                        assertEquals(cardTop, card.screenTop())
                    }
                }
            }
        }
    }

    @Test fun lowConfidenceSummaryHasNoLevel() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews(),
                summary = sampleSummary(reviewCount = 3, confidence = SummaryConfidence.LOW)))
        }) { scenario ->
            content(scenario)
            scrollTo<ProfileItem.Section>(scenario)
            scenario.onActivity { activity ->
                val card = activity.holder<ProfileItem.Summary>()
                assertEquals("Сводка по 3 отзывам", card.text(R.id.label))
                assertEquals(View.GONE, card.findViewById<View>(R.id.level_row).visibility)
                assertFalse(card.findViewById<View>(R.id.level_dot).isShown)
                assertTrue(card.findViewById<View>(R.id.description).isShown)
            }
            frame(scenario, "summary-low-${spec.name}")
        }
    }

    @Test fun summaryWithoutProsConsOrTagsHidesThoseBlocks() = appearances { spec ->
        val empty = sampleSummary(reviewCount = 21, pros = emptyList(), cons = emptyList(), tags = emptyList(),
            scales = SummaryScaleKind.entries.map { SummaryScale(it, SummaryScaleValue.NOT_ENOUGH_DATA, null) })
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews(), summary = empty))
        }) { scenario ->
            content(scenario)
            scrollTo<ProfileItem.Section>(scenario)
            expandSummary(scenario)
            scenario.onActivity { activity ->
                val card = activity.holder<ProfileItem.Summary>()
                assertEquals("Сводка по 21 отзыву", card.text(R.id.label))
                assertEquals(View.GONE, card.findViewById<View>(R.id.pros).visibility)
                assertEquals(View.GONE, card.findViewById<View>(R.id.cons).visibility)
                assertEquals(View.GONE, card.findViewById<View>(R.id.tags).visibility)
                assertEquals(5, card.scales().count { it.isShown })
                assertEquals(List(5) { "мало данных" }, card.scales().map { it.text(R.id.value) })
                assertTrue(card.scales().none { it.findViewById<View>(R.id.reason).isShown })
                val muted = card.context.color.onSurfaceVariant
                assertTrue(card.scales().all { it.findViewById<TextView>(R.id.value).currentTextColor == muted })
            }
            frame(scenario, "summary-empty-${spec.name}")
        }
    }

    @Test fun longSummaryFitsNarrowScreenAndLargeFont() {
        val narrow = Appearances.Spec("light-narrow", fontScale = 1.3f, widthDp = 320)
        val specs = listOf(Appearances.light, narrow) + if (Appearances.fullMatrix) Appearances.all.drop(1) else emptyList()
        val long = sampleSummary(
            reviewCount = 60,
            description = LONG_DESCRIPTION,
            pros = List(4) { index -> "${index + 1}. " + LONG_POINT.take(97) },
            cons = List(4) { index -> "${index + 1}. " + LONG_CON.take(97) },
            tags = listOf(SummaryTag.UNCLEAR_REQUIREMENTS, SummaryTag.ATTENDANCE_REQUIRED, SummaryTag.INTERESTING_CLASSES,
                SummaryTag.FLEXIBLE_DEADLINES, SummaryTag.FREQUENT_TESTS, SummaryTag.READS_SLIDES),
            scales = SummaryScaleKind.entries.map { SummaryScale(it, SummaryScaleValue.MEDIUM, LONG_POINT.take(100)) },
            level = TeacherLevel.VERY_NEGATIVE,
            confidence = SummaryConfidence.HIGH,
        )
        specs.forEach { spec ->
            preview(spec, {
                UserProfilePreviewActivity.person = AppResult.Success(teacher())
                UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews(), summary = long))
            }) { scenario ->
                content(scenario)
                scrollTo<ProfileItem.Section>(scenario)
                expandSummary(scenario)
                scenario.onActivity { activity ->
                    val card = activity.holder<ProfileItem.Summary>()
                    assertEquals(400, LONG_DESCRIPTION.length)
                    assertEquals(LONG_DESCRIPTION, card.text(R.id.description))
                    assertEquals(4, card.points(R.id.pros).size)
                    assertEquals(4, card.points(R.id.cons).size)
                    val tags = card.findViewById<ViewGroup>(R.id.tags)
                    assertEquals(6, tags.childCount)
                    assertTrue("Tags wrap into several rows", tags.children.map { it.top }.distinct().count() > 1)
                    tags.children.forEach { assertTrue(it.right <= tags.width) }
                    // The sign of a point sits on its first line at any font scale.
                    val point = card.findViewById<ViewGroup>(R.id.pros).getChildAt(0)
                    assertEquals(point.findViewById<TextView>(R.id.text).lineHeight, point.findViewById<View>(R.id.icon).height)
                    assertTrue(card.scales().all { it.findViewById<View>(R.id.reason).isShown })
                }
                frame(scenario, "summary-long-top-${spec.name}")
                scenario.onActivity { it.list().scrollBy(0, it.holder<ProfileItem.Summary>().height / 2) }
                frame(scenario, "summary-long-bottom-${spec.name}")
            }
        }
    }

    @Test fun everyLevelHasItsTone() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews(), summary = sampleSummary()))
        }) { scenario ->
            content(scenario)
            scrollTo<ProfileItem.Section>(scenario)
            TeacherLevel.entries.forEach { level ->
                scenario.onActivity { it.publishReviews(reviewsOf(teacherReviews(), summary = sampleSummary(level = level))) }
                TestUi.eventually {
                    scenario.onActivity { activity ->
                        val card = activity.holder<ProfileItem.Summary>()
                        val tone = level.tone()
                        assertEquals(activity.getString(tone.label), card.text(R.id.level))
                        val dot = card.findViewById<ImageView>(R.id.level_dot)
                        assertTrue(dot.isShown)
                        assertEquals(tone.color(activity), dot.imageTintList?.defaultColor)
                        assertEquals(tone.description(activity), card.findViewById<View>(R.id.level_row).contentDescription)
                    }
                }
                frame(scenario, "summary-level-${level.name.lowercase()}-${spec.name}")
            }
            scenario.onActivity { activity ->
                val colors = TeacherLevelTone.entries.map { it.color(activity) }
                assertEquals(5, colors.distinct().size)
            }
        }
    }

    @Test fun profileWithoutSummaryIsUnchanged() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(mixedReviews())
        }) { scenario ->
            content(scenario)
            scenario.onActivity { activity ->
                val reviews = activity.items().dropWhile { it !is ProfileItem.Section }
                assertTrue(activity.items().none { it is ProfileItem.Summary })
                assertTrue(reviews[1] is ProfileItem.OwnReview)
                assertEquals(listOf("named", "anonymous", "copy"), reviews.drop(2).map { (it as ProfileItem.Review).review.id })
            }
            scrollTo<ProfileItem.Section>(scenario)
            scenario.onActivity { activity ->
                val section = activity.holder<ProfileItem.Section>()
                assertEquals(section.screenTop() + section.height, activity.holder<ProfileItem.OwnReview>().screenTop())
            }
            frame(scenario, "summary-none-${spec.name}")
        }
    }

    @Test fun lateReviewsWithSummaryDoNotMoveTheHeader() = appearances { spec ->
        preview(spec, {
            UserProfilePreviewActivity.person = AppResult.Success(teacher())
            UserProfilePreviewActivity.reviews = AppResult.Success(reviewsOf(teacherReviews(), summary = sampleSummary()))
            UserProfilePreviewActivity.reviewsDelayMs = 6_000
        }) { scenario ->
            content(scenario, attempts = 200)
            var positions: Pair<Int, Int>? = null
            scenario.onActivity {
                assertTrue(it.items().none { item -> item is ProfileItem.Section || item is ProfileItem.Summary })
                positions = it.identityPositions()
            }
            TestUi.eventually(attempts = 200) {
                scenario.onActivity {
                    assertTrue(it.items().any { item -> item is ProfileItem.Summary })
                    assertEquals(positions, it.identityPositions())
                }
            }
            noSnackbar(scenario)
            frame(scenario, "summary-late-${spec.name}")
        }
    }

    private fun appearances(block: (Appearances.Spec) -> Unit) = Appearances.default.forEach(block)

    /** Brings «Подробнее» on screen, taps it, waits for the scales and returns to the section heading. */
    private fun expandSummary(scenario: ActivityScenario<UserProfilePreviewActivity>) {
        scenario.onActivity { activity ->
            val toggle = activity.holder<ProfileItem.Summary>().findViewById<View>(R.id.scales_toggle)
            val list = activity.list()
            val overflow = toggle.screenTop() + toggle.height - (list.screenTop() + list.height)
            if (overflow > 0) list.scrollBy(0, overflow)
        }
        TestUi.settle(80)
        clickScalesToggle(scenario)
        TestUi.eventually { scenario.onActivity { it.holder<ProfileItem.Summary>().assertScalesFolded(expanded = true) } }
        scrollTo<ProfileItem.Section>(scenario)
    }

    /**
     * Taps «Подробнее»/«Свернуть» on the main thread. Espresso's click needs the button 90 % on screen and fails with
     * an animations hint on a busy emulator; the toggle's own behaviour is what is under test here.
     */
    private fun clickScalesToggle(scenario: ActivityScenario<UserProfilePreviewActivity>) {
        scenario.onActivity { activity ->
            val toggle = activity.holder<ProfileItem.Summary>().findViewById<View>(R.id.scales_toggle)
            assertTrue(toggle.isShown && toggle.isEnabled)
            toggle.performClick()
        }
        TestUi.settle(80)
    }

    /**
     * The ISU number in the hero: just the number, a copy symbol and a full target. [copy] taps it and checks the
     * clipboard; the system's copy preview then covers the screen, so screenshots come before or not at all.
     */
    private fun assertIsu(activity: UserProfilePreviewActivity, copy: Boolean = false) {
        val isu = activity.findViewById<View>(R.id.isu)
        assertEquals(ISU.toString(), activity.findViewById<TextView>(R.id.isu_value).text.toString())
        assertEquals("Номер ИСУ $ISU, скопировать", isu.contentDescription)
        assertTrue(isu.isClickable && isu.height >= activity.resources.getDimensionPixelSize(R.dimen.design_touch_target))
        if (!copy) return
        isu.performClick()
        val clip = checkNotNull(activity.getSystemService(android.content.ClipboardManager::class.java).primaryClip)
        assertEquals(ISU.toString(), clip.getItemAt(0).text.toString())
    }

    /** Rows of one connected group: one width, 2 dp apart, the outer corners only at the ends. */
    private fun assertGroup(rows: List<View>) {
        val density = rows.first().resources.displayMetrics.density
        assertEquals(1, rows.map { it.width }.distinct().size)
        rows.zipWithNext().forEach { (upper, lower) -> assertEquals(2 * density, (lower.screenTop() - upper.screenTop() - upper.height).toFloat(), 1f) }
        val bounds = android.graphics.RectF(0f, 0f, 100f, 100f)
        rows.forEachIndexed { index, row ->
            val shape = ((row.background as android.graphics.drawable.RippleDrawable).getDrawable(0)
                as com.google.android.material.shape.MaterialShapeDrawable).shapeAppearanceModel
            assertEquals(if (index == 0) 20 * density else 4 * density, shape.topLeftCornerSize.getCornerSize(bounds), 0.5f)
            assertEquals(if (index == rows.lastIndex) 20 * density else 4 * density, shape.bottomLeftCornerSize.getCornerSize(bounds), 0.5f)
        }
    }

    private fun UserProfilePreviewActivity.factsHolder(kind: ProfileFactKind): View =
        checkNotNull(list().findViewHolderForAdapterPosition(items().indexOfFirst { it is ProfileItem.Facts && it.kind == kind })).itemView

    /** The scales and their text button: label, TalkBack state, a full touch target and the content edge. */
    private fun View.assertScalesFolded(expanded: Boolean) {
        assertEquals(expanded, findViewById<View>(R.id.scales).isShown)
        assertEquals(if (expanded) View.VISIBLE else View.GONE, findViewById<View>(R.id.scales).visibility)
        val toggle = findViewById<TextView>(R.id.scales_toggle)
        assertTrue(toggle.isShown)
        assertEquals(if (expanded) "Свернуть" else "Подробнее", toggle.text.toString())
        val node = toggle.createAccessibilityNodeInfo()
        assertEquals(if (expanded) "развёрнуто" else "свёрнуто", node.stateDescription?.toString())
        assertTrue(node.isClickable)
        assertTrue(node.isEnabled)
        val touchTarget = resources.getDimensionPixelSize(R.dimen.design_touch_target)
        assertTrue("Toggle ${toggle.width}x${toggle.height}", toggle.height >= touchTarget && toggle.width >= touchTarget)
        assertEquals(findViewById<View>(R.id.description).left, toggle.left + toggle.paddingStart)
        // The toggle ends the card; the target reaches into its padding, not past it.
        assertTrue(toggle.bottom <= (toggle.parent as View).height)
    }

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
        UserProfilePreviewActivity.appearance = PreviewAppearance()
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
        UserProfilePreviewActivity.openedEditors.clear()
        UserProfilePreviewActivity.openedReports.clear()
        UserProfilePreviewActivity.mutationDelayMs = 0
        UserProfilePreviewActivity.mutationError = null
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

    private fun snackbar(scenario: ActivityScenario<UserProfilePreviewActivity>, attempts: Int = 40, text: String? = null) {
        TestUi.eventually(attempts = attempts) {
            scenario.onActivity {
                val view = it.findViewById<TextView>(MaterialR.id.snackbar_text)
                assertTrue(view?.isShown == true)
                if (text == null) assertEquals("Часть данных не загрузилась", view.text.toString())
                else assertTrue(view.text.toString(), view.text.contains(text))
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
            ViewChecks.assertTextFits(it.window.decorView, ellipsizable = ::isReviewMeta)
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

    /** The review's meta line is the one line deliberately shortened to two lines. */
    private fun isReviewMeta(view: TextView) = view.id == R.id.meta && view.maxLines == 2

    private fun showReview(scenario: ActivityScenario<UserProfilePreviewActivity>, id: String) {
        scenario.onActivity {
            val position = it.items().indexOfFirst { item -> item is ProfileItem.Review && item.review.id == id }
            assertTrue(position >= 0)
            (it.list().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
        }
        TestUi.settle(60)
    }

    private fun assertReview(
        scenario: ActivityScenario<UserProfilePreviewActivity>,
        review: TeacherReview,
        canVote: Boolean = false,
        canReport: Boolean = false
    ) {
        showReview(scenario, review.id)
        scenario.onActivity { activity ->
            val row = activity.reviewRow(review.id)
            val meta = row.findViewById<TextView>(R.id.meta)
            val expectedMeta = listOfNotNull(review.subject, review.written?.text(activity)).joinToString(", ")
            assertEquals(expectedMeta.isNotEmpty(), meta.isShown)
            assertEquals(expectedMeta, meta.text.toString())
            assertEquals(review.text, row.findViewById<TextView>(R.id.text).text.toString())
            val community = review.origin as? ReviewOrigin.Community
            val copy = review.origin as? ReviewOrigin.Reviews
            val author = row.findViewById<TextView>(R.id.author)
            assertEquals(community?.author != null, author.isShown)
            if (community?.author != null) {
                assertEquals(shortPersonName(community.author!!.name), author.text.toString())
                assertEquals(community.author!!.name, author.contentDescription)
            }
            assertEquals(community?.verified == true, row.findViewById<View>(R.id.verified).isShown)
            assertEquals(community != null && !community.verified, row.findViewById<View>(R.id.unverified).isShown)
            val source = row.findViewById<TextView>(R.id.source)
            assertEquals(copy != null, source.isShown)
            if (copy != null) {
                assertEquals(copy.sourceTitle ?: "Reviews", source.text.toString())
                assertTrue(source.height >= 48 * source.resources.displayMetrics.density - 1)
            }
            // Who wrote it stays inside the row, however many lines a long name or source takes.
            listOf(author, source, row.findViewById<TextView>(R.id.anonymous)).filter { it.isShown }.forEach { origin ->
                val lastLine = Rect().also { origin.getLineBounds(origin.lineCount - 1, it) }
                assertTrue(origin.text.toString(), origin.screenTop() + lastLine.bottom <= row.screenTop() + row.height)
            }
            assertEquals(canReport && community != null && !community.reportedByMe, row.findViewById<View>(R.id.more).isShown)
            assertEquals(canVote, row.findViewById<View>(R.id.vote_up).isShown)
            assertEquals(canVote, row.findViewById<View>(R.id.vote_down).isShown)
            assertEquals(review.score.toString().replace('-', '−'), row.findViewById<TextView>(R.id.score).text.toString())
            assertEquals(community != null && community.author == null, row.findViewById<View>(R.id.anonymous).isShown)
            assertEquals(canVote || review.score != 0, row.findViewById<View>(R.id.score).isShown)
            ViewChecks.assertTextFits(activity.window.decorView, ellipsizable = ::isReviewMeta)
            ViewChecks.assertTouchTargets(activity.window.decorView)
        }
    }

    private fun UserProfilePreviewActivity.reviewRow(id: String): View {
        val position = items().indexOfFirst { it is ProfileItem.Review && it.review.id == id }
        return checkNotNull(list().findViewHolderForAdapterPosition(position)).itemView
    }

    private fun UserProfilePreviewActivity.list(): RecyclerView = findViewById(R.id.profile_list)
    private fun UserProfilePreviewActivity.items(): List<ProfileItem> = (list().adapter as UserProfileAdapter).currentList
    private inline fun <reified T : ProfileItem> UserProfilePreviewActivity.holder(): View =
        checkNotNull(list().findViewHolderForAdapterPosition(items().indexOfFirst { it is T })).itemView
    private fun View.screenTop(): Int = IntArray(2).also(::getLocationOnScreen)[1]
    private fun View.screenLeft(): Int = IntArray(2).also(::getLocationOnScreen)[0]
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
        copiedReview("review-one", LONG_SUBJECT, ReviewDate.Month(YearMonth(2025, 1)), "Отзывы ПИ", "https://example.org/reviews/1", "Понятно объясняет материал и подробно отвечает на вопросы."),
        copiedReview("review-two", null, ReviewDate.BeforeYear(2023), null, "https://example.org/reviews/2", "На занятиях было интересно."),
        copiedReview("review-three", null, null, null, "https://example.org/reviews/3", LONG_REVIEW)
    )
    private fun copiedReview(id: String, subject: String?, written: ReviewDate?, sourceTitle: String?, sourceUrl: String, text: String) =
        TeacherReview(id, subject, written, text, score = 0, myVote = 0, origin = ReviewOrigin.Reviews(sourceTitle, sourceUrl))
    private fun communityReview(
        id: String,
        subject: String?,
        written: ReviewDate?,
        text: String,
        author: UserSummary? = null,
        verified: Boolean = false,
        reportedByMe: Boolean = false,
        score: Int = 0,
        myVote: Int = 0
    ) = TeacherReview(id, subject, written, text, score, myVote, ReviewOrigin.Community(verified, author, reportedByMe))
    private fun ownReview(status: OwnReviewStatus, note: String? = null, verified: Boolean = false) = OwnTeacherReview(
        UserProfilePreviewActivity.OWN_REVIEW_ID, "Математический анализ",
        "Лекции понятные, на практике разбираем задачи из контрольных. Вопросы можно задавать в любое время.",
        anonymous = true, status = status, reviewNote = note, score = 0, verified = verified,
        written = ReviewDate.Month(YearMonth(2026, 9)))
    /** The own review, a named verified review, an anonymous unverified one and a Reviews copy, in Backend's order. */
    private fun mixedReviews(mine: OwnTeacherReview? = ownReview(OwnReviewStatus.PENDING)) = reviewsOf(listOf(
        communityReview("named", LONG_SUBJECT, ReviewDate.Month(YearMonth(2025, 1)),
            "Объясняет сложные темы на простых примерах, всегда отвечает на вопросы после пары.", author = AUTHOR, verified = true, score = 5),
        communityReview("anonymous", null, ReviewDate.Month(YearMonth(2024, 11)), LONG_REVIEW),
        copiedReview("copy", "Математический анализ", ReviewDate.BeforeYear(2023), "Отзывы ПИ", "https://example.org/reviews/copy",
            "На занятиях было интересно."),
    ), canWrite = true, canVote = true, canReport = true, knownTeacher = true, mine = mine)
    private fun reviewsOf(
        reviews: List<TeacherReview>,
        canWrite: Boolean = false,
        canVote: Boolean = false,
        canReport: Boolean = false,
        knownTeacher: Boolean = false,
        mine: OwnTeacherReview? = null,
        summary: TeacherSummary? = null
    ) = TeacherReviews(ISU, reviews, mine, canWrite, canVote, canReport, knownTeacher, summary)
    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()
    private fun View.points(id: Int): List<String> =
        findViewById<ViewGroup>(id).children.map { it.findViewById<TextView>(R.id.text).text.toString() }.toList()
    private fun View.tags(): List<String> = findViewById<ViewGroup>(R.id.tags).children.map { (it as TextView).text.toString() }.toList()
    private fun View.scales(): List<View> = findViewById<ViewGroup>(R.id.scales).children.toList()

    private companion object {
        const val LONG_DESCRIPTION = "Большинство студентов пишут, что лекции понятные и хорошо структурированные, а на практике разбирают задачи " +
            "из контрольных. Часть отзывов отмечает строгую защиту лабораторных и высокие требования к оформлению отчётов. " +
            "Мнения о нагрузке расходятся: одним хватает занятий, другим приходится много заниматься самостоятельно дома. " +
            "Все материалы выкладывает заранее, консультации перед экзаменом помогают."
        const val LONG_POINT = "Подробно разбирает решения на практических занятиях и отвечает на вопросы после пары и в переписке в любое время"
        const val LONG_CON = "Строго принимает лабораторные работы и снижает баллы за каждую неточность в отчёте и за опоздание со сдачей"
        const val BACKEND_NAME = "Соколов Артём Игоревич"
        const val LONG_REASON = "В отзыве есть оценки личных качеств преподавателя; оставьте только то, что касается занятий и материалов"
        val AUTHOR = UserSummary(200002, "Преображенская Евгения Владиславовна", null, emptyList(), UserSharing(sport = false, schedule = false))
        const val LONG_DEPARTMENT = "Факультет информационных технологий и программирования, кафедра прикладной математики и теоретической информатики"
        const val UNTITLED_DEPARTMENT = "Институт международного развития и партнёрства"
        const val LONG_SUBJECT = "Математические методы моделирования сложных информационных систем"
        val LONG_REVIEW = "Преподаватель последовательно объясняет сложные темы, разбирает примеры и отвечает на вопросы студентов. " +
            "На практических занятиях можно обсудить разные подходы к решению задачи и понять, почему один из них лучше подходит.\n\n" +
            "Материалы помогают подготовиться к контрольной работе. Особенно полезны подробные комментарии к решениям: " +
            "после обсуждения становится понятно, какие шаги стоит проверить ещё раз и как избежать похожих ошибок в дальнейшем."
    }
}
