package dev.alllexey.itmowidgets.designsystem.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.allDrawableResources
import org.jetbrains.compose.resources.decodeToImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shared and custom icons of `docs/design/icons.tsv` are Android vector drawables in `composeResources`
 * (ADR 0028): CMP's own vector parser reads every one of them, the house format and the hand-drawn `custom` files
 * alike, and `Res.drawable` has an accessor per file.
 */
class IconResourcesTest {

    private val drawables = File(DRAWABLES).listFiles().orEmpty().sortedBy { it.name }

    @Test
    fun `every icon decodes with the CMP vector parser`() {
        assertTrue(drawables.isNotEmpty())
        drawables.forEach { file ->
            val text = file.readText()
            val vector = file.readBytes().decodeToImageVector(Density(1f))

            assertEquals(file.name, attribute(text, "width").dp, vector.defaultWidth)
            assertEquals(file.name, attribute(text, "height").dp, vector.defaultHeight)
            assertEquals(file.name, attribute(text, "viewportWidth"), vector.viewportWidth)
            assertEquals(file.name, attribute(text, "viewportHeight"), vector.viewportHeight)
            assertEquals(file.name, PATH.findAll(text).count(), paths(vector.root).size)
            assertTrue(file.name, paths(vector.root).all { it.pathData.isNotEmpty() })
        }
    }

    @Test
    fun `house format icons keep the 960 origin shift`() {
        val house = drawables.filter { HOUSE_GROUP in it.readText() }
        assertTrue(house.isNotEmpty())
        house.forEach { file ->
            val vector: ImageVector = file.readBytes().decodeToImageVector(Density(1f))
            val group = vector.root.single() as VectorGroup

            assertEquals(file.name, 960f, group.translationY)
        }
    }

    @Test
    fun `Res has a drawable for every file`() {
        assertEquals(drawables.map { it.nameWithoutExtension }, Res.allDrawableResources.keys.sorted())
    }

    private fun attribute(text: String, name: String): Float =
        Regex("""android:$name="([0-9.]+)(?:dp)?"""").find(text)?.groupValues?.get(1)?.toFloat()
            ?: error("no android:$name")

    private fun paths(node: VectorNode): List<VectorPath> = when (node) {
        is VectorPath -> listOf(node)
        is VectorGroup -> node.flatMap { paths(it) }
    }

    private companion object {
        const val DRAWABLES = "src/commonMain/composeResources/drawable"
        const val HOUSE_GROUP = """<group android:translateY="960">"""
        val PATH = Regex("""<path\b""")
    }
}
