package dev.alllexey.itmowidgets.testkit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.alllexey.itmowidgets.testkit.screenshot.ShotsCompare
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The harness probe: one fixed capture that proves record, verify and the golden location of the `shots` tasks
 * (`shared/testing/screenshots/ProbeSwatch.png`). Host-test runs skip it like every `*ScreenshotTest`.
 */
@RunWith(RobolectricTestRunner::class)
class ProbeScreenshotTest {
    @Test
    fun probeSwatch() {
        captureRoboImage("ProbeSwatch.png", ShotsCompare.options) {
            Row(Modifier.background(Color.White).padding(8.dp)) {
                Box(Modifier.size(24.dp).background(Color(0xFF6750A4)))
                BasicText("Проба 1.3", Modifier.padding(start = 8.dp), style = TextStyle(fontSize = 16.sp))
            }
        }
    }
}
