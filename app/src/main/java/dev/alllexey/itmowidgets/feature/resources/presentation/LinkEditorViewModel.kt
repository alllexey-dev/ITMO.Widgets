package dev.alllexey.itmowidgets.feature.resources.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.toUiText
import dev.alllexey.itmowidgets.feature.resources.domain.guessCategory
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val urlError: UiText? = null,
    val titleError: UiText? = null,
    val saving: Boolean = false,
    val editing: Boolean = false,
) {
    val canSave: Boolean get() = url.isNotBlank() && category != null && !saving
    val selected: LinkAudienceOption get() = options.firstOrNull { it.visibility == visibility && it.flowId == flowId }
        ?: LinkAudienceOption.Private
}

/** Adds a link, or edits the viewer's own link given by [SubjectLinksArgs.LINK_ID]. */
@HiltViewModel
class LinkEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: SubjectLinksRepository,
) : ViewModel() {
    val scope = ResourceScope(checkNotNull(handle[SubjectLinksArgs.SUBJECT_ID]),
        checkNotNull(handle[SubjectLinksArgs.SUBJECT_NAME]), checkNotNull(handle[SubjectLinksArgs.PERIOD_KEY]))
    private val editedId: String? = handle[SubjectLinksArgs.LINK_ID]
    /** Kept across process death so a retried save reaches the same link. */
    private val id: String = editedId ?: handle.get<String>(KEY_NEW_ID) ?: UUID.randomUUID().toString().also { handle[KEY_NEW_ID] = it }
    private var categoryChosen = editedId != null
    private var prefilled = editedId == null
    private val _uiState = MutableStateFlow(LinkEditorUiState(editing = editedId != null))
    val uiState: StateFlow<LinkEditorUiState> = _uiState.asStateFlow()
    private val channel = Channel<LinkEvent>(Channel.BUFFERED)
    val events = channel.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.observe(scope).collect { state ->
                val snapshot = (state as? SubjectLinksState.Content)?.snapshot
                _uiState.update { it.prefilledFrom(snapshot).withOptions(snapshot) }
            }
        }
    }

    /** The site suggests the category until the user picks one. */
    fun onUrlChanged(url: String) = _uiState.update {
        it.copy(url = url, urlError = null, category = if (categoryChosen) it.category else guessCategory(url))
    }

    fun onCategorySelected(category: LinkCategory) {
        categoryChosen = true
        _uiState.update { it.copy(category = category) }
    }

    fun onTitleChanged(title: String) = _uiState.update { it.copy(title = title, titleError = null) }

    fun onAudienceSelected(option: LinkAudienceOption) = _uiState.update {
        if (option in it.options) it.copy(visibility = option.visibility, flowId = option.flowId) else it
    }

    fun save() {
        val state = _uiState.value
        if (state.saving) return
        val url = state.url.trim()
        val title = state.title.trim()
        val urlError = if (isHttpsLink(url)) null else UiText.Resource(R.string.links_invalid_url)
        val titleError = if (title.length <= MAX_TITLE_LENGTH) null else UiText.Resource(R.string.links_title_too_long)
        val category = state.category
        if (urlError != null || titleError != null || category == null) {
            _uiState.update { it.copy(urlError = urlError, titleError = titleError) }
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = repository.save(scope, id, category, url, title.ifEmpty { null }, state.visibility, state.flowId)
            _uiState.update { it.copy(saving = false) }
            channel.send(when (result) {
                is AppResult.Success -> LinkEvent.Saved
                is AppResult.Failure -> LinkEvent.Failed(result.error.toUiText())
            })
        }
    }

    private fun LinkEditorUiState.prefilledFrom(snapshot: SubjectLinksSnapshot?): LinkEditorUiState {
        if (prefilled) return this
        val link = snapshot?.mine?.firstOrNull { it.id == editedId } ?: return this
        prefilled = true
        return copy(url = link.url, category = link.category, title = link.title.orEmpty(), visibility = link.visibility,
            flowId = link.flowId)
    }

    /** A choice that is no longer offered, such as a flow gone from the schedule, falls back to only me. */
    private fun LinkEditorUiState.withOptions(snapshot: SubjectLinksSnapshot?): LinkEditorUiState {
        val options = if (snapshot?.servicesEnabled != true) listOf(LinkAudienceOption.Private) else
            listOf(LinkAudienceOption.Private) + snapshot.audiences.map(LinkAudienceOption::Flow) + LinkAudienceOption.All
        val kept = options.any { it.visibility == visibility && it.flowId == flowId }
        return copy(options = options, premoderation = snapshot?.premoderation ?: true,
            visibility = if (kept) visibility else LinkVisibility.PRIVATE, flowId = if (kept) flowId else null)
    }

    private fun isHttpsLink(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }

    private companion object {
        const val KEY_NEW_ID = "link_editor_new_id"
        const val MAX_TITLE_LENGTH = 120
    }
}
