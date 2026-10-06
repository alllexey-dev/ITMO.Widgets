package dev.alllexey.itmowidgets.designsystem

import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdateFragment
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdatePreviewActivity
import dev.alllexey.itmowidgets.feature.update.ui.toScreenArguments
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The harness proof of [XmlReferenceCapture]: the update screen as a layout, as a Hilt Fragment and through its Hilt
 * debug host, into `app/screenshots/`. In `testDebug`, since the debug hosts exist only in debug builds.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class XmlReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    @get:Rule
    val stopKoin = StopKoinRule()

    private val references = XmlReferenceCapture(shots)

    /** `HiltTestApplication` starts no Koin graph; the screen's ViewModel comes from the release one. */
    @Before
    fun startKoin() {
        KoinStarter.ensureStarted(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun layout() = references.layout("AppUpdateLayout", R.layout.fragment_app_update) { view ->
        view.findViewById<TextView>(R.id.update_title).setText(R.string.app_update_title)
        view.findViewById<TextView>(R.id.update_versions).text =
            view.context.getString(R.string.app_update_versions, "2.1", "2.2")
        view.findViewById<TextView>(R.id.update_description).setText(R.string.app_update_description)
    }

    @Test
    fun fragment() = references.fragment("AppUpdateFragment_offer") {
        AppUpdateFragment().apply { arguments = AppUpdatePreviewActivity.offer.toScreenArguments() }
    }

    @Test
    fun debugHost() = references.host(
        "AppUpdateScreen_offer",
        AppUpdatePreviewActivity::class.java,
        appearance = { AppUpdatePreviewActivity.appearance = it },
        ready = { it.fragment.isResumed },
        view = { it.fragment.requireView() },
    )
}
