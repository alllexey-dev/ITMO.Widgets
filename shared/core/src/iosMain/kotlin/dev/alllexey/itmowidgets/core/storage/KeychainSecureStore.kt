package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecMissingEntitlement
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The iOS [SecureStore]: one Keychain generic password per name, service [SERVICE], account = name, in the access
 * group from this process's Info.plist (`KeychainGroup`), shared by the app and the notification service.
 * `kSecAttrAccessibleAfterFirstUnlock`: the notification service and background refresh read it while the device
 * is locked. Never synchronised to iCloud, never logged.
 *
 * Without the access-group entitlement (an unsigned or re-signed build, a Kotlin/Native test binary) the Keychain
 * answers `errSecMissingEntitlement`; the store then uses the process's default group for the rest of its life and
 * logs that once. Hence Keychain tests run in the Xcode test target, which has the app's entitlements.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)
class KeychainSecureStore(
    accessGroup: String?,
    private val log: AppLog
) : SecureStore {

    private val group: String? = accessGroup?.takeIf { it.isNotBlank() }
    private val groupRefused = AtomicBoolean(false)

    override fun read(name: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = withGroup { group ->
            query(name, group, kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)
                .use { SecItemCopyMatching(it, result.ptr) }
        }
        when (status) {
            errSecSuccess -> decode(name, CFBridgingRelease(result.value) as? NSData)
            errSecItemNotFound -> null
            else -> throw KeychainException("read", name, status)
        }
    }

    override fun write(name: String, value: String) {
        val data = encode(value)
        val status = withGroup { group ->
            val updated = query(name, group).use { search ->
                attributes(kSecValueData to data, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock)
                    .use { SecItemUpdate(search, it) }
            }
            if (updated != errSecItemNotFound) return@withGroup updated
            query(name, group, kSecValueData to data, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock)
                .use { SecItemAdd(it, null) }
        }
        if (status != errSecSuccess) throw KeychainException("write", name, status)
    }

    override fun delete(name: String) {
        val status = withGroup { group -> query(name, group).use { SecItemDelete(it) } }
        if (status != errSecSuccess && status != errSecItemNotFound) throw KeychainException("delete", name, status)
    }

    /** Runs [operation] in the access group; on a missing entitlement, once more and from now on without it. */
    private inline fun withGroup(operation: (group: String?) -> Int): Int {
        if (group == null || groupRefused.load()) return operation(null)
        val status = operation(group)
        if (status != errSecMissingEntitlement) return status
        if (groupRefused.compareAndSet(expectedValue = false, newValue = true)) {
            log.warn(TAG, "Keychain access group not entitled; using the default group")
        }
        return operation(null)
    }

    private fun query(
        name: String,
        group: String?,
        vararg extra: Pair<CFStringRef?, Any?>
    ): CFDictionaryRef {
        require(name.isNotEmpty() && '/' !in name) { "'$name' is not a secret name" }
        val entries = buildList {
            add(kSecClass to kSecClassGenericPassword)
            add(kSecAttrService to SERVICE)
            add(kSecAttrAccount to name)
            if (group != null) add(kSecAttrAccessGroup to group)
            addAll(extra)
        }
        return attributes(*entries.toTypedArray())
    }

    /**
     * A +1 CFDictionary of [entries]; [use] releases it. A value is a Core Foundation constant (a `CPointer`), passed
     * as it is: the Security framework checks the CF type, and a Kotlin `true` bridged to `NSNumber` is not a
     * `CFBoolean` (`errSecParam`). Any other value is an Objective-C object (`String`, `NSData`), bridged.
     */
    private fun attributes(vararg entries: Pair<CFStringRef?, Any?>): CFDictionaryRef {
        val dictionary = checkNotNull(
            CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                entries.size.convert(),
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            )
        )
        entries.forEach { (key, value) ->
            if (value is CPointer<*>) {
                CFDictionarySetValue(dictionary, key, value)
            } else {
                val bridged = CFBridgingRetain(value)
                CFDictionarySetValue(dictionary, key, bridged)
                CFRelease(bridged)
            }
        }
        return dictionary
    }

    private inline fun <R> CFDictionaryRef.use(block: (CFDictionaryRef) -> R): R = try {
        block(this)
    } finally {
        CFRelease(this)
    }

    @OptIn(BetaInteropApi::class)
    private fun encode(value: String): NSData =
        checkNotNull(NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding)) { "Not UTF-8" }

    @OptIn(BetaInteropApi::class)
    private fun decode(name: String, data: NSData?): String {
        val text = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding) }
        return checkNotNull(text) { "Keychain item '$name' is not UTF-8 text" }.toString()
    }

    companion object {

        /** The `kSecAttrService` of every item; the item's account is its name. */
        const val SERVICE = "dev.alllexey.itmowidgets"

        /** The store in the access group this process's Info.plist names. */
        fun fromMainBundle(log: AppLog): KeychainSecureStore =
            KeychainSecureStore(BundleIdentifiers.fromMainBundle().keychainGroup, log)

        private const val TAG = "KeychainSecureStore"
    }
}

/** A Keychain call failed with an `OSStatus` other than "not found"; the message never holds a value. */
class KeychainException(operation: String, name: String, val status: Int) :
    IllegalStateException("Keychain $operation of '$name' failed: OSStatus $status")
