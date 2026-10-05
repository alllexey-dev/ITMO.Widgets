package dev.alllexey.itmowidgets.feature.qr.di

import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The QR pass definitions Koin constructs; the repository and the wall clock come from the app's bridges. */
val qrModule = module {
    viewModelOf(::QrCodeViewModel)
}
