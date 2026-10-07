package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

/** The titled shell of a user's schedule, as `UserScheduleFragment` drew it with `fragment_user_schedule.xml`. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserScheduleScreenTest {

    @Test
    fun theTitleNamesTheFirstNameOrTheScheduleAndBackLeaves() = runComposeUiTest {
        var name by mutableStateOf<String?>("Тестовый друг Константинопольский")
        var backs = 0
        setContent {
            ItmoTheme {
                UserScheduleScreen(name = name, onBack = { backs++ }) { Box(Modifier.testTag(BODY)) }
            }
        }

        onNodeWithText("Расписание: Тестовый").assertExists()
        onNodeWithTag(BODY).assertExists()
        assertTouchTargets()
        name = "  "
        waitForIdle()
        onNodeWithText("Расписание").assertExists()
        name = null
        waitForIdle()
        onNodeWithText("Расписание").assertExists()
        onNodeWithContentDescription("Назад").performClick()

        assertEquals(1, backs)
    }

    private companion object {
        const val BODY = "user_schedule_body"
    }
}
