package dev.alllexey.itmowidgets.feature.qr.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetState
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore
import dev.alllexey.itmowidgets.feature.qr.work.QrWidgetWork
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home-screen QR pass.
 *
 * The pass is covered by a spoiler and revealed by a tap. The reveal is animated
 * from [onReceive] through `goAsync()` rather than from a worker: WorkManager gives
 * no timing guarantee, so a tap would visibly lag, and every frame is an IPC round
 * trip that has to stay short-lived.
 */
@AndroidEntryPoint
class QrCodeWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var repository: QrCodeRepository

    @Inject lateinit var stateStore: QrWidgetStateStore

    @Inject lateinit var images: QrWidgetImages

    @Inject lateinit var appearance: QrAppearancePreferences

    /**
     * Draws from the cache straight away and only then asks for a refresh.
     *
     * The broadcast arrives far more often than the declared update period, so the
     * drawing must not depend on a worker running.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                appWidgetIds.forEach { appWidgetId -> renderCached(context, appWidgetId) }
                QrWidgetWork.enqueueUpdate(context)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun renderCached(context: Context, appWidgetId: Int) {
        var state = stateStore.getState(appWidgetId)
        // A transition owns the widget while it runs; drawing over it would tear.
        if (state == QrWidgetState.REVEALING || state == QrWidgetState.HIDING) {
            if (transitioningWidgetIds.contains(appWidgetId)) return

            // HyperOS may kill the provider process between animation frames. The
            // persisted transient state then has no coroutine behind it, so recover
            // to a safe, tappable state instead of leaving the initial layout blank.
            state = QrWidgetState.HIDDEN
            stateStore.setState(appWidgetId, state)
        }

        // The legacy widget kept its last pass as an offline fallback. Expiration is
        // still used to decide whether a network refresh is needed, but it must not
        // turn a previously working widget into an empty square.
        val qrHex = repository.currentQrHex(allowExpired = true)
        if (qrHex == null) {
            stateStore.setState(appWidgetId, QrWidgetState.HIDDEN)
            render(context, appWidgetId, images.spoiler())
            return
        }

        val revealed = !appearance.isSpoilerEnabled() || state == QrWidgetState.VISIBLE
        stateStore.setState(
            appWidgetId,
            if (revealed) QrWidgetState.VISIBLE else QrWidgetState.HIDDEN
        )
        render(
            context,
            appWidgetId,
            if (revealed) images.qrCode(qrHex) else images.spoiler()
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                appWidgetIds.forEach { id ->
                    autoHideJobs.remove(id)?.cancel()
                    transitioningWidgetIds.remove(id)
                    QrWidgetWork.cancelAutoHide(context, id)
                    stateStore.clearState(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_WIDGET_CLICK && intent.action != ACTION_AUTO_HIDE) return

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_WIDGET_CLICK -> handleClick(context, appWidgetId)
                    ACTION_AUTO_HIDE -> {
                        autoHideJobs.remove(appWidgetId)?.cancel()
                        hideIfVisible(context, appWidgetId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleClick(context: Context, appWidgetId: Int) {
        when (stateStore.getState(appWidgetId)) {
            QrWidgetState.HIDDEN -> reveal(context, appWidgetId)
            // The legacy widget refreshed an already revealed code on the next tap.
            QrWidgetState.VISIBLE -> refreshVisible(context, appWidgetId)
            // A transition is already running; a second tap must not fight it.
            QrWidgetState.REVEALING, QrWidgetState.HIDING -> {
                if (!transitioningWidgetIds.contains(appWidgetId)) {
                    stateStore.setState(appWidgetId, QrWidgetState.HIDDEN)
                    reveal(context, appWidgetId)
                }
            }
        }
    }

    private suspend fun reveal(context: Context, appWidgetId: Int) {
        if (!transitioningWidgetIds.add(appWidgetId)) return
        try {
            revealGuarded(context, appWidgetId)
        } finally {
            transitioningWidgetIds.remove(appWidgetId)
            if (stateStore.getState(appWidgetId) == QrWidgetState.REVEALING) {
                stateStore.setState(appWidgetId, QrWidgetState.HIDDEN)
                render(context, appWidgetId, images.spoiler())
            }
        }
    }

    private suspend fun revealGuarded(context: Context, appWidgetId: Int) {
        var qrHex = repository.currentQrHex(allowExpired = true)
        if (qrHex == null) {
            repository.refreshQrHex(force = true)
            qrHex = repository.currentQrHex(allowExpired = true)
        }
        if (qrHex == null) {
            stateStore.setState(appWidgetId, QrWidgetState.HIDDEN)
            render(context, appWidgetId, images.spoiler())
            return
        }

        val spoiler = images.spoiler()
        val code = images.qrCode(qrHex)

        stateStore.setState(appWidgetId, QrWidgetState.REVEALING)
        animate(context, appWidgetId, from = spoiler, to = code)
        stateStore.setState(appWidgetId, QrWidgetState.VISIBLE)
        render(context, appWidgetId, code)

        scheduleAutoHideIfNeeded(context, appWidgetId)
    }

    private suspend fun refreshVisible(context: Context, appWidgetId: Int) {
        repository.refreshQrHex(force = true)
        val qrHex = repository.currentQrHex(allowExpired = true)
        if (qrHex == null) {
            stateStore.setState(appWidgetId, QrWidgetState.HIDDEN)
            render(context, appWidgetId, images.spoiler())
            return
        }

        stateStore.setState(appWidgetId, QrWidgetState.VISIBLE)
        render(context, appWidgetId, images.qrCode(qrHex))
        scheduleAutoHideIfNeeded(context, appWidgetId)
    }

    private suspend fun scheduleAutoHideIfNeeded(context: Context, appWidgetId: Int) {
        autoHideJobs.remove(appWidgetId)?.cancel()
        val appContext = context.applicationContext
        if (!appearance.isSpoilerEnabled()) {
            QrWidgetWork.cancelAutoHide(appContext, appWidgetId)
            return
        }

        QrWidgetWork.scheduleAutoHide(appContext, appWidgetId)
        val job = scope.launch {
            delay(QrWidgetWork.AUTO_HIDE_DELAY_MILLIS)
            QrWidgetWork.cancelAutoHide(appContext, appWidgetId)
            hideIfVisible(appContext, appWidgetId)
        }
        autoHideJobs.put(appWidgetId, job)?.cancel()
        job.invokeOnCompletion { autoHideJobs.remove(appWidgetId, job) }
    }

    private suspend fun hideIfVisible(context: Context, appWidgetId: Int) {
        if (!appearance.isSpoilerEnabled()) return
        if (stateStore.getState(appWidgetId) != QrWidgetState.VISIBLE) return

        stateStore.setState(appWidgetId, QrWidgetState.HIDDEN)
        render(context, appWidgetId, images.spoiler())
    }

    /**
     * Pushes frames with nothing but the bitmap set.
     *
     * Re-applying the click intent and the visibility flags on every frame makes the
     * launcher rebuild the view and the widget visibly flickers. The settled state is
     * drawn by [render] once the animation ends.
     */
    private suspend fun animate(
        context: Context,
        appWidgetId: Int,
        from: Bitmap,
        to: Bitmap
    ) {
        val animation = QrWidgetAnimation.of(appearance.spoilerAnimationType(), from, to)
            ?: return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        var views = RemoteViews(context.packageName, R.layout.widget_qr_code)

        repeat(ANIMATION_FRAMES) { frame ->
            // Every setter appends an action, so the object is rebuilt periodically
            // instead of carrying all frames' bitmaps in a single parcel.
            if (frame > 0 && frame % VIEWS_RESET_FRAMES == 0) {
                views = RemoteViews(context.packageName, R.layout.widget_qr_code)
            }

            val progress = (frame + 1).toFloat() / ANIMATION_FRAMES
            views.setImageViewBitmap(
                R.id.qr_code_image,
                animation.frame(QrWidgetAnimation.ease(progress))
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
            delay(FRAME_DELAY_MS)
        }
    }

    companion object {

        const val ACTION_WIDGET_CLICK = "dev.alllexey.itmowidgets.action.QR_WIDGET_CLICK"
        const val ACTION_AUTO_HIDE = "dev.alllexey.itmowidgets.action.QR_WIDGET_AUTO_HIDE"

        /** ~300 ms at 60 fps: smooth, and well inside the receiver's budget. */
        private const val ANIMATION_FRAMES = 18
        private const val FRAME_DELAY_MS = 16L
        private const val VIEWS_RESET_FRAMES = 2

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val autoHideJobs = ConcurrentHashMap<Int, Job>()
        private val transitioningWidgetIds = ConcurrentHashMap.newKeySet<Int>()

        fun widgetIds(context: Context): IntArray {
            return AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, QrCodeWidgetProvider::class.java)
            )
        }

        /** Draws widget content and always keeps the tap inside the widget flow. */
        fun render(context: Context, appWidgetId: Int, bitmap: Bitmap) {
            val views = RemoteViews(context.packageName, R.layout.widget_qr_code)
            views.setImageViewBitmap(R.id.qr_code_image, bitmap)
            views.setOnClickPendingIntent(R.id.qr_code_image, clickIntent(context, appWidgetId))
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }

        private fun clickIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, QrCodeWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
