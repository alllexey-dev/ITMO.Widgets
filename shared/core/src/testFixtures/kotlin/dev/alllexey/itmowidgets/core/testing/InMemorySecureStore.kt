package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.storage.SecureStore

/** The secrets of one test in [values]; a second store over the same map reads what the first wrote. */
class InMemorySecureStore(val values: MutableMap<String, String> = mutableMapOf()) : SecureStore {

    override fun read(name: String): String? = values[name]

    override fun write(name: String, value: String) {
        values[name] = value
    }

    override fun delete(name: String) {
        values.remove(name)
    }
}
