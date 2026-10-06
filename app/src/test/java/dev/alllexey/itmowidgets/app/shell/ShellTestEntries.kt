package dev.alllexey.itmowidgets.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the fake entries saw: the tab roots' bounds and composition (now, and every time one entered it in order), taps
 * on a tab, Back inside a form sheet.
 */
class ShellProbe {
    val tabBounds = mutableMapOf<AppTab, Rect>()
    val composedTabs = mutableSetOf<AppTab>()
    val compositions = mutableListOf<AppTab>()
    var tabClicks = 0
    var formBacks = 0
}

/** Synthetic keys and arguments of the shell tests. */
object ShellSamples {
    val links = SubjectLinksArgs(subjectId = 5, subjectName = "Математика", periodKey = "2026/2027:1")
    val teacher = TeacherReviewArgs(teacherIsu = 100002, teacherName = "Иванов Иван Иванович")
    const val ISU = 4242
}

/**
 * Fake entries for every layer: tab roots with a list, a saveable sub-tab and a ViewModel; plain overlay screens; an
 * arguments probe; a free and a form sheet; a dialog. [AppRoutes.Diagnostics], [AppRoutes.Auth] and every other key
 * stay unregistered.
 */
fun fakeEntries(probe: ShellProbe): EntryRegistry = entryRegistry {
    entry<AppRoutes.TabRoot> { key, _ -> FakeTabRoot(key.tab, probe) }
    entry<AppRoutes.Settings> { key, _ -> FakeScreen("settings:${key.page}") }
    entry<AppRoutes.Friends> { _, _ -> FakeScreen("friends") }
    entry<AppRoutes.UserProfile>(args = { bundleOf(UserScreenArgs.ISU to it.isu) }) { _, _ -> ArgsProbe() }
    entry<AppRoutes.SubjectLinks> { _, _ -> FakeSheet("links") }
    entry<AppRoutes.IcsExport> { _, _ -> FakeSheet("ics") }
    entry<AppRoutes.ReviewEditor> { _, _ ->
        BackHandler { probe.formBacks++ }
        FakeSheet("review")
    }
    entry<AppRoutes.LinkUnavailable> { _, _ -> Surface(Modifier.size(200.dp).testTag("dialog:link")) {} }
}

class CountingViewModel : ViewModel() {
    val id = ids.incrementAndGet()

    companion object {
        private val ids = AtomicInteger()
    }
}

@Composable
private fun FakeTabRoot(tab: AppTab, probe: ShellProbe) {
    val counter = viewModel { CountingViewModel() }
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    DisposableEffect(tab) {
        probe.composedTabs += tab
        probe.compositions += tab
        onDispose { probe.composedTabs -= tab }
    }
    Column(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { probe.tabBounds[tab] = it.boundsInRoot() }
            // No clickable: its merged semantics would hide the tagged children from the finders.
            .pointerInput(Unit) { detectTapGestures { probe.tabClicks++ } },
    ) {
        Text("sub:$subTab", Modifier.testTag("sub:$tab"))
        Text("vm:${counter.id}", Modifier.testTag("vm:$tab"))
        Button(onClick = { subTab++ }, Modifier.testTag("nextSub:$tab")) { Text("next") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("list:$tab"), state = list) {
            items(100) { Text("$tab $it", Modifier.height(48.dp).testTag("item:$tab:$it")) }
        }
    }
}

@Composable
private fun FakeScreen(name: String) {
    Box(Modifier.fillMaxSize().testTag("screen:$name"))
}

@Composable
private fun FakeSheet(name: String) {
    Column(Modifier.fillMaxWidth().height(240.dp).testTag("content:$name")) { Text(name) }
}

class ArgsViewModel(handle: SavedStateHandle) : ViewModel() {
    val isu: Int? = handle[UserScreenArgs.ISU]
}

@Composable
private fun ArgsProbe() {
    val model = viewModel { ArgsViewModel(createSavedStateHandle()) }
    Text("isu:${model.isu}", Modifier.testTag("args"))
}
