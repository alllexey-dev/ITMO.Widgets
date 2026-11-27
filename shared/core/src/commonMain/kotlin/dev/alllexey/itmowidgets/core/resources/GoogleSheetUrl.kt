package dev.alllexey.itmowidgets.core.resources

import dev.alllexey.itmowidgets.core.url.StrictUri

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
        private const val DRIVE = "drive.google.com"
        private val PATH = Regex("""^/spreadsheets(?:/u/\d+)?/d/([A-Za-z0-9_-]+)(?:/.*)?$""")
        private val DRIVE_PATH = Regex("""^/file(?:/u/\d+)?/d/([A-Za-z0-9_-]+)(?:/.*)?$""")
        private val ID = Regex("""[A-Za-z0-9_-]{20,}""")

        /**
         * A sheet address ([parseSheets]) or a Google Drive file (`https://drive.google.com/file/d/<id>`,
         * `.../open?id=<id>`): a spreadsheet uploaded to Drive (`.xlsx`) is read by the same id at the Sheets
         * address. Whether a Drive file is a spreadsheet at all shows only when it is downloaded.
         */
        fun parse(url: String): GoogleSheetUrl? = parseSheets(url) ?: parseDriveFile(url)

        /** Null for anything but an `https://docs.google.com/spreadsheets/d/<id>` address. */
        fun parseSheets(url: String): GoogleSheetUrl? {
            val uri = StrictUri.parse(url.trim()) ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (!uri.host.equals(HOST, ignoreCase = true)) return null
            val id = PATH.matchEntire(uri.rawPath.orEmpty())?.groupValues?.get(1) ?: return null
            if (id == "e" || !ID.matches(id)) return null
            val gid = parameter(uri.rawFragment, "gid") ?: parameter(uri.rawQuery, "gid")
            return GoogleSheetUrl(id, gid?.toLongOrNull())
        }

        private fun parseDriveFile(url: String): GoogleSheetUrl? {
            val uri = StrictUri.parse(url.trim()) ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (!uri.host.equals(DRIVE, ignoreCase = true)) return null
            val path = uri.rawPath.orEmpty()
            val id = DRIVE_PATH.matchEntire(path)?.groupValues?.get(1)
                ?: parameter(uri.rawQuery, "id").takeIf { path == "/open" }
                ?: return null
            return GoogleSheetUrl(id, null).takeIf { ID.matches(id) }
        }

        private fun parameter(text: String?, name: String): String? = text?.split('&')
            ?.firstOrNull { it.substringBefore('=') == name && '=' in it }
            ?.substringAfter('=')
    }
}

/**
 * Whether the link offers «Мои баллы»: a sheet address always, a Google Drive file only under «Баллы», where it is
 * a spreadsheet almost surely (elsewhere it may as well be a PDF).
 */
val SubjectLink.offersSheetScores: Boolean
    get() = GoogleSheetUrl.parseSheets(url) != null ||
        (category == LinkCategory.SCORES && GoogleSheetUrl.parse(url) != null)
