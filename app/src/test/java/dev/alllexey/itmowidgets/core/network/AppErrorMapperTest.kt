package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.result.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppErrorMapperTest {

    @Test
    fun `maps network failures`() {
        assertEquals(AppError.Network, IOException().toAppError())
        assertEquals(AppError.Network, MyItmoException.Network(SocketTimeoutException()).toAppError())
        assertEquals(AppError.Network, BackendException.Transport(IOException()).toAppError())
    }

    @Test
    fun `a network failure anywhere in the cause chain is a network error, not an ended session`() {
        assertEquals(AppError.Network, RuntimeException(RuntimeException(UnknownHostException())).toAppError())
        assertEquals(AppError.Network, IOException(MyItmoException.Auth(401)).toAppError())
        assertEquals(AppError.Unauthorized, MyItmoException.Auth(401).toAppError())
    }

    @Test
    fun `maps MyItmoApi 2_x failures with the released semantics`() {
        assertEquals(AppError.Network, MyItmoException.Network(IOException()).toAppError())
        assertEquals(AppError.Network, RuntimeException(MyItmoException.Network(UnknownHostException())).toAppError())
        assertEquals(AppError.Unauthorized, MyItmoException.Auth(400).toAppError())
        assertEquals(AppError.Unauthorized, MyItmoException.Api(200, 401).toAppError())
        assertEquals(AppError.Forbidden, MyItmoException.Http(403).toAppError())
        assertEquals(AppError.NotFound, MyItmoException.Http(404).toAppError())
        // ITMO.ID failing to answer a refresh keeps the session: retriable, never a request to sign in again.
        for (failure in listOf(MyItmoException.Http(401), MyItmoException.Http(503), MyItmoException.Decode())) {
            assertSame(failure, (failure.toAppError() as AppError.Unknown).cause)
        }
    }

    @Test
    fun `maps Core 2_0 failures with the released semantics`() {
        assertEquals(AppError.Unauthorized, BackendException.Unauthorized().toAppError())
        assertEquals(AppError.Forbidden, BackendException.Forbidden(null).toAppError())
        assertEquals(AppError.Forbidden, BackendException.Forbidden("permission_denied").toAppError())
        assertEquals(AppError.NotFound, BackendException.NotFound(null).toAppError())
        for (failure in listOf(BackendException.Http(500, null), BackendException.Contract(IllegalStateException()))) {
            assertSame(failure, (failure.toAppError() as AppError.Unknown).cause)
        }
    }

    @Test
    fun `only Backend's restricted error code turns a 403 into restricted`() {
        assertEquals(AppError.Restricted, BackendException.Forbidden("restricted").toAppError())
        assertEquals(AppError.Restricted, RuntimeException(BackendException.Forbidden("restricted")).toAppError())
        assertEquals(AppError.Forbidden, BackendException.Forbidden("forbidden").toAppError())
    }

    @Test
    fun `preserves unexpected failure as unknown cause`() {
        val cause = IllegalStateException("Unexpected")
        val error = cause.toAppError()

        assertSame(cause, (error as AppError.Unknown).cause)
    }
}
