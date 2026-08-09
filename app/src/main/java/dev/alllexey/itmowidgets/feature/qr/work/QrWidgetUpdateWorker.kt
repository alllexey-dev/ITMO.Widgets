package dev.alllexey.itmowidgets.feature.qr.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetState
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrCodeWidgetProvider

/**
 * Refreshes the pass and redraws every widget instance.
 *
 * A failed refresh falls back to the cached code: an outdated pass is still worth
 * more than an empty square.
 */
class QrWidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val dependencies = QrWidgetEntryPoint.from(appContext)
    private val repository = dependencies.qrCodeRepository()
    private val stateStore = dependencies.qrWidgetStateStore()
    private val images = dependencies.qrWidgetImages()
    private val appearance = dependencies.qrAppearancePreferences()

    override suspend fun doWork(): Result {
        val appWidgetIds = QrCodeWidgetProvider.widgetIds(applicationContext)
        if (appWidgetIds.isEmpty()) return Result.success()

        repository.refreshQrHex(force = true)
        // Match the working v2.0 widget: refresh first, then retain the last cached
        // pass as an offline fallback instead of replacing it with an empty view.
        val qrHex = repository.currentQrHex(allowExpired = true)

        val spoilerEnabled = appearance.isSpoilerEnabled()
        appWidgetIds.forEach { appWidgetId ->
            val decision = decideQrWidgetRefresh(
                currentState = stateStore.getState(appWidgetId),
                hasQrCode = qrHex != null,
                spoilerEnabled = spoilerEnabled
            ) ?: return@forEach

            if (decision.cancelAutoHide) {
                QrWidgetWork.cancelAutoHide(applicationContext, appWidgetId)
            }
            stateStore.setState(appWidgetId, decision.state)
            val bitmap = when (decision.content) {
                QrWidgetRefreshContent.SPOILER -> images.spoiler()
                QrWidgetRefreshContent.QR_CODE -> images.qrCode(requireNotNull(qrHex))
            }
            QrCodeWidgetProvider.render(applicationContext, appWidgetId, bitmap)
        }

        return Result.success()
    }
}

internal enum class QrWidgetRefreshContent {
    SPOILER,
    QR_CODE,
}

internal data class QrWidgetRefreshDecision(
    val state: QrWidgetState,
    val content: QrWidgetRefreshContent,
    val cancelAutoHide: Boolean,
)

/** Keeps a background refresh from covering or repainting a pass being used. */
internal fun decideQrWidgetRefresh(
    currentState: QrWidgetState,
    hasQrCode: Boolean,
    spoilerEnabled: Boolean,
): QrWidgetRefreshDecision? {
    if (currentState == QrWidgetState.REVEALING || currentState == QrWidgetState.HIDING) {
        return null
    }
    if (!hasQrCode && currentState == QrWidgetState.VISIBLE) return null

    if (!spoilerEnabled && hasQrCode) {
        return QrWidgetRefreshDecision(
            state = QrWidgetState.VISIBLE,
            content = QrWidgetRefreshContent.QR_CODE,
            cancelAutoHide = true
        )
    }
    if (currentState == QrWidgetState.VISIBLE) return null

    return QrWidgetRefreshDecision(
        state = QrWidgetState.HIDDEN,
        content = QrWidgetRefreshContent.SPOILER,
        cancelAutoHide = true
    )
}
