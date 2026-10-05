package dev.alllexey.itmowidgets.client.contract

/**
 * A client field Backend has not shipped yet: the one planned lead of Core 2.0 (ADR 0026, analysis 92), whose name
 * Backend card [backendCard] fixed. The model check skips it, and fails once the snapshot's [schema] has [property],
 * so the entry is removed in the re-sync that brings it.
 */
class PendingBackendField(val schema: String, val property: String, val backendCard: String) {
    override fun toString(): String = "$schema.$property ($backendCard)"
}

/** Every [PendingBackendField]. The `platform` query of `GET /api/app/version-info` (BK-17) is in the snapshot. */
object PendingBackendFields {
    val all: List<PendingBackendField> = listOf(
        PendingBackendField("RegisterDeviceRequest", "platform", "BK-16b"),
        PendingBackendField("RegisterDeviceRequest", "alertsAllowed", "BK-16b"),
        PendingBackendField("RegisterDeviceRequest", "appVersion", "BK-16b"),
    )
}
