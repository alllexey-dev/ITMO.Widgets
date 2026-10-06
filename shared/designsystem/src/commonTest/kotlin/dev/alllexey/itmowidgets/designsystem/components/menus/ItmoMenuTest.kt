package dev.alllexey.itmowidgets.designsystem.components.menus

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
class ItmoMenuTest {
    @Test
    fun anItemDismissesTheMenuAndThenRunsInBothStyles() = runComposeUiTest {
        var style by mutableStateOf(ItmoPlatformStyle.Material)
        var expanded by mutableStateOf(true)
        val events = mutableListOf<String>()
        val roles = mutableMapOf<ItmoPlatformStyle, Role?>()
        setContent {
            ItmoTheme(platformStyle = style) {
                Box {
                    Text(ANCHOR)
                    ItmoMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            events += "dismiss"
                            expanded = false
                        },
                        groups = listOf(
                            listOf(ItmoMenuItem(OPEN, onClick = { events += OPEN })),
                            listOf(
                                ItmoMenuItem(EDIT, onClick = { events += EDIT }, enabled = false),
                                ItmoMenuItem(DELETE, onClick = { events += DELETE }, destructive = true),
                            ),
                        ),
                    )
                }
            }
        }

        ItmoPlatformStyle.entries.forEach {
            style = it
            expanded = true
            waitForIdle()
            roles[it] = onNodeWithText(OPEN).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
            onNodeWithText(EDIT).assertIsNotEnabled().performClick()
            assertTouchTargets(it.minTouchTarget)
            onNodeWithText(DELETE).performClick()
            waitForIdle()
            onNodeWithText(DELETE).assertDoesNotExist()
            onNodeWithText(ANCHOR).assertIsDisplayed()
        }

        assertEquals(listOf("dismiss", DELETE, "dismiss", DELETE), events)
        assertEquals(roles.getValue(ItmoPlatformStyle.Material), roles.getValue(ItmoPlatformStyle.Ios))
    }

    private companion object {
        const val ANCHOR = "Ссылка"
        const val OPEN = "Открыть"
        const val EDIT = "Изменить"
        const val DELETE = "Удалить"
    }
}
