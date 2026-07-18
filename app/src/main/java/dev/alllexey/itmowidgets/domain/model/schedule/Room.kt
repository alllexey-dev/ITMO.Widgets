package dev.alllexey.itmowidgets.domain.model.schedule

import java.util.regex.Pattern

@JvmInline
value class Room(val raw: String) {

    fun shorten(): String {
        val roomLow = raw.lowercase()
        if (roomLow.contains("актовый")) return "Акт. зал"
        val m1 = Pattern.compile("[0-9]{4}/[0-9]").matcher(roomLow)
        if (m1.find()) {
            return m1.group()
        }

        val m2 = Pattern.compile("[0-9]{4}").matcher(roomLow)
        if (m2.find()) {
            return m2.group()
        }
        return raw
    }
}
