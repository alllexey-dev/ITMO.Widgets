package dev.alllexey.itmowidgets.core.session

interface BackendDeviceSession {

    suspend fun registerCurrentDevice()

    suspend fun unregisterCurrentDevice()
}
