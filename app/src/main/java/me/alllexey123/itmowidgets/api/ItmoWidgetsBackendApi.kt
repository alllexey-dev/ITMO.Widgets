package me.alllexey123.itmowidgets.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ItmoWidgetsBackendApi {

    @POST("/api/auth/itmo-token")
    fun loginViaItmoToken(@Body request: BackendLoginRequest): Call<ApiResponse<BackendTokenResponse>>

    @POST("/api/device/register-device")
    fun registerToken(@Body request: BackendRegisterDeviceRequest): Call<ApiResponse<String>>


    @POST("/api/auth/refresh")
    fun refreshToken(@Body request: BackendRefreshTokenRequest): Call<ApiResponse<BackendTokenResponse>>

}