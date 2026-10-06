package dev.alllexey.itmowidgets.feature.sport

import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshotTest
import java.util.Locale
import org.junit.Rule
import org.junit.rules.ExternalResource

class SportScreenshotTest : PreviewScreenshotTest() {

    /**
     * Compose plurals follow the process default locale, which the app keeps Russian; the JVM's is not, and the home
     * card's counts ("Ещё 28 баллов") would take the English "other" form.
     */
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
