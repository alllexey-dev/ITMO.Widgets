package dev.alllexey.itmowidgets.core.resources

import java.net.URI

/**
 * The address of a Google Sheet: [spreadsheetId] and the tab [gid] the link points at, if it does. Used by the link
 * actions sheet (whether a link offers «Мои баллы») and by the recordbook (which sheet to download). Published
 * addresses (`/spreadsheets/d/e/…`) are not sheets in this sense: they have no export.
 */
data class GoogleSheetUrl(val spreadsheetId: String, val gid: Long?) {

    /** The tab [gid] of this sheet in the browser. */
    fun tabUrl(gid: Long): String = "https://docs.google.com/spreadsheets/d/$spreadsheetId/edit#gid=$gid"

    companion object {
        private const val HOST = "docs.google.com"
        private val PATH = Regex("""^/spreadsheets(?:/u/\d+)?/d/([A-Za-z0-9_-]+)(?:/.*)?$""")
        private val ID = Regex("""[A-Za-z0-9_-]{20,}""")

        /** Null for anything but an `https://docs.google.com/spreadsheets/d/<id>` address. */
        fun parse(url: String): GoogleSheetUrl? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (!uri.host.equals(HOST, ignoreCase = true)) return null
            val id = PATH.matchEntire(uri.rawPath.orEmpty())?.groupValues?.get(1) ?: return null
            if (id == "e" || !ID.matches(id)) return null
            val gid = parameter(uri.rawFragment, "gid") ?: parameter(uri.rawQuery, "gid")
            return GoogleSheetUrl(id, gid?.toLongOrNull())
        }

        private fun parameter(text: String?, name: String): String? = text?.split('&')
            ?.firstOrNull { it.substringBefore('=') == name && '=' in it }
            ?.substringAfter('=')
    }
}
