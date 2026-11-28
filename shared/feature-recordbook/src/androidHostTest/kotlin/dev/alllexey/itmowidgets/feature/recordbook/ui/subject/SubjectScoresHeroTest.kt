package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTab
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookSamples
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

/** The `Баллы` hero: with a connected sheet a tap on it changes the total, without one it is no button. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SubjectScoresHeroTest {

    private val opened = mutableListOf<SheetScoresArgs>()
    private val actions = RecordbookSubjectActions(onOpenSheetScores = { opened += it })

    @Test
    fun aTapOnTheHeroWithASheetChangesTheTotal() = runComposeUiTest {
        setContent { ItmoTheme { RecordbookSubjectScreen(RecordbookSamples.subject(SubjectTab.SCORES, scores = "sheet"), actions) } }

        onNodeWithTag(SubjectTestTags.HERO)
            .assert(SemanticsMatcher("labelled Изменить итог") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Изменить итог" })
            .performClick()

        assertEquals(listOf(SheetScoresArgs.Step.TOTAL), opened.map { it.step })
    }

    @Test
    fun withoutASheetTheHeroIsNoButton() = runComposeUiTest {
        setContent { ItmoTheme { RecordbookSubjectScreen(RecordbookSamples.subject(SubjectTab.SCORES, scores = "plain"), actions) } }

        onNodeWithTag(SubjectTestTags.HERO).assertHasNoClickAction()
    }
}
