package dev.alllexey.itmowidgets.core.notification

fun interface FcmTokenSync {
    suspend fun sync()
}
