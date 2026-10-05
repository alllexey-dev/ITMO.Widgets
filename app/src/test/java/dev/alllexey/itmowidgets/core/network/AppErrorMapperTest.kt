package dev.alllexey.itmowidgets.core.network

import api.myitmo.utils.ApiException
import api.myitmo.utils.TokenRefreshException
import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.core.result.AppError
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppErrorMapperTest {

    @Test
    fun `maps network failures`() {
        assertEquals(AppError.Network, IOException().toAppError())
        assertEquals(
            AppError.Network,
            ApiException("Network failure", IOException()).toAppError()
        )
    }

    @Test
    fun `a network failure anywhere in the cause chain is a network error, not an ended session`() {
        assertEquals(
            AppError.Network,
            TokenRefreshException("Refresh failed", RuntimeException(UnknownHostException())).toAppError()
        )
        assertEquals(AppError.Network, IOException(TokenRefreshException("Expired")).toAppError())
        assertEquals(AppError.Network, ApiException("Network error", SocketTimeoutException()).toAppError())
        assertEquals(AppError.Unauthorized, TokenRefreshException("Expired").toAppError())
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
    fun `maps authorization and access status codes`() {
        assertEquals(AppError.Unauthorized, httpException(401).toAppError())
        assertEquals(AppError.Forbidden, httpException(403).toAppError())
        assertEquals(AppError.NotFound, ApiException(404, "Missing").toAppError())
        assertEquals(
            AppError.Unauthorized,
            TokenRefreshException("Expired").toAppError()
        )
    }

    @Test
    fun `preserves unexpected failure as unknown cause`() {
        val cause = IllegalStateException("Unexpected")
        val error = cause.toAppError()

        assertSame(cause, (error as AppError.Unknown).cause)
    }

    @Test
    fun `restricted code is preserved even when an error body is parsed twice`() {
        val error = HttpException(Response.error<Unit>(403, """{"error":{"code":"restricted"}}""".toResponseBody()))
        assertEquals(AppError.Restricted, error.toAppError())
        assertEquals(AppError.Restricted, error.toAppError())
    }

    @Test
    fun `only Backend's restricted error code turns a 403 into restricted`() {
        val restricted = listOf(
            """{"success":false,"data":null,"error":{"message":"Synthetic","code":"restricted"}}""",
            """{"error":{"code":"restricted"},"extra":[1,2]}"""
        )
        val forbidden = listOf(
            """{"success":false,"data":null,"error":{"message":"Synthetic","code":"forbidden"}}""",
            """{"success":false,"error":{"message":"Synthetic","code":null}}""",
            """{"success":false,"error":null}""",
            """{"error":"restricted"}""",
            """{"error":{"code":{"value":"restricted"}}}""",
            "<html>restricted</html>", "null", "[]", ""
        )
        for (body in restricted) {
            assertEquals(body, AppError.Restricted, HttpException(Response.error<Unit>(403, body.toResponseBody())).toAppError())
        }
        for (body in forbidden) {
            assertEquals(body, AppError.Forbidden, HttpException(Response.error<Unit>(403, body.toResponseBody())).toAppError())
        }
    }

    private fun httpException(statusCode: Int): HttpException {
        return HttpException(
            Response.error<Unit>(
                statusCode,
                "".toResponseBody()
            )
        )
    }
}
