package dev.alllexey.itmowidgets.client.contract

/**
 * A client field whose name Backend has agreed but not shipped yet; [reason] says why the client leads. The model
 * check skips it, and fails once the snapshot's [schema] has [property], so the entry is removed in the re-sync that
 * brings it.
 */
class PendingBackendField(val schema: String, val property: String, val reason: String) {
    override fun toString(): String = "$schema.$property ($reason)"
}

/** Every [PendingBackendField]: none since Backend 1.12.0 shipped `UserData.gender`. */
object PendingBackendFields {
    val all: List<PendingBackendField> = emptyList()
}
