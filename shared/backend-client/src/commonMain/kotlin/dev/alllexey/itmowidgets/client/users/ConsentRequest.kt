package dev.alllexey.itmowidgets.client.users

import kotlinx.serialization.Serializable

/** The version of the services consent the user accepted; at least 1. */
@Serializable
data class ConsentRequest(val version: Int) {
    init {
        require(version >= 1) { "consent version must be at least 1" }
    }
}
