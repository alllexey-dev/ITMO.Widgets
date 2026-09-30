package dev.alllexey.itmowidgets.feature.home

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.HomeFixture
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.home.ui.HomeFeedAdapter
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsBottomSheet
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toSettingsNavigation
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks
import dev.alllexey.itmowidgets.testing.ViewChecks.assertTextFits
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The feed on the fixture source: every card kind, sheets through the navigator, hints, empty and hidden states. */
@RunWith(AndroidJUnit4::class)
class HomeFeedVisualTest {

    @After
    fun reset() {
        SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
        SettingsNavigationTestActivity.homeFixture = HomeFixture()
    }

    @Test
    fun everyCardFitsInFeedOrderAndSurvivesRecreation() {
        Appearances.default.forEachIndexed { index, spec ->
            SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
            launch { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(
                        listOf(
                            HomeCardKind.SCHEDULE, HomeCardKind.SCHEDULE_CHANGES, HomeCardKind.SPORT,
                            HomeCardKind.FRIEND_REQUESTS, HomeCardKind.HINT_WIDGETS
                        ),
                        activity.adapter().currentList.map { it.kind }
                    )
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_feed).visibility)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.empty_state).visibility)
                    assertTextFits(activity.feed(), allowEllipsis = true)
                    ViewChecks.assertTouchTargets(activity.feed())
                }
                capture("feed-$index")
                scenario.onActivity { it.feed().scrollToPosition(4) }
                settle()
                scenario.onActivity { activity ->
                    assertTextFits(activity.feed(), allowEllipsis = true)
                    ViewChecks.assertTouchTargets(activity.feed())
                }
                capture("feed-end-$index")
                scenario.recreate()
                settle()
                scenario.onActivity { assertEquals(5, it.adapter().itemCount) }
            }
        }
    }

    @Test
    fun scheduleChangesCardOpensHistoryAndDismisses() {
        val subject = "Проектирование и анализ распределённых информационных систем реального времени"
        Appearances.default.forEach { spec ->
            SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
            SettingsNavigationTestActivity.homeFixture = HomeFixture(
                cards = HomeFixture.defaultCards().map {
                    if (it is HomeCard.ScheduleChanges) it.copy(latest = HomeFixture.scheduleChangeSample(subject)) else it
                }
            )
            launch { scenario ->
                var scheduleTop = 0
                scenario.onActivity { activity ->
                    val card = activity.feed().descendants().first { it.id == R.id.home_schedule_changes_card }
                    assertEquals("3", card.findViewById<TextView>(R.id.schedule_changes_count).text.toString())
                    val latest = card.findViewById<TextView>(R.id.schedule_changes_latest).text.toString()
                    assertEquals("$subject — перенесена на ср, 9 сентября, 10:00", latest)
                    assertEquals("Изменения в расписании, 3. $latest", card.contentDescription.toString())
                    assertTrue(card.isClickable)
                    val dismiss = card.findViewById<View>(R.id.schedule_changes_dismiss)
                    val min = 48 * activity.resources.displayMetrics.density - 1
                    assertTrue(dismiss.width >= min && dismiss.height >= min)
                    assertEquals("Прочитано", dismiss.contentDescription.toString())
                    assertTextFits(card)
                    ViewChecks.assertTouchTargets(card)
                    scheduleTop = activity.feed().getChildAt(0).top
                    card.performClick()
                    assertEquals(listOf(AppScreen.SCHEDULE_CHANGES), activity.openedScreens)
                }
                capture("schedule-changes-${spec.name}")
                scenario.onActivity { activity ->
                    activity.feed().descendants().first { it.id == R.id.schedule_changes_dismiss }.performClick()
                }
                settle()
                scenario.onActivity { activity ->
                    assertEquals(listOf(HomeCardKind.SCHEDULE_CHANGES), SettingsNavigationTestActivity.homeSource!!.dismissed)
                    assertEquals(
                        listOf(HomeCardKind.SCHEDULE, HomeCardKind.SPORT, HomeCardKind.FRIEND_REQUESTS, HomeCardKind.HINT_WIDGETS),
                        activity.adapter().currentList.map { it.kind }
                    )
                    assertTrue(activity.feed().descendants().none { it.id == R.id.home_schedule_changes_card })
                    assertEquals(scheduleTop, activity.feed().getChildAt(0).top)
                    assertEquals(listOf(AppScreen.SCHEDULE_CHANGES), activity.openedScreens)
                }
            }
        }
    }

    @Test
    fun firstLoadWithoutAnyAnswerShowsTheSkeletonInEveryAppearance() {
        SettingsNavigationTestActivity.homeFixture = HomeFixture(neverAnswers = true)
        Appearances.default.forEachIndexed { index, spec ->
            SettingsNavigationTestActivity.appearance = spec.toSettingsNavigation()
            launch { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.loading).visibility)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.home_feed).visibility)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.empty_state).visibility)
                }
                capture("loading-$index")
            }
        }
    }

    @Test
    fun rowsOpenTheLessonAndPendingSheetsThroughTheNavigator() {
        launch { scenario ->
            scenario.onActivity { it.scheduleRows().getChildAt(0).performClick() }
            settle()
            scenario.onActivity { activity ->
                val sheet = activity.supportFragmentManager.findFragmentByTag(LessonDetailsBottomSheet.TAG)
                assertNotNull(sheet)
                assertTrue(sheet!!.requireView().descendants().filterIsInstance<TextView>().any { it.text == "Математический анализ" })
            }
            capture("lesson-sheet")
            scenario.onActivity {
                (it.supportFragmentManager.findFragmentByTag(LessonDetailsBottomSheet.TAG) as DialogFragment).dismiss()
            }
            settle()
            scenario.onActivity { it.scheduleRows().getChildAt(2).performClick() }
            settle()
            scenario.onActivity { activity ->
                assertNull(activity.supportFragmentManager.findFragmentByTag(LessonDetailsBottomSheet.TAG))
                assertNotNull(activity.supportFragmentManager.findFragmentByTag(PendingSportDetailsBottomSheet.TAG))
            }
            capture("pending-sheet")
        }
    }

    @Test
    fun dismissingAHintWritesTheStoreAndTheCardLeaves() {
        launch { scenario ->
            scenario.onActivity { it.feed().scrollToPosition(4) }
            settle()
            scenario.onActivity { activity ->
                activity.feed().descendants().first { it.id == R.id.hint_dismiss }.performClick()
            }
            settle()
            scenario.onActivity { activity ->
                assertEquals(setOf(HomeHint.WIDGETS), SettingsNavigationTestActivity.homeHintStore!!.dismissed.value)
                // The real hint source drops the card once the store changes; the fixture source is told by hand.
                val source = SettingsNavigationTestActivity.homeSource!!
                source.cards.value = source.cards.value.filterNot { it is HomeCard.Hint }
            }
            settle()
            scenario.onActivity { assertEquals(4, it.adapter().itemCount) }
        }
    }

    @Test
    fun anEmptyFeedShowsTheEmptyStateAndHiddenKindsStayOut() {
        SettingsNavigationTestActivity.homeFixture = HomeFixture(cards = emptyList())
        launch { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.empty_state).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.home_feed).visibility)
                assertTextFits(activity.findViewById(R.id.empty_state))
            }
            capture("empty")
        }

        SettingsNavigationTestActivity.homeFixture = HomeFixture(hidden = setOf(HomeCardKind.SPORT, HomeCardKind.FRIEND_REQUESTS))
        launch { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    listOf(HomeCardKind.SCHEDULE, HomeCardKind.SCHEDULE_CHANGES, HomeCardKind.HINT_WIDGETS),
                    activity.adapter().currentList.map { it.kind }
                )
                SettingsNavigationTestActivity.homePreferences!!.hidden.value = emptySet()
            }
            settle()
            scenario.onActivity { assertEquals(5, it.adapter().itemCount) }
        }
    }

    @Test
    fun aFailedRefreshKeepsTheCardsAndOffersRetry() {
        SettingsNavigationTestActivity.homeFixture = HomeFixture(refreshResult = AppResult.Failure(AppError.Network))
        launch { scenario ->
            scenario.onActivity { activity ->
                assertEquals(5, activity.adapter().itemCount)
                assertEquals(1, SettingsNavigationTestActivity.homeSource!!.refreshes)
                assertTrue(activity.hasRetrySnackbar())
                assertFalse(activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing)
            }
            capture("refresh-failed")
        }
    }

    private fun launch(block: (ActivityScenario<SettingsNavigationTestActivity>) -> Unit) {
        SettingsNavigationTestActivity.startDestination = R.id.navigation_home
        ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
            settle()
            block(scenario)
        }
    }

    private fun SettingsNavigationTestActivity.feed(): RecyclerView = findViewById(R.id.home_feed)

    private fun SettingsNavigationTestActivity.adapter(): HomeFeedAdapter = feed().adapter as HomeFeedAdapter

    private fun SettingsNavigationTestActivity.scheduleRows(): ViewGroup = feed().descendants().first { it.id == R.id.schedule_rows } as ViewGroup

    private fun SettingsNavigationTestActivity.hasRetrySnackbar(): Boolean =
        window.decorView.descendants().filterIsInstance<TextView>().any { it.text == getString(R.string.common_retry) }

    private fun settle() = TestUi.settle(650)

    private fun capture(name: String) = Screenshots.capture("home-screenshots", name) { settle() }
}
