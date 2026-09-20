package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import android.graphics.Bitmap
import dev.alllexey.itmowidgets.core.qr.QrPassImages
import javax.inject.Inject

class ToolkitQrPassImages @Inject constructor(private val toolkit: QrToolkit) : QrPassImages {
    override suspend fun qr(hex: String): Bitmap = toolkit.generateQrBitmap(hex)

    override suspend fun spoiler(): Bitmap = toolkit.generateSpoilerBitmap()
}
