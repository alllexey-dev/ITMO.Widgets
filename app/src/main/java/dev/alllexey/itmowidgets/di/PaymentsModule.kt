package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.core.payments.check.ImmediatePaymentsNotifier
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheck
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheckPart
import dev.alllexey.itmowidgets.core.payments.check.PaymentsNoticeLog
import dev.alllexey.itmowidgets.core.payments.check.PaymentsNotifier
import dev.alllexey.itmowidgets.core.payments.check.PaymentsScheduler
import dev.alllexey.itmowidgets.core.payments.check.PaymentsTracking
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.PeriodicCheckScheduler
import dev.alllexey.itmowidgets.core.payments.work.PAYMENTS_SPEC
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The check `Общежитие и оплата` on Android over every feature's [PaymentsCheckPart] (dormitory, tuition), which the
 * feature modules bind under their own qualifiers: posting through the app's notifier, which holds a notice of the
 * quiet hours for the next run, and the WorkManager scheduler. iOS binds the same in `paymentsCheckIosModule`.
 */
val androidPaymentsModule = module {
    single<PaymentsNotifier> { ImmediatePaymentsNotifier(get()) }
    single<PaymentsScheduler> {
        object : PaymentsScheduler, CheckScheduler by PeriodicCheckScheduler(androidContext(), PAYMENTS_SPEC) {}
    }
    single { PaymentsNoticeLog(get<AppDirectories>(), get(), get()) }
    single<SessionDataCleaner>(named("payments-notices")) { get<PaymentsNoticeLog>() }
    single { PaymentsCheck(getAll<PaymentsCheckPart>(), get(), get(), get(), get(), get(), get(), get()) }
    single { PaymentsTracking(get(), get()) }
    single<BackgroundCheck>(named("payments")) { get<PaymentsTracking>() }
}
