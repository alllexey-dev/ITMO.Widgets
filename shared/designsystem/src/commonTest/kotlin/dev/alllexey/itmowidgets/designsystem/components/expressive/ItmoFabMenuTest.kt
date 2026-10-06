package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoFabMenuTest {
    @Test
    fun toggleOpensAndAnItemClosesTheMenuBeforeItRunsInBothStyles() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        val events = mutableListOf<String>()
        val semantics = mutableMapOf<ItmoPlatformStyle, List<Any?>>()
        var expanded by mutableStateOf(false)
        setContent {
            ItmoTheme(platformStyle = style) {
                ItmoFabMenu(
                    items = listOf(ItmoFabMenuItem(ITEM, ColorPainter(Color.Black), onClick = { events += "item" })),
                    expanded = expanded,
                    onExpandedChange = {
                        events += "expanded=$it"
                        expanded = it
                    },
                    icon = ColorPainter(Color.Black),
                    label = TOGGLE,
                )
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            val config = onNodeWithContentDescription(TOGGLE).fetchSemanticsNode().config
            semantics[it] = listOf(SemanticsProperties.Role, SemanticsProperties.ToggleableState).map { key ->
                config.getOrNull(key)
            }
            onNodeWithContentDescription(TOGGLE).performClick()
            waitForIdle()
            onNodeWithText(ITEM).performClick()
            waitForIdle()
            assertTouchTargets(it.minTouchTarget)
        }

        val once = listOf("expanded=true", "expanded=false", "item")
        assertEquals(once + once, events)
        assertSameSemantics(semantics)
    }

    private fun assertSameSemantics(semantics: Map<ItmoPlatformStyle, List<Any?>>) =
        assertEquals(semantics.getValue(ItmoPlatformStyle.Material), semantics.getValue(ItmoPlatformStyle.Ios))

    private companion object {
        const val TOGGLE = "Быстрые действия"
        const val ITEM = "QR-пропуск"
    }
}
