package dev.alllexey.itmowidgets.feature.sport.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.ui.common.openSportMap
import org.koin.android.ext.android.inject

/**
 * Another user's sport (overlay `user_sport`), kept by name for the overlay graph. The screen is `UserSportRoute` from
 * `:shared:feature-sport`; its Koin ViewModel reads the ISU and the name from this Fragment's arguments
 * (`UserScreenArgs.ISU`, `UserScreenArgs.NAME`). This host closes the screen and starts the `geo:` map.
 */
@AndroidEntryPoint
class UserSportFragment : Fragment() {

    private val timeProvider: AcademicTimeProvider by inject()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            UserSportRoute(
                time = timeProvider,
                onBack = { closeScreen() },
                onOpenMap = ::openMap,
            )
        }

    /** Without a map app nothing opens. */
    private fun openMap(booking: SportBooking) {
        booking.extractBuildingAddress()?.let { requireContext().openSportMap(it) }
    }
}
