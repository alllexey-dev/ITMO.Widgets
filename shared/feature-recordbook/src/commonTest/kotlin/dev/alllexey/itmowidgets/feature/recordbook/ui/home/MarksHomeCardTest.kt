package dev.alllexey.itmowidgets.feature.recordbook.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.ui.home.preview.MarksHomePreviewSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class MarksHomeCardTest {

    @Test
    fun theRendererClaimsTheMarksCard() {
        assertEquals(setOf(HomeCardKind.MARKS), MarksHomeCardRenderer.kinds)
    }

    @Test
    fun theCardReadsItsSubjectsOpensTheRecordbookAndIsMarkedRead() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val actions = HomeCardActions(onOpenMarks = { calls += "marks" }, onDismiss = { calls += "read-$it" })
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                MarksHomeCardRenderer.Content(MarksHomePreviewSamples.card(), actions, Modifier.testTag(CARD))
            }
        }
        assertTouchTargets()

        onNodeWithTag(CARD)
            .assertContentDescriptionEquals("Новые оценки, 3. Базы данных, Дискретная математика, Алгоритмы и структуры данных")
            .performClick()
        onNodeWithTag(HomeCardTestTags.DISMISS).performClick()

        assertEquals(listOf("marks", "read-${HomeCardKind.MARKS}"), calls)
    }

    private companion object {
        const val CARD = "card"
    }
}
