package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BarsApiContractTest {
    @Test fun `Retrofit paths query encoding and body match the observed BARS contract`() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val body = when {
                chain.request().url.encodedPath.endsWith("login") -> ""
                chain.request().url.encodedPath.endsWith("personal") -> "{\"name\":\"current_term\",\"value\":\"0\"}"
                chain.request().url.encodedPath.endsWith("student") -> "{\"students\":[],\"headers\":{}}"
                else -> "[]"
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://bars.itmo.ru/backend/rest/").client(client)
            .addConverterFactory(GsonConverterFactory.create(Gson())).build().create(BarsApi::class.java)
        api.disciplines("Bearer synthetic-credential")
        api.journal("Bearer synthetic-credential", 8, "flow", "a/b")
        api.selectPeriod("Bearer synthetic-credential", BarsSetting("current_term", "0"))
        api.login("synthetic&code", "https://bars.itmo.ru/rest/login")
        assertEquals("/backend/rest/journal/disciplines", requests[0].url.encodedPath)
        assertEquals("true", requests[0].url.queryParameter("withCheckpointPlansOnly"))
        assertEquals("/backend/rest/marks/8/flow/a%2Fb/student", requests[1].url.encodedPath)
        assertEquals("POST", requests[2].method)
        assertEquals("/backend/rest/config/personal", requests[2].url.encodedPath)
        val buffer = okio.Buffer(); requests[2].body!!.writeTo(buffer)
        assertEquals("{\"name\":\"current_term\",\"value\":\"0\"}", buffer.readUtf8())
        assertEquals("synthetic&code", requests[3].url.queryParameter("code"))
        assertNull(requests[3].header("Authorization"))
        assertTrue(requests.all { it.header("Cookie") == null })
    }
}
