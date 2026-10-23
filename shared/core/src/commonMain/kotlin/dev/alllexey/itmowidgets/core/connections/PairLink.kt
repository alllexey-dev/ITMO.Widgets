package dev.alllexey.itmowidgets.core.connections

import dev.alllexey.itmowidgets.core.navigation.AppLinks
import dev.alllexey.itmowidgets.core.url.StrictUri
import kotlin.io.encoding.Base64

/**
 * A client's pairing link `https://<host>/pair#1.<CODE>.<publicKey>.<secret>` (the alpha's `v1.` is read too), from the
 * in-app scanner, a pasted link or an App Link: the Backend code, the client's X25519 public key (32 bytes) and its
 * pairing secret (16 bytes).
 * Key and secret exist only in the fragment, which browsers never send to a server, so this link is never logged or
 * sent to Backend: [toString] is redacted.
 */
class PairLink private constructor(
    /** [AppLinks.PROD_HOST] or [AppLinks.DEV_HOST]. */
    val host: String,
    val code: String,
    val publicKey: ByteArray,
    val secret: ByteArray,
) {
    /** The canonical link, for a route that hands it to the pairing sheet. */
    fun toUrl(): String = "https://$host$PATH#$VERSION.$code.${BASE64.encode(publicKey)}.${BASE64.encode(secret)}"

    override fun toString(): String = "PairLink(redacted)"

    companion object {
        const val PATH = "/pair"
        private const val VERSION = "1"
        private const val PUBLIC_KEY_BYTES = 32
        private const val SECRET_BYTES = 16
        private val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        private val HOSTS = setOf(AppLinks.PROD_HOST, AppLinks.DEV_HOST)

        // The code alphabet of web login (no 0 O 1 I L); 43 and 22 base64url characters carry 32 and 16 bytes.
        private val FRAGMENT =
            Regex("""v?1\.([ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8})\.([A-Za-z0-9_-]{43})\.([A-Za-z0-9_-]{22})""")

        /**
         * Null unless [text] is exactly such a link: `https`, one of the app's two hosts, no user info or port, path
         * exactly [PATH], no query, and a `1` (or `v1`) fragment whose key and secret decode to their lengths.
         */
        fun parse(text: String): PairLink? {
            val uri = StrictUri.parse(text.trim()) ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            val host = uri.host?.lowercase()?.takeIf { it in HOSTS } ?: return null
            if (uri.rawUserInfo != null || uri.port != null || uri.rawPath != PATH || uri.rawQuery != null) return null
            val parts = FRAGMENT.matchEntire(uri.rawFragment ?: return null)?.groupValues ?: return null
            val publicKey = decode(parts[2])?.takeIf { it.size == PUBLIC_KEY_BYTES } ?: return null
            val secret = decode(parts[3])?.takeIf { it.size == SECRET_BYTES } ?: return null
            return PairLink(host, parts[1], publicKey, secret)
        }

        /** Non-zero trailing bits fail too, so one key has exactly one spelling. */
        private fun decode(text: String): ByteArray? = try {
            BASE64.decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
