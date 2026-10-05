package dev.alllexey.itmowidgets.feature.sport.reference

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.di.TimeModule
import dev.alllexey.itmowidgets.feature.sport.reference.SportReferenceFixtures.showsRows
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportSignFragment
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `Запись` under the name of LP-5c's `SportSignScreen` preview: the real page in the demo session on a fixed clock,
 * the filters, the week strip and today's catalog. LP-5c deletes this class.
 */
@HiltAndroidTest
@UninstallModules(TimeModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SportSignReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    @Inject
    lateinit var demo: DemoPreferences

    private val references = XmlReferenceCapture(shots, module = "feature-sport")

    @Before
    fun startDemo() {
        shots.inject()
        SportReferenceFixtures.startDemo(demo)
    }

    @Test
    fun content() = SportReferenceFixtures.withoutAnimations {
        references.fragment("SportSignScreen_content", ready = { fragment ->
            fragment.view?.let { view ->
                view.findViewById<RecyclerView>(R.id.main_recycler_view).showsRows() &&
                    view.findViewById<View>(R.id.sport_lesson_card_view)?.isShown == true
            } == true
        }) { SportSignFragment() }
    }

    @Module(includes = [SportReferenceTime::class])
    @InstallIn(SingletonComponent::class)
    interface FixedTime
}
