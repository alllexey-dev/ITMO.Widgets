package dev.alllexey.itmowidgets.core.di

import dev.alllexey.itmowidgets.core.payments.check.IosPaymentsNotifier
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheck
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheckPart
import dev.alllexey.itmowidgets.core.payments.check.PaymentsNoticeLog
import dev.alllexey.itmowidgets.core.payments.check.PaymentsNotifier
import dev.alllexey.itmowidgets.core.payments.check.PaymentsScheduler
import dev.alllexey.itmowidgets.core.payments.check.PaymentsTracking
import dev.alllexey.itmowidgets.core.payments.check.RefreshTaskPaymentsScheduler
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.work.AppRefreshScheduler
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.core.work.RefreshStep
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The check `Общежитие и оплата` on iOS over every feature's [PaymentsCheckPart] (dormitory, tuition): posting over
 * `IosAppNotifier`, the app's one refresh task as its scheduler and the background runner's step under
 * [RefreshStepKeys.PAYMENTS]. Needs `iosBackgroundModule`, so it stays out of a test binary's graph like it; Android
 * binds the same in `androidPaymentsModule`.
 */
val paymentsCheckIosModule = module {
    single<PaymentsNotifier> { IosPaymentsNotifier(get()) }
    single<PaymentsScheduler> { RefreshTaskPaymentsScheduler(get<AppRefreshScheduler>(), get()) }
    single { PaymentsNoticeLog(get<AppDirectories>(), get(), get()) }
    single<SessionDataCleaner>(named("payments-notices")) { get<PaymentsNoticeLog>() }
    single { PaymentsCheck(getAll<PaymentsCheckPart>(), get(), get(), get(), get(), get(), get(), get()) }
    single { PaymentsTracking(get(), get()) }
    single<BackgroundCheck>(named("payments")) { get<PaymentsTracking>() }
    single(named(RefreshStepKeys.PAYMENTS)) {
        val check = get<PaymentsCheck>()
        RefreshStep(RefreshStepKeys.PAYMENTS, RefreshStepKeys.PAYMENTS_PERIOD) { check.run() }
    }
}
