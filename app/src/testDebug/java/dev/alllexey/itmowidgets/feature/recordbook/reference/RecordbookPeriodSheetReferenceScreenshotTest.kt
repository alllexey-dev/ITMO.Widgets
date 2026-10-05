package dev.alllexey.itmowidgets.feature.recordbook.reference

import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.designsystem.AppScreenshotRule
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewFixtures.Scene
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** XML references of the period sheet (`RecordbookPeriodBottomSheet`) under the future `RecordbookPeriodSheet` preview. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RecordbookPeriodSheetReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = RecordbookReferences(shots)

    @Test
    fun content() = references.capture("RecordbookPeriodSheet_content", Scene.PERIOD_SHEET)
}
