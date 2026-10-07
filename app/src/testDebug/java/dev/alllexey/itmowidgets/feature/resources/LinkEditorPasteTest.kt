package dev.alllexey.itmowidgets.feature.resources

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.designsystem.ReferenceHostActivity
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.ui.LinkEditorBottomSheet
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewRootImpl
import org.robolectric.util.ReflectionHelpers

/**
 * The clipboard half of the deleted `SubjectLinksVisualTest.editorPastesAGuessedLinkAndOffersOnlyAvailableAudiences`:
 * [LinkEditorBottomSheet] reads Robolectric's clipboard once its window first gets focus and hands a single https link
 * to the view model, which guesses the category. The guess and the audiences on the sheet are `LinkEditorSheetTest`'s.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class LinkEditorPasteTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val stopKoin = StopKoinRule()

    @Before
    fun setUp() {
        LinksHostFixtures.install()
    }

    @Test
    fun aNewSheetStartsWithTheCopiedLinkAndItsGuessedCategory() {
        val state = openWithClipboard("  $SHEET  ")

        assertEquals(SHEET, state.url)
        assertEquals(LinkCategory.SCORES, state.category)
    }

    @Test
    fun onlyASingleHttpsLinkIsPasted() {
        listOf("http://example.org/notes", "Таблица $SHEET", "not a link").forEach { clip ->
            assertEquals(clip, "", openWithClipboard(clip).url)
        }
    }

    @Test
    fun anEditedLinkKeepsItsOwnAddress() {
        val state = openWithClipboard(SHEET, linkId = "own-scores")

        assertEquals("https://docs.google.com/spreadsheets/d/own", state.url)
    }

    /** Opens the editor with [clip] copied, hands its window the focus and returns the view model's state then. */
    private fun openWithClipboard(clip: String, linkId: String? = null): LinkEditorUiState {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("link", clip))
        val controller = Robolectric.buildActivity(ReferenceHostActivity::class.java).setup()
        try {
            val activity = controller.get()
            LinkEditorBottomSheet.newInstance(LinksHostFixtures.ARGS, linkId)
                .show(activity.supportFragmentManager, LinkEditorBottomSheet.TAG)
            shadowOf(Looper.getMainLooper()).idle()
            val sheet = activity.supportFragmentManager.findFragmentByTag(LinkEditorBottomSheet.TAG) as DialogFragment
            // Robolectric keeps the focus on the activity when a dialog shows; the sheet's window gets it by hand.
            activity.window.decorView.windowFocus(false)
            checkNotNull(sheet.dialog?.window).decorView.windowFocus(true)
            shadowOf(Looper.getMainLooper()).idle()
            return sheet.getViewModel<LinkEditorViewModel>().uiState.value
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun View.windowFocus(focused: Boolean) =
        Shadow.extract<ShadowViewRootImpl>(ReflectionHelpers.callInstanceMethod<Any>(this, "getViewRootImpl"))
            .callWindowFocusChanged(focused)

    private companion object {
        const val SHEET = "https://docs.google.com/spreadsheets/d/synthetic"
    }
}
