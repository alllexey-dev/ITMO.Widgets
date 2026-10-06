package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.lifecycle.ViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Makes Koin build every [T] that a Fragment or Activity asks for next with [create], in a JVM test whose host
 * obtains [T] through Koin (`by viewModel()`). It starts the app graph when nothing started it, as under
 * `HiltTestApplication`, so the test also needs [StopKoinRule]; release definitions of other types stay as they are.
 */
inline fun <reified T : ViewModel> presetViewModel(context: Context, noinline create: () -> T) {
    KoinStarter.ensureStarted(context).loadModules(listOf(module { viewModel<T> { create() } }), allowOverride = true)
}
