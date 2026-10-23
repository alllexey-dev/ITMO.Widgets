package dev.alllexey.itmowidgets.core.connections

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PairLinkTest {

    @Test
    fun readsTheCodeKeyAndSecretOnBothHosts() {
        listOf("widgets.alllexey.dev", "dev.widgets.alllexey.dev").forEach { host ->
            val link = assertNotNull(PairLink.parse("https://$host/pair#$FRAGMENT"), host)

            assertEquals(host, link.host)
            assertEquals("ABCD2345", link.code)
            assertContentEquals(PUBLIC_KEY, link.publicKey)
            assertContentEquals(SECRET, link.secret)
            assertEquals("https://$host/pair#$FRAGMENT", link.toUrl())
        }
    }

    @Test
    fun theAlphaV1FragmentIsReadAndWrittenBackAsVersion1() {
        val link = assertNotNull(PairLink.parse("https://widgets.alllexey.dev/pair#v1.ABCD2345.$KEY.$SECRET_TEXT"))

        assertEquals("ABCD2345", link.code)
        assertContentEquals(PUBLIC_KEY, link.publicKey)
        assertContentEquals(SECRET, link.secret)
        assertEquals("https://widgets.alllexey.dev/pair#$FRAGMENT", link.toUrl())
    }

    @Test
    fun aPastedLinkMayCarrySpacesAndAnUppercaseSchemeOrHost() {
        val link = assertNotNull(PairLink.parse("  HTTPS://Widgets.Alllexey.Dev/pair#$FRAGMENT\n"))

        assertEquals("https://widgets.alllexey.dev/pair#$FRAGMENT", link.toUrl())
    }

    @Test
    fun refusesEverythingElse() {
        listOf(
            "http://widgets.alllexey.dev/pair#$FRAGMENT",
            "https://example.com/pair#$FRAGMENT",
            "https://widgets.alllexey.dev.example.com/pair#$FRAGMENT",
            "https://user@widgets.alllexey.dev/pair#$FRAGMENT",
            "https://widgets.alllexey.dev:443/pair#$FRAGMENT",
            "https://widgets.alllexey.dev/pair/#$FRAGMENT",
            "https://widgets.alllexey.dev/Pair#$FRAGMENT",
            "https://widgets.alllexey.dev/app/pair#$FRAGMENT",
            // The key never travels in a query, which a server would see.
            "https://widgets.alllexey.dev/pair?$FRAGMENT",
            "https://widgets.alllexey.dev/pair?x=1#$FRAGMENT",
            "https://widgets.alllexey.dev/pair",
            "https://widgets.alllexey.dev/pair#",
            "https://widgets.alllexey.dev/pair#v2.ABCD2345.$KEY.$SECRET_TEXT",
            "https://widgets.alllexey.dev/pair#2.ABCD2345.$KEY.$SECRET_TEXT",
            "https://widgets.alllexey.dev/pair#V1.ABCD2345.$KEY.$SECRET_TEXT",
            "https://widgets.alllexey.dev/pair#v1.abcd2345.$KEY.$SECRET_TEXT",
            "https://widgets.alllexey.dev/pair#v1.ABCD2340.$KEY.$SECRET_TEXT",
            "https://widgets.alllexey.dev/pair#v1.ABCD234.$KEY.$SECRET_TEXT",
            "https://widgets.alllexey.dev/pair#v1.ABCD2345.${KEY.dropLast(1)}.$SECRET_TEXT",
            "https://widgets.alllexey.dev/pair#v1.ABCD2345.$KEY=.$SECRET_TEXT",
            "https://widgets.alllexey.dev/pair#v1.ABCD2345.$KEY.${SECRET_TEXT}A",
            // Base64 with `+` and `/` is not base64url; a set trailing bit is a second spelling of the same key.
            "https://widgets.alllexey.dev/pair#v1.ABCD2345.${KEY.replaceFirst('Q', '+')}.$SECRET_TEXT",
            "https://widgets.alllexey.dev/pair#v1.ABCD2345.${KEY.dropLast(1)}b.$SECRET_TEXT",
            "https://widgets.alllexey.dev/pair#v1.ABCD2345.$KEY.$SECRET_TEXT.extra",
            "https://widgets.alllexey.dev/pair#v1.ABCD2345.$KEY.$SECRET_TEXT#again",
            "widgets.alllexey.dev/pair#$FRAGMENT",
            "ABCD2345",
            "",
        ).forEach { text -> assertNull(PairLink.parse(text), text) }
    }

    @Test
    fun neverPrintsTheKeyOrSecret() {
        val link = assertNotNull(PairLink.parse("https://widgets.alllexey.dev/pair#$FRAGMENT"))

        assertEquals("PairLink(redacted)", link.toString())
        assertFalse(KEY in "$link" || SECRET_TEXT in "$link")
    }

    private companion object {
        // RFC 9180 A.2's recipient public key and the bytes 0..15.
        const val KEY = "QxDul9iMwfCIpVdsd6sM9cOseX89lROcbIS1QpxZZio"
        const val SECRET_TEXT = "AAECAwQFBgcICQoLDA0ODw"
        const val FRAGMENT = "1.ABCD2345.$KEY.$SECRET_TEXT"
        val PUBLIC_KEY = "4310ee97d88cc1f088a5576c77ab0cf5c3ac797f3d95139c6c84b5429c59662a".hexToByteArray()
        val SECRET = ByteArray(16) { it.toByte() }
    }
}
