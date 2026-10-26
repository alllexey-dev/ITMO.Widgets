package dev.alllexey.itmowidgets.feature.messages.data

/**
 * Synthetic answers in the shape ISU sent on 2026-10-26 (field names and types only; every value is invented). The
 * real list item also carries `msg_attach_id`, `sender_id`, `date_month`, `is_fav`, `is_archive`, `is_shown`, `status`,
 * `feedback` and `file_list`, which the app does not read.
 */
internal object IsuFixtures {

    const val ISU = 999001

    /** A page of three: unread with an attachment, read with a link, and one without a `msgid` that is skipped. */
    val PAGE = """
        {"unrd_cntr": 4, "incoming": [
          {"msgid": 70001, "msg_attach_id": 1, "sender_id": 999101, "sender_name": "Белова Ольга Сергеевна",
           "subject": "Перенос лекции &amp; практики", "date": "21.10.2026", "sender_date": "21 окт. 15:51",
           "date_month": "21 окт.", "is_read": "N", "is_fav": 0, "is_archive": "N", "is_shown": "visible",
           "status": "delete", "feedback": null, "file_list": "plan.pdf:",
           "text": "<p>Лекция <strong>переносится</strong> на пятницу.</p><p><span style=\"color: red\">Аудитория 1404</span></p>",
           "files": [{"file_id": 501, "hash_id": "0123456789abcdef0123456789abcdef", "name": "plan.pdf", "type": "application/pdf"},
                     {"file_id": 502, "hash_id": "../../etc", "name": "bad", "type": "text/html"}]},
          {"msgid": "70000", "sender_name": "Деканат", "subject": "Чат курса", "date": "03.09.2026",
           "sender_date": "3 сен. 09:05", "is_read": "Y",
           "text": "<a href=\"https://t.me/example\" target=\"_blank\">чат</a> и https://example.org/labs.", "files": []},
          {"sender_name": "Без номера", "subject": "skip", "text": "", "files": []}
        ], "outgoing": []}
    """.trimIndent()

    /** The page after the last one. */
    const val EMPTY = """{"unrd_cntr": 0, "incoming": [], "outgoing": []}"""

    /** What a lost APEX session answers to the AJAX call. */
    const val LOST = "<html><body>Your session has ended</body></html>"

    /** The inbox shell page with the signed-in account's number. */
    fun inboxPage(isu: Int) =
        """<html><body><input type="hidden" name="p_t01" value="$isu" id="USER_ID"><div id="app"></div></body></html>"""

    const val KEYCLOAK_FORM = """<html><body><form id="kc-form-login" action="https://id.itmo.ru/x"></form></body></html>"""
}
