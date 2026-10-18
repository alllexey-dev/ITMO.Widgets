package dev.alllexey.itmowidgets.app.shell.entries

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.map.presentation.CampusMapViewModel
import dev.alllexey.itmowidgets.feature.map.ui.CampusMapRoute
import org.koin.core.parameter.parametersOf

/** The campus map, full screen over the bar; its route arguments travel as the view model's saved state. */
internal fun EntryRegistry.Builder.mapEntries() {
    entry<AppRoutes.CampusMap>(
        args = { bundleOf(*CampusMapViewModel.argsOf(it.args).toList().toTypedArray()) },
    ) { key, navigator ->
        CampusMapRoute(entryViewModel(), onBack = { navigator.close(key) })
    }
}

/** Koin's definition of [VM] in this entry's own store, with the entry's `SavedStateHandle` (its arguments). */
@Composable
private inline fun <reified VM : ViewModel> entryViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}
