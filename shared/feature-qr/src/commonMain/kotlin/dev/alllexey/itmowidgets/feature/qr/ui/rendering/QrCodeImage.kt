package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import kotlin.math.roundToInt

/**
 * A QR code as the pass screen draws it: [matrix] (outer index x, inner y, as [QrCodeGenerator.toBooleans] returns
 * it) on a rounded square of [QrColors.background]. The geometry of `QrBitmapRenderer.render` in `:app`: modules
 * snapped to whole pixels inside a relative padding, a module corner rounded only where no neighbour touches it, and
 * the inner corner of an L of dark modules filled round. Square; the largest square that fits [modifier].
 */
@Composable
fun QrCodeImage(matrix: List<List<Boolean>>, colors: QrColors, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val side = size.minDimension
        translate((size.width - side) / 2, (size.height - side) / 2) {
            drawQrCode(matrix, colors, side)
        }
    }
}

private fun DrawScope.drawQrCode(matrix: List<List<Boolean>>, colors: QrColors, side: Float) {
    drawRoundRect(colors.background, size = Size(side, side), cornerRadius = CornerRadius(side * RELATIVE_ROUNDING))
    val count = matrix.size
    if (count == 0) return

    val module = (side * (1 - RELATIVE_PADDING * 2) / count).roundToInt().toFloat()
    val padding = (side - module * count) / 2
    val radius = CornerRadius(module * MODULE_ROUNDING)
    fun dark(x: Int, y: Int) = matrix.getOrNull(x)?.getOrNull(y) == true
    fun cell(x: Int, y: Int): Rect {
        val left = (x * module + padding).roundToInt().toFloat()
        val top = (y * module + padding).roundToInt().toFloat()
        return Rect(Offset(left, top), Size(module, module))
    }

    val modules = Path()
    val innerFills = Path()
    val innerHoles = Path()
    for (x in 0 until count) {
        for (y in 0 until count) {
            val top = dark(x, y - 1)
            val bottom = dark(x, y + 1)
            val left = dark(x - 1, y)
            val right = dark(x + 1, y)
            if (dark(x, y)) {
                modules.addRoundRect(
                    RoundRect(
                        cell(x, y),
                        topLeft = radius.takeIf { !top && !left } ?: CornerRadius.Zero,
                        topRight = radius.takeIf { !top && !right } ?: CornerRadius.Zero,
                        bottomRight = radius.takeIf { !bottom && !right } ?: CornerRadius.Zero,
                        bottomLeft = radius.takeIf { !bottom && !left } ?: CornerRadius.Zero,
                    ),
                )
            } else {
                // A light module inside an L of three dark ones: module colour under a background cell rounded
                // toward that corner, so the dark shape turns the corner smoothly.
                val topLeft = top && left && dark(x - 1, y - 1)
                val topRight = top && right && dark(x + 1, y - 1)
                val bottomRight = bottom && right && dark(x + 1, y + 1)
                val bottomLeft = bottom && left && dark(x - 1, y + 1)
                if (topLeft || topRight || bottomRight || bottomLeft) {
                    val rect = cell(x, y)
                    innerFills.addRect(rect)
                    innerHoles.addRoundRect(
                        RoundRect(
                            rect,
                            topLeft = radius.takeIf { topLeft } ?: CornerRadius.Zero,
                            topRight = radius.takeIf { topRight } ?: CornerRadius.Zero,
                            bottomRight = radius.takeIf { bottomRight } ?: CornerRadius.Zero,
                            bottomLeft = radius.takeIf { bottomLeft } ?: CornerRadius.Zero,
                        ),
                    )
                }
            }
        }
    }
    drawPath(modules, colors.foreground)
    drawPath(innerFills, colors.foreground)
    drawPath(innerHoles, colors.background)
}

/** `QrToolkit.defaultRelativePadding()` and `defaultRounding()` of `:app`; a module rounds to a circle. */
private const val RELATIVE_PADDING = 0.05f
private const val RELATIVE_ROUNDING = 0.06f
private const val MODULE_ROUNDING = 0.5f

private val previewMatrix: List<List<Boolean>>
    get() = QrCodeGenerator().let { it.toBooleans(it.generate("ITMO-TEST")) }

@Preview
@Composable
private fun QrCodeImageStaticPreview() = ItmoPreview {
    QrCodeImage(previewMatrix, QrColors.Static, Modifier.padding(16.dp).size(240.dp))
}

@Preview
@Composable
private fun QrCodeImageDynamicPreview() = ItmoPreview {
    QrCodeImage(previewMatrix, qrColors(dynamic = true), Modifier.padding(16.dp).size(240.dp))
}
