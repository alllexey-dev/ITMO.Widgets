package dev.alllexey.itmowidgets.core.di

import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.session.KeychainTokenStorage
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import org.koin.dsl.koinApplication

/**
 * [iosCoreModule] in a Koin application of its own, outside the global context, as Swift reaches it before IO-05
 * starts the app's graph: the hosted `SessionTests` run the real bindings inside the app's entitlements (Keychain
 * group, App Group). Koin's `get` is reified, so Swift reads through these properties.
 */
class IosCoreGraph(host: IosCoreHost) {

    private val application = koinApplication {
        allowOverride(false)
        modules(iosCoreModule(host))
    }

    val tokenStorage: KeychainTokenStorage
        get() = application.koin.get()

    val appGroupDirectory: AppGroupDirectory
        get() = application.koin.get()

    /** Every contribution to the open set, the order unspecified. */
    val sessionDataCleaners: List<SessionDataCleaner>
        get() = application.koin.getAll()

    fun close() = application.close()
}
