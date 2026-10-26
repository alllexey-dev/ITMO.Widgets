package dev.alllexey.itmowidgets.feature.messages.presentation

import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.messages.domain.AttachmentFile
import dev.alllexey.itmowidgets.feature.messages.domain.IsuAttachment
import dev.alllexey.itmowidgets.feature.messages.domain.IsuFolder
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessage
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessagesPage
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessagesRepository
import dev.alllexey.itmowidgets.feature.messages.domain.MessageBlock
import dev.alllexey.itmowidgets.feature.messages.domain.MessageBody
import dev.alllexey.itmowidgets.feature.messages.domain.MessageSpan
import dev.alllexey.itmowidgets.shared.feature.messages.MessagesPlurals
import dev.alllexey.itmowidgets.shared.feature.messages.MessagesStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class IsuMessagesViewModelTest {
    private val main = TestMainDispatcher()
    private val repository = FakeRepository()
    private val time = FixedAcademicTime(LocalDateTime(2026, 10, 26, 12, 0))

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theInboxLoadsWithItsUnreadCounter() = runTest(main.dispatcher) {
        repository.pages[IsuFolder.INBOX to 1] = page((1..10).map { message(it.toLong(), read = it > 3) }, unread = 3, last = false)

        val state = viewModel().loaded()

        val list = assertIs<IsuMessagesList.Messages>(state.list)
        assertEquals(10, list.rows.size)
        assertEquals(MoreState.AVAILABLE, list.more)
        assertEquals(3, state.unread)
        assertEquals(UiText.Plural(MessagesPlurals.isu_messages_unread_count, 3, listOf(3)), state.unreadText)
        assertTrue(list.rows.first().unread)
    }

    @Test
    fun moreAppendsTheNextPageWithoutRepeatsAndEnds() = runTest(main.dispatcher) {
        repository.pages[IsuFolder.INBOX to 1] = page((1..10).map { message(it.toLong()) }, last = false)
        repository.pages[IsuFolder.INBOX to 2] = page(listOf(message(10), message(11)), last = true)
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.loadMore()
        advanceUntilIdle()

        val list = assertIs<IsuMessagesList.Messages>(current(viewModel).list)
        assertEquals((1L..11L).toList(), list.rows.map { it.id })
        assertEquals(MoreState.END, list.more)
    }

    @Test
    fun theArchiveLoadsOnFirstViewAndKeepsTheInbox() = runTest(main.dispatcher) {
        repository.pages[IsuFolder.INBOX to 1] = page(listOf(message(1)))
        repository.pages[IsuFolder.ARCHIVE to 1] = page(emptyList())
        val viewModel = viewModel()
        viewModel.loaded()

        viewModel.selectFolder(IsuFolder.ARCHIVE)
        advanceUntilIdle()
        val archive = current(viewModel)
        viewModel.selectFolder(IsuFolder.INBOX)
        advanceUntilIdle()

        assertEquals(IsuMessagesList.Empty(UiText.Res(MessagesStrings.isu_messages_empty_archive), UiText.Res(MessagesStrings.isu_messages_empty_archive_text)), archive.list)
        assertIs<IsuMessagesList.Messages>(current(viewModel).list)
        assertEquals(listOf(IsuFolder.INBOX to 1, IsuFolder.ARCHIVE to 1), repository.asked)
    }

    @Test
    fun anEndedSessionAsksToSignInAndOtherFailuresSayWhy() = runTest(main.dispatcher) {
        repository.failure = AppError.Unauthorized
        val signIn = assertIs<IsuMessagesList.Failed>(viewModel().loaded().list)
        repository.failure = AppError.Network
        val offline = assertIs<IsuMessagesList.Failed>(viewModel().loaded().list)

        assertTrue(signIn.signIn)
        assertEquals(UiText.Res(MessagesStrings.isu_messages_sign_in), signIn.title)
        assertEquals(AppError.Network.toUiText(), offline.text)
    }

    @Test
    fun aFailedRefreshKeepsTheListAndSaysSo() = runTest(main.dispatcher) {
        repository.pages[IsuFolder.INBOX to 1] = page(listOf(message(1)))
        val viewModel = viewModel()
        viewModel.loaded()
        val events = mutableListOf<IsuMessagesEvent>()
        val collecting = launch { viewModel.events.collect { events += it } }

        repository.failure = AppError.Network
        viewModel.refresh(RefreshMode.Pull)
        advanceUntilIdle()

        assertIs<IsuMessagesList.Messages>(current(viewModel).list)
        assertEquals(listOf<IsuMessagesEvent>(IsuMessagesEvent.Snackbar(AppError.Network.toUiText())), events)
        collecting.cancel()
    }

    @Test
    fun openingShowsTheListsCopyAndAsksNothing() = runTest(main.dispatcher) {
        repository.pages[IsuFolder.INBOX to 1] = page(listOf(message(1, read = false, attachments = listOf(PDF))))
        val viewModel = viewModel()
        viewModel.loaded()
        val asked = repository.asked.size

        viewModel.open(1)
        advanceUntilIdle()

        val opened = assertNotNull(current(viewModel).opened)
        assertEquals("Тема 1", opened.subject)
        assertEquals("пн, 26 октября, 09:30", opened.date)
        assertEquals(UiText.Res(MessagesStrings.isu_messages_kind_pdf), opened.attachments.single().kind)
        assertTrue(opened.unread, "ISU still counts it unread")
        assertEquals(asked, repository.asked.size)
        viewModel.close()
        advanceUntilIdle()
        assertNull(current(viewModel).opened)
    }

    @Test
    fun anAttachmentOpensInTheViewerOrSaysItFailed() = runTest(main.dispatcher) {
        repository.pages[IsuFolder.INBOX to 1] = page(listOf(message(1, attachments = listOf(PDF))))
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.open(1)
        val events = mutableListOf<IsuMessagesEvent>()
        val collecting = launch { viewModel.events.collect { events += it } }

        viewModel.openAttachment(PDF.id)
        advanceUntilIdle()
        repository.download = AppResult.Failure(AppError.Network)
        viewModel.openAttachment(PDF.id)
        advanceUntilIdle()

        assertEquals(
            listOf(
                IsuMessagesEvent.OpenFile("content://file", "application/pdf"),
                IsuMessagesEvent.Snackbar(UiText.Res(MessagesStrings.isu_messages_file_failed)),
            ),
            events,
        )
        assertNull(current(viewModel).downloading)
        collecting.cancel()
    }

    @Test
    fun datesReadAsTimeTodayDayThisYearFullBefore() {
        val today = LocalDate(2026, 10, 26)

        assertEquals("09:30", IsuMessagesViewModel.listDate(today, LocalTime(9, 30), today))
        assertEquals("21 окт.", IsuMessagesViewModel.listDate(LocalDate(2026, 10, 21), LocalTime(9, 30), today))
        assertEquals("2 июля 2025", IsuMessagesViewModel.listDate(LocalDate(2025, 7, 2), null, today))
        assertEquals("2 июля 2025, 10:00", IsuMessagesViewModel.detailDate(LocalDate(2025, 7, 2), LocalTime(10, 0), today))
    }

    @Test
    fun fileKindsComeFromTheTypeOrTheExtension() {
        assertEquals(UiText.Res(MessagesStrings.isu_messages_kind_word), IsuMessagesViewModel.kind(IsuAttachment(HASH, "a.doc", "application/msword")))
        assertEquals(UiText.Res(MessagesStrings.isu_messages_kind_excel), IsuMessagesViewModel.kind(IsuAttachment(HASH, "a.xlsx", null)))
        assertEquals(UiText.Res(MessagesStrings.isu_messages_kind_image), IsuMessagesViewModel.kind(IsuAttachment(HASH, "a", "image/jpeg")))
        assertEquals(UiText.Res(MessagesStrings.isu_messages_kind_file), IsuMessagesViewModel.kind(IsuAttachment(HASH, "a.bin", null)))
    }

    /** A view model whose state is collected for the whole test, as a screen would. */
    private fun TestScope.viewModel() = IsuMessagesViewModel(repository, time).also { viewModel ->
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private suspend fun IsuMessagesViewModel.loaded(): IsuMessagesUiState = uiState.first { it.list != IsuMessagesList.Loading }

    private fun TestScope.current(viewModel: IsuMessagesViewModel): IsuMessagesUiState {
        advanceUntilIdle()
        return viewModel.uiState.value
    }

    private class FakeRepository : IsuMessagesRepository {
        val pages = mutableMapOf<Pair<IsuFolder, Int>, IsuMessagesPage>()
        val asked = mutableListOf<Pair<IsuFolder, Int>>()
        var failure: AppError? = null
        var download: AppResult<AttachmentFile> = AppResult.Success(AttachmentFile("content://file", "application/pdf"))

        override suspend fun page(folder: IsuFolder, number: Int): AppResult<IsuMessagesPage> {
            asked += folder to number
            failure?.let { return AppResult.Failure(it) }
            return AppResult.Success(pages[folder to number] ?: page(emptyList()))
        }

        override suspend fun download(attachment: IsuAttachment): AppResult<AttachmentFile> = download
    }

    private companion object {
        const val HASH = "0123456789abcdef0123456789abcdef"
        val PDF = IsuAttachment(HASH, "plan.pdf", "application/pdf")

        fun page(messages: List<IsuMessage>, unread: Int = 0, last: Boolean = true) = IsuMessagesPage(messages, unread, last)

        fun message(id: Long, read: Boolean = true, attachments: List<IsuAttachment> = emptyList()) = IsuMessage(
            id = id,
            sender = "Отправитель $id",
            subject = "Тема $id",
            date = LocalDate(2026, 10, 26),
            time = LocalTime(9, 30),
            read = read,
            body = MessageBody(listOf(MessageBlock(listOf(MessageSpan("Текст $id"))))),
            attachments = attachments,
        )
    }
}
