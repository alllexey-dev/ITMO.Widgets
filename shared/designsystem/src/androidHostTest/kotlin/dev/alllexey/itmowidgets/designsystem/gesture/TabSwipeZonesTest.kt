package dev.alllexey.itmowidgets.designsystem.gesture

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The zones the modifiers keep in [LocalTabSwipeRegistry], as a shell outside nested scrolling would read them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class TabSwipeZonesTest {

    @get:Rule
    val compose = createComposeRule()

    private val registry = TabSwipeRegistry()
    private val row = LazyListState()
    private var shown by mutableStateOf(true)

    @Test
    fun `a handover row reports its room in root pixels until it leaves`() {
        show()
        val inRow = centerOf(ROW)
        assertEquals(listOf(true, false), bothDirections(inRow))

        compose.runOnIdle { runBlocking { row.scrollBy(10_000f) } }
        compose.runOnIdle { assertEquals(listOf(false, true), bothDirections(inRow)) }

        shown = false
        compose.runOnIdle { assertEquals(listOf(false, false), bothDirections(inRow)) }
    }

    @Test
    fun `a blocked strip keeps both directions and the rest of the page keeps none`() {
        show()

        compose.runOnIdle {
            assertEquals(listOf(true, true), bothDirections(centerOf(STRIP)))
            assertEquals(listOf(false, false), bothDirections(Offset(10f, 600f)))
        }
    }

    @Test
    fun `without a registry the modifiers compose and report nothing`() {
        compose.setContent { Zones() }

        compose.onNodeWithTag(ROW).assertExists()
        compose.runOnIdle { assertEquals(listOf(false, false), bothDirections(centerOf(ROW))) }
    }

    private fun show() {
        compose.setContent { CompositionLocalProvider(LocalTabSwipeRegistry provides registry) { Zones() } }
    }

    @Composable
    private fun Zones() {
        if (!shown) return
        Column {
            Box(Modifier.fillMaxWidth().height(56.dp).testTag(STRIP).tabSwipeBlocked())
            LazyRow(Modifier.fillMaxWidth().height(80.dp).testTag(ROW).tabSwipeHandover(row), state = row) {
                items(10) { Box(Modifier.size(100.dp, 80.dp)) }
            }
        }
    }

    private fun centerOf(tag: String): Offset = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    private fun bothDirections(point: Offset): List<Boolean> =
        listOf(true, false).map { registry.wantsDrag(point, towardsNext = it) }

    private companion object {
        const val ROW = "row"
        const val STRIP = "strip"
    }
}
