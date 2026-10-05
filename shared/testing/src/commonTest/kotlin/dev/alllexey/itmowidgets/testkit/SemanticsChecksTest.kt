package dev.alllexey.itmowidgets.testkit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SemanticsChecksTest {
    @Test
    fun touchTargetsPassAt48Dp() = runComposeUiTest {
        setContent { Box(Modifier.size(48.dp).clickable {}) }

        assertTouchTargets()
    }

    @Test
    fun touchTargetsFailBelow48Dp() = runComposeUiTest {
        setContent { Box(Modifier.size(width = 48.dp, height = 40.dp).clickable {}) }

        assertFailsWith<IllegalStateException> { assertTouchTargets() }
    }

    @Test
    fun touchTargetsCountOuterMinimumSize() = runComposeUiTest {
        // The shape of Material's minimumInteractiveComponentSize: a 24 dp clickable centred in 48 dp.
        setContent { Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).wrapContentSize().clickable {}.size(24.dp)) }

        assertTouchTargets()
    }

    @Test
    fun textThatFitsPasses() = runComposeUiTest {
        setContent { BasicText(LONG_NAME) }

        assertNoTextOverflow()
    }

    @Test
    fun shortTextInAWideAreaPasses() = runComposeUiTest {
        // A wrap-content text narrower than its constraints, as a button label or a centred title.
        setContent { Column(Modifier.width(320.dp)) { BasicText(SHORT_LABEL) } }

        assertNoTextOverflow()
    }

    @Test
    fun unwrappedTextWiderThanItsBoundsFails() = runComposeUiTest {
        setContent { Box(Modifier.width(40.dp)) { BasicText(LONG_NAME, softWrap = false) } }

        assertFailsWith<IllegalStateException> { assertNoTextOverflow() }
    }

    @Test
    fun ellipsizedTextFailsUnlessAllowed() = runComposeUiTest {
        setContent {
            Column {
                BasicText(LONG_NAME, Modifier.width(40.dp).testTag(SHORTENED), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        assertFailsWith<IllegalStateException> { assertNoTextOverflow() }
        assertNoTextOverflow(allowed = hasTestTag(SHORTENED))
    }

    private companion object {
        const val LONG_NAME = "Константинопольский Александр Владимирович"
        const val SHORTENED = "shortened"
        const val SHORT_LABEL = "Повторить"
    }
}
