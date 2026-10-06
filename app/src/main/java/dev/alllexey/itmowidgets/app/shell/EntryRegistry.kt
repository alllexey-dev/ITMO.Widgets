package dev.alllexey.itmowidgets.app.shell

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.DialogSceneStrategy
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.RouteKind
import kotlin.reflect.KClass

/**
 * What the shell shows for each key: the content and the `SavedStateHandle` arguments, registered per key class by the
 * `entries/<Tab>Entries.kt` files that [shellEntries] aggregates. The layer comes from the key itself
 * ([AppRoute.kind], [AppRoute.sheetPolicy]), never from a registration. A key nobody registered yet shows
 * [UnregisteredEntry] (the Compose shell stays debug-only until every key is registered).
 */
class EntryRegistry private constructor(private val registrations: Map<KClass<out AppRoute>, Registration<*>>) {

    fun isRegistered(route: AppRoute): Boolean = route::class in registrations

    /** The undecorated entry of [route] under [contentKey], with its layer's scene metadata and its arguments. */
    fun entry(route: AppRoute, contentKey: Any, navigator: Nav3AppNavigator): NavEntry<AppRoute> {
        @Suppress("UNCHECKED_CAST")
        val registration = registrations[route::class] as Registration<AppRoute>?
        val args = registration?.args?.invoke(route)
        return NavEntry(route, contentKey, metadataOf(route, args)) { key ->
            if (registration == null) UnregisteredEntry(key) else registration.content(key, navigator)
        }
    }

    private fun metadataOf(route: AppRoute, args: Bundle?): Map<String, Any> {
        val layer = when (route.kind) {
            RouteKind.SHEET -> bottomSheet(checkNotNull(route.sheetPolicy) { "$route is a sheet without a policy" })
            RouteKind.DIALOG -> DialogSceneStrategy.dialog()
            RouteKind.GATE, RouteKind.TAB_ROOT, RouteKind.SCREEN -> emptyMap()
        }
        return if (args == null) layer else layer + routeArgs(args)
    }

    private class Registration<K : AppRoute>(
        val args: (K) -> Bundle?,
        val content: @Composable (K, Nav3AppNavigator) -> Unit,
    )

    /** Collects registrations; a key class registered twice is a mistake of two cards. */
    class Builder internal constructor() {
        private val registrations = mutableMapOf<KClass<out AppRoute>, Registration<*>>()

        /**
         * Registers [K]: [content] renders the feature's `<Name>Route` with callbacks to the navigator, [args] puts the
         * key's fields under the keys its ViewModel reads from `SavedStateHandle`.
         */
        inline fun <reified K : AppRoute> entry(
            noinline args: (K) -> Bundle? = { null },
            noinline content: @Composable (key: K, navigator: Nav3AppNavigator) -> Unit,
        ) = register(K::class, args, content)

        @PublishedApi
        internal fun <K : AppRoute> register(
            type: KClass<K>,
            args: (K) -> Bundle?,
            content: @Composable (K, Nav3AppNavigator) -> Unit,
        ) {
            check(type !in registrations) { "${type.simpleName} is registered twice" }
            registrations[type] = Registration(args, content)
        }

        internal fun build() = EntryRegistry(registrations.toMap())
    }

    companion object {
        /** The test tag of the placeholder an unregistered [route] shows. */
        fun placeholderTag(route: AppRoute): String = "unregistered:${route::class.simpleName}"
    }
}

fun entryRegistry(build: EntryRegistry.Builder.() -> Unit): EntryRegistry = EntryRegistry.Builder().apply(build).build()

/** An empty surface tagged with the key's class name: no text, since the shell never shows it outside debug builds. */
@Composable
private fun UnregisteredEntry(route: AppRoute) {
    Surface(Modifier.fillMaxSize().testTag(EntryRegistry.placeholderTag(route))) {}
}
