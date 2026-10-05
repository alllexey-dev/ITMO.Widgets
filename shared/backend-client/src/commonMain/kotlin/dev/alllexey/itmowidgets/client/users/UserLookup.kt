package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.common.UserProfile
import kotlinx.serialization.Serializable

/**
 * Registered profiles for ISU numbers found elsewhere (MyITMO search); not a name search. Backend accepts at most
 * 50 positive ISUs, counted before deduplication, and answers anything else with HTTP 400 `invalid_request`; an
 * empty list is valid.
 */
@Serializable
data class UserLookupRequest(val isus: List<Int>)

/** The registered users among the asked ISUs, deduplicated in first-occurrence order; unknown ISUs are omitted. */
@Serializable
data class UserLookupResponse(val users: List<UserProfile>)
