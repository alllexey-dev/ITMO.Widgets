package dev.alllexey.itmowidgets.feature.sport.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.core.home.HomeCardActions
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardTestTags
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.ui.home.preview.SportHomePreviewSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SportHomeCardTest {

    private val renderer = SportHomeCardRenderer(SportHomePreviewSamples.zone)

    @Test
    fun theRendererClaimsTheSportCard() {
        assertEquals(setOf(HomeCardKind.SPORT), renderer.kinds)
    }

    @Test
    fun aQueueRowOpensItsLessonAndTheCardOpensTheSportTab() = runComposeUiTest {
        val pending = mutableListOf<PendingSportDetailsArgs>()
        var sport = 0
        val actions = HomeCardActions(onPendingSport = { pending += it }, onOpenSport = { sport++ })
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                renderer.Content(SportHomePreviewSamples.longNameCard(), actions, Modifier.testTag(CARD))
            }
        }
        assertTouchTargets()

        onAllNodesWithTag(HomeCardTestTags.SPORT_ROW).assertCountEquals(3)
        onAllNodesWithTag(HomeCardTestTags.SPORT_ROW)[1].performClick()
        // Its centre holds a queue row; the card's own click opens the sport tab.
        onNodeWithTag(CARD).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(102L), pending.map { it.lessonId })
        assertEquals(1, sport)
    }

    private companion object {
        const val CARD = "card"
    }
}
