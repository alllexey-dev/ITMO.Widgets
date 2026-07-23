package dev.alllexey.itmowidgets.core.network

import api.myitmo.utils.ApiException
import api.myitmo.utils.TokenRefreshException
import dev.alllexey.itmowidgets.core.result.AppError
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

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

    private fun httpException(statusCode: Int): HttpException {
        return HttpException(
            Response.error<Unit>(
                statusCode,
                "".toResponseBody()
            )
        )
    }
}
