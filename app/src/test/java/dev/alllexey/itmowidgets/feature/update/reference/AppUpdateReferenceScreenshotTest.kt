package dev.alllexey.itmowidgets.feature.update.reference

import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.designsystem.XmlReferenceCapture
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdateFragment
import dev.alllexey.itmowidgets.feature.update.ui.toScreenArguments
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Today's update screen under the names of LA-8's `AppUpdateScreen` previews, in
 * `shared/feature-account/screenshots/`: the offers of `AppUpdateVisualTest`, as `AppUpdatePreviewActivity` hosts
 * them (the Fragment alone renders the same pixels, `XmlReferenceScreenshotTest`), without a version check.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppUpdateReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = XmlReferenceCapture(shots, module = "feature-account")

    @Test
    fun supported() = update(
        "AppUpdateScreen_supported",
        offer(note = "Виджет расписания обновляется быстрее, зачётка помнит выбранный семестр."),
    )

    @Test
    fun unsupported() = update("AppUpdateScreen_unsupported", offer(unsupported = true))

    private fun update(preview: String, offer: AppUpdate) = references.fragment(preview) {
        AppUpdateFragment().apply { arguments = offer.toScreenArguments() }
    }

    private fun offer(note: String = "", unsupported: Boolean = false) =
        AppUpdate(AppVersionName("2.1"), AppVersionName("2.2"), note, unsupported)
}
