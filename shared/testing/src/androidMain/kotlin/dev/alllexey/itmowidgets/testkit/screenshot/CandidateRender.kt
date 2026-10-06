package dev.alllexey.itmowidgets.testkit.screenshot

import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance
import dev.alllexey.itmowidgets.designsystem.tokens.M3eCandidateApi
import dev.alllexey.itmowidgets.designsystem.tokens.M3eCandidates
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * The candidate render of M3-02a ([ShotsRun.variants]): each capture is recorded into
 * `<out>/captures/<variant>/<module>/` instead of being compared, and the module's `baselines` test lays them out as
 * one contact sheet per variant and appearance, `<out>/<module>_<variant>_<appearance>.png`, every preview scaled to
 * dp size under its name. M3-02 deletes it with the candidates.
 */
internal class CandidateRender(private val module: String, private val out: File = ShotsRun.variantOut) {

    fun captureFile(case: PreviewCase): File =
        File(out, "captures/${checkNotNull(case.variant)}/$module/${case.fileName}").also { it.parentFile.mkdirs() }

    /** Writes the sheets of every variant in light and dark; returns them. */
    fun writeSheets(bases: Collection<String>): List<File> = ShotsRun.variants.flatMap { variant ->
        PreviewAppearance.Default.map { appearance ->
            val tiles = bases.mapNotNull { base ->
                val file = File(out, "captures/$variant/$module/${BaselineDirectory.fileName(base, appearance)}")
                // Scaled on load: a module's full-size captures do not fit the test JVM's heap together.
                file.takeIf(File::isFile)?.let { base to scale(ImageIO.read(it)) }
            }
            File(out, "${module}_${variant}_${appearance.name}.png").also { sheet ->
                ImageIO.write(sheet(tiles, "$module - $variant - ${appearance.name}", appearance.dark), PNG, sheet)
            }
        }
    }

    private fun sheet(tiles: List<Pair<String, BufferedImage>>, title: String, dark: Boolean): BufferedImage {
        val rows = tiles.chunked(COLUMNS)
        val rowHeights = rows.map { row -> row.maxOf { it.second.height } + LABEL_HEIGHT }
        val width = GAP + COLUMNS * (TILE_WIDTH + GAP)
        val height = TITLE_HEIGHT + rowHeights.sumOf { it + GAP } + GAP
        val sheet = BufferedImage(width, max(height, TITLE_HEIGHT), BufferedImage.TYPE_INT_RGB)
        val graphics = sheet.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.color = if (dark) SHEET_DARK else SHEET_LIGHT
        graphics.fillRect(0, 0, sheet.width, sheet.height)
        val ink = if (dark) Color.WHITE else Color.BLACK
        graphics.color = ink
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, TITLE_FONT)
        graphics.drawString(title, GAP, TITLE_HEIGHT - GAP)
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, LABEL_FONT)
        var y = TITLE_HEIGHT
        rows.forEachIndexed { index, row ->
            row.forEachIndexed { column, (name, image) ->
                val x = GAP + column * (TILE_WIDTH + GAP)
                graphics.color = ink
                graphics.drawString(name.take(LABEL_CHARS), x, y + LABEL_HEIGHT - LABEL_BASELINE_GAP)
                graphics.drawImage(image, x, y + LABEL_HEIGHT, null)
            }
            y += rowHeights[index] + GAP
        }
        graphics.dispose()
        return sheet
    }

    /** From the capture density to dp size, at most [TILE_WIDTH] wide. */
    private fun scale(image: BufferedImage): BufferedImage {
        val factor = minOf(1.0 / DENSITY_FACTOR, TILE_WIDTH.toDouble() / image.width)
        val width = max(1, (image.width * factor).toInt())
        val height = max(1, (image.height * factor).toInt())
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(image, 0, 0, width, height, null)
        graphics.dispose()
        return scaled
    }

    companion object {
        /** Records wherever the run would compare. */
        val options: RoborazziOptions = ShotsCompare.options.copy(taskType = RoborazziTaskType.Record)

        /** Fails fast on a name the design system does not know. */
        @OptIn(M3eCandidateApi::class)
        fun checkVariants() {
            val unknown = ShotsRun.variants - M3eCandidates.names.toSet()
            check(unknown.isEmpty()) { "Unknown -Pshots.variant $unknown; one of ${M3eCandidates.names}" }
        }

        /** The module of a run: the parent of its baselines directory (`shared/<module>/screenshots`). */
        fun moduleOf(directory: BaselineDirectory): String = directory.dir.absoluteFile.parentFile.name

        private const val PNG = "png"
        private const val COLUMNS = 6
        private const val TILE_WIDTH = 411
        private const val GAP = 16
        private const val TITLE_HEIGHT = 56
        private const val TITLE_FONT = 28
        private const val LABEL_HEIGHT = 22
        private const val LABEL_FONT = 13
        private const val LABEL_BASELINE_GAP = 6
        private const val LABEL_CHARS = 60

        /** xxhdpi: three pixels per dp. */
        private const val DENSITY_FACTOR = 3.0
        private val SHEET_LIGHT = Color(0xE0, 0xE0, 0xE0)
        private val SHEET_DARK = Color(0x20, 0x20, 0x20)
    }
}
