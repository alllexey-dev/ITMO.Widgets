package dev.alllexey.itmowidgets.feature.connections.domain

/** The `KEYCLOAK_IDENTITY` cookie of ITMO.ID's realm, as the isolated sign-in store holds it. */
object KeycloakIdentityCookie {
    const val NAME = "KEYCLOAK_IDENTITY"

    /** Where the cookie lives: id.itmo.ru, path `/auth/realms/itmo/`. */
    const val URL = "https://id.itmo.ru/auth/realms/itmo/"
    const val DOMAIN = "id.itmo.ru"

    /** The cookie's value in a `Cookie` header (`a=1; KEYCLOAK_IDENTITY=...`), or null; another name never matches. */
    fun fromCookieHeader(header: String?): String? = header?.split(';')
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$NAME=") }
        ?.substringAfter('=')
        ?.trim('"')
        ?.ifBlank { null }
}
