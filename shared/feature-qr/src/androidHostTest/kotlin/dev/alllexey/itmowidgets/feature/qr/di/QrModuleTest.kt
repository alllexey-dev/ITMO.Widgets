package dev.alllexey.itmowidgets.feature.qr.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class QrModuleTest {

    /** The repository and the wall clock are bridged from the app's Hilt graph; every other type is Koin's. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theQrModuleResolvesWithTheBridgedRepositoryAndClock() {
        qrModule.verify(extraTypes = listOf(QrCodeRepository::class, Clock::class, SavedStateHandle::class))
    }
}
