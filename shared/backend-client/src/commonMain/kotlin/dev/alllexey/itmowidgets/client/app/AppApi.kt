package dev.alllexey.itmowidgets.client.app

import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.client.error.BackendException
import kotlin.coroutines.cancellation.CancellationException

/**
 * App (routes under `/api/app`): the versions Backend advertises for each app. Semantics are in Backend's
 * `app-version.md` contract. The routes are anonymous; a token is still sent when the token source has one.
 *
 * Not mirrored: `GET /api/app/version`, the latest Android version as a bare string, which Backend keeps for 2.0.x
 * clients only.
 */
interface AppApi {

    /**
     * `GET /api/app/version-info`, with `platform` in the query only when [platform] is not `null`. Without it
     * Backend answers the Android values, as released 2.1 and 2.2 read them; a Backend before 1.8.0 ignores the
     * query and answers the Android values for `IOS` too. `minVersion` is advisory: the app decides what to show.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun versionInfo(platform: DevicePlatform?): AppVersionInfo
}
