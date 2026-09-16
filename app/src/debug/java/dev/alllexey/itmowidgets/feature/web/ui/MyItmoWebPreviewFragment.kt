package dev.alllexey.itmowidgets.feature.web.ui

import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import dev.alllexey.itmowidgets.R
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import dev.alllexey.itmowidgets.BuildConfig
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic transport only; every request is intercepted so no fixture reaches an external service. */
class MyItmoWebPreviewFragment : MyItmoWebFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<WebView>(R.id.web_view).settings.cacheMode = WebSettings.LOAD_NO_CACHE
        super.onViewCreated(view, savedInstanceState)
    }

    override fun interceptRequest(request: WebResourceRequest): WebResourceResponse {
        check(BuildConfig.DEBUG)
        gate?.await(30, TimeUnit.SECONDS)
        val error = failMainFrame && request.isForMainFrame
        if (request.isForMainFrame) mainRequests.incrementAndGet()
        if (error) errorResponses.incrementAndGet()
        val page = if (request.url.path == "/test/second") "Вторая страница" else "Учебный личный кабинет"
        val html = """
            <!doctype html><html lang="ru"><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>body{font:18px sans-serif;padding:20px;line-height:1.5;color:#17231c;background:#f4faf5}
            h1{font-size:26px}a{color:#006c4c} @media(prefers-color-scheme:dark){body{color:#e0e9e1;background:#101812}a{color:#7ad8b0}}</style>
            <title>Тестовый Мой ИТМО</title></head><body><h1>$page</h1>
            <p>Александра Константиновна Константинопольская</p>
            <p>Синтетические данные для проверки интерфейса. Запросы к MyITMO не отправляются.</p>
            <a id="next" href="/test/second">Открыть вторую страницу</a></body></html>
        """.trimIndent()
        return WebResourceResponse("text/html", "UTF-8", if (error) 503 else 200,
            if (error) "Service Unavailable" else "OK", mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(html.toByteArray()))
    }

    companion object {
        val mainRequests = AtomicInteger()
        val errorResponses = AtomicInteger()
        @Volatile var failMainFrame = false
        @Volatile var gate: CountDownLatch? = null
    }
}
