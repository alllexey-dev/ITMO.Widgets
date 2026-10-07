package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.client.error.BackendException
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlin.test.Test
import kotlin.test.assertTrue
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNotConnectedToInternet

class DarwinNetworkFailureTest {

    private val offline = DarwinHttpRequestException(
        NSError.errorWithDomain(NSURLErrorDomain, NSURLErrorNotConnectedToInternet, null)
    )

    @Test
    fun theDarwinEngineFailureIsANetworkFailure() {
        assertTrue(offline.isCausedByNetworkFailure())
    }

    @Test
    fun theDarwinEngineFailureWrappedByEitherClientIsANetworkFailure() {
        assertTrue(BackendException.Transport(offline).isCausedByNetworkFailure())
        assertTrue(IllegalStateException("refresh", MyItmoException.Network(offline)).isCausedByNetworkFailure())
    }
}
