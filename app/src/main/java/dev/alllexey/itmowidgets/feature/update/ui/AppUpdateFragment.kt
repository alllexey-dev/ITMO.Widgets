package dev.alllexey.itmowidgets.feature.update.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import javax.inject.Inject

/**
 * Offers the release the backend reports as newer than this build (`app_update`, kept by name for the overlay
 * graph). The screen is `AppUpdateRoute` from `:shared:feature-account`; its Koin ViewModel reads the offer from this
 * Fragment's arguments. This host keeps what only Android does: the distribution's [UpdateAction].
 */
@AndroidEntryPoint
class AppUpdateFragment : Fragment() {
    @Inject lateinit var updateAction: UpdateAction

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            AppUpdateRoute(
                onUpdate = { unsupported, onFailed -> updateAction.start(requireActivity(), unsupported, onFailed) },
                onClose = { closeScreen() },
            )
        }
}
