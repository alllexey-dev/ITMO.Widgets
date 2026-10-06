package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ProgressButtonTest {
    @Test
    fun secondTapIsIgnoredWhileInProgress() = runComposeUiTest {
        var clicks = 0
        var busy by mutableStateOf(false)
        setContent {
            ItmoTheme {
                ProgressButton(LABEL, onClick = { clicks++; busy = true }, inProgress = busy)
            }
        }

        onNodeWithText(LABEL).performClick()
        onNodeWithText(LABEL).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun progressKeepsGeometryAndLabelInEveryStyle() = runComposeUiTest {
        var busy by mutableStateOf(false)
        setContent {
            ItmoTheme {
                Column {
                    ProgressButtonStyle.entries.forEach { style ->
                        ProgressButton(LABEL, {}, Modifier.testTag("$style"), style, inProgress = busy)
                        ProgressButton(LABEL, {}, Modifier.testTag("$style+icon"), style, inProgress = busy, icon = Icon)
                    }
                }
            }
        }
        val tags = ProgressButtonStyle.entries.flatMap { listOf("$it", "$it+icon") }
        val idle = tags.associateWith { onNodeWithTag(it).getBoundsInRoot() }

        busy = true
        waitForIdle()

        tags.forEach { assertEquals(idle.getValue(it), onNodeWithTag(it).getBoundsInRoot(), it) }
        onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(tags.size)
        tags.forEach { onNodeWithTag(it).assertTextEquals(LABEL) }
        assertTouchTargets()
    }

    @Test
    fun theIosButtonKeepsItsGeometryAndIgnoresASecondTapInEveryStyle() = runComposeUiTest {
        var clicks = 0
        var busy by mutableStateOf(false)
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Ios) {
                Column {
                    ProgressButtonStyle.entries.forEach { style ->
                        ProgressButton(LABEL, { clicks++ }, Modifier.testTag("$style"), style, inProgress = busy)
                        ProgressButton(LABEL, {}, Modifier.testTag("$style+icon"), style, inProgress = busy, icon = Icon)
                    }
                }
            }
        }
        val tags = ProgressButtonStyle.entries.flatMap { listOf("$it", "$it+icon") }
        val idle = tags.associateWith { onNodeWithTag(it).getBoundsInRoot() }

        busy = true
        waitForIdle()
        ProgressButtonStyle.entries.forEach { onNodeWithTag("$it").performClick() }

        assertEquals(0, clicks)
        tags.forEach { assertEquals(idle.getValue(it), onNodeWithTag(it).getBoundsInRoot(), it) }
        onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(tags.size)
        tags.forEach { onNodeWithTag(it).assertTextEquals(LABEL).assert(hasRole(Role.Button)) }
        assertTouchTargets(ItmoPlatformStyle.Ios.minTouchTarget)
    }

    @Test
    fun theIosFilledButtonIsAMediumCapsuleInA44PtTarget() = runComposeUiTest {
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Ios) { ProgressButton(LABEL, {}, Modifier.testTag(LABEL)) }
        }

        val node = onNodeWithTag(LABEL).fetchSemanticsNode()
        with(node.layoutInfo.density) {
            assertEquals(ItmoPlatformStyle.Ios.minTouchTarget.roundToPx(), node.layoutInfo.height, "target")
            assertEquals(IosMetrics.buttonHeight.roundToPx(), node.size.height, "capsule")
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private companion object {
        const val LABEL = "Записаться"
        val Icon: Painter = ColorPainter(Color.Black)
    }
}
