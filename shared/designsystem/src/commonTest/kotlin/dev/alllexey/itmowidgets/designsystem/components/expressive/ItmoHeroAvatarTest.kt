package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoHeroAvatarTest {
    @Test
    fun maskKeepsTheHeroSizeAndLabelInBothStates() = runComposeUiTest {
        var on by mutableStateOf(false)
        setContent {
            ItmoTheme(expressive = on) {
                ItmoHeroAvatar(name = NAME, pictureUrl = null, contentDescription = NAME)
            }
        }

        listOf(false, true).forEach {
            on = it
            onNodeWithContentDescription(NAME).assertWidthIsEqualTo(72.dp).assertHeightIsEqualTo(72.dp)
        }
    }

    private companion object {
        const val NAME = "Иванов Иван"
    }
}
