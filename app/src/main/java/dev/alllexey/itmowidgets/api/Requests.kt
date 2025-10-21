package dev.alllexey.itmowidgets.api

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ErrorDetails?
)

data class ErrorDetails(
    val message: String,
    val code: String?
)

data class BackendRegisterDeviceRequest(val fcmToken: String, val deviceName: String)

data class BackendLoginRequest(val itmoToken: String)

data class BackendRefreshTokenRequest(val refreshToken: String)
