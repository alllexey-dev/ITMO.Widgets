package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SkeletonTest {
    @Test
    fun takesTheHeightOfItsRows() = runComposeUiTest {
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                Skeleton(SkeletonStyle.List, Modifier.testTag(TAG), rows = 3)
            }
        }

        // Material's 16 dp screen margin (iOS: 20 pt) twice, three 64 dp rows, two 12 dp gaps; six rows
        // would not fit Robolectric's 470 dp window.
        onNodeWithTag(TAG).assertHeightIsEqualTo(248.dp)
    }

    @Test
    fun takesIosMarginsUnderTheIosStyle() = runComposeUiTest {
        setContent {
            ItmoTheme(platformStyle = ItmoPlatformStyle.Ios) {
                Skeleton(SkeletonStyle.List, Modifier.testTag(TAG), rows = 3)
            }
        }

        // UIKit's 20 pt margin twice, the same three 64 dp rows and two 12 dp gaps.
        onNodeWithTag(TAG).assertHeightIsEqualTo(256.dp)
    }

    @Test
    fun staysInsideShorterBounds() = runComposeUiTest {
        setContent {
            ItmoTheme {
                Box(Modifier.height(200.dp)) { Skeleton(SkeletonStyle.Cards, Modifier.testTag(TAG)) }
            }
        }

        onNodeWithTag(TAG).assertHeightIsEqualTo(200.dp)
    }

    private companion object {
        const val TAG = "skeleton"
    }
}
