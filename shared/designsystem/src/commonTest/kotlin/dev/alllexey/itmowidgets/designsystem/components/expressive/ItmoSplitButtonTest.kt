package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
class ItmoSplitButtonTest {
    @Test
    fun leadingHalfRunsTheActionAndTrailingHalfOpensTheMenuInBothStyles() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        val events = mutableListOf<String>()
        val semantics = mutableMapOf<ItmoPlatformStyle, List<Any?>>()
        setContent {
            ItmoTheme(platformStyle = style) {
                ItmoSplitButton(
                    label = ACTION,
                    onClick = { events += "action" },
                    menuLabel = MENU,
                    options = listOf(ItmoSplitButtonOption(OPTION, onClick = { events += "option" })),
                    menuExpanded = false,
                    onMenuExpandedChange = { events += "menu=$it" },
                )
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            onNodeWithText(ACTION).performClick()
            val config = onNodeWithContentDescription(MENU).fetchSemanticsNode().config
            semantics[it] = listOf(SemanticsProperties.Role, SemanticsProperties.ToggleableState).map { key ->
                config.getOrNull(key)
            }
            onNodeWithContentDescription(MENU).performClick()
            assertTouchTargets(it.minTouchTarget)
        }

        assertEquals(listOf("action", "menu=true", "action", "menu=true"), events)
        assertSameSemantics(semantics)
    }

    @Test
    fun anOptionClosesTheMenuBeforeItRunsInBothStyles() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        var expanded by mutableStateOf(true)
        val events = mutableListOf<String>()
        setContent {
            ItmoTheme(platformStyle = style) {
                ItmoSplitButton(
                    label = ACTION,
                    onClick = {},
                    menuLabel = MENU,
                    options = listOf(ItmoSplitButtonOption(OPTION, onClick = { events += "option" })),
                    menuExpanded = expanded,
                    onMenuExpandedChange = {
                        events += "menu=$it"
                        expanded = it
                    },
                )
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            expanded = true
            waitForIdle()
            onNodeWithText(OPTION).performClick()
            waitForIdle()
        }

        assertEquals(listOf("menu=false", "option", "menu=false", "option"), events)
    }

    private fun assertSameSemantics(semantics: Map<ItmoPlatformStyle, List<Any?>>) =
        assertEquals(semantics.getValue(ItmoPlatformStyle.Material), semantics.getValue(ItmoPlatformStyle.Ios))

    private companion object {
        const val ACTION = "Экспорт в календарь"
        const val MENU = "Выбрать период"
        const val OPTION = "Эта неделя"
    }
}
