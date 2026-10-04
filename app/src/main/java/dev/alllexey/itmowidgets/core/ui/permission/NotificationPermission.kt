package dev.alllexey.itmowidgets.core.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * The system dialog for `POST_NOTIFICATIONS`; the result is whether it was granted.
 *
 * Launch it only where [notificationPermissionIsRuntime] holds: older Android has no such permission.
 */
class RequestNotificationPermission : ActivityResultContract<Unit, Boolean>() {

    private val delegate = ActivityResultContracts.RequestPermission()

    override fun createIntent(context: Context, input: Unit): Intent =
        delegate.createIntent(context, Manifest.permission.POST_NOTIFICATIONS)

    override fun getSynchronousResult(context: Context, input: Unit): SynchronousResult<Boolean>? =
        delegate.getSynchronousResult(context, Manifest.permission.POST_NOTIFICATIONS)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = delegate.parseResult(resultCode, intent)
}

/** Android 13 made notifications a runtime permission; before it only the system switch exists. */
fun notificationPermissionIsRuntime(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

/** True below Android 13, where nothing has to be granted. */
fun Context.hasNotificationPermission(): Boolean =
    !notificationPermissionIsRuntime(Build.VERSION.SDK_INT) ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** The app's page in the system notification settings: channels and the switch live there. */
fun Context.openAppNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    )
}

/** The permission dialog when it can still appear; otherwise the app's notification page. */
fun Context.requestNotifications(launcher: ActivityResultLauncher<Unit>) {
    if (hasNotificationPermission()) openAppNotificationSettings() else launcher.launch(Unit)
}

/** A denial without a dialog means the permission is locked; only the system page can undo that. */
fun Activity.openNotificationSettingsIfLocked(granted: Boolean) {
    if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
        openAppNotificationSettings()
    }
}
