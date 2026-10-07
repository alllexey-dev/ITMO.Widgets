package dev.alllexey.itmowidgets.feature.settings.reference

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Defines [create]'s view model in Koin, where the Fragment's `by viewModel()` asks for it, so a Hilt Fragment
 * renders fixture state. The test class stops Koin around each test with `StopKoinRule`.
 */
internal inline fun <F : Fragment, reified VM : ViewModel> F.withViewModel(crossinline create: () -> VM): F = apply {
    val fixture = module { viewModel<VM> { create() } }
    // The first view of a test starts Koin; the next appearances replace the definition with their own.
    runCatching { loadKoinModules(fixture) }.onFailure { startKoin { modules(fixture) } }
}
