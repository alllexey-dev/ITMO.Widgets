package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.alllexey.itmowidgets.ios.di.IosKoin
import org.koin.core.parameter.parametersOf

/**
 * Koin's definition of [VM] with [arguments] as its `SavedStateHandle`, as Android's shell seeds an entry's handle from
 * its key, in a store of its own that is cleared when this call leaves the composition: a sheet's content lives as
 * long as its controller, a dialog inside it only while it shows. `koinViewModel()` would hand the definition the
 * controller's empty handle, since a Compose controller on iOS has no destination arguments (IO-09f).
 */
@Composable
internal inline fun <reified VM : ViewModel> hostedViewModel(vararg arguments: Pair<String, Any?>): VM {
    val store = remember { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    return remember(store) {
        val handle = SavedStateHandle(mapOf(*arguments))
        val factory = viewModelFactory {
            addInitializer(VM::class) { IosKoin.koin().get<VM> { parametersOf(handle) } }
        }
        ViewModelProvider.create(store, factory)[VM::class]
    }
}
