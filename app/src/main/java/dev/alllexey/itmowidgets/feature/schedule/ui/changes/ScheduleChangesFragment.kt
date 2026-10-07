package dev.alllexey.itmowidgets.feature.schedule.ui.changes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView

/**
 * The schedule changes destination (`@id/schedule_changes`), kept by name for the overlay graph and the notification.
 * The screen is `ScheduleChangesRoute` from `:shared:feature-schedule`; its ViewModel comes from Koin and opening the
 * screen marks everything read while the Fragment is started.
 */
@AndroidEntryPoint
class ScheduleChangesFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView { ScheduleChangesRoute(onBack = { closeScreen() }) }
}
