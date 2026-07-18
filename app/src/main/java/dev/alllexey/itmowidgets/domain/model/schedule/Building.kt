package dev.alllexey.itmowidgets.domain.model.schedule

@JvmInline
value class Building(val raw: String) {

    fun shorten(forceLength: Int? = null): String {
        val name = raw.lowercase()
        if (name.contains("кронв")) return "Кронва"
        if (name.contains("ломо")) return "Ломо"
        if (name.contains("гривц")) return "Грива"
        if (name.contains("бирж")) return "Биржа"
        if (name.contains("песоч")) return "Песочка"
        if (name.contains("чайк")) return "Чайка"
        if (name.contains("вязем")) return "Вязьма"

        return if (forceLength != null) raw.substring(0, forceLength) else raw
    }
}
