package dev.alllexey.itmowidgets.feature.schedule.reference

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import dev.alllexey.itmowidgets.feature.schedule.reference.ScheduleReferenceFixtures.CHANGED_PAIR_ID
import dev.alllexey.itmowidgets.feature.schedule.reference.ScheduleReferenceFixtures.TEACHER_ISU
import dev.alllexey.itmowidgets.feature.schedule.reference.ScheduleReferenceFixtures.today
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsBottomSheet
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `LessonDetailsContent` of LS-5a before its port: the lesson sheet over the schedule host, with the opt-in on and
 * the friends, the teacher's tone and the change in memory. The sheet is a dialog window, so the capture is the sheet
 * itself, not the activity behind it.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LessonDetailsReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-schedule")

    @Before
    fun hostDefaults() = ScheduleReferenceFixtures.resetScheduleHost()

    @After
    fun resetHost() = ScheduleReferenceFixtures.resetScheduleHost()

    /** Everything a lesson can carry: teacher with a profile, flow, room, link, note, the change and two friends. */
    @Test
    fun full() = capture(
        "LessonDetailsContent_full",
        ScheduleReferenceFixtures.lesson(
            CHANGED_PAIR_ID, LocalTime.of(10, 0), "Физика", 3, "Практика", teacherIsu = TEACHER_ISU, room = "2202",
            building = "ул. Ломоносова, 9", note = "Организационная информация о занятии",
            zoomUrl = "https://example.invalid/meeting", zoomPassword = "1234",
        ).toArgs(),
        Backend(friends = { AppResult.Success(ScheduleReferenceFixtures.friends()) }),
    )

    @Test
    fun friendsLoading() = capture("LessonDetailsContent_friends-loading", plainLesson(), Backend(friends = {
        awaitCancellation()
    }))

    @Test
    fun friendsError() = capture("LessonDetailsContent_friends-error", plainLesson(), Backend(friends = {
        AppResult.Failure(AppError.Network)
    }))

    @Test
    fun friendsEmpty() = capture("LessonDetailsContent_friends-empty", plainLesson(), Backend(friends = {
        AppResult.Success(emptyList())
    }))

    @Test
    fun teacherLevel() = capture(
        "LessonDetailsContent_teacher-level",
        plainLesson(teacherIsu = TEACHER_ISU),
        Backend(friends = { AppResult.Success(emptyList()) }, levels = mapOf(TEACHER_ISU to TeacherLevel.POSITIVE)),
    )

    /** No teacher, room or link, an unknown subject: the sheet keeps only the time. */
    @Test
    fun minimal() = capture(
        "LessonDetailsContent_minimal",
        LessonDetailsArgs(
            pairId = 7, date = today.toString(), subjectName = "", typeId = 5, format = "", start = "10:00",
            end = "11:30", teacherFio = null, teacherIsu = null, room = null, building = null, buildingId = null,
            mainBuildingId = null, note = null, zoomUrl = null, zoomPassword = null, zoomInfo = null,
        ),
        Backend(enabled = false),
    )

    private fun plainLesson(teacherIsu: Int? = null) = ScheduleReferenceFixtures.lesson(
        3, LocalTime.of(11, 40), "Программирование", 2, "Лабораторная", teacherIsu = teacherIsu,
    ).toArgs()

    private fun Lesson.toArgs(): LessonDetailsArgs = toDetailsArgs(today)

    private fun capture(preview: String, lesson: LessonDetailsArgs, backend: Backend) =
        ScheduleReferenceFixtures.onEachLaunch(ScheduleLifecycleTestActivity::class.java, onCreated = { activity ->
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(backend, false)
            LessonDetailsBottomSheet.newInstance(lesson)
                .show(activity.supportFragmentManager, LessonDetailsBottomSheet.TAG)
        }) {
            references.host(
                preview,
                ScheduleLifecycleTestActivity::class.java,
                appearance = { ScheduleLifecycleTestActivity.appearance = it },
                ready = { activity -> activity.sheet()?.let { it.isResumed && it.requireView().height > 0 } == true },
                view = { activity -> ScheduleReferenceFixtures.sheetSurface(activity, checkNotNull(activity.sheet())) },
            )
        }

    private fun ScheduleLifecycleTestActivity.sheet() =
        supportFragmentManager.findFragmentByTag(LessonDetailsBottomSheet.TAG) as LessonDetailsBottomSheet?

    /**
     * The sheet's view model over [CustomServicesRepository], [LessonFriendsRepository] and [TeacherLevelsRepository]
     * in memory, and the host's change; set before Hilt would build the real one.
     */
    private class Backend(
        private val enabled: Boolean = true,
        private val friends: suspend () -> AppResult<List<UserSummary>> = { AppResult.Success(emptyList()) },
        private val levels: Map<Int, TeacherLevel> = emptyMap(),
    ) : FragmentManager.FragmentLifecycleCallbacks() {

        override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
            if (fragment !is LessonDetailsBottomSheet) return
            val arguments = fragment.requireArguments()
            ViewModelProvider(fragment, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = LessonDetailsViewModel(
                    SavedStateHandle(
                        mapOf(
                            LessonDetailsViewModel.ARG_PAIR_ID to arguments.getLong(LessonDetailsViewModel.ARG_PAIR_ID),
                            LessonDetailsViewModel.ARG_DATE to arguments.getString(LessonDetailsViewModel.ARG_DATE),
                            LessonDetailsViewModel.ARG_TEACHER_ISU to
                                arguments.getInt(LessonDetailsViewModel.ARG_TEACHER_ISU, 0),
                        ),
                    ),
                    object : LessonFriendsRepository {
                        override suspend fun friendsOnLesson(pairId: Long, date: LocalDate) = friends()
                    },
                    object : CustomServicesRepository {
                        override fun observeEnabled() = MutableStateFlow(enabled)
                        override suspend fun isEnabled() = enabled
                        override suspend fun setEnabled(enabled: Boolean) = Unit
                    },
                    object : TeacherLevelsRepository {
                        override suspend fun levels(isus: Set<Int>) = levels.filterKeys { it in isus }
                    },
                    FakeScheduleChangesRepository(ScheduleReferenceFixtures.roomChange()),
                ) as T
            })[LessonDetailsViewModel::class.java]
        }
    }
}
