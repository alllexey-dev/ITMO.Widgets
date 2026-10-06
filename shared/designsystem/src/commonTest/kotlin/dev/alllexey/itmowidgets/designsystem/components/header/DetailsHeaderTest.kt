package dev.alllexey.itmowidgets.designsystem.components.header

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.MinTouchTarget
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class DetailsHeaderTest {
    @Test
    fun aTeacherWithAnIdIsAOneTargetRowWithItsActionLabel() = runComposeUiTest {
        val events = mutableListOf<String>()
        setContent {
            ItmoTheme {
                DetailsHeader(
                    title = TITLE,
                    date = DATE,
                    time = TIME,
                    teacher = DetailsTeacher(
                        DetailsFact(TEACHER_LABEL, TEACHER),
                        onClick = { events += TEACHER },
                        clickLabel = OPEN_PROFILE,
                        tone = Color.Green,
                        toneDescription = TONE,
                    ),
                    place = DetailsFact(PLACE_LABEL, PLACE),
                    map = DetailsMapAction(MAP, onClick = { events += MAP }),
                )
            }
        }

        onNodeWithText(TEACHER)
            .assertHeightIsAtLeast(MinTouchTarget)
            .assert(SemanticsMatcher("click label") { it.config[SemanticsActions.OnClick].label == OPEN_PROFILE })
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(TEACHER_LABEL, TONE)))
            .performClick()
        onNodeWithText(MAP).performClick()

        assertEquals(listOf(TEACHER, MAP), events)
        onNodeWithText(TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertTouchTargets()
        assertNoTextOverflow()
    }

    @Test
    fun aTeacherWithoutAnIdIsReadOnlyAndBlankRowsDisappear() = runComposeUiTest {
        setContent {
            ItmoTheme {
                DetailsHeader(
                    title = TITLE,
                    date = DATE,
                    time = TIME,
                    kind = " ",
                    teacher = DetailsTeacher(DetailsFact(TEACHER_LABEL, TEACHER)),
                    flow = DetailsFact(FLOW_LABEL, ""),
                )
            }
        }

        onNodeWithText(TEACHER).assertHasNoClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(TEACHER_LABEL)))
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(FLOW_LABEL)), useUnmergedTree = true)
            .assertDoesNotExist()
        onNodeWithText(MAP).assertDoesNotExist()
    }

    @Test
    fun dateTimeAndDurationReadAsOneItem() = runComposeUiTest {
        setContent { ItmoTheme { DetailsHeader(title = TITLE, date = DATE, time = TIME, duration = DURATION) } }

        onNodeWithText(DATE).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(DATE, TIME, DURATION).map { it.annotated() }))
    }

    private fun String.annotated() = AnnotatedString(this)

    private companion object {
        const val TITLE = "Математический анализ"
        const val DATE = "Понедельник, 7 сентября 2026"
        const val TIME = "08:20-09:50"
        const val DURATION = "90 мин"
        const val TEACHER_LABEL = "Преподаватель"
        const val TEACHER = "Иванов Иван"
        const val OPEN_PROFILE = "Открыть профиль"
        const val TONE = "Тон отзывов: скорее положительные"
        const val PLACE_LABEL = "Место"
        const val PLACE = "1506 · Кронверкский проспект, 49"
        const val FLOW_LABEL = "Поток"
        const val MAP = "Открыть на карте"
    }
}
