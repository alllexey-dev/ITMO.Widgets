package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.testing.CrossProcessLockContract
import kotlin.test.Test

class SingleProcessLockTest {

    /** Every holder in the process shares one instance, as the app graph hands it out. */
    @Test
    fun keepsTheCrossProcessLockContract() = CrossProcessLockContract.checkAll {
        val lock = SingleProcessLock()
        val opener: () -> CrossProcessLock = { lock }
        opener
    }
}
