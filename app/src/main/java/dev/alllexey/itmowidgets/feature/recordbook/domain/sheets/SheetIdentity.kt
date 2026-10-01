package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/** What a row key is: the ISU number or the normalised name in the row. */
enum class KeyKind { ISU, NAME }

/** Who the own row belongs to: the ISU and the name from ITMO.ID. */
data class OwnIdentity(val isu: Int?, val name: String?) {
    override fun toString(): String = "OwnIdentity(isu=${isu != null}, name=${name != null})"
}

/** How a cell names a person and whether it names the viewer. Pure. */
object SheetIdentity {
    private val ISU = Regex("""\d{4,8}""")
    private val NAME_WORD = Regex("""[\p{L}.\-]+""")
    private val HAS_LETTER = Regex("""\p{L}""")
    private val NOT_NAMES = setOf(
        "фио", "студент", "студента", "студенты", "фамилия", "имя", "отчество", "name", "student", "students",
    )

    /**
     * The forms a sheet writes [name] in, normalised: «Фамилия Имя Отчество», «Фамилия Имя», «Фамилия И.О.»,
     * «Фамилия И.». The order of the words in ITMO.ID is not known, so the surname is tried first and last.
     */
    fun nameVariants(name: String): Set<String> {
        val words = SheetText.normalize(name).split(' ').filter { it.isNotEmpty() }
        if (words.size < 2) return emptySet()
        val surnameFirst = Triple(words.first(), words[1], words.getOrNull(2))
        val surnameLast = Triple(words.last(), words.first(), words.getOrNull(1)?.takeIf { words.size > 2 })
        return listOf(surnameFirst, surnameLast).flatMapTo(linkedSetOf()) { (surname, given, patronymic) ->
            val initials = "${given.first()}." + (patronymic?.let { "${it.first()}." } ?: "")
            listOfNotNull(
                patronymic?.let { "$surname $given $it" },
                "$surname $given",
                "$surname $initials",
                "$surname ${given.first()}.",
            )
        }
    }

    /** Which own key [cell] holds, if any: the ISU first, then a form of the name. */
    fun matchOf(cell: String, identity: OwnIdentity): KeyKind? = when {
        identity.isu != null && isuMatches(cell, identity.isu.toString()) -> KeyKind.ISU
        identity.name != null && SheetText.normalize(cell) in nameVariants(identity.name) -> KeyKind.NAME
        else -> null
    }

    /**
     * Whether [cell] looks like a student's key: an ISU is 4–8 digits, a name is 2–8 words of letters, dots and
     * hyphens (foreign students' names run long) and not a column title such as «ФИО студента».
     */
    fun personKind(cell: String): KeyKind? {
        val compact = cell.filterNot(Char::isWhitespace)
        if (ISU.matches(compact)) return KeyKind.ISU
        val words = SheetText.normalize(cell).replace(".", ". ").trim().split(' ').filter { it.isNotEmpty() }
        if (words.size !in 2..8) return null
        if (words.any { !NAME_WORD.matches(it) || !HAS_LETTER.containsMatchIn(it) }) return null
        if (words.any { it.trimEnd('.') in NOT_NAMES }) return null
        return KeyKind.NAME
    }

    /** Whether [cell] is a column title of the people, such as «ФИО» or «FULL NAME ↓». */
    fun isPeopleTitle(cell: String): Boolean =
        SheetText.tokens(cell).any { it in NOT_NAMES } || SheetText.normalize(cell).filter(Char::isLetter).startsWith("фио")

    /** Whether [cell] holds [key] of [kind]: digits without spaces for an ISU, the normalised text for a name. */
    fun holds(cell: String, key: String, kind: KeyKind): Boolean = when (kind) {
        KeyKind.ISU -> isuMatches(cell, key)
        KeyKind.NAME -> SheetText.normalize(cell) == key
    }

    /** The key a [kind] cell stands for. */
    fun keyOf(cell: String, kind: KeyKind): String = when (kind) {
        KeyKind.ISU -> cell.filterNot(Char::isWhitespace)
        KeyKind.NAME -> SheetText.normalize(cell)
    }

    private fun isuMatches(cell: String, isu: String): Boolean = cell.filterNot(Char::isWhitespace) == isu
}
