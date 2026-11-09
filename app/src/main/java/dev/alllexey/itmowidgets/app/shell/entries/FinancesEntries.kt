package dev.alllexey.itmowidgets.app.shell.entries

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.finances.presentation.TuitionViewModel
import dev.alllexey.itmowidgets.feature.finances.ui.TuitionRoute
import org.koin.core.parameter.parametersOf

/** `Оплата обучения`, pushed from Профиль; `Оплатить` opens my.itmo in the system browser from the route itself. */
internal fun EntryRegistry.Builder.financesEntries() {
    entry<AppRoutes.Tuition> { key, navigator ->
        val viewModel: TuitionViewModel = entryViewModel()
        TuitionRoute(viewModel, onBack = { navigator.close(key) })
    }
}

/** Koin's definition of [VM] in this entry's own store. */
@Composable
private inline fun <reified VM : ViewModel> entryViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}
