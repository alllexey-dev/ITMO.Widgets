package dev.alllexey.itmowidgets.core.url

import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [UrlEncoding] writes and reads exactly what `URLEncoder` and `URLDecoder` did in UTF-8. */
class UrlEncodingTest {

    private val samples = listOf(
        "", "AbCd-12_x", "a b+c", "Кронверкский пр., 49", "Some Street 5, Санкт-Петербург", "a.b*c~d!e'f(g)h",
        ":/?#[]@!$&'()*+,;=%", "\t\n\u0000\u007F\u0080\u00A0\u00FF", "\uD83D\uDE00 emoji", "lone \uD800 high",
        "lone \uDC00 low", "\uDC00\uD800 reversed", "\uD800", "\uFFFF\uFFFE", "\u0800\u07FF\u10000",
    ) + noise()

    @Test fun `form encoding matches URLEncoder`() {
        for (value in samples) assertEquals(value, URLEncoder.encode(value, "UTF-8"), UrlEncoding.formEncode(value))
    }

    @Test fun `percent encoding matches URLEncoder with spaces as %20`() {
        for (value in samples) {
            assertEquals(value, URLEncoder.encode(value, "UTF-8").replace("+", "%20"), UrlEncoding.percentEncode(value))
        }
    }

    @Test fun `form decoding matches URLDecoder`() {
        val encoded = listOf("ABCD%2D2345", "a+b%20c", "%D0%B9%D0%B9", "%C3%28", "%FF%FE%41", "%f0%9f%98%80", "plain",
            "%E2%82", "+%2B+") + samples.map { URLEncoder.encode(it, "UTF-8") }
        for (value in encoded) assertEquals(value, URLDecoder.decode(value, "UTF-8"), UrlEncoding.formDecode(value))
    }

    @Test fun `a malformed escape decodes to null where URLDecoder throws`() {
        for (value in listOf("%", "%4", "abc%", "%zz", "%4g", "a%2")) {
            assertEquals(value, null, runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull())
            assertNull(value, UrlEncoding.formDecode(value))
        }
    }

    private fun noise(): List<String> {
        val random = Random(11)
        return List(5_000) {
            buildString { repeat(random.nextInt(0, 10)) { append(Char(random.nextInt(0, 0x10000))) } }
        }
    }
}
