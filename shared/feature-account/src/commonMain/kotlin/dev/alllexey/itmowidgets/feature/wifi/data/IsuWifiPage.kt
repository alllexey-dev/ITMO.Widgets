package dev.alllexey.itmowidgets.feature.wifi.data

import dev.alllexey.itmowidgets.core.network.IsuException
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiAccess
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials

/**
 * ISU's «Настройки Wi-Fi» page (`f?p=2156:6`), server-rendered APEX: the login is the text of `span#P6_USERNAME`, the
 * active password the text of `span.wifi-pass` inside `span#P6_PASS_CURRENT` (hidden there only by CSS), and the
 * hidden `input#USER_ID` names whose cabinet it is. Only reads; the page's button that issues a new password is
 * never touched.
 */
internal object IsuWifiPage {
    const val APP = 2156
    const val PAGE = "6"

    /**
     * @param owner the signed-in user's ISU; a page of another cabinet is refused.
     * @throws IsuException when the page no longer has the expected fields.
     */
    fun parse(html: String, owner: Int?): WifiAccess {
        val pageOwner = USER_ID.find(html)?.value?.let { VALUE.find(it)?.groupValues?.get(1) }
        if (owner != null && pageOwner != null && pageOwner != owner.toString()) {
            throw IsuException("ISU served another account's Wi-Fi page")
        }
        val login = USERNAME.find(html)?.groupValues?.get(1)?.let(::text)
            ?: throw IsuException("ISU Wi-Fi page has no login")
        val current = html.indexOf(CURRENT).takeIf { it >= 0 } ?: throw IsuException("ISU Wi-Fi page has no password field")
        // The field ends at the next label; the password span is inside it, or absent while there is no password.
        val field = html.substring(current, html.indexOf("<label", current).takeIf { it >= 0 } ?: html.length)
        val password = PASSWORD.find(field)?.groupValues?.get(1)?.let(::text).orEmpty()
        return if (login.isEmpty() || password.isEmpty()) {
            WifiAccess.NoPassword
        } else {
            WifiAccess.Ready(WifiCredentials(login, password))
        }
    }

    /** The text of an element: entities decoded, outer spaces trimmed. */
    private fun text(raw: String): String = ENTITY.replace(raw) { match ->
        val name = match.groupValues[1]
        when {
            name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
            else -> NAMED[name]
        } ?: match.value
    }.trim()

    private fun codePoint(code: Int): String? = code.takeIf { it in 1..0xFFFF }?.toChar()?.toString()

    private const val CURRENT = "id=\"P6_PASS_CURRENT\""
    private val USERNAME = Regex("""<span[^>]*\bid="P6_USERNAME"[^>]*>([^<]*)</span>""")
    private val PASSWORD = Regex("""<span[^>]*\bclass="[^"]*\bwifi-pass\b[^"]*"[^>]*>([^<]*)</span>""")
    private val USER_ID = Regex("""<input[^>]*\bid="USER_ID"[^>]*>""")
    private val VALUE = Regex("""\bvalue="([0-9]+)"""")
    private val ENTITY = Regex("""&(#x?[0-9a-fA-F]+|[a-z]+);""")
    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
}
