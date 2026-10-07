package dev.alllexey.itmowidgets.feature.weblogin.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.ui.expandToContent
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.designsystem.host.SheetSpec
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Approves a browser's sign-in to the web version with a scanned QR or a typed code, drawn by `WebLoginSheetRoute`
 * of `:shared:feature-account`. The Fragment keeps the stable entry points (class name, [TAG]), fits the sheet to its
 * content with the window resized for the keyboard, and runs Google's QR scanner.
 */
@AndroidEntryPoint
class WebLoginBottomSheet : ItmoBottomSheetFragment() {
    private val viewModel: WebLoginViewModel by viewModel()

    override val spec: SheetSpec get() = SPEC

    @Composable
    override fun SheetContent() {
        WebLoginSheetRoute(onScan = ::scan, onClose = ::onCloseRequest, viewModel = viewModel)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // A new step changes the sheet's height; a keystroke or progress does not.
        viewModel.uiState.map { it::class }.distinctUntilChanged().drop(1)
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { view.post { if (isAdded) expandToContent() } }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun scan() = scanWebLoginCode(requireContext(), viewModel)

    companion object {
        const val TAG = "WebLoginBottomSheet"

        /** As tall as the step, up to 90 % of the screen; the keyboard lifts the sheet over the field. */
        private val SPEC = SheetSpec(textInput = true)
    }
}

/**
 * Google's scanner runs in Play services and needs no camera permission. Its answer may come after the sheet is
 * gone, so [model] is captured up front. Both hosts of `WebLoginSheetRoute` call it (this sheet and the Compose
 * shell's web sign-in entry).
 */
fun scanWebLoginCode(context: Context, model: WebLoginViewModel) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> model.onScanned(barcode.rawValue.orEmpty()) }
        .addOnFailureListener { model.onScannerUnavailable() }
}
