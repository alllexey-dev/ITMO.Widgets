package dev.alllexey.itmowidgets.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MockHttpTest {
    @Test
    fun respondJsonSetsBodyStatusAndContentType() = runTest {
        val client = HttpClient(MockEngine { respondJson("""{"ok":false}""", HttpStatusCode.Conflict) })

        val response = client.get("https://example.test/api")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("""{"ok":false}""", response.bodyAsText())
    }

    @Test
    fun bodyTextReadsTheSentBody() = runTest {
        val engine = MockEngine { respondJson("{}") }
        val client = HttpClient(engine)

        client.post("https://example.test/api") { setBody("name=Иванов") }
        client.get("https://example.test/api")

        assertEquals(listOf("name=Иванов", ""), engine.requestHistory.map { it.bodyText() })
    }
}
