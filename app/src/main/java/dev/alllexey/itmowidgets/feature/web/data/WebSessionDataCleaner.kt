package dev.alllexey.itmowidgets.feature.web.data

import android.content.Context
import android.webkit.WebView
import dagger.hilt.android.qualifiers.ApplicationContext
import android.webkit.CookieManager
import android.webkit.WebStorage
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Web sessions must not survive native sign-out or switching to another account. */
class WebSessionDataCleaner @Inject constructor(@param:ApplicationContext private val context: Context) : SessionDataCleaner {
    override suspend fun clearSessionData() {
        val cookies = withContext(Dispatchers.Main.immediate) {
            WebStorage.getInstance().deleteAllData()
            WebView(context).apply { clearCache(true); destroy() }
            CookieManager.getInstance().also { manager ->
                suspendCancellableCoroutine<Unit> { continuation ->
                    manager.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
                }
            }
        }
        withContext(Dispatchers.IO) { cookies.flush() }
    }
}
