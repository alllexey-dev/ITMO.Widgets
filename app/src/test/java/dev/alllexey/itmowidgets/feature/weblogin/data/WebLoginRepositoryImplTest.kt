package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.OffsetDateTime
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import dev.alllexey.itmowidgets.core.model.WebLoginPreview as WirePreview

class WebLoginRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val challengeId = Uuid.parse("00000000-0000-0000-0000-000000000042")
    private val challenge = challengeId.toJavaUuid()
    private val createdAt = OffsetDateTime.parse("2026-09-24T09:04:30Z")
    private val expiresAt = OffsetDateTime.parse("2026-09-24T09:06:30Z")

    @Test fun `preview and approval go to Backend with the code and the challenge`() = runTest {
        val api = FakeWebLoginApi().apply { preview = ApiResponse.success(WirePreview(challenge, " Chrome ", createdAt, expiresAt)) }
        val repository = WebLoginRepositoryImpl(services(enabled = true), api.instance, noDemo(), dispatchers = dispatchers)

        assertEquals(AppResult.Success(WebLoginPreview(challengeId, "Chrome", Instant.parse("2026-09-24T09:04:30Z"), Instant.parse("2026-09-24T09:06:30Z"))), repository.preview("ABCD2345"))
        assertEquals(AppResult.Success(Unit), repository.approve(challengeId))
        assertEquals(listOf("webLoginPreview:ABCD2345", "approveWebLogin:$challenge"), api.calls)
    }

    @Test fun `a blank user agent reads as none`() = runTest {
        val api = FakeWebLoginApi().apply { preview = ApiResponse.success(WirePreview(challenge, "  ", createdAt, expiresAt)) }

        val result = WebLoginRepositoryImpl(services(enabled = true), api.instance, noDemo(), dispatchers = dispatchers).preview("ABCD2345")

        assertEquals(null, (result as AppResult.Success).value.userAgent)
    }

    @Test fun `without the connection nothing reaches Backend`() = runTest {
        val api = FakeWebLoginApi()
        val repository = WebLoginRepositoryImpl(services(enabled = false), api.instance, noDemo(), dispatchers = dispatchers)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.approve(challengeId))
        assertTrue(api.calls.isEmpty())
    }

    @Test fun `an unknown used or expired code is not found`() = runTest {
        val notFound = HttpException(Response.error<Unit>(404, """{"success":false,"error":{"code":"not_found"}}""".toResponseBody()))
        val api = FakeWebLoginApi().apply { failure = notFound }
        val repository = WebLoginRepositoryImpl(services(enabled = true), api.instance, noDemo(), dispatchers = dispatchers)

        assertEquals(AppResult.Failure(AppError.NotFound), repository.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.NotFound), repository.approve(challengeId))
    }

    @Test fun `network failures and error bodies are typed`() = runTest {
        val api = FakeWebLoginApi().apply { failure = IOException("offline") }
        val repository = WebLoginRepositoryImpl(services(enabled = true), api.instance, noDemo(), dispatchers = dispatchers)
        assertEquals(AppResult.Failure(AppError.Network), repository.preview("ABCD2345"))

        api.failure = null
        api.preview = ApiResponse(success = false, data = null, error = ApiResponse.ErrorDetails("gone", "not_found"))
        assertEquals(AppResult.Failure(AppError.NotFound), repository.preview("ABCD2345"))

        api.preview = ApiResponse(success = true, data = null, error = null)
        assertTrue((repository.preview("ABCD2345") as AppResult.Failure).error is AppError.Unknown)
    }

    private fun services(enabled: Boolean) = FakeBackendGate(enabled)

    private class FakeWebLoginApi {
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
