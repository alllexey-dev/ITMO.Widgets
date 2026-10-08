package dev.alllexey.itmowidgets.feature.schedule.ui.details

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.navigationArgs
import dev.alllexey.itmowidgets.core.navigation.putNavigationArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.MapLauncher
import dev.alllexey.itmowidgets.core.ui.navigation.openRoot
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.designsystem.host.SheetHeight
import dev.alllexey.itmowidgets.designsystem.host.SheetSpec
import javax.inject.Inject
import kotlinx.datetime.TimeZone

/**
 * A queued or predicted sport booking from the schedule, drawn by `PendingSportDetailsContent` of
 * `:shared:feature-schedule`: the shared header, then the booking conditions as one condition card. Managing the
 * queue happens on the sport tab. The Fragment keeps the stable entry points (class name, [TAG], [newInstance], the
 * argument key) and performs the effects: the `geo:` map, the profile and the sport tab after closing.
 */
@AndroidEntryPoint
class PendingSportDetailsBottomSheet : ItmoBottomSheetFragment() {

    @Inject lateinit var timeProvider: AcademicTimeProvider

    private val booking: PendingSportDetailsArgs by lazy {
        requireNotNull(requireArguments().navigationArgs<PendingSportDetailsArgs>(ARG_BOOKING))
    }

    /** What the body asks of the host; instrumented tests call it to check the effects without the Compose tree. */
    @VisibleForTesting
    internal val actions = PendingSportDetailsActions(
        onMap = ::openMap,
        onProfile = ::openProfile,
        onOpenSport = ::openSport,
        onClose = ::onCloseRequest,
    )

    override val spec: SheetSpec get() = SPEC

    @Composable
    override fun SheetContent() {
        PendingSportDetailsContent(
            PendingSportDetailsSheetState(booking, timeProvider.timeZone),
            actions,
            Modifier.fillMaxSize(),
        )
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.backgroundTintList = ColorStateList.valueOf(
                requireContext().color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLowest)
            )
            (it.background as? MaterialShapeDrawable)?.elevation = 0f
        }
    }

    /** The profile is a contextual screen above the tabs; the sheet has nothing to add once it opens. */
    private fun openProfile(isu: Int) {
        dismiss()
        openUserProfile(isu)
    }

    /** Managing the queue happens on the sport tab. */
    private fun openSport() {
        dismiss()
        openRoot(AppRoot.SPORT)
    }

    private fun openMap() = requireContext().openPendingSportMap(booking, requireView())

    companion object {
        const val TAG = "PendingSportDetailsBottomSheet"
        private const val ARG_BOOKING = "arg_pending_booking"

        /** 90 % of the screen however short the content, as the View sheet opened. */
        private val SPEC = SheetSpec(height = SheetHeight.Tall)

        fun newInstance(booking: PendingSportBooking, zone: TimeZone): PendingSportDetailsBottomSheet =
            newInstance(booking.toDetailsArgs(zone))

        fun newInstance(args: PendingSportDetailsArgs): PendingSportDetailsBottomSheet = PendingSportDetailsBottomSheet().apply {
            arguments = Bundle().apply { putNavigationArgs(ARG_BOOKING, args) }
        }
    }
}

/**
 * Opens the room of [booking] in a `geo:` handler; without one a snackbar on [anchor] says so. Shared by
 * [PendingSportDetailsBottomSheet] and the Compose shell's pending sport entry.
 */
internal fun Context.openPendingSportMap(booking: PendingSportDetailsArgs, anchor: View) {
    val destination = MapDestination(label = booking.sectionName, address = booking.roomName)
    if (!MapLauncher.open(this, destination)) {
        Snackbar.make(anchor, R.string.schedule_map_unavailable, Snackbar.LENGTH_SHORT).show()
    }
}
