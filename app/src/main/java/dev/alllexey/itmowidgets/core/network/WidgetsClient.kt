package dev.alllexey.itmowidgets.core.network

import android.os.Build
import android.util.Log
import api.myitmo.MyItmo
import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.ItmoWidgets
import dev.alllexey.itmowidgets.core.utils.ItmoWidgetsException
import dev.alllexey.itmowidgets.core.ItmoWidgetsImpl
import dev.alllexey.itmowidgets.core.model.RegisterDeviceRequest
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.util.OffsetDateTimeAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

class WidgetsClient(
    myItmo: MyItmo,
    settings: AppSettingsStorage,
    baseUrl: String = STABLE_BASE_URL
) : ItmoWidgetsImpl(myItmo, baseUrl) {

    override val okHttpClient = super.okHttpClient.newBuilder()
        .build()

    suspend fun sendFirebaseToken(token: String, deviceName: String = getDeviceName()): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.registerDevice(RegisterDeviceRequest(token, deviceName))
                if (response.success && response.data != null) {
                    Result.success(response.data!!)
                } else {
                    val errorMessage = response.error?.message ?: "Failed to register token"
                    Result.failure(ItmoWidgetsException(errorMessage))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception sending token to server", e)
                Result.failure(e)
            }
        }
    }

    companion object {
        private const val TAG = "ItmoWidgetsBackend"

        fun getDeviceName(): String {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            val capitalizedManufacturer = manufacturer.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            return "$capitalizedManufacturer $model"
        }
    }
}
