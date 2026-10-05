package dev.alllexey.itmowidgets.feature.qr.data.remote

/**
 * Synthetic answers of qr.itmo.su and ITMO.ID, shaped like MyItmoApi's `kmp/fixtures/qr/pass.json` and
 * `kmp/fixtures/itmoid/token-success.json` at the pin. Kept as constants, not JVM resources, so they move to
 * `commonTest` unchanged. No value here is a real pass or token.
 */
object QrRemoteFixtures {

    /** A pass with a leading zero, which must survive as text. */
    const val PASS_HEX = "001122aabb"

    const val PASS = """{"response":{"qr_hex":"$PASS_HEX"}}"""

    /** A successful answer whose payload has no `qr_hex`. */
    const val PASS_WITHOUT_HEX = """{"response":{}}"""

    /** A successful answer with no payload at all. */
    const val PASS_NULL = """{"response":null}"""

    const val ERROR_502 = "<html><body>Bad Gateway</body></html>"

    const val REFRESHED_ACCESS = "refreshed-access"

    const val TOKEN_SUCCESS = """{"access_token":"$REFRESHED_ACCESS","expires_in":300,"refresh_token":"refreshed-refresh",""" +
        """"refresh_expires_in":3600,"id_token":"header.payload.signature",""" +
        """"session_state":"00000000-0000-4000-8000-000000000001"}"""
}
