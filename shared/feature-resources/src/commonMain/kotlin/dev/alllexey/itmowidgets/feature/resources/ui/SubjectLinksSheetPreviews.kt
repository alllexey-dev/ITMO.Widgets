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
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LX-1c recorded the XML references as
 * `SubjectLinksSheetContent_<state>`. Each state therefore is a function called `SubjectLinksSheetContent` in a holder
 * class of its own. The sheet wraps its content on the sheet's colour, as `SubjectLinksBottomSheet` opens it; the full
 * lists get the tall window of the references, so every section is in the capture.
 */

/** Every category, chats and past years: own links with their badge, others' with the vote pill. */
internal class SubjectLinksSheetContentPreview {
    @Preview(name = "content", heightDp = TALL_HEIGHT)
    @Composable
    fun SubjectLinksSheetContent() = LinksPreview(SubjectLinksSamples.content)
}

/** A vote restriction: others' links keep their score without the arrows. */
internal class SubjectLinksSheetRestrictedPreview {
    @Preview(name = "restricted", heightDp = TALL_HEIGHT)
    @Composable
    fun SubjectLinksSheetContent() = LinksPreview(SubjectLinksSamples.restricted)
}

/** Without the connection to ITMO.Widgets nothing can be voted on. */
internal class SubjectLinksSheetOfflinePreview {
    @Preview(name = "offline", heightDp = TALL_HEIGHT)
    @Composable
    fun SubjectLinksSheetContent() = LinksPreview(SubjectLinksSamples.offline)
}

/** Own links on review, rejected and hidden say so after their audience. */
internal class SubjectLinksSheetOwnPreview {
    @Preview(name = "own")
    @Composable
    fun SubjectLinksSheetContent() = LinksPreview(SubjectLinksSamples.own)
}

/** The first load without a cache. */
internal class SubjectLinksSheetLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun SubjectLinksSheetContent() = LinksPreview(SubjectLinksSamples.loading)
}

internal class SubjectLinksSheetEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun SubjectLinksSheetContent() = LinksPreview(SubjectLinksSamples.empty)
}

/** The first load failed: the reason and a retry. */
internal class SubjectLinksSheetErrorPreview {
    @Preview(name = "error")
    @Composable
    fun SubjectLinksSheetContent() = LinksPreview(SubjectLinksSamples.error)
}

@Composable
private fun LinksPreview(state: SubjectLinksUiState) = ItmoPreview {
    val shape = ItmoTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLow),
    ) {
        SubjectLinksSheetContent(
            SubjectLinksSamples.scope.subjectName,
            state,
            SubjectLinksActions(),
            Modifier.fillMaxWidth(),
        )
    }
}

/** The references' 2000 dp window: the whole list fits under the sheet's 90 % cap. */
private const val TALL_HEIGHT = 2000
