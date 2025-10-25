package dev.alllexey.itmowidgets.api

import dev.alllexey.itmowidgets.data.UserSettingsStorage
import okhttp3.Interceptor
import okhttp3.Response
import okio.IOException

class SettingsStateInterceptor(val settingsStorage: UserSettingsStorage) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (settingsStorage.getCustomServicesState()) {
            return chain.proceed(chain.request())
        }
        throw IOException("Custom services are disabled, cannot proceed")
    }
}