package dev.alllexey.itmowidgets.core.url

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [StrictUri] reads every corpus input into the same parts as `java.net.URI`, and fails where it throws. */
class StrictUriParityTest {

    @Test fun `hand-written traps parse as on the JVM`() = assertParity(UrlParityCorpus.policyUrls)

    @Test fun `combined links parse as on the JVM`() = assertParity(UrlParityCorpus.combinations(20_000))

    @Test fun `random delimiter noise parses as on the JVM`() = assertParity(UrlParityCorpus.noise(50_000))

    @Test fun `a server authority is split into its parts`() {
        val uri = checkNotNull(StrictUri.parse("HTTPS://u:p@My.Itmo.Ru:443/a%20b/%D0%B9?x=1&x=2#f"))

        assertEquals("HTTPS", uri.scheme)
        assertEquals("u:p", uri.rawUserInfo)
        assertEquals("My.Itmo.Ru", uri.host)
        assertEquals(443, uri.port)
        assertEquals("/a%20b/%D0%B9", uri.rawPath)
        assertEquals("/a b/й", uri.path)
        assertEquals("x=1&x=2", uri.rawQuery)
        assertEquals("f", uri.rawFragment)
    }

    @Test fun `a Unicode or underscored host is a registry name without a host`() {
        for (url in listOf("https://пример.рф/", "https://my_itmo.ru/", "https://my.itmo.ru:99999999999/")) {
            val uri = checkNotNull(StrictUri.parse(url))
            assertNull(url, uri.host)
            assertNull(url, uri.rawUserInfo)
            assertNull(url, uri.port)
        }
    }

    @Test fun `illegal characters fail the whole reference`() {
        for (url in listOf("https://my.itmo.ru\\@evil.invalid", "https://my.itmo.ru/a b", "https://my.itmo.ru/%zz",
            "https://my.itmo.ru/#a#b", "https://", "https:", "1https://my.itmo.ru/")) {
            assertNull(url, StrictUri.parse(url))
        }
    }

    private fun assertParity(inputs: List<String>) {
        for (input in inputs) {
            assertEquals(input, jvm(input), common(input))
        }
    }

    private fun jvm(input: String): Parts? {
        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        return Parts(uri.scheme, uri.rawUserInfo, uri.host, uri.port.takeIf { it != -1 }, uri.rawPath, uri.path,
            uri.rawQuery, uri.rawFragment)
    }

    private fun common(input: String): Parts? {
        val uri = StrictUri.parse(input) ?: return null
        return Parts(uri.scheme, uri.rawUserInfo, uri.host, uri.port, uri.rawPath, uri.path, uri.rawQuery,
            uri.rawFragment)
    }

    private data class Parts(
        val scheme: String?,
        val rawUserInfo: String?,
        val host: String?,
        val port: Int?,
        val rawPath: String?,
        val path: String?,
        val rawQuery: String?,
        val rawFragment: String?,
    )
}
