package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/** What a row key is: the ISU number or the normalised name in the row. */
enum class KeyKind { ISU, NAME }

/**
 * Who the own row belongs to: the ISU, the display name and the structured name parts from ITMO.ID. Its `name` has
 * two of the three parts in an unknown order, so the parts, when present, are what finds a full name in a sheet.
 */
data class OwnIdentity(
    val isu: Int?,
    val name: String?,
    val familyName: String? = null,
    val givenName: String? = null,
    val middleName: String? = null,
) {
    override fun toString(): String = "OwnIdentity(isu=${isu != null}, name=${name != null}, parts=${familyName != null})"
}

/** How a cell names a person and whether it names the viewer. Pure. */
object SheetIdentity {
    private val ISU = Regex("""\d{4,8}""")
    private val NAME_WORD = Regex("""[\p{L}.\-]+""")
    private val HAS_LETTER = Regex("""\p{L}""")
    private val NOT_NAMES = setOf(
        "фио", "студент", "студента", "студенты", "фамилия", "имя", "отчество", "name", "student", "students",
        // A summary row under the students: «Итого по группе», «Средний балл».
        "итого", "всего", "среднее", "средний", "total", "average",
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
        return listOf(surnameFirst, surnameLast).flatMapTo(linkedSetOf(words.joinToString(" "))) { (surname, given, patronymic) ->
            forms(surname, given, patronymic)
        }
    }

    /**
     * Every form of [identity]'s name: the forms of the structured parts first («Фамилия Имя Отчество», a patronymic
     * of several words such as «Азад оглы» included, and «Имя Фамилия» of foreign students), then those of `name`.
     */
    fun nameVariants(identity: OwnIdentity): Set<String> {
        val family = identity.familyName?.let(SheetText::normalize)?.takeIf { it.isNotEmpty() }
        val given = identity.givenName?.let(SheetText::normalize)?.takeIf { it.isNotEmpty() }
        val variants = linkedSetOf<String>()
        if (family != null && given != null) {
            variants += forms(family, given, identity.middleName?.let(SheetText::normalize)?.takeIf { it.isNotEmpty() })
            variants += "$given $family"
        }
        identity.name?.let { variants += nameVariants(it) }
        return variants
    }

    /** Which own key [cell] holds, if any: the ISU first, then a form of the name. */
    fun matchOf(cell: String, identity: OwnIdentity): KeyKind? = when {
        identity.isu != null && isuMatches(cell, identity.isu.toString()) -> KeyKind.ISU
        SheetText.normalize(cell) in nameVariants(identity) -> KeyKind.NAME
        else -> null
    }

    private fun forms(surname: String, given: String, patronymic: String?): List<String> {
        val initials = "${given.first()}." + (patronymic?.let { "${it.first()}." } ?: "")
        return listOfNotNull(
            patronymic?.let { "$surname $given $it" },
            "$surname $given",
            "$surname $initials",
            "$surname ${given.first()}.",
        )
    }

    /**
     * Whether [cell] looks like a student's key: an ISU is 4–8 digits, a name is 2–8 words of letters, dots and
     * hyphens (foreign students' names run long), not only initials and not a column title such as «ФИО студента».
     */
    fun personKind(cell: String): KeyKind? {
        val compact = cell.filterNot(Char::isWhitespace)
        if (ISU.matches(compact)) return KeyKind.ISU
        val words = SheetText.normalize(cell).replace(".", ". ").trim().split(' ').filter { it.isNotEmpty() }
        if (words.size !in 2..8) return null
        if (words.any { !NAME_WORD.matches(it) || !HAS_LETTER.containsMatchIn(it) }) return null
        if (words.any { it.trimEnd('.') in NOT_NAMES }) return null
        // Only initials, such as the column title «Ф.И.О.», are no name.
        if (words.none { it.count(Char::isLetter) > 1 }) return null
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
