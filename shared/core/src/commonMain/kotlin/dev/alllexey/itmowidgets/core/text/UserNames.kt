package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.shared.core.CoreStrings

/** Backend sends an empty name until the owner's identity is published; never show it raw. */
fun userDisplayName(name: String, isu: Int): UiText =
    name.trim().takeIf(String::isNotEmpty)?.let(UiText::Dynamic)
        ?: UiText.Res(CoreStrings.user_name_placeholder, listOf(isu))

/**
 * «Фамилия Имя Отчество» as «Фамилия И. О.»; a single word stays as it is. The initials are joined by no-break
 * spaces so a line never ends between them.
 */
fun shortPersonName(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (parts.size < 2) return name.trim()
    return parts.first() + " " + parts.drop(1).joinToString("\u00A0") { "${it.first().uppercaseChar()}." }
}

/**
 * «Фамилия Имя» from ITMO.ID's `family_name` and `given_name`; null unless both are there. ITMO.ID's `name` claim
 * lacks one of the three parts and has no fixed order, so it is only the fallback.
 */
fun personName(familyName: String?, givenName: String?): String? {
    val family = familyName?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val given = givenName?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return "$family $given"
}

/**
 * The letters of an avatar without a photo: the first letters of the surname and the given name. ITMO.ID's
 * [familyName] and [givenName] when both are there; otherwise words 1 and 2 of [name], which the app and Backend
 * keep as «Фамилия Имя [Отчество]» (My ITMO teachers, accounts without the parts). One letter for one word, `?`
 * for none.
 */
fun personInitials(name: String, familyName: String?, givenName: String?): String {
    val family = familyName?.trim().orEmpty()
    val given = givenName?.trim().orEmpty()
    val parts = if (family.isNotEmpty() && given.isNotEmpty()) {
        listOf(family, given)
    } else {
        name.trim().split(Regex("\\s+")).filter(String::isNotEmpty).take(2)
    }
    if (parts.isEmpty()) return "?"
    return parts.joinToString("") { it.take(1) }.uppercase()
}

/**
 * The name to call a person by, as in `Спорт: Иван`: ITMO.ID's [givenName] when it is there; otherwise word 2 of
 * [name] («Фамилия Имя [Отчество]»), or its only word. Empty for an empty [name].
 */
fun personGivenName(name: String, givenName: String?): String {
    givenName?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val parts = name.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return parts.getOrNull(1) ?: parts.firstOrNull().orEmpty()
}

/** [personInitials] of a name without ITMO.ID's parts. */
fun personInitials(name: String): String = personInitials(name, null, null)
