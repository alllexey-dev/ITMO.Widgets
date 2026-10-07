package dev.alllexey.itmowidgets.feature.schedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRouteActions
import dev.alllexey.itmowidgets.feature.schedule.ui.list.UserScheduleScreen
import javax.inject.Inject
import kotlinx.datetime.LocalDate
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Another user's schedule as a contextual screen: `UserScheduleScreen` around that user's `ScheduleRoute`. The
 * ViewModel reads the user from the Fragment's arguments (`UserScreenArgs.ISU` is `ScheduleViewModel.ARG_USER_ISU`).
 * Every lesson opens the lesson sheet; a user's schedule has no pending sport rows and no friend picker.
 */
@AndroidEntryPoint
class UserScheduleFragment : Fragment() {

    private val viewModel: ScheduleViewModel by viewModel()

    @Inject
    lateinit var timeProvider: AcademicTimeProvider

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val name = requireArguments().getString(UserScreenArgs.NAME)
        return itmoComposeView {
            UserScheduleScreen(name = name, onBack = { closeScreen() }) {
                ScheduleRoute(
                    ownTab = false,
                    actions = ScheduleRouteActions(onLessonClick = ::showLessonDetails),
                    viewModel = viewModel,
                    timeProvider = timeProvider,
                )
            }
        }
    }

    private fun showLessonDetails(lesson: Lesson, date: LocalDate) {
        if (childFragmentManager.findFragmentByTag(LessonDetailsBottomSheet.TAG) != null) return
        LessonDetailsBottomSheet.newInstance(lesson, date).show(childFragmentManager, LessonDetailsBottomSheet.TAG)
    }
}
