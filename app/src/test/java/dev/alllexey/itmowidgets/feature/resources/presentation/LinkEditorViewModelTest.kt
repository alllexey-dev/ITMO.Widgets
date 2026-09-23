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
    private val group = LinkAudience(LinkVisibility.GROUP, "P3119")
    private val flow = LinkAudience(LinkVisibility.FLOW, "P3119, P3120")

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

    @Test fun `visibilities are private, the viewer's audiences and all`() = runTest(main.dispatcher) {
        show(linksSnapshot(audiences = listOf(flow, group)))
        val vm = model()

        assertEquals(listOf(LinkVisibility.PRIVATE, LinkVisibility.GROUP, LinkVisibility.FLOW, LinkVisibility.ALL), vm.uiState.value.visibilities)
        assertEquals(listOf(flow, group), vm.uiState.value.audiences)

        show(linksSnapshot(audiences = listOf(group))); runCurrent()
        assertEquals(listOf(LinkVisibility.PRIVATE, LinkVisibility.GROUP, LinkVisibility.ALL), vm.uiState.value.visibilities)
    }

    @Test fun `premoderation of the period reaches the form`() = runTest(main.dispatcher) {
        show(linksSnapshot(audiences = listOf(group)).copy(premoderation = false))
        val vm = model()
        assertFalse(vm.uiState.value.premoderation)

        show(linksSnapshot(audiences = listOf(group))); runCurrent()
        assertTrue(vm.uiState.value.premoderation)
    }

    @Test fun `without the connection only private remains and a public choice falls back`() = runTest(main.dispatcher) {
        show(linksSnapshot(audiences = listOf(group)))
        val vm = model()
        vm.onVisibilitySelected(LinkVisibility.ALL)
        assertEquals(LinkVisibility.ALL, vm.uiState.value.visibility)

        show(linksSnapshot(servicesEnabled = false)); runCurrent()

        assertEquals(listOf(LinkVisibility.PRIVATE), vm.uiState.value.visibilities)
        assertEquals(LinkVisibility.PRIVATE, vm.uiState.value.visibility)
        vm.onVisibilitySelected(LinkVisibility.GROUP)
        assertEquals(LinkVisibility.PRIVATE, vm.uiState.value.visibility)
    }

    @Test fun `saving sends the form and reports it`() = runTest(main.dispatcher) {
        show(linksSnapshot(audiences = listOf(group)))
        val vm = model()
        vm.onUrlChanged(" https://docs.google.com/spreadsheets/d/abc ")
        vm.onVisibilitySelected(LinkVisibility.GROUP)

        vm.save(); runCurrent()

        assertEquals(LinkEvent.Saved, vm.events.first())
        val saved = repository.lastSave!!
        assertEquals("https://docs.google.com/spreadsheets/d/abc", saved.url)
        assertEquals(LinkCategory.SCORES, saved.category)
        assertNull(saved.title)
        assertEquals(LinkVisibility.GROUP, saved.visibility)
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

    @Test fun `editing starts from the own link and keeps its category`() = runTest(main.dispatcher) {
        show(linksSnapshot(mine = listOf(subjectLink("own", LinkCategory.NOTES, title = "Конспект"))))
        val vm = model(linkId = "own")

        assertTrue(vm.uiState.value.editing)
        assertEquals("https://example.org/own", vm.uiState.value.url)
        assertEquals("Конспект", vm.uiState.value.title)
        vm.onUrlChanged("https://github.com/itmo/labs")
        assertEquals(LinkCategory.NOTES, vm.uiState.value.category)

        vm.save(); runCurrent()
        assertEquals("own", repository.lastSave!!.id)
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
