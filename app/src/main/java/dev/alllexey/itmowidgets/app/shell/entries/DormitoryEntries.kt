package dev.alllexey.itmowidgets.app.shell.entries

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryViewModel
import dev.alllexey.itmowidgets.feature.dormitory.presentation.DormitoryWeb
import dev.alllexey.itmowidgets.feature.dormitory.ui.DormitoryRoute
import org.koin.core.parameter.parametersOf

/** `Общежитие`, pushed from Профиль; picking a date and uploading documents open my.itmo's dormitory section. */
internal fun EntryRegistry.Builder.dormitoryEntries() {
    entry<AppRoutes.Dormitory> { key, navigator ->
        val viewModel: DormitoryViewModel = entryViewModel()
        DormitoryRoute(viewModel, onBack = { navigator.close(key) }, onOpenMyItmo = { navigator.open(DormitoryWeb.route) })
    }
}

/** Koin's definition of [VM] in this entry's own store. */
@Composable
private inline fun <reified VM : ViewModel> entryViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}
