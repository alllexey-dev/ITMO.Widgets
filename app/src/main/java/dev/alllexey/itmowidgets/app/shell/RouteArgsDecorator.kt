package dev.alllexey.itmowidgets.app.shell

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner

/** The arguments of one entry, under the keys its ViewModel reads from `SavedStateHandle` today. */
object RouteArgsKey : NavMetadataKey<Bundle>

fun routeArgs(args: Bundle): Map<String, Any> = metadata { put(RouteArgsKey, args) }

/**
 * Seeds each entry's [RouteArgsKey] into the default creation extras ([DEFAULT_ARGS_KEY]) of the entry's
 * `ViewModelStoreOwner`, so a ViewModel created inside it (`koinViewModel()` in a feature's `<Name>Route`) finds its
 * arguments in `SavedStateHandle` exactly as under its Fragment host. Goes after the ViewModel store decorator, whose
 * per-entry owner it wraps; an entry without arguments is left alone.
 */
class RouteArgsDecorator<T : Any> : NavEntryDecorator<T>(
    decorate = { entry ->
        val args = entry.metadata[RouteArgsKey]
        val owner = LocalViewModelStoreOwner.current
        if (args == null || owner == null) {
            entry.Content()
        } else {
            val registry = LocalSavedStateRegistryOwner.current
            val seeded = remember(owner, registry, args) { ArgsSeededOwner(owner, registry, args) }
            CompositionLocalProvider(LocalViewModelStoreOwner provides seeded) { entry.Content() }
        }
    },
)

@Composable
fun <T : Any> rememberRouteArgsDecorator(): RouteArgsDecorator<T> = remember { RouteArgsDecorator() }

/** [owner]'s store and factory with [args] added to its default arguments. */
private class ArgsSeededOwner(
    private val owner: ViewModelStoreOwner,
    private val registry: SavedStateRegistryOwner,
    private val args: Bundle,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    private val defaults = owner as? HasDefaultViewModelProviderFactory

    override val viewModelStore: ViewModelStore get() = owner.viewModelStore

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = defaults?.defaultViewModelProviderFactory ?: SavedStateViewModelFactory()

    override val defaultViewModelCreationExtras: CreationExtras
        get() {
            val base = defaults?.defaultViewModelCreationExtras ?: CreationExtras.Empty
            return MutableCreationExtras(base).apply {
                set(DEFAULT_ARGS_KEY, Bundle(base[DEFAULT_ARGS_KEY] ?: Bundle.EMPTY).apply { putAll(args) })
                if (base[VIEW_MODEL_STORE_OWNER_KEY] == null) set(VIEW_MODEL_STORE_OWNER_KEY, owner)
                if (base[SAVED_STATE_REGISTRY_OWNER_KEY] == null) set(SAVED_STATE_REGISTRY_OWNER_KEY, registry)
            }
        }
}
