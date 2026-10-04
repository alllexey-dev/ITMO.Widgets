package dev.alllexey.itmowidgets.core.result

import api.myitmo.utils.ApiException
import api.myitmo.utils.TokenRefreshException
import dev.alllexey.itmowidgets.core.network.appResultOf
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

class AppResultsTest {

    @Test
    fun `wraps the value of a block that returns`() {
        assertEquals(AppResult.Success(42), appResultOf { 42 })
    }

    @Test
    fun `rethrows cancellation instead of mapping it`() {
        val cancellation = CancellationException("Left the screen")

        val thrown = assertThrows(CancellationException::class.java) {
            appResultOf<Unit>({ AppError.Network }) { throw cancellation }
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun `maps other failures through the given mapper`() {
        val failure = IllegalStateException("Broken")
        var mapped: Exception? = null

        val result = appResultOf<Unit>({ mapped = it; AppError.Forbidden }) { throw failure }

        assertEquals(AppResult.Failure(AppError.Forbidden), result)
        assertSame(failure, mapped)
    }

    @Test
    fun `an IOException deep in the chain is a network error`() {
        val deep = RuntimeException(ApiException("Request failed", RuntimeException(UnknownHostException())))

        assertEquals(AppResult.Failure(AppError.Network), failureOf(deep))
    }

    @Test
    fun `an IOException inside a token refresh is a network error, not an ended session`() {
        val refresh = TokenRefreshException("Refresh failed", RuntimeException(IOException()))

        assertEquals(AppResult.Failure(AppError.Network), failureOf(refresh))
    }

    @Test
    fun `401 is unauthorized`() {
        assertEquals(AppResult.Failure(AppError.Unauthorized), failureOf(httpException(401)))
    }

    @Test
    fun `403 with the restricted code is restricted`() {
        val restricted = httpException(403, """{"error":{"code":"restricted"}}""")

        assertEquals(AppResult.Failure(AppError.Restricted), failureOf(restricted))
    }

    @Test
    fun `other 403 is forbidden`() {
        assertEquals(AppResult.Failure(AppError.Forbidden), failureOf(httpException(403, """{"error":{"code":"permission_denied"}}""")))
        assertEquals(AppResult.Failure(AppError.Forbidden), failureOf(httpException(403)))
    }

    @Test
    fun `404 is not found`() {
        assertEquals(AppResult.Failure(AppError.NotFound), failureOf(httpException(404)))
    }

    private fun failureOf(failure: Exception): AppResult<Unit> = appResultOf { throw failure }

    private fun httpException(statusCode: Int, body: String = ""): HttpException =
        HttpException(Response.error<Unit>(statusCode, body.toResponseBody()))
}
