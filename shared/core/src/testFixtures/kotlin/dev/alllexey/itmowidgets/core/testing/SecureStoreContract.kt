package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.storage.SecureStore
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What every [SecureStore] guarantees, for a platform test to run on its store with [checkAll]. Plain functions
 * rather than an abstract test class: `:app`'s JVM tests compile these fixtures without a kotlin.test runner.
 */
object SecureStoreContract {

    /**
     * Runs every check on its own storage: [freshStorage] makes a storage that is empty and returns its opener;
     * every call of the opener opens another store over that storage, as a later process would.
     */
    fun checkAll(freshStorage: () -> () -> SecureStore) {
        checks.forEach { (name, check) ->
            try {
                check(freshStorage())
            } catch (failure: Throwable) {
                throw AssertionError("SecureStore contract: $name", failure)
            }
        }
    }

    private val checks: Map<String, (openStore: () -> SecureStore) -> Unit> = mapOf(
        "a name never written reads null" to { openStore ->
            assertNull(openStore().read(NAME))
        },
        "a written value reads back, also from a store opened later" to { openStore ->
            val store = openStore()
            store.write(NAME, VALUE)
            assertEquals(VALUE, store.read(NAME))
            assertEquals(VALUE, openStore().read(NAME))
        },
        "a write replaces the whole value" to { openStore ->
            val store = openStore()
            store.write(NAME, VALUE.repeat(4))
            store.write(NAME, OTHER_VALUE)
            assertEquals(OTHER_VALUE, openStore().read(NAME))
        },
        "delete removes the value and a missing name is no error" to { openStore ->
            val store = openStore()
            store.write(NAME, VALUE)
            store.delete(NAME)
            store.delete(OTHER_NAME)
            assertNull(store.read(NAME))
            assertNull(openStore().read(NAME))
        },
        "names are independent" to { openStore ->
            val store = openStore()
            store.write(NAME, VALUE)
            store.write(OTHER_NAME, OTHER_VALUE)
            store.delete(OTHER_NAME)
            assertEquals(VALUE, openStore().read(NAME))
            assertNull(openStore().read(OTHER_NAME))
        },
        "a value keeps its lines and non-ASCII text" to { openStore ->
            openStore().write(NAME, MULTILINE_VALUE)
            assertEquals(MULTILINE_VALUE, openStore().read(NAME))
        },
    )

    private const val NAME = "contract_tokens.enc"
    private const val OTHER_NAME = "other_tokens.enc"
    private const val VALUE = "synthetic-secret"
    private const val OTHER_VALUE = "other"
    private const val MULTILINE_VALUE = "123456\nBearer synthetic-session\nСессия ✓"
}
