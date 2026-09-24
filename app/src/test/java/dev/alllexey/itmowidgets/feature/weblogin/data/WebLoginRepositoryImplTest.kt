package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import dev.alllexey.itmowidgets.core.model.WebLoginPreview as WirePreview

class WebLoginRepositoryImplTest {
    private val challenge = UUID.fromString("00000000-0000-0000-0000-000000000042")
    private val createdAt = OffsetDateTime.parse("2026-09-24T09:04:30Z")
    private val expiresAt = OffsetDateTime.parse("2026-09-24T09:06:30Z")

    @Test fun `preview and approval go to Backend with the code and the challenge`() = runTest {
        val api = FakeApi().apply { preview = ApiResponse.success(WirePreview(challenge, " Chrome ", createdAt, expiresAt)) }
        val repository = WebLoginRepositoryImpl(services(enabled = true), api.instance)

        assertEquals(AppResult.Success(WebLoginPreview(challenge, "Chrome", createdAt, expiresAt)), repository.preview("ABCD2345"))
        assertEquals(AppResult.Success(Unit), repository.approve(challenge))
        assertEquals(listOf("webLoginPreview:ABCD2345", "approveWebLogin:$challenge"), api.calls)
    }

    @Test fun `a blank user agent reads as none`() = runTest {
        val api = FakeApi().apply { preview = ApiResponse.success(WirePreview(challenge, "  ", createdAt, expiresAt)) }

        val result = WebLoginRepositoryImpl(services(enabled = true), api.instance).preview("ABCD2345")

        assertEquals(null, (result as AppResult.Success).value.userAgent)
    }

    @Test fun `without the connection nothing reaches Backend`() = runTest {
        val api = FakeApi()
        val repository = WebLoginRepositoryImpl(services(enabled = false), api.instance)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.approve(challenge))
        assertTrue(api.calls.isEmpty())
    }

    @Test fun `an unknown used or expired code is not found`() = runTest {
        val notFound = HttpException(Response.error<Unit>(404, """{"success":false,"error":{"code":"not_found"}}""".toResponseBody()))
        val api = FakeApi().apply { failure = notFound }
        val repository = WebLoginRepositoryImpl(services(enabled = true), api.instance)

        assertEquals(AppResult.Failure(AppError.NotFound), repository.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.NotFound), repository.approve(challenge))
    }

    @Test fun `network failures and error bodies are typed`() = runTest {
        val api = FakeApi().apply { failure = IOException("offline") }
        val repository = WebLoginRepositoryImpl(services(enabled = true), api.instance)
        assertEquals(AppResult.Failure(AppError.Network), repository.preview("ABCD2345"))

        api.failure = null
        api.preview = ApiResponse(success = false, data = null, error = ApiResponse.ErrorDetails("gone", "not_found"))
        assertEquals(AppResult.Failure(AppError.NotFound), repository.preview("ABCD2345"))

        api.preview = ApiResponse(success = true, data = null, error = null)
        assertTrue((repository.preview("ABCD2345") as AppResult.Failure).error is AppError.Unknown)
    }

    private fun services(enabled: Boolean) = object : CustomServicesRepository {
        override fun observeEnabled(): Flow<Boolean> = flowOf(enabled)
        override suspend fun isEnabled(): Boolean = enabled
        override suspend fun setEnabled(enabled: Boolean) = error("not used")
    }

    private class FakeApi {
        var preview: ApiResponse<WirePreview?> = ApiResponse(success = true, data = null, error = null)
        var failure: Exception? = null
        val calls = mutableListOf<String>()

        val instance = Proxy.newProxyInstance(ItmoWidgetsApi::class.java.classLoader, arrayOf(ItmoWidgetsApi::class.java)) { _, method, raw ->
            val args = raw.orEmpty()
            calls += "${method.name}:${args.first()}"
            failure?.let { throw it }
            when (method.name) {
                "webLoginPreview" -> preview
                "approveWebLogin" -> ApiResponse(success = true, data = null, error = null)
                else -> error("Unexpected API method: ${method.name}")
            }
        } as ItmoWidgetsApi
    }
}
