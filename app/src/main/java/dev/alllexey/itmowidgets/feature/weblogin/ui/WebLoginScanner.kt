package dev.alllexey.itmowidgets.feature.weblogin.ui

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel

/**
 * Google's scanner runs in Play services and needs no camera permission. Its answer may come after the sheet is
 * gone, so [model] is captured up front. The Compose shell's web sign-in entry calls it.
 */
fun scanWebLoginCode(context: Context, model: WebLoginViewModel) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> model.onScanned(barcode.rawValue.orEmpty()) }
        .addOnFailureListener { model.onScannerUnavailable() }
}
