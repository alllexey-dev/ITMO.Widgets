package dev.alllexey.itmowidgets.feature.resources

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.ui.pasteClipboardLink
import dev.alllexey.itmowidgets.feature.resources.ui.toArguments
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The clipboard half of the deleted `SubjectLinksVisualTest.editorPastesAGuessedLinkAndOffersOnlyAvailableAudiences`:
 * [pasteClipboardLink] reads Robolectric's clipboard and hands a single https link to the view model, which guesses
 * the category. That the shell's editor entry calls it once its window has focus is `ResourcesEntriesTest`'s; the
 * guess and the audiences on the sheet are `LinkEditorSheetTest`'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LinkEditorPasteTest {

    @Test
    fun aNewEditorTakesTheCopiedLinkAndItsGuessedCategory() {
        val state = pasteWithClipboard("  $SHEET  ")

        assertEquals(SHEET, state.url)
        assertEquals(LinkCategory.SCORES, state.category)
    }

    @Test
    fun onlyASingleHttpsLinkIsPasted() {
        listOf("http://example.org/notes", "Таблица $SHEET", "not a link").forEach { clip ->
            assertEquals(clip, "", pasteWithClipboard(clip).url)
        }
    }

    @Test
    fun anEditedLinkKeepsItsOwnAddress() {
        val state = pasteWithClipboard(SHEET, linkId = "own-scores")

        assertEquals("https://docs.google.com/spreadsheets/d/own", state.url)
    }

    /** An editor over the fixture with [clip] copied pastes it; returns the view model's state then. */
    private fun pasteWithClipboard(clip: String, linkId: String? = null): LinkEditorUiState {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("link", clip))
        val repository = LinksHostRepository().apply {
            servicesEnabled = true
            snapshots.value = mapOf(LinksHostFixtures.SCOPE.key to LinksHostFixtures.fixture())
        }
        val handle = LinksHostFixtures.ARGS.toArguments(linkId).let { args ->
            SavedStateHandle(args.keySet().associateWith { args.get(it) })
        }
        val viewModel = LinkEditorViewModel(handle, repository)
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.pasteClipboardLink(context)
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel.uiState.value
    }

    private companion object {
        const val SHEET = "https://docs.google.com/spreadsheets/d/synthetic"
    }
}
