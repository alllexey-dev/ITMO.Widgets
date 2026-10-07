package dev.alllexey.itmowidgets.feature.sport.ui.my

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportFragment
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * The `Мой спорт` page of `SportPagerAdapter`, kept by name until L17 mounts the sport tab. The screen is
 * `SportMyScreen` from `:shared:feature-sport` over the Activity's `SportMyViewModel`, which the feed and the schedule
 * share. This host keeps what only Android does: the details sheet and its result, the map intent, the switch to
 * `Запись` and the cancellation errors, shown only while the page is the resumed one.
 */
@AndroidEntryPoint
class SportMyFragment : Fragment() {

    private val viewModel: SportMyViewModel by activityViewModel()
    private val timeProvider: AcademicTimeProvider by inject()
    private val snackbars = SnackbarHostState()

    /** The booking whose cancellation waits for the confirmation. */
    private var pendingCancel by mutableStateOf<SportBooking?>(null)

    private val actions = SportMyActions(
        onRefresh = { viewModel.refresh(RefreshMode.Pull) },
        onRetry = { viewModel.refresh(RefreshMode.Force) },
        onOpenSign = { (parentFragment as? SportFragment)?.changeView(1) },
        onOpenDetails = { booking ->
            SportCommonDetailsBottomSheet.newInstance(booking, actionsEnabled = true)
                .show(childFragmentManager, SportCommonDetailsBottomSheet.TAG)
        },
        onOpenMap = { booking -> openMap(booking) },
        onRequestCancel = { booking -> pendingCancel = booking },
        onConfirmCancel = { booking ->
            pendingCancel = null
            viewModel.cancelBooking(booking)
        },
        onDismissCancel = { pendingCancel = null },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            val state by viewModel.uiState.collectAsState()
            SportMyScreen(
                state = state,
                time = timeProvider,
                actions = actions,
                pendingCancel = pendingCancel,
                snackbarHostState = snackbars,
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        childFragmentManager.setFragmentResultListener(SportCommonDetailsBottomSheet.ACTION_REQUEST, viewLifecycleOwner) { _, result ->
            // The sheet offered its action some time ago: confirm only while the booking still offers the same one.
            viewModel.uiState.value.cancelCandidate(
                lessonId = result.getLong(SportCommonDetailsBottomSheet.RESULT_LESSON_ID),
                action = result.getString(SportCommonDetailsBottomSheet.RESULT_ACTION),
                now = timeProvider.now(),
            )?.let { pendingCancel = it }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            // ViewPager keeps the neighbouring page STARTED; only the page in front reports a failed cancellation.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.events.collect { snackbars.showSportMyEvent(it) }
            }
        }
        viewModel.ensureDataLoaded()
    }

    private fun openMap(booking: SportBooking) {
        val address = booking.extractBuildingAddress() ?: return
        val intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(address)}".toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No map app: the menu item does nothing, as before.
        }
    }
}
