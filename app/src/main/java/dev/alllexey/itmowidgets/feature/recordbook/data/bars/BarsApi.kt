package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Temporary in-app official BARS transport, isolated for a later library extraction. */
interface BarsApi {
    @GET("login")
    suspend fun login(@Query("code") code: String, @Query("customRedirectUri") redirect: String): Response<Unit>

    @GET("users/current_user/")
    suspend fun currentUser(@Header("Authorization") token: String): Response<BarsUser>

    @POST("config/personal")
    suspend fun selectPeriod(@Header("Authorization") token: String, @Body setting: BarsSetting): Response<BarsSetting>

    @GET("journal/disciplines")
    suspend fun disciplines(@Header("Authorization") token: String,
        @Query("withCheckpointPlansOnly") withPlans: Boolean = true): Response<List<BarsDiscipline>>

    @GET("journal/groups-and-flows")
    suspend fun groupsAndFlows(@Header("Authorization") token: String): Response<List<BarsGroupOrFlow>>

    @GET("marks/{plan}/{type}/{identifier}/student")
    suspend fun journal(@Header("Authorization") token: String, @Path("plan") plan: Long,
        @Path("type") type: String, @Path("identifier") identifier: String): Response<BarsJournal>
}
