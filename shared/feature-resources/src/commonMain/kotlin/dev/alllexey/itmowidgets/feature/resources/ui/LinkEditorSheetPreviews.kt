package dev.alllexey.itmowidgets.feature.resources.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorUiState

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LX-1c recorded the XML references as
 * `LinkEditorSheetContent_<state>`. Each state therefore is a function called `LinkEditorSheetContent` in a holder
 * class of its own. The editor wraps its content on the sheet's colour, as `LinkEditorBottomSheet` opens it.
 */

/** A pasted Google Sheet: `Таблица баллов` guessed, three nested flows offered, only me chosen. */
internal class LinkEditorSheetNewPreview {
    @Preview(name = "new")
    @Composable
    fun LinkEditorSheetContent() = EditorPreview(LinkEditorSamples.new)
}

/** An own link shared with one flow. */
internal class LinkEditorSheetEditPreview {
    @Preview(name = "edit")
    @Composable
    fun LinkEditorSheetContent() = EditorPreview(LinkEditorSamples.edit)
}

/** Without the connection only `Только я` is offered and the line below says why; a chat shows its messenger. */
internal class LinkEditorSheetOfflinePreview {
    @Preview(name = "offline")
    @Composable
    fun LinkEditorSheetContent() = EditorPreview(LinkEditorSamples.offline)
}

/** An address that is not https after a save, with `Конспекты` scrolled into view. */
internal class LinkEditorSheetUrlErrorPreview {
    @Preview(name = "url-error")
    @Composable
    fun LinkEditorSheetContent() = EditorPreview(LinkEditorSamples.urlError)
}

/** A title over the limit wraps to six lines and says so. */
internal class LinkEditorSheetLongTitlePreview {
    @Preview(name = "long-title")
    @Composable
    fun LinkEditorSheetContent() = EditorPreview(LinkEditorSamples.longTitle)
}

/** The save in progress. */
internal class LinkEditorSheetSavingPreview {
    @Preview(name = "saving")
    @Composable
    fun LinkEditorSheetContent() = EditorPreview(LinkEditorSamples.saving)
}

@Composable
private fun EditorPreview(state: LinkEditorUiState) = ItmoPreview {
    val shape = ItmoTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLow),
    ) {
        LinkEditorSheetContent(state, LinkEditorActions(), Modifier.fillMaxWidth())
    }
}
