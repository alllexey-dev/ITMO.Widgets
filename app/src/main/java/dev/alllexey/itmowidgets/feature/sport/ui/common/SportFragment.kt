package dev.alllexey.itmowidgets.feature.sport.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.navigation.SportLessonRequest
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.ui.SportHostActions
import dev.alllexey.itmowidgets.feature.sport.ui.SportPage
import dev.alllexey.itmowidgets.feature.sport.ui.SportRoute
import dev.alllexey.itmowidgets.feature.sport.ui.SportSharedLesson
import dev.alllexey.itmowidgets.feature.sport.ui.rememberSportPagerState
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSheetAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.android.ext.android.inject

/**
 * The sport tab (`navigation_sport`), kept by name until L17 mounts the tab in the Nav3 shell. The screen is
 * `SportRoute` from `:shared:feature-sport`; its ViewModels live in this Fragment's store, which Navigation keeps on
 * the tab's saved back stack, so `Запись` keeps its week and filters across a round trip through the other tabs.
 *
 * This host does what only Android does: it relays shared-lesson links of the activity's [SportLessonRequest], opens
 * the details sheet on its child `FragmentManager` and hands the sheet's result to the page that opened it, starts the
 * `geo:` map and shows the debug template-lesson Toast.
 */
@AndroidEntryPoint
class SportFragment : Fragment() {

    private val timeProvider: AcademicTimeProvider by inject()

    private val sharedLessons = Channel<SportSharedLesson>(Channel.UNLIMITED)
    private val mySheetActions = Channel<SportSheetAction>(Channel.UNLIMITED)
    private val signSheetActions = Channel<SportSheetAction>(Channel.UNLIMITED)
    private val pageRequests = Channel<PageRequest>(Channel.CONFLATED)

    // One flow per channel for the Fragment's life, so recomposition never restarts the route's collectors.
    private val sharedLessonFlow: Flow<SportSharedLesson> = sharedLessons.receiveAsFlow()
    private val mySheetActionFlow: Flow<SportSheetAction> = mySheetActions.receiveAsFlow()
    private val signSheetActionFlow: Flow<SportSheetAction> = signSheetActions.receiveAsFlow()

    /** The page whose details sheet is open: the sheet's result goes back there, also after recreation. */
    private var sheetPage = SportPage.MY

    private var pager: PagerState? = null

    /** The page in front, or null before the screen is composed; the instrumented tests read it. */
    val currentPage: SportPage?
        get() = pager?.let { SportPage.at(it.currentPage) }

    private val host = SportHostActions(
        onOpenBooking = { booking -> openDetails(SportPage.MY, booking) },
        onOpenLesson = { lesson, busy -> openDetails(SportPage.SIGN, lesson, busy) },
        onOpenMap = { booking -> openMap(booking) },
        onTemplateLesson = { requireContext().showTemplateLessonNotice() },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(STATE_SHEET_PAGE)?.let { sheetPage = SportPage.valueOf(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            val pagerState = rememberSportPagerState()
            DisposableEffect(pagerState) {
                pager = pagerState
                onDispose { if (pager === pagerState) pager = null }
            }
            LaunchedEffect(pagerState) {
                for (request in pageRequests) {
                    if (request.animate) {
                        pagerState.animateScrollToPage(request.page.ordinal)
                    } else {
                        pagerState.scrollToPage(request.page.ordinal)
                    }
                }
            }
            SportRoute(
                time = timeProvider,
                host = host,
                pagerState = pagerState,
                sharedLessons = sharedLessonFlow,
                mySheetActions = mySheetActionFlow,
                signSheetActions = signSheetActionFlow,
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // A shared lesson opens on `Запись`; the page finds it in the merged catalog even when the filters hide it.
        requireActivity().supportFragmentManager.setFragmentResultListener(
            SportLessonRequest.KEY,
            viewLifecycleOwner
        ) { _, result ->
            sharedLessons.trySend(
                SportSharedLesson(
                    lessonId = result.getLong(SportLessonRequest.LESSON_ID),
                    predicted = result.getBoolean(SportLessonRequest.PREDICTED),
                )
            )
        }
        childFragmentManager.setFragmentResultListener(
            SportCommonDetailsBottomSheet.ACTION_REQUEST,
            viewLifecycleOwner
        ) { _, result ->
            val action = SportSheetAction(
                lessonId = result.getLong(SportCommonDetailsBottomSheet.RESULT_LESSON_ID),
                action = result.getString(SportCommonDetailsBottomSheet.RESULT_ACTION),
            )
            when (sheetPage) {
                SportPage.MY -> mySheetActions.trySend(action)
                SportPage.SIGN -> signSheetActions.trySend(action)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SHEET_PAGE, sheetPage.name)
    }

    /** Moves to the page at [index] (0 `Мой спорт`, 1 `Запись`), sliding there unless [animate] is off. */
    fun changeView(index: Int, animate: Boolean = true) {
        pageRequests.trySend(PageRequest(SportPage.at(index), animate))
    }

    private fun openDetails(page: SportPage, item: SportCommon, busy: Boolean = false) {
        sheetPage = page
        SportCommonDetailsBottomSheet.newInstance(item, actionsEnabled = true, busy = busy)
            .show(childFragmentManager, SportCommonDetailsBottomSheet.TAG)
    }

    /** Without a map app the menu item does nothing, as before. */
    private fun openMap(booking: SportBooking) {
        booking.extractBuildingAddress()?.let { requireContext().openSportMap(it) }
    }

    private data class PageRequest(val page: SportPage, val animate: Boolean)

    private companion object {
        const val STATE_SHEET_PAGE = "sport_sheet_page"
    }
}
