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

/** XML references of the recordbook list (`RecordbookFragment`) under the future `RecordbookScreen` preview. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RecordbookScreenReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = RecordbookReferences(shots)

    @Test
    fun content() = references.capture("RecordbookScreen_content", Scene.LIST_CONTENT)

    @Test
    fun empty() = references.capture("RecordbookScreen_empty", Scene.LIST_EMPTY)

    @Test
    fun error() = references.capture("RecordbookScreen_error", Scene.LIST_ERROR)
}
