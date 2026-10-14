package dev.alllexey.itmowidgets.feature.schedule

import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshotTest
import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshots
import java.util.Locale
import org.junit.Rule
import org.junit.rules.ExternalResource

// Light, dark and the narrow phone. At 320 dp the week strip and the month grid are seven days across, narrower than
// ATF 48 dp targets: exempt there, as the sport strip.
@PreviewScreenshots(allAppearances = true, narrowTouchTargetExemptions = ["ScheduleFeed_", "ScheduleMonth_"])
class ScheduleScreenshotTest : PreviewScreenshotTest() {

    /** Compose plurals follow the process default locale, which the app keeps Russian; the JVM's is not ("5 пар"). */
    @get:Rule
    val russianPlurals = object : ExternalResource() {
        private var previous: Locale? = null

        override fun before() {
            previous = Locale.getDefault()
            Locale.setDefault(Locale.forLanguageTag("ru"))
        }

        override fun after() {
            previous?.let(Locale::setDefault)
        }
    }
}
