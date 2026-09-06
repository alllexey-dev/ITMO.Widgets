package dev.alllexey.itmowidgets.core.session

interface SessionLifecycleEffects {

    suspend fun prepareForSessionChange()

    suspend fun onSignedIn()

    suspend fun onSignedOut()
}
