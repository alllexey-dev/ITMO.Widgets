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

/** XML references of the subject hub (`RecordbookSubjectFragment`) under the future `RecordbookSubjectScreen` preview. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RecordbookSubjectScreenReferenceScreenshotTest {

    @get:Rule
    val shots = AppScreenshotRule(this)

    private val references = RecordbookReferences(shots)

    @Test
    fun session() = references.capture("RecordbookSubjectScreen_session", Scene.SUBJECT_SESSION)

    @Test
    fun credit() = references.capture("RecordbookSubjectScreen_credit", Scene.SUBJECT_CREDIT)

    @Test
    fun sport() = references.capture("RecordbookSubjectScreen_sport", Scene.SUBJECT_SPORT)

    @Test
    fun bars() = references.capture("RecordbookSubjectScreen_bars", Scene.SUBJECT_BARS)

    @Test
    fun sheet() = references.capture("RecordbookSubjectScreen_sheet", Scene.SUBJECT_SHEET)

    @Test
    fun binding() = references.capture("RecordbookSubjectScreen_binding", Scene.SUBJECT_BINDING)
}
