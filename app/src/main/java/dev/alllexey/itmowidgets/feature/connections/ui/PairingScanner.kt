package dev.alllexey.itmowidgets.feature.connections.ui

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Google's scanner runs in Play services and needs no camera permission. Its answer may come after the screen is
 * gone, so the callbacks are captured up front: [onScanned] gets the QR's text, [onUnavailable] runs when the scanner
 * cannot start.
 */
fun scanPairingLink(context: Context, onScanned: (String) -> Unit, onUnavailable: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> onScanned(barcode.rawValue.orEmpty()) }
        .addOnFailureListener { onUnavailable() }
}
