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

/** XML references of the «Мои баллы» sheet (`SheetScoresBottomSheet`) under the future `SheetScoresSheet` preview. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SheetScoresSheetReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = RecordbookReferences(shots)

    @Test
    fun pickRow() = references.capture("SheetScoresSheet_pick-row", Scene.SHEET_PICK_ROW)

    @Test
    fun pickTotal() = references.capture("SheetScoresSheet_pick-total", Scene.SHEET_PICK_TOTAL)

    @Test
    fun failure() = references.capture("SheetScoresSheet_failure", Scene.SHEET_FAILURE)
}
