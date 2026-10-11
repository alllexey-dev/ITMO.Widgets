package dev.alllexey.itmowidgets.ios

import dev.alllexey.itmowidgets.core.diagnostics.OsLogAppLog
import dev.alllexey.itmowidgets.core.storage.KeychainSecureStore
import dev.alllexey.itmowidgets.core.storage.SecureStore

/**
 * The Keychain [SecureStore] as Swift reaches it before the Koin graph starts; the hosted
 * `KeychainTests` use it, because a Kotlin/Native test binary has no Keychain entitlement.
 */
object IosSecureStore {
    fun keychain(): SecureStore = KeychainSecureStore.fromMainBundle(OsLogAppLog())
}
