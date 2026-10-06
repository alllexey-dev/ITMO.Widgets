package dev.alllexey.itmowidgets.feature.home.reference

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.HomeFixture
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's XML feed (`HomeFragment`) in the debug host with its `HomeFixture`, under the names of LH-2's `HomeScreen`
 * previews; LH-2's first record overwrites them and its diff is the parity diff. Synthetic cards only.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HomeReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-home")

    @After
    fun resetFixture() {
        SettingsNavigationTestActivity.homeFixture = HomeFixture()
    }

    @Test
    fun content() = feed("HomeScreenContentPreview", HomeFixture()) { it.feed().isShown }

    @Test
    fun longNames() = feed("HomeScreenLongNamesPreview", HomeFixture(cards = longNameCards())) { it.feed().isShown }

    @Test
    fun empty() = feed("HomeScreenEmptyPreview", HomeFixture(cards = emptyList())) {
        it.findViewById<View>(R.id.empty_state).isShown
    }

    @Test
    fun loading() = feed("HomeScreenLoadingPreview", HomeFixture(neverAnswers = true)) {
        it.findViewById<View>(R.id.loading).isShown
    }

    @Test
    fun refreshing() {
        // Once per launched screen: each appearance launches its own.
        var asked: View? = null
        feed("HomeScreenRefreshingPreview", HomeFixture()) { view ->
            if (asked !== view && view.feed().isShown) {
                // After the first silent refresh, which would drop a pull while it runs.
                asked = view
                SettingsNavigationTestActivity.homeSource!!.refreshDelayMs = PENDING_MS
                home(view).getViewModel<HomeViewModel>().refresh(RefreshMode.Pull)
            }
            view.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing
        }
    }

    private fun feed(preview: String, fixture: HomeFixture, shows: (View) -> Boolean) {
        SettingsNavigationTestActivity.homeFixture = fixture
        references.host(
            preview,
            SettingsNavigationTestActivity::class.java,
            appearance = { SettingsNavigationTestActivity.appearance = it },
            ready = { activity ->
                val screen = homeView(activity)
                // A gone list never lays out, so it always has pending updates.
                screen != null && shows(screen) && (!screen.feed().isShown || !screen.feed().hasPendingAdapterUpdates())
            },
            view = { homeView(it)!! },
        )
    }

    private fun homeView(activity: SettingsNavigationTestActivity): View? =
        activity.host.childFragmentManager.primaryNavigationFragment?.view

    private fun home(view: View): Fragment = FragmentManager.findFragment(view)

    private fun View.feed(): RecyclerView = findViewById(R.id.home_feed)

    private companion object {
        /** Longer than the capture's settle timeout, so the refresh stays pending. */
        const val PENDING_MS = 60_000L

        const val LONG_SUBJECT = "Проектирование и разработка распределённых информационных систем реального времени"

        fun longNameCards(): List<HomeCard> {
            val schedule = HomeFixture.schedule()
            return listOf(
                HomeCard.Hint(HomeHint.NOTIFICATIONS),
                HomeCard.ScheduleChanges(unread = 12, latest = HomeFixture.scheduleChangeSample(LONG_SUBJECT)),
                HomeCard.Marks(listOf(LONG_SUBJECT, "Теория вероятностей и математическая статистика", "Физика", "Химия")),
                HomeCard.FriendRequests(
                    listOf(
                        HomeFixture.user(300001, "Александра-Виктория Константинопольская-Преображенская"),
                        HomeFixture.user(300002, "Иван Петров"),
                        HomeFixture.user(300003, "Мария Иванова"),
                        HomeFixture.user(300004, "Пётр Сидоров"),
                    )
                ),
                HomeCard.Sport(
                    SportScoreSummary(attendances = 30, bonus = 5),
                    listOf(
                        HomeFixture.booking(1, 10).copy(sectionName = "Оздоровительная физическая культура для начинающих"),
                        HomeFixture.booking(2, 12, prediction = true),
                        HomeFixture.booking(3, 14),
                        HomeFixture.booking(4, 16),
                        HomeFixture.booking(5, 18),
                    )
                ),
                schedule.copy(
                    rows = listOf(
                        HomeScheduleRow.Lesson(
                            HomeFixture.lesson(1, "09:30", "11:00", LONG_SUBJECT, typeId = 5),
                            HomeLessonState.CURRENT,
                            progress = 0.2f,
                        ),
                        HomeScheduleRow.Lesson(
                            HomeFixture.lesson(2, "11:20", "12:50", LONG_SUBJECT, typeId = 10),
                            HomeLessonState.NEXT,
                        ),
                        HomeScheduleRow.PendingSport(HomeFixture.pending(1, 13), predicted = true),
                    ),
                    completed = 3,
                ),
            )
        }
    }
}
