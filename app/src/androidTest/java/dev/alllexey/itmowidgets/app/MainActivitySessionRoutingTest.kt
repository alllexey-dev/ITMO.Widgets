package dev.alllexey.itmowidgets.app

import android.view.View
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.testing.TestSession
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not

@RunWith(AndroidJUnit4::class)
class MainActivitySessionRoutingTest {

    @After
    fun clearSessionAndFirstRunFlag() {
        TestSession.signOut()
        TestSession.resetOnboarding()
    }

    @Test
    fun activeSessionOpensAuthenticatedGraphWithoutShowingAuthDestination() {
        TestSession.seedActiveSession()
        TestSession.completeOnboarding()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var decorView: View
            scenario.onActivity { activity -> decorView = activity.window.decorView }

            eventually {
                onView(withId(R.id.bottom_nav_view))
                    .inRoot(withDecorView(`is`(decorView)))
                    .check(matches(isDisplayed()))
            }

            onView(withText(R.string.auth_title))
                .inRoot(withDecorView(`is`(decorView)))
                .check(doesNotExist())
        }
    }

    @Test
    fun firstRunOpensTheFlowInsteadOfTheBottomTabs() {
        TestSession.seedActiveSession()
        TestSession.resetOnboarding()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var decorView: View
            scenario.onActivity { activity -> decorView = activity.window.decorView }

            eventually {
                onView(withId(R.id.onboarding_root))
                    .inRoot(withDecorView(`is`(decorView)))
                    .check(matches(isDisplayed()))
            }

            // The flow owns the window until it is passed.
            onView(withId(R.id.bottom_nav_view))
                .inRoot(withDecorView(`is`(decorView)))
                .check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun aReplayTakesTheWindowBackFromTheTabs() {
        TestSession.seedActiveSession()
        TestSession.completeOnboarding()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var decorView: View
            scenario.onActivity { activity -> decorView = activity.window.decorView }
            eventually {
                onView(withId(R.id.bottom_nav_view))
                    .inRoot(withDecorView(`is`(decorView)))
                    .check(matches(isDisplayed()))
            }

            // What `Повторить первоначальную настройку` does: only the flag changes.
            TestSession.resetOnboarding()

            eventually {
                onView(withId(R.id.onboarding_root))
                    .inRoot(withDecorView(`is`(decorView)))
                    .check(matches(isDisplayed()))
            }
            onView(withId(R.id.bottom_nav_view))
                .inRoot(withDecorView(`is`(decorView)))
                .check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun tabsDoNotStackUpAfterTheFirstRunFlow() {
        TestSession.seedActiveSession()
        TestSession.resetOnboarding()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            eventually { scenario.onActivity { assertEquals(R.id.onboarding, root(it).navController.currentDestination?.id) } }
            TestSession.completeOnboarding()
            eventually { scenario.onActivity { assertEquals(R.id.navigation_home, root(it).navController.currentDestination?.id) } }

            for (destination in listOf(R.id.navigation_schedule, R.id.navigation_sport, R.id.navigation_home)) {
                onView(withId(destination)).perform(click())
                eventually {
                    scenario.onActivity {
                        assertEquals(destination, root(it).navController.currentDestination?.id)
                        val previous = root(it).navController.previousBackStackEntry?.destination?.id
                        assertTrue("The stack grew: $previous", previous == null || previous == R.id.navigation_home)
                    }
                }
            }
            onView(withId(R.id.navigation_schedule)).perform(click())
            eventually { scenario.onActivity { assertEquals(R.id.navigation_schedule, root(it).navController.currentDestination?.id) } }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            eventually {
                scenario.onActivity {
                    assertEquals(R.id.navigation_home, root(it).navController.currentDestination?.id)
                    assertNull(root(it).navController.previousBackStackEntry)
                }
            }
        }
    }

    private fun root(activity: MainActivity) =
        activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

    private fun eventually(assertion: () -> Unit) =
        TestUi.eventually(attempts = RETRY_COUNT, delayMillis = RETRY_DELAY_MILLIS, assertion = assertion)

    private companion object {
        const val RETRY_COUNT = 20
        const val RETRY_DELAY_MILLIS = 100L
    }
}
