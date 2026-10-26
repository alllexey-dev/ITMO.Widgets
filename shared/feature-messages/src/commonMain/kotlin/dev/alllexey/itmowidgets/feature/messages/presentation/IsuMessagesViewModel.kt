package dev.alllexey.itmowidgets.feature.messages.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.messages.domain.IsuAttachment
import dev.alllexey.itmowidgets.feature.messages.domain.IsuFolder
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessage
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessagesRepository
import dev.alllexey.itmowidgets.shared.feature.messages.MessagesPlurals
import dev.alllexey.itmowidgets.shared.feature.messages.MessagesStrings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char

/**
 * ISU messages (docs/features/isu-messages.md): Входящие and Архив, ten messages a page with `Показать ещё`, one
 * message opened over the list with its attachments. Opening a message reads it from the list already loaded, so ISU
 * never learns it was opened and keeps it unread. Each folder loads on first view and keeps its pages until a refresh.
 */
class IsuMessagesViewModel(
    private val repository: IsuMessagesRepository,
    private val time: AcademicTimeProvider,
) : ViewModel() {

    private val model = MutableStateFlow(Model())
    private val loads = mutableMapOf<IsuFolder, Job>()
    private val queue = EventQueue<IsuMessagesEvent>()

    val events: Flow<IsuMessagesEvent> = queue.events

    val uiState: StateFlow<IsuMessagesUiState> = model.map(::present)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), present(model.value))

    init {
        load(IsuFolder.INBOX, RefreshMode.Silent)
    }

    /** A pull or a retry reloads the shown folder from its first page. */
    fun refresh(mode: RefreshMode = RefreshMode.Pull) = load(model.value.folder, mode)

    fun selectFolder(folder: IsuFolder) {
        if (folder == model.value.folder) return
        model.update { it.copy(folder = folder, opened = null) }
        if (model.value.folders[folder] == null) load(folder, RefreshMode.Silent)
    }

    fun loadMore() {
        val folder = model.value.folder
        val data = model.value.folders[folder] as? Folder.Loaded ?: return
        if (data.more != MoreState.AVAILABLE && data.more != MoreState.FAILED) return
        if (loads[folder]?.isActive == true) return
        model.update { it.with(folder, data.copy(more = MoreState.LOADING)) }
        loads[folder] = viewModelScope.launch {
            val result = repository.page(folder, data.next)
            model.update { state ->
                val current = state.folders[folder] as? Folder.Loaded ?: return@update state
                when (result) {
                    is AppResult.Failure -> state.with(folder, current.copy(more = MoreState.FAILED))
                    is AppResult.Success -> {
                        val known = current.messages.mapTo(HashSet()) { it.id }
                        val added = result.value.messages.filter { it.id !in known }
                        // A page with nothing new ends the list, so a server that repeats a page cannot loop it.
                        val last = result.value.last || added.isEmpty()
                        state.with(folder, current.copy(messages = current.messages + added, next = current.next + 1, more = if (last) MoreState.END else MoreState.AVAILABLE))
                    }
                }
            }
        }
    }

    fun open(id: Long) = model.update { state ->
        state.copy(opened = (state.folders[state.folder] as? Folder.Loaded)?.messages?.firstOrNull { it.id == id })
    }

    fun close() = model.update { it.copy(opened = null) }

    fun openAttachment(id: String) {
        val attachment = model.value.opened?.attachments?.firstOrNull { it.id == id } ?: return
        if (model.value.downloading != null) return
        model.update { it.copy(downloading = id) }
        viewModelScope.launch {
            val result = repository.download(attachment)
            model.update { it.copy(downloading = null) }
            when (result) {
                is AppResult.Success -> queue.send(IsuMessagesEvent.OpenFile(result.value.uri, result.value.mimeType))
                is AppResult.Failure -> queue.send(IsuMessagesEvent.Snackbar(downloadError(result.error)))
            }
        }
    }

    /** The screen could not hand a file or a link to the system. */
    fun onOpenFailed(file: Boolean) {
        val key = if (file) MessagesStrings.isu_messages_no_viewer else MessagesStrings.isu_messages_link_failed
        viewModelScope.launch { queue.send(IsuMessagesEvent.Snackbar(UiText.Res(key))) }
    }

    private fun load(folder: IsuFolder, mode: RefreshMode) {
        val running = loads[folder]
        if (running?.isActive == true && mode != RefreshMode.Force) {
            if (mode.showsIndicator) model.update { it.copy(refreshing = it.refreshing + folder) }
            return
        }
        running?.cancel()
        model.update { state ->
            state.copy(
                refreshing = if (mode.showsIndicator) state.refreshing + folder else state.refreshing,
                folders = if (state.folders[folder] is Folder.Loaded) state.folders else state.folders + (folder to Folder.Loading),
            )
        }
        loads[folder] = viewModelScope.launch {
            val result = repository.page(folder, 1)
            model.update { state ->
                val previous = state.folders[folder]
                val next = when (result) {
                    is AppResult.Success -> Folder.Loaded(result.value.messages, next = 2, more = if (result.value.last) MoreState.END else MoreState.AVAILABLE)
                    // A failed refresh keeps the list it had and says so in a snackbar.
                    is AppResult.Failure -> if (previous is Folder.Loaded) previous else Folder.Failed(result.error)
                }
                val unread = if (folder == IsuFolder.INBOX && result is AppResult.Success) result.value.unread else state.unread
                state.with(folder, next).copy(refreshing = state.refreshing - folder, unread = unread)
            }
            if (result is AppResult.Failure && model.value.folders[folder] is Folder.Loaded) {
                queue.send(IsuMessagesEvent.Snackbar(result.error.toUiText()))
            }
        }
    }

    private fun present(model: Model): IsuMessagesUiState {
        val today = time.today()
        return IsuMessagesUiState(
            folder = model.folder,
            list = when (val folder = model.folders[model.folder]) {
                null, Folder.Loading -> IsuMessagesList.Loading
                is Folder.Failed -> failed(folder.error)
                is Folder.Loaded -> if (folder.messages.isEmpty()) {
                    empty(model.folder)
                } else {
                    IsuMessagesList.Messages(folder.messages.map { row(it, today) }, folder.more)
                }
            },
            unread = model.unread,
            unreadText = model.unread.takeIf { it > 0 }?.let { UiText.Plural(MessagesPlurals.isu_messages_unread_count, it, listOf(it)) },
            refreshing = model.folder in model.refreshing,
            opened = model.opened?.let { detail(it, today) },
            downloading = model.downloading,
        )
    }

    private fun failed(error: AppError): IsuMessagesList.Failed = if (error == AppError.Unauthorized) {
        IsuMessagesList.Failed(UiText.Res(MessagesStrings.isu_messages_sign_in), UiText.Res(MessagesStrings.isu_messages_sign_in_text), signIn = true)
    } else {
        IsuMessagesList.Failed(UiText.Res(MessagesStrings.isu_messages_error), error.toUiText(), signIn = false)
    }

    private fun empty(folder: IsuFolder): IsuMessagesList.Empty = when (folder) {
        IsuFolder.INBOX -> IsuMessagesList.Empty(UiText.Res(MessagesStrings.isu_messages_empty_inbox), UiText.Res(MessagesStrings.isu_messages_empty_inbox_text))
        IsuFolder.ARCHIVE -> IsuMessagesList.Empty(UiText.Res(MessagesStrings.isu_messages_empty_archive), UiText.Res(MessagesStrings.isu_messages_empty_archive_text))
    }

    private fun row(message: IsuMessage, today: LocalDate) = IsuMessageRow(
        id = message.id,
        sender = message.sender,
        subject = message.subject,
        preview = message.body.plainText.replace(LINE_BREAKS, " ").take(PREVIEW_LENGTH),
        date = listDate(message.date, message.time, today),
        unread = !message.read,
        attachments = message.attachments.size,
    )

    private fun detail(message: IsuMessage, today: LocalDate) = IsuMessageDetail(
        id = message.id,
        subject = message.subject,
        sender = message.sender,
        date = detailDate(message.date, message.time, today),
        body = message.body,
        attachments = message.attachments.map { IsuAttachmentRow(it.id, it.name, kind(it)) },
        unread = !message.read,
    )

    private fun downloadError(error: AppError): UiText =
        if (error == AppError.DemoUnavailable) error.toUiText() else UiText.Res(MessagesStrings.isu_messages_file_failed)

    private data class Model(
        val folder: IsuFolder = IsuFolder.INBOX,
        val folders: Map<IsuFolder, Folder> = emptyMap(),
        val refreshing: Set<IsuFolder> = emptySet(),
        val unread: Int = 0,
        val opened: IsuMessage? = null,
        val downloading: String? = null,
    ) {
        fun with(folder: IsuFolder, data: Folder) = copy(folders = folders + (folder to data))
    }

    private sealed interface Folder {
        data object Loading : Folder
        data class Failed(val error: AppError) : Folder
        data class Loaded(val messages: List<IsuMessage>, val next: Int, val more: MoreState) : Folder
    }

    internal companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val PREVIEW_LENGTH = 160
        private val LINE_BREAKS = Regex("\\s*\\n\\s*")

        /** `26 окт.` */
        private val DAY_SHORT_MONTH = LocalDate.Format { date(DateTexts.DAY); char(' '); date(DateTexts.MONTH_SHORT) }

        /** The list's date: the time today, `26 окт.` this year, `2 июля 2025` before. */
        fun listDate(date: LocalDate?, time: LocalTime?, today: LocalDate): String = when {
            date == null -> time?.format(DateTexts.TIME).orEmpty()
            date == today && time != null -> time.format(DateTexts.TIME)
            date.year == today.year -> date.format(DAY_SHORT_MONTH)
            else -> date.format(DateTexts.DAY_MONTH_YEAR)
        }

        /** The opened message's date: `пн, 26 октября, 09:12`, with the year before this one. */
        fun detailDate(date: LocalDate?, time: LocalTime?, today: LocalDate): String {
            val day = when {
                date == null -> null
                date.year == today.year -> date.format(DateTexts.SHORT_WEEKDAY_DAY_MONTH)
                else -> date.format(DateTexts.DAY_MONTH_YEAR)
            }
            return listOfNotNull(day, time?.format(DateTexts.TIME)).joinToString(", ")
        }

        /** What a file is, by its MIME type, else by its extension. */
        fun kind(attachment: IsuAttachment): UiText {
            val mime = attachment.mimeType.orEmpty()
            val extension = attachment.name.substringAfterLast('.', "").lowercase()
            val key = when {
                mime == "application/pdf" || extension == "pdf" -> MessagesStrings.isu_messages_kind_pdf
                "msword" in mime || "wordprocessingml" in mime || extension in setOf("doc", "docx", "odt", "rtf") -> MessagesStrings.isu_messages_kind_word
                "ms-excel" in mime || "spreadsheetml" in mime || extension in setOf("xls", "xlsx", "ods", "csv") -> MessagesStrings.isu_messages_kind_excel
                mime.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "gif", "heic", "webp") -> MessagesStrings.isu_messages_kind_image
                "zip" in mime || "rar" in mime || "7z" in mime || extension in setOf("zip", "rar", "7z") -> MessagesStrings.isu_messages_kind_archive
                else -> MessagesStrings.isu_messages_kind_file
            }
            return UiText.Res(key)
        }
    }
}
