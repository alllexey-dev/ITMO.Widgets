package dev.alllexey.itmowidgets.feature.sport.ui.sign

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SportLessonRequest
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * The `Запись` page of the sport tab, kept by name for `SportPagerAdapter`. The screen is `SportSignRoute` from
 * `:shared:feature-sport` on the activity's ViewModel; this host opens the lesson details sheet, hands its actions to
 * the route, relays shared-lesson links and shows the debug template-lesson Toast.
 */
@AndroidEntryPoint
class SportSignFragment : Fragment() {

    @Inject lateinit var timeProvider: AcademicTimeProvider

    private val viewModel: SportSignViewModel by activityViewModel()

    /** The details sheet's actions; the route checks each one again while the page is resumed. */
    private val sheetActions = Channel<SportSheetAction>(Channel.UNLIMITED)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            SportSignRoute(
                time = timeProvider,
                onOpenLesson = ::openDetails,
                onTemplateLesson = ::showTemplateMessage,
                viewModel = viewModel,
                sheetActions = sheetActions.receiveAsFlow(),
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.setFragmentResultListener(
            SportCommonDetailsBottomSheet.ACTION_REQUEST,
            viewLifecycleOwner
        ) { _, result ->
            sheetActions.trySend(
                SportSheetAction(
                    lessonId = result.getLong(SportCommonDetailsBottomSheet.RESULT_LESSON_ID),
                    action = result.getString(SportCommonDetailsBottomSheet.RESULT_ACTION)
                )
            )
        }
        // A shared lesson opens here even when the filters hide it; the ViewModel finds it in the merged catalog.
        parentFragmentManager.setFragmentResultListener(SportLessonRequest.KEY, viewLifecycleOwner) { _, result ->
            viewModel.openSharedLesson(
                result.getLong(SportLessonRequest.LESSON_ID),
                result.getBoolean(SportLessonRequest.PREDICTED)
            )
        }
    }

    private fun openDetails(lesson: SportLesson, busy: Boolean) {
        SportCommonDetailsBottomSheet.newInstance(lesson, actionsEnabled = true, busy = busy)
            .show(childFragmentManager, SportCommonDetailsBottomSheet.TAG)
    }

    private fun showTemplateMessage() {
        Toast.makeText(requireContext(), R.string.debug_sport_lesson_action_disabled, Toast.LENGTH_SHORT).show()
    }
}
