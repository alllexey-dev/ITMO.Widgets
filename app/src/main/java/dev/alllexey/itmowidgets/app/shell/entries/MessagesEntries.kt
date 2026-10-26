package dev.alllexey.itmowidgets.app.shell.entries

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.messages.ui.IsuMessagesRoute
import org.koin.core.parameter.parametersOf

/** Сообщения ИСУ, pushed from Профиль. */
internal fun EntryRegistry.Builder.messagesEntries() {
    entry<AppRoutes.IsuMessages> { key, navigator ->
        IsuMessagesRoute(entryViewModel(), onBack = { navigator.close(key) })
    }
}

/** Koin's definition of [VM] in this entry's own store. */
@Composable
private inline fun <reified VM : ViewModel> entryViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}
