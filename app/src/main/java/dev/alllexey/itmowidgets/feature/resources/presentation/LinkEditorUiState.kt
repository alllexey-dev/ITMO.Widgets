package dev.alllexey.itmowidgets.feature.resources.presentation

import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility

/** One row of «Кто видит»: only me, one schedule flow of the viewer, or everybody. */
sealed interface LinkAudienceOption {
    val visibility: LinkVisibility
    val flowId: Long? get() = null

    data object Private : LinkAudienceOption {
        override val visibility = LinkVisibility.PRIVATE
    }

    data class Flow(val audience: LinkAudience) : LinkAudienceOption {
        override val visibility = LinkVisibility.FLOW
        override val flowId: Long get() = audience.flowId
    }

    data object All : LinkAudienceOption {
        override val visibility = LinkVisibility.ALL
    }
}

/** Why a field of the link form was not accepted; the view picks the text. */
enum class LinkFieldError {
    /** Only an https address with a host and without user info is a link. */
    URL_NOT_HTTPS,
    TITLE_TOO_LONG,
}

data class LinkEditorUiState(
    val url: String = "",
    val category: LinkCategory? = null,
    val title: String = "",
    val visibility: LinkVisibility = LinkVisibility.PRIVATE,
    /** The chosen flow of a FLOW link; null otherwise. */
    val flowId: Long? = null,
    /** Only me, every flow of the viewer in the server's order, everybody; only me without the ITMO.Widgets connection. */
    val options: List<LinkAudienceOption> = listOf(LinkAudienceOption.Private),
    /** Links for everyone wait for review first. */
    val premoderation: Boolean = true,
    val urlError: LinkFieldError? = null,
    val titleError: LinkFieldError? = null,
    val saving: Boolean = false,
    val editing: Boolean = false,
) {
    val canSave: Boolean get() = url.isNotBlank() && category != null && !saving
    val selected: LinkAudienceOption get() = options.firstOrNull { it.visibility == visibility && it.flowId == flowId }
        ?: LinkAudienceOption.Private
}
