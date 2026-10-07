package dev.alllexey.itmowidgets.feature.resources.ui

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkAudienceOption
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkFieldError

/**
 * Synthetic editor states for the previews and host tests, built on [SubjectLinksSamples]: the states of the LX-1c
 * references (a new link with its category guessed from the site and three nested flows, the own `own-scores` link,
 * no connection, an address that is not https) plus a title over the limit and a save in progress.
 */
internal object LinkEditorSamples {
    const val SHEET_URL = "https://docs.google.com/spreadsheets/d/1SyntheticSheetForVisualTests_0123456/edit"
    const val CHAT_URL = "https://t.me/synthetic_chat"

    /** Everybody first, then the viewer's flows down the tree, the author alone last, as the view model orders them. */
    val options: List<LinkAudienceOption> = listOf(LinkAudienceOption.All) +
        SubjectLinksSamples.snapshot.audiences.map(LinkAudienceOption::Flow) + LinkAudienceOption.Private

    val new = LinkEditorUiState(url = SHEET_URL, category = LinkCategory.SCORES, options = options)

    val edit: LinkEditorUiState = SubjectLinksSamples.snapshot.mine.first { it.id == "own-scores" }.let { link ->
        LinkEditorUiState(
            url = link.url,
            category = link.category,
            title = link.title.orEmpty(),
            visibility = link.visibility,
            flowId = link.flowId,
            options = options,
            editing = true,
        )
    }

    val offline = LinkEditorUiState(url = CHAT_URL, category = LinkCategory.CHAT)

    val urlError = LinkEditorUiState(
        url = "http://example.org/notes",
        category = LinkCategory.NOTES,
        options = options,
        urlError = LinkFieldError.URL_NOT_HTTPS,
    )

    /** Two of the longest titles: wrapped to the six lines the field allows, over the 120 characters it accepts. */
    val longTitle = LinkEditorUiState(
        url = "https://www.notion.so/synthetic",
        category = LinkCategory.NOTES,
        title = "${SubjectLinksSamples.LONG_TITLE}. ${SubjectLinksSamples.LONG_TITLE}",
        options = options,
        titleError = LinkFieldError.TITLE_TOO_LONG,
    )

    val saving = edit.copy(saving = true)
}
