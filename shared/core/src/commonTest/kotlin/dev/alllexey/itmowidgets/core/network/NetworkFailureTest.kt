package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.client.error.BackendException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkFailureTest {

    @Test
    fun aKtorIoFailureIsANetworkFailure() {
        assertTrue(kotlinx.io.IOException("offline").isCausedByNetworkFailure())
    }

    @Test
    fun anOkioIoFailureIsANetworkFailureOnEveryPlatform() {
        assertTrue(okio.IOException("offline").isCausedByNetworkFailure())
    }

    @Test
    fun aWrappedIoFailureIsANetworkFailure() {
        assertTrue(BackendException.Transport(okio.IOException("offline")).isCausedByNetworkFailure())
        assertTrue(IllegalStateException("refresh", kotlinx.io.IOException("no route")).isCausedByNetworkFailure())
        assertTrue(MyItmoException.Network(IllegalStateException("engine")).isCausedByNetworkFailure())
    }

    @Test
    fun aFailureWithoutIoInItsChainIsNotANetworkFailure() {
        assertFalse(IllegalStateException("garbled", IllegalArgumentException("body")).isCausedByNetworkFailure())
    }
}
