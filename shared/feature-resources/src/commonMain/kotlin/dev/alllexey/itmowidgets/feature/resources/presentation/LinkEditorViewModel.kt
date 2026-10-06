package dev.alllexey.itmowidgets.feature.resources.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.url.StrictUri
import dev.alllexey.itmowidgets.feature.resources.domain.guessCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/** Adds a link, or edits the viewer's own link given by [SubjectLinksArgs.LINK_ID]. */
class LinkEditorViewModel(
    handle: SavedStateHandle,
    private val repository: SubjectLinksRepository,
) : ViewModel() {
    private val scope = ResourceScope(checkNotNull(handle[SubjectLinksArgs.SUBJECT_ID]),
        checkNotNull(handle[SubjectLinksArgs.SUBJECT_NAME]), checkNotNull(handle[SubjectLinksArgs.PERIOD_KEY]))
    private val editedId: String? = handle[SubjectLinksArgs.LINK_ID]
    /** Kept across process death so a retried save reaches the same link. */
    private val id: String = editedId ?: handle.get<String>(KEY_NEW_ID) ?: Uuid.random().toString().also { handle[KEY_NEW_ID] = it }
    private var categoryChosen = editedId != null
    private var prefilled = editedId == null
    private val state = MutableStateFlow(LinkEditorUiState(editing = editedId != null))
    private val eventQueue = EventQueue<LinkEvent>()

    val uiState: StateFlow<LinkEditorUiState> = state.asStateFlow()

    val events: Flow<LinkEvent> = eventQueue.events

    init {
        viewModelScope.launch {
            repository.observe(scope).collect { links ->
                val snapshot = (links as? SubjectLinksState.Content)?.snapshot
                state.update { it.prefilledFrom(snapshot).withOptions(snapshot) }
            }
        }
    }

    /** The site suggests the category until the user picks one. */
    fun onUrlChanged(url: String) = state.update {
        it.copy(url = url, urlError = null, category = if (categoryChosen) it.category else guessCategory(url))
    }

    fun onCategorySelected(category: LinkCategory) {
        categoryChosen = true
        state.update { it.copy(category = category) }
    }

    fun onTitleChanged(title: String) = state.update { it.copy(title = title, titleError = null) }

    fun onAudienceSelected(option: LinkAudienceOption) = state.update {
        if (option in it.options) it.copy(visibility = option.visibility, flowId = option.flowId) else it
    }

    fun save() {
        val form = state.value
        if (form.saving) return
        val url = form.url.trim()
        val title = form.title.trim()
        val urlError = if (isHttpsLink(url)) null else LinkFieldError.URL_NOT_HTTPS
        val titleError = if (title.length <= MAX_TITLE_LENGTH) null else LinkFieldError.TITLE_TOO_LONG
        val category = form.category
        if (urlError != null || titleError != null || category == null) {
            state.update { it.copy(urlError = urlError, titleError = titleError) }
            return
        }
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = repository.save(scope, id, category, url, title.ifEmpty { null }, form.visibility, form.flowId)
            state.update { it.copy(saving = false) }
            eventQueue.send(when (result) {
                is AppResult.Success -> LinkEvent.Saved
                is AppResult.Failure -> LinkEvent.Failed(result.error)
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
            // Widest audience first, then the flows down the tree, the author alone last.
            listOf(LinkAudienceOption.All) + snapshot.audiences.map(LinkAudienceOption::Flow) + LinkAudienceOption.Private
        val kept = options.any { it.visibility == visibility && it.flowId == flowId }
        return copy(options = options, premoderation = snapshot?.premoderation ?: true,
            visibility = if (kept) visibility else LinkVisibility.PRIVATE, flowId = if (kept) flowId else null)
    }

    private fun isHttpsLink(url: String): Boolean {
        val uri = StrictUri.parse(url) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }

    private companion object {
        const val KEY_NEW_ID = "link_editor_new_id"
        const val MAX_TITLE_LENGTH = 120
    }
}
