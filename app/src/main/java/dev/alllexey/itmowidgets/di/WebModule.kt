package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.web.data.WebSessionDataCleaner
import dev.alllexey.itmowidgets.feature.connections.data.TinkHpkeSender
import dev.alllexey.itmowidgets.feature.connections.domain.HpkeSender
import org.koin.core.qualifier.named
import org.koin.dsl.module

val webModule = module {
    single<SessionDataCleaner>(named("web")) { WebSessionDataCleaner(get(), get()) }
    // Pairing seals with Tink; the screen lock (BiometricDeviceUnlock) needs the activity, so the sheet makes it.
    single<HpkeSender> { TinkHpkeSender() }
}
