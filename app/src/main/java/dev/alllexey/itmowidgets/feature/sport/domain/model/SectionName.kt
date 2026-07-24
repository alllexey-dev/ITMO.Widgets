package dev.alllexey.itmowidgets.feature.sport.domain.model

@JvmInline
value class SectionName(val raw: String) {

    fun shorten(): String {
        return shorteningMap[raw] ?: raw
    }

    companion object {
        fun deshorten(shortened: String): SectionName {
            return SectionName(deshorteningMap[shortened] ?: shortened)
        }

        private val shorteningMap = mapOf(
            "Спортивный туризм (северная ходьба)" to "Северная ходьба",
            "Фитнес (функциональная тренировка)" to "Фитнес (функциональный)",
            "Современные танцы (Клуб парных танцев \"Потанцуем\")" to "Современные танцы",
            "Спортивный туризм (северная ходьба - маршруты)" to "Северная ходьба (маршруты)",
            "Современные танцы (Студия Flame)" to "Современные танцы"
        )

        private val deshorteningMap = shorteningMap.map { it.value to it.key }.associate { it }
    }
}
