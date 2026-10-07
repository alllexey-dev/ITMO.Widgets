package dev.alllexey.itmowidgets.feature.schedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.navigation.ScheduleTodayRequest
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.openPendingSportDetails
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRouteActions
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRouteRequest
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.LocalDate
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The schedule tab (`@id/navigation_schedule`) and, through [newInstance], a user's schedule. The screen is
 * `ScheduleRoute` from `:shared:feature-schedule`; the Fragment keeps the navigation: the lesson and pending sport
 * sheets, the friend picker and its result, and the `Сегодня` request of `MainActivity`.
 */
@AndroidEntryPoint
class ScheduleFragment : Fragment() {

    private val viewModel: ScheduleViewModel by viewModel()

    @Inject
    lateinit var timeProvider: AcademicTimeProvider

    /** Fragment results for the route; buffered, so a result delivered before the first frame is not lost. */
    private val requests = Channel<ScheduleRouteRequest>(Channel.UNLIMITED)
    private val requestFlow = requests.receiveAsFlow()

    private val userIsu: Int? by lazy {
        val value = arguments?.getInt(ScheduleViewModel.ARG_USER_ISU, NO_USER_ISU) ?: NO_USER_ISU
        if (value == NO_USER_ISU) null else value
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            ScheduleRoute(
                ownTab = userIsu == null,
                actions = ScheduleRouteActions(
                    onLessonClick = ::showLessonDetails,
                    onPendingClick = ::showPendingSportDetails,
                    onPickFriend = ::openFriendSelector,
                ),
                requests = requestFlow,
                viewModel = viewModel,
                timeProvider = timeProvider,
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        parentFragmentManager.setFragmentResultListener(FriendSelectionContract.RESULT_KEY, viewLifecycleOwner) { _, bundle ->
            requests.trySend(ScheduleRouteRequest.SelectUser(bundle.selectedUser()))
        }
        if (userIsu == null) {
            requireActivity().supportFragmentManager.setFragmentResultListener(
                ScheduleTodayRequest.KEY,
                viewLifecycleOwner,
            ) { _, _ -> requests.trySend(ScheduleRouteRequest.Today) }
        }
    }

    /**
     * The own schedule's sport lessons are bookings: the navigator hands them to the sport tab's sheet with
     * `Отменить`. Everything else, and a friend's schedule, opens the lesson sheet here.
     */
    private fun showLessonDetails(lesson: Lesson, date: LocalDate) {
        if (childFragmentManager.findFragmentByTag(LessonDetailsBottomSheet.TAG) != null) return
        val navigator = activity as? AppNavigator
        if (navigator != null && userIsu == null && lesson.typeId.raw == SPORT_TYPE_ID) {
            navigator.openLessonDetails(lesson.toDetailsArgs(date))
            return
        }
        LessonDetailsBottomSheet.newInstance(lesson, date).show(childFragmentManager, LessonDetailsBottomSheet.TAG)
    }

    /** Through the navigator, so the sport tab's sheet with its actions can answer when it knows the queue. */
    private fun showPendingSportDetails(booking: PendingSportBooking) =
        openPendingSportDetails(booking.toDetailsArgs(timeProvider.timeZone))

    private fun openFriendSelector(selected: SelectedUser?) {
        findNavController().navigate(
            R.id.friend_selector,
            Bundle().apply {
                putInt(FriendSelectionContract.ARG_SELECTED_ISU, selected?.isu ?: FriendSelectionContract.NO_USER_ISU)
            },
        )
    }

    private fun Bundle.selectedUser(): SelectedUser? {
        if (getBoolean(FriendSelectionContract.RESULT_USE_MY_SCHEDULE)) return null
        return SelectedUser(
            isu = getInt(FriendSelectionContract.RESULT_USER_ISU),
            name = getString(FriendSelectionContract.RESULT_USER_NAME).orEmpty(),
            avatar = getString(FriendSelectionContract.RESULT_USER_PICTURE_URL),
        )
    }

    companion object {
        private const val SPORT_TYPE_ID = 11
        private const val NO_USER_ISU = -1

        fun newInstance(userIsu: Int?): ScheduleFragment {
            return ScheduleFragment().apply {
                arguments = Bundle().apply {
                    putInt(ScheduleViewModel.ARG_USER_ISU, userIsu ?: NO_USER_ISU)
                }
            }
        }
    }
}
