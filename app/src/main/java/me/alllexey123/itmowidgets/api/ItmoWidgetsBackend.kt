package me.alllexey123.itmowidgets.api

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.alllexey123.itmowidgets.AppContainer
import okhttp3.OkHttpClient
import okio.IOException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ItmoWidgetsBackend(
    val appContainer: AppContainer,
) {

    val myItmo = appContainer.myItmo

    val storage = appContainer.storage

    val retrofit: Retrofit = Retrofit.Builder()
        .client(OkHttpClient.Builder().addInterceptor(BackendTokenInterceptor(this)).build())
        .baseUrl("https://itmowidgets.alllexey.ru")
        .addConverterFactory(GsonConverterFactory.create(myItmo.gson))
        .build()

    val api: ItmoWidgetsBackendApi = retrofit.create(ItmoWidgetsBackendApi::class.java)

    fun loginBlocking(): BackendTokenResponse {
        val now = System.currentTimeMillis()
        if (myItmo.storage.accessToken == null || myItmo.storage.accessExpiresAt < now) {
            if (myItmo.storage.refreshToken == null) {
                throw IOException("Could not login. ITMO ID refresh token is null.")
            }
            if (myItmo.storage.refreshExpiresAt != 0L && myItmo.storage.refreshExpiresAt < now) {
                throw IOException("Could not login. ITMO ID refresh token is expired.")
            }
            myItmo.refreshTokens(myItmo.storage.refreshToken)
            if (myItmo.storage.accessToken == null) {
                throw IOException("Could not login. ITMO ID access token is null after refresh.")
            }
        }

        val response =
            api.loginViaItmoToken(BackendLoginRequest(myItmo.storage.accessToken)).execute()

        if (!response.isSuccessful || response.body() == null) {
            storage.clearBackendTokens()
            throw IOException("Could not login. Server response: ${response.code()}")
        }

        val tokens = response.body()!!.data!!
        storage.updateBackendTokens(tokens)
        return tokens
    }

    fun refreshTokensBlocking(): BackendTokenResponse {
        val refreshToken = storage.getBackendRefreshToken()
        if (refreshToken == null) return loginBlocking()
        val response = api.refreshToken(BackendRefreshTokenRequest(refreshToken)).execute()

        if (!response.isSuccessful || response.body() == null) {
            storage.clearBackendTokens()
            throw IOException("Could not refresh backend tokens. Server response: ${response.code()}")
        }

        val newTokens = response.body()!!.data!!
        storage.updateBackendTokens(newTokens)
        return newTokens
    }

    fun sendFirebaseToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = sendFirebaseTokenBlocking(token)
                if (response.isSuccessful) {
                    Log.d(TAG, "Token registered successfully")
                } else {
                    Log.e(
                        TAG,
                        "Failed to register token: ${
                            response.errorBody()?.string()
                        }, ${response.body()?.error}"
                    )
                    Log.e(TAG, response.code().toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception sending token to server", e)
            }
        }
    }

    fun sendFirebaseTokenBlocking(token: String): Response<ApiResponse<String>?> {
        val deviceName = getDeviceName()
        return api.registerToken(BackendRegisterDeviceRequest(token, deviceName)).execute()
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        val capitalizedManufacturer = manufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

        return "$capitalizedManufacturer $model"
    }

    companion object {
        private const val TAG = "ItmoWidgetsBackend"
    }
}