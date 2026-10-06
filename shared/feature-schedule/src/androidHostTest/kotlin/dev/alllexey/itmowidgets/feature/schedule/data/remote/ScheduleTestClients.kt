package dev.alllexey.itmowidgets.feature.schedule.data.remote

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.feature.schedule.data.Core2Harness
import dev.alllexey.itmowidgets.feature.schedule.data.InMemoryTokenStorage
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock

internal const val MY_ITMO_HOST = "my.itmo.ru"
internal const val PERSONAL_SCHEDULE_PATH = "/api/schedule/schedule/personal"

/**
 * A real MyItmoApi 2.x client over a MockEngine with a stored session valid for minutes: [answer] gives the status
 * and body of every my.itmo.ru request (it may block or throw), ITMO.ID answers a refresh with a new token pair and
 * any other host fails the test.
 */
internal fun scheduleMyItmoClient(answer: (HttpRequestData) -> Pair<Int, String>): MyItmoClient {
    val engine = MockEngine { request ->
        when (request.url.host) {
            Core2Harness.ITMO_ID_HOST -> respondJson(Core2Harness.TOKEN_RESPONSE)
            MY_ITMO_HOST -> answer(request).let { (status, body) -> respondJson(body, HttpStatusCode.fromValue(status)) }
            else -> throw AssertionError("Unexpected host ${request.url.host}")
        }
    }
    return MyItmoClientFactory.create(
        storage = InMemoryTokenStorage(Core2Harness.session()),
        engine = engine,
        clock = FakeClock(Core2Harness.NOW)
    )
}

/** A 2.x client whose session and engine fail the test: the demo session must not reach My ITMO. */
internal fun unreachableScheduleMyItmoClient(): MyItmoClient = MyItmoClientFactory.create(
    storage = object : TokenStorage {
        override suspend fun read(): TokenSet? = throw AssertionError("The demo session read the ITMO session")

        override suspend fun write(tokens: TokenSet?) = throw AssertionError("The demo session wrote the ITMO session")
    },
    engine = MockEngine { request -> throw AssertionError("The demo session asked ${request.url.host}${request.url.encodedPath}") },
    clock = Clock.System
)

/** The `date_start..date_end` of a personal schedule request. */
internal fun HttpRequestData.requestedRange(): String =
    "${url.parameters["date_start"]}..${url.parameters["date_end"]}"
