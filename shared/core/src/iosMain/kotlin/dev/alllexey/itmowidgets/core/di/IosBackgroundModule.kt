package dev.alllexey.itmowidgets.core.di

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.IosAppNotifier
import dev.alllexey.itmowidgets.core.notification.LocalNotificationCenter
import dev.alllexey.itmowidgets.core.notification.UserNotificationCenter
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.work.AppRefreshScheduler
import dev.alllexey.itmowidgets.core.work.RefreshStepLog
import dev.alllexey.itmowidgets.core.work.SystemAppRefreshSubmitter
import dev.alllexey.itmowidgets.core.work.UserDefaultsRefreshStepLog
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

/**
 * The background side of the app process (IO-14), beside [iosCoreModule]: one [IosAppNotifier] over
 * `UNUserNotificationCenter` as the graph's [AppNotifier], the one app refresh task's [AppRefreshScheduler], and the
 * background runner's [RefreshStepLog] in `NSUserDefaults`; the notifier and the log are sign-out cleaners. Kept out
 * of [iosCoreModule] because the system centre and scheduler exist only in the app, not in a test binary.
 */
val iosBackgroundModule = module {
    single<LocalNotificationCenter> {
        val log = get<AppLog>()
        UserNotificationCenter { identifier, reason -> log.warn(TAG, "The system dropped $identifier: $reason") }
    }
    single { IosAppNotifier(get(), get(), get()) } binds arrayOf(AppNotifier::class)
    single<SessionDataCleaner>(named("notifications")) { get<IosAppNotifier>() }

    single { AppRefreshScheduler(SystemAppRefreshSubmitter, get(), get()) }
    single { UserDefaultsRefreshStepLog(NSUserDefaults.standardUserDefaults) } binds arrayOf(RefreshStepLog::class)
    single<SessionDataCleaner>(named("background-refresh")) { get<UserDefaultsRefreshStepLog>() }
}

private const val TAG = "AppNotifier"
