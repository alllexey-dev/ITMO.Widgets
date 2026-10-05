package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class PillTest {
    @Test
    fun pillsAreTextAndDotsAreSilent() = runComposeUiTest {
        setContent {
            ItmoTheme {
                Row {
                    Pill(MINE)
                    Pill(PENDING, tone = ItmoTheme.colorScheme.tertiary)
                    ToneDot(ItmoTheme.extendedColors.teacherLevelPositive)
                    ToneDot(null)
                }
            }
        }

        onNodeWithText(MINE).assertExists()
        onNodeWithText(PENDING).assertExists()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(2)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(0)
    }

    @Test
    fun anEmptyToneDotKeepsItsPlace() = runComposeUiTest {
        setContent {
            ItmoTheme {
                Row {
                    ToneDot(ItmoTheme.extendedColors.teacherLevelMixed, Modifier.testTag(TONED))
                    ToneDot(null, Modifier.testTag(EMPTY))
                }
            }
        }

        assertEquals(onNodeWithTag(TONED).fetchSemanticsNode().size, onNodeWithTag(EMPTY).fetchSemanticsNode().size)
    }

    private companion object {
        const val MINE = "моя"
        const val PENDING = "На проверке"
        const val TONED = "toned"
        const val EMPTY = "empty"
    }
}
