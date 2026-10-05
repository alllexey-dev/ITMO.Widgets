package dev.alllexey.itmowidgets.core.demo

import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider

/** The demo session has no ITMO.ID token; its user is the fictional [DemoPeople.ME]. */
class DemoCurrentUserProvider(
    private val demo: DemoMode,
    private val signedIn: CurrentUserProvider
) : CurrentUserProvider {

    override suspend fun getCurrentUser(): CurrentUser? =
        if (demo.isActive()) DemoPeople.ME else signedIn.getCurrentUser()
}
