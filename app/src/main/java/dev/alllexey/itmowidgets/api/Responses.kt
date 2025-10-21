package dev.alllexey.itmowidgets.api

data class BackendTokenResponse(
    val accessToken: String,
    val accessTokenExpiresIn: Long,
    val refreshToken: String,
    val refreshTokenExpiresIn: Long
)