package dev.alllexey.itmowidgets.feature.sport.reference

import android.view.View
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.di.TimeModule
import dev.alllexey.itmowidgets.feature.sport.reference.SportReferenceFixtures.showsRows
import dev.alllexey.itmowidgets.feature.sport.ui.user.UserSportFragment
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Another user's sport under the names of LP-6's `UserSportScreen` previews: the real page in the demo session on a
 * fixed clock, Ivan's volleyball visits and Dmitry without bookings. LP-6 deletes this class.
 */
@HiltAndroidTest
@UninstallModules(TimeModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class UserSportReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    @get:Rule
    val koin = SportReferenceKoin()

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
        references.fragment("UserSportScreen_content", ready = { fragment ->
            fragment.view?.findViewById<RecyclerView>(R.id.recycler_view)?.showsRows() == true
        }) { user(DemoPeople.IVAN.isu, DemoPeople.IVAN.name) }
    }

    @Test
    fun empty() = SportReferenceFixtures.withoutAnimations {
        references.fragment("UserSportScreen_empty", ready = { fragment ->
            fragment.view?.findViewById<View>(R.id.state_container)?.isShown == true
        }) { user(DemoPeople.DMITRY.isu, DemoPeople.DMITRY.name) }
    }

    private fun user(isu: Int, name: String) = UserSportFragment().apply {
        arguments = bundleOf(UserScreenArgs.ISU to isu, UserScreenArgs.NAME to name)
    }

    @Module(includes = [SportReferenceTime::class])
    @InstallIn(SingletonComponent::class)
    interface FixedTime
}
