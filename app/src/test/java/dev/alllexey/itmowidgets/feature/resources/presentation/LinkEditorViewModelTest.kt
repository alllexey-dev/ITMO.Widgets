package dev.alllexey.itmowidgets.feature.resources.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeSubjectLinksRepository()
    private val lecture = LinkAudience(7101, "ФИЗ ПИИКТ 3", typeId = 1, depth = 1)
    private val practice = LinkAudience(7102, "ФИЗ ПИИКТ 3.2", typeId = 3, depth = 2)
    private val lab = LinkAudience(7103, "ФИЗ ПИИКТ 3.2.1", typeId = 2, depth = 3)

    @Test fun `the site suggests a category until one is picked`() = runTest(main.dispatcher) {
        val vm = model()

        vm.onUrlChanged("https://github.com/itmo/labs")
        assertEquals(LinkCategory.TASKS, vm.uiState.value.category)
        vm.onUrlChanged("https://example.org")
        assertNull(vm.uiState.value.category)

        vm.onCategorySelected(LinkCategory.NOTES)
        vm.onUrlChanged("https://t.me/chat")
        assertEquals(LinkCategory.NOTES, vm.uiState.value.category)
    }

    @Test fun `a link that is not https or a long title is not saved`() = runTest(main.dispatcher) {
        val vm = model()
        vm.onCategorySelected(LinkCategory.OTHER)

        vm.onUrlChanged("http://example.org"); vm.save(); runCurrent()
        assertEquals(UiText.Resource(R.string.links_invalid_url), vm.uiState.value.urlError)

        vm.onUrlChanged("https://example.org"); vm.onTitleChanged("а".repeat(121)); vm.save(); runCurrent()
        assertNull(vm.uiState.value.urlError)
        assertEquals(UiText.Resource(R.string.links_title_too_long), vm.uiState.value.titleError)
        assertTrue(repository.actions.isEmpty())
    }

    @Test fun `options are only me, every flow of the viewer in order and everybody`() = runTest(main.dispatcher) {
        show(linksSnapshot(audiences = listOf(lecture, practice, lab)))
        val vm = model()

        assertEquals(listOf(LinkAudienceOption.All, LinkAudienceOption.Flow(lecture), LinkAudienceOption.Flow(practice),
            LinkAudienceOption.Flow(lab), LinkAudienceOption.Private), vm.uiState.value.options)
        assertEquals(LinkAudienceOption.Private, vm.uiState.value.selected)

        show(linksSnapshot()); runCurrent()
        assertEquals(listOf(LinkAudienceOption.All, LinkAudienceOption.Private), vm.uiState.value.options)
    }

    @Test fun `choosing a flow keeps its id and a flow gone from the schedule falls back to only me`() = runTest(main.dispatcher) {
        show(linksSnapshot(audiences = listOf(lecture, practice, lab)))
        val vm = model()

        vm.onAudienceSelected(LinkAudienceOption.Flow(lab))
        assertEquals(LinkVisibility.FLOW, vm.uiState.value.visibility)
        assertEquals(7103L, vm.uiState.value.flowId)
        assertEquals(LinkAudienceOption.Flow(lab), vm.uiState.value.selected)
        vm.onAudienceSelected(LinkAudienceOption.Flow(lecture))
        assertEquals(7101L, vm.uiState.value.flowId)
        vm.onAudienceSelected(LinkAudienceOption.Flow(LinkAudience(9999, "Чужой поток", typeId = 1, depth = 1)))
        assertEquals(7101L, vm.uiState.value.flowId)

        show(linksSnapshot(audiences = listOf(practice, lab))); runCurrent()
        assertEquals(LinkVisibility.PRIVATE, vm.uiState.value.visibility)
        assertNull(vm.uiState.value.flowId)
        vm.onAudienceSelected(LinkAudienceOption.All)
        assertEquals(LinkVisibility.ALL, vm.uiState.value.visibility)
        assertNull(vm.uiState.value.flowId)
    }

    @Test fun `premoderation of the period reaches the form`() = runTest(main.dispatcher) {
        show(linksSnapshot(audiences = listOf(lecture)).copy(premoderation = false))
        val vm = model()
        assertFalse(vm.uiState.value.premoderation)

        show(linksSnapshot(audiences = listOf(lecture))); runCurrent()
        assertTrue(vm.uiState.value.premoderation)
    }

    @Test fun `without the connection only private remains and a shared choice falls back`() = runTest(main.dispatcher) {
        show(linksSnapshot(audiences = listOf(lecture)))
        val vm = model()
        vm.onAudienceSelected(LinkAudienceOption.Flow(lecture))
        assertEquals(LinkVisibility.FLOW, vm.uiState.value.visibility)

        show(linksSnapshot(audiences = listOf(lecture), servicesEnabled = false)); runCurrent()

        assertEquals(listOf(LinkAudienceOption.Private), vm.uiState.value.options)
        assertEquals(LinkVisibility.PRIVATE, vm.uiState.value.visibility)
        assertNull(vm.uiState.value.flowId)
        vm.onAudienceSelected(LinkAudienceOption.All)
        assertEquals(LinkVisibility.PRIVATE, vm.uiState.value.visibility)
    }

    @Test fun `saving sends the form with the chosen flow and reports it`() = runTest(main.dispatcher) {
        show(linksSnapshot(audiences = listOf(lecture, practice, lab)))
        val vm = model()
        vm.onUrlChanged(" https://docs.google.com/spreadsheets/d/abc ")
        vm.onAudienceSelected(LinkAudienceOption.Flow(practice))

        vm.save(); runCurrent()

        assertEquals(LinkEvent.Saved, vm.events.first())
        val saved = repository.lastSave!!
        assertEquals("https://docs.google.com/spreadsheets/d/abc", saved.url)
        assertEquals(LinkCategory.SCORES, saved.category)
        assertNull(saved.title)
        assertEquals(LinkVisibility.FLOW, saved.visibility)
        assertEquals(7102L, saved.flowId)
    }

    @Test fun `a failed save keeps the form and reports the error`() = runTest(main.dispatcher) {
        repository.result = AppResult.Failure(AppError.Network)
        val vm = model()
        vm.onUrlChanged("https://github.com/itmo/labs")

        vm.save(); runCurrent()

        assertEquals(LinkEvent.Failed(UiText.Resource(R.string.common_error_network)), vm.events.first())
        assertEquals("https://github.com/itmo/labs", vm.uiState.value.url)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test fun `editing starts from the own link and keeps its category and flow`() = runTest(main.dispatcher) {
        show(linksSnapshot(mine = listOf(subjectLink("own", LinkCategory.NOTES, LinkVisibility.FLOW, title = "Конспект", flowId = 7103)),
            audiences = listOf(lecture, practice, lab)))
        val vm = model(linkId = "own")

        assertTrue(vm.uiState.value.editing)
        assertEquals(LinkAudienceOption.Flow(lab), vm.uiState.value.selected)
        assertEquals("https://example.org/own", vm.uiState.value.url)
        assertEquals("Конспект", vm.uiState.value.title)
        vm.onUrlChanged("https://github.com/itmo/labs")
        assertEquals(LinkCategory.NOTES, vm.uiState.value.category)

        vm.save(); runCurrent()
        assertEquals("own", repository.lastSave!!.id)
        assertEquals(7103L, repository.lastSave!!.flowId)
    }

    private fun show(snapshot: SubjectLinksSnapshot) { repository.state.value = SubjectLinksState.Content(snapshot) }

    private fun TestScope.model(linkId: String? = null): LinkEditorViewModel {
        val vm = LinkEditorViewModel(SavedStateHandle(buildMap {
            put(SubjectLinksArgs.SUBJECT_ID, 42L); put(SubjectLinksArgs.SUBJECT_NAME, "Предмет"); put(SubjectLinksArgs.PERIOD_KEY, "2026-1")
            linkId?.let { put(SubjectLinksArgs.LINK_ID, it) }
        }), repository)
        runCurrent()
        return vm
    }
}
