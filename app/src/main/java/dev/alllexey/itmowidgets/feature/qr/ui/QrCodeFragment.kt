package dev.alllexey.itmowidgets.feature.qr.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView

/**
 * The QR pass destination (`@id/qr_pass`), kept by name for the overlay graph, the tile and the shortcut. The
 * screen is `QrPassRoute` from `:shared:feature-qr`; its ViewModel comes from Koin, not from Hilt's default factory.
 */
@AndroidEntryPoint
class QrCodeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView { QrPassRoute(onBack = { closeScreen() }) }
}
