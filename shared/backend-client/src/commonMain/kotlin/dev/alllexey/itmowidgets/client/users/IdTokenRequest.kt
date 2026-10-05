package dev.alllexey.itmowidgets.client.users

import kotlinx.serialization.Serializable

/** The caller's ITMO.ID id token; Backend copies its name, picture and groups and does not store the token. */
@Serializable
data class IdTokenRequest(val idToken: String) {
    override fun toString(): String = "IdTokenRequest(idToken=<redacted>)"
}
