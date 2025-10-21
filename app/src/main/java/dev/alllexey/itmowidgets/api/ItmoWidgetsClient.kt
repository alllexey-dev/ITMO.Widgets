package dev.alllexey.itmowidgets.api

import android.os.Build
import android.util.Log
import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.utils.ItmoWidgetsException
import dev.alllexey.itmowidgets.core.ItmoWidgetsImpl
import dev.alllexey.itmowidgets.core.model.RegisterDeviceRequest
import dev.alllexey.itmowidgets.core.utils.ItmoWidgetsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ItmoWidgetsClient(
    myItmo: MyItmo,
    storage: ItmoWidgetsStorage
) : ItmoWidgetsImpl(myItmo, storage) {

    suspend fun sendFirebaseToken(token: String, deviceName: String = getDeviceName()): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.registerToken(RegisterDeviceRequest(token, deviceName))
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