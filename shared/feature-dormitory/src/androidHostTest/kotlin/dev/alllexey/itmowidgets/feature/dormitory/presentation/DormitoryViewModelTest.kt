package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.dormitory.DormitoryHarness
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.dormitory.DormitoryStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

class DormitoryViewModelTest {

    private val main = TestMainDispatcher()
    private lateinit var harness: DormitoryHarness

    @BeforeTest
    fun setUp() {
        main.install()
        harness = DormitoryHarness(main.dispatcher)
    }

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theQueueShowsTheStageAndThePlaceWithoutAskingForDocuments() = runTest(main.dispatcher) {
        harness.answer("status-queue.json")

        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        val shown = state.content as DormitoryContent.Shown
        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_queue), shown.stage?.title)
        assertEquals(UiText.Res(DormitoryStrings.dormitory_queue_place, listOf(42)), shown.stage?.queuePlace)
        assertEquals(false, shown.stage?.chooseDate)
        assertNull(shown.dormitory)
        assertNull(shown.documents)
        assertNull(state.stale)
        assertEquals(listOf(DormitoryHarness.STATUS, DormitoryHarness.PERIODS), harness.paths())
    }

    @Test
    fun anOfferedPlaceAsksToChooseADateAndListsTheDocumentsToUpload() = runTest(main.dispatcher) {
        harness.answer("status-offered.json", documents = "documents.json")

        val shown = viewModel().also { advanceUntilIdle() }.uiState.value.content as DormitoryContent.Shown

        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_place_offered), shown.stage?.title)
        assertEquals(DormitoryTone.ATTENTION, shown.stage?.tone)
        assertEquals(true, shown.stage?.chooseDate)
        assertEquals(UiText.Res(DormitoryStrings.dormitory_place_offered_seen, listOf("12:00")), shown.stage?.seen)
        assertEquals(
            DormitoryDocuments(
                rows = listOf(
                    DormitoryDocumentRow("Синтетическая справка", "Отсутствует", confirmed = false),
                    DormitoryDocumentRow("Синтетический анализ", "Подтверждено", confirmed = true),
                ),
                upload = true,
            ),
            shown.documents,
        )
        assertEquals(listOf(DormitoryHarness.STATUS, DormitoryHarness.DOCUMENTS, DormitoryHarness.PERIODS), harness.paths())
        assertEquals(AppRoutes.MyItmoWeb("/services/dormitories"), DormitoryWeb.route)
    }

    @Test
    fun aChosenDateKeepsTheDocumentsAndNeedsNoUploadOnceAllAreConfirmed() = runTest(main.dispatcher) {
        harness.answer("status-date-chosen.json", documents = "documents-confirmed.json")

        val shown = viewModel().also { advanceUntilIdle() }.uiState.value.content as DormitoryContent.Shown

        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_date_chosen), shown.stage?.title)
        assertEquals(false, shown.stage?.chooseDate)
        assertNull(shown.stage?.seen)
        assertEquals(listOf(DormitoryDocumentRow("Синтетическая справка", "Подтверждено", confirmed = true)), shown.documents?.rows)
        assertEquals(false, shown.documents?.upload)
    }

    @Test
    fun aSettledStudentSeesTheDormitoryOnTheMapAndTheInstructionsAsSafeText() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")

        val shown = viewModel().also { advanceUntilIdle() }.uiState.value.content as DormitoryContent.Shown

        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_settled), shown.stage?.title)
        assertEquals(DormitoryTone.DONE, shown.stage?.tone)
        assertNull(shown.documents)
        val place = checkNotNull(shown.dormitory)
        assertEquals("Синтетическое общежитие", place.name)
        assertEquals(MapDestination("Синтетическое общежитие", "Тестовая ул., 1"), place.map)
        assertEquals("Возьмите паспорт.\n• фото 3x4", place.instructions.plainText)
        assertTrue(place.instructions.blocks.first().spans.any { it.bold && it.text == "паспорт" })
        assertEquals(listOf(DormitoryHarness.STATUS, DormitoryHarness.PERIODS), harness.paths())
    }

    @Test
    fun withoutAnApplicationTheScreenSaysSo() = runTest(main.dispatcher) {
        harness.answer("status-empty.json")

        assertEquals(DormitoryContent.NoApplication, viewModel().also { advanceUntilIdle() }.uiState.value.content)
    }

    @Test
    fun aNullListOfPeriodsCountsAsNoContracts() = runTest(main.dispatcher) {
        harness.answer("status-empty.json")
        harness.contracts("periods-null.json", emptyMap())

        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertEquals(DormitoryContent.NoApplication, state.content)
        assertNull(state.stale)
    }

    @Test
    fun anErrorOfThePeriodsStillKeepsAStatusThatLoaded() = runTest(main.dispatcher) {
        harness.answer("status-empty.json")
        harness.contracts("error.json", emptyMap())

        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertEquals(DormitoryContent.NoApplication, state.content)
        assertEquals(UiText.Res(CoreStrings.common_updated_now), state.stale)
    }

    @Test
    fun aFailedContractsCallKeepsTheStoredContractsUnderTheirAgeBesideTheNewStatus() = runTest(main.dispatcher) {
        harness.answer("status-queue.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to "contracts-debt.json"))
        viewModel().also { advanceUntilIdle() }

        harness.clock.advanceBy(3.hours)
        harness.answer("status-settled.json")
        harness.contracts("periods-current.json", mapOf("2026-01-01" to "error.json"))
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        val shown = state.content as DormitoryContent.Shown
        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_settled), shown.stage?.title)
        assertEquals(listOf("00-00000001"), shown.contracts?.current?.map { it.id })
        assertEquals(UiText.Res(CoreStrings.common_updated_hours, listOf(3)), state.stale)
    }

    @Test
    fun offlineAfterARestartTheStoredAnswerShowsWithItsAge() = runTest(main.dispatcher) {
        harness.answer("status-queue.json")
        viewModel().also { advanceUntilIdle() }

        harness.offline = true
        harness.clock.advanceBy(2.hours)
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertEquals(UiText.Res(DormitoryStrings.dormitory_queue_place, listOf(42)), (state.content as DormitoryContent.Shown).stage?.queuePlace)
        assertEquals(UiText.Res(CoreStrings.common_updated_hours, listOf(2)), state.stale)
    }

    @Test
    fun anItmoErrorKeepsTheLastAnswerAndAPullThatWorksDropsTheAge() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")
        viewModel().also { advanceUntilIdle() }
        harness.answer("error.json")
        harness.clock.advanceBy(3.days)

        val viewModel = viewModel().also { advanceUntilIdle() }
        assertEquals(listOf(DormitoryHarness.STATUS, DormitoryHarness.PERIODS, DormitoryHarness.STATUS), harness.paths())
        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_settled), (viewModel.uiState.value.content as DormitoryContent.Shown).stage?.title)
        assertEquals(UiText.Plural(CorePlurals.common_updated_days, 3, listOf(3)), viewModel.uiState.value.stale)

        harness.answer("status-queue.json")
        viewModel.refresh()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.stale)
        assertEquals(UiText.Res(DormitoryStrings.dormitory_stage_queue), (viewModel.uiState.value.content as DormitoryContent.Shown).stage?.title)
    }

    @Test
    fun anAnswerThatCannotBeStoredIsAFailure() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")
        // A directory where the new file goes: the write fails as a full or broken disk would.
        harness.files.createDirectories("/no-backup/dormitory/state.json.new".toPath())

        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertEquals(DormitoryContent.Failed(AppError.Unknown().toUiText()), state.content)
    }

    @Test
    fun withNothingStoredAFailureIsTheScreenAndARetryLoads() = runTest(main.dispatcher) {
        harness.offline = true
        val viewModel = viewModel().also { advanceUntilIdle() }
        assertEquals(DormitoryContent.Failed(AppError.Network.toUiText()), viewModel.uiState.value.content)

        harness.offline = false
        harness.answer("status-queue.json")
        viewModel.refresh(RefreshMode.Force)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.content is DormitoryContent.Shown)
    }

    @Test
    fun signOutLeavesNothingOnTheDevice() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")
        val repository = harness.repository()
        repository.refresh()

        repository.clearSessionData()
        harness.offline = true
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertEquals(DormitoryContent.Failed(AppError.Network.toUiText()), state.content)
    }

    @Test
    fun anotherAccountsAnswerIsNotShown() = runTest(main.dispatcher) {
        harness.answer("status-settled.json")
        viewModel().also { advanceUntilIdle() }

        harness.isu = 100002
        harness.offline = true
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertEquals(DormitoryContent.Failed(AppError.Network.toUiText()), state.content)
    }

    private fun viewModel() = DormitoryViewModel(harness.repository(), harness.time)
}
