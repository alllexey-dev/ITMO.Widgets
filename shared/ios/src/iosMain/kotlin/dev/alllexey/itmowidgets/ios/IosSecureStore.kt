package dev.alllexey.itmowidgets.ios

import dev.alllexey.itmowidgets.core.diagnostics.OsLogAppLog
import dev.alllexey.itmowidgets.core.storage.KeychainSecureStore
import dev.alllexey.itmowidgets.core.storage.SecureStore

/**
 * The Keychain [SecureStore] as Swift reaches it before IO-05 starts the Koin graph; the hosted
 * `KeychainTests` use it, because a Kotlin/Native test binary has no Keychain entitlement (L18 IO-04a).
 */
object IosSecureStore {
    fun keychain(): SecureStore = KeychainSecureStore.fromMainBundle(OsLogAppLog())
}
