package dev.alllexey.itmowidgets.app.shell.entries

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.navigation.SportDetailsOpener
import dev.alllexey.itmowidgets.feature.sport.navigation.SportRoutes
import dev.alllexey.itmowidgets.feature.sport.ui.SportPage
import dev.alllexey.itmowidgets.feature.sport.ui.SportScreenTestTags
import dev.alllexey.itmowidgets.feature.sport.ui.details.SportDetailsSheetTestTags
import dev.alllexey.itmowidgets.feature.sport.ui.user.UserSportScreenTestTags
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinContext
import org.koin.core.context.startKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sport keys in the Compose shell with the real [shellEntries] and Koin ViewModels on synthetic data: the tab
 * root and its shared-lesson request, the details sheet replying to its opener and another user's sport.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class SportEntriesTest {

    @get:Rule(order = 0)
    val stopKoin = StopKoinRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sport = SportTestGraph(scope)
    private val navigator = Nav3AppNavigator(ShellBackStack(AppTab.SPORT))

    @Before
    fun startGraph() {
        val koin = startKoin { modules(sport.module(), socialTestModule()) }.koin
        compose.setContent {
            // Koin Compose caches the first graph it reads for the JVM; this test's graph replaces the stopped one.
            KoinContext(koin) {
                ItmoTheme { ShellContent(navigator, shellEntries(), ShellSurface.Tabs(demoBanner = false), onDemoSignIn = {}) }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun stopScope() = scope.cancel()

    @Test
    fun sportTabRootRendersTheSportScreen() {
        compose.onNodeWithTag(SportScreenTestTags.PAGER).assertIsDisplayed()
        compose.onNodeWithTag(SportScreenTestTags.tab(SportPage.MY)).assertIsSelected()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.TabRoot(AppTab.SPORT))).assertDoesNotExist()
    }

    @Test
    fun aSharedLessonRequestOpensTheSignPageOnce() {
        act { apply(EntryRoute(AppTab.SPORT, request = TabRequest.SportLesson(LESSON_ID))) }

        compose.onNodeWithTag(SportScreenTestTags.tab(SportPage.SIGN)).assertIsSelected()
        assertNull(navigator.pendingRequest(AppTab.SPORT))
    }

    @Test
    fun aMySportSheetActionAsksOnMySport() {
        sport.bookings.value = LoadState.Content(listOf(UPCOMING))
        compose.waitForIdle()
        act { open(SportRoutes.details(UPCOMING, SportDetailsOpener.MY)) }

        compose.onNodeWithTag(SportDetailsSheetTestTags.ACTION).performClick()
        compose.waitForIdle()

        assertEquals(emptyList<AppRoute>(), navigator.state.floating)
        compose.onNodeWithText(CANCEL_QUESTION).assertIsDisplayed()
    }

    @Test
    fun aSignSheetActionDoesNotReachMySport() {
        sport.bookings.value = LoadState.Content(listOf(UPCOMING))
        compose.waitForIdle()
        act { open(SportRoutes.details(UPCOMING, SportDetailsOpener.SIGN)) }

        compose.onNodeWithTag(SportDetailsSheetTestTags.ACTION).performClick()
        compose.waitForIdle()

        assertEquals(emptyList<AppRoute>(), navigator.state.floating)
        compose.onNodeWithText(CANCEL_QUESTION).assertDoesNotExist()
    }

    @Test
    fun anotherUsersSportRendersAndBackClosesIt() {
        act { open(AppRoutes.UserSport(SocialSamples.FRIEND_ISU, SocialSamples.FRIEND_NAME)) }

        compose.onNodeWithTag(UserSportScreenTestTags.STATE).assertIsDisplayed()
        compose.onNodeWithContentDescription(BACK).performClick()
        compose.waitForIdle()
        assertEquals(emptyList<AppRoute>(), navigator.state.overlays)
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    private companion object {
        const val LESSON_ID = 7L
        const val CANCEL_QUESTION = "Отменить запись на это занятие?"
        const val BACK = "Назад"

        /** A signed booking tomorrow, so it offers `Отменить` at the graph's fixed time. */
        val UPCOMING: SportBooking = SportCardFixtures.booking(LESSON_ID).let {
            val start = it.start + 30.days
            it.copy(start = start, end = start + 90.minutes)
        }
    }
}
