package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.testing.InMemorySecureStore
import dev.alllexey.itmowidgets.core.testing.SecureStoreContract
import kotlin.test.Test

class InMemorySecureStoreTest {

    @Test
    fun theFakeForConsumerTestsKeepsTheSecureStoreContract() = SecureStoreContract.checkAll {
        val values = mutableMapOf<String, String>()
        val opener: () -> SecureStore = { InMemorySecureStore(values) }
        opener
    }
}
