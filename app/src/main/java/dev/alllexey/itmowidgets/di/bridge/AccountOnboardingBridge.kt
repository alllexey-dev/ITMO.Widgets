package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository

/**
 * Koin to Hilt for the first-run flag, which `onboardingDataModule` constructs: the debug `OnboardingTestEntryPoint`
 * of the routing tests still reads it from Hilt. Unscoped on purpose: Koin owns the lifetime and returns its single
 * every time. `ensureStarted`, because Hilt can ask before `Application.onCreate()`.
 */
@Module
@InstallIn(SingletonComponent::class)
object AccountOnboardingBridge {

    @Provides
    fun onboardingRepository(@ApplicationContext context: Context): OnboardingRepository =
        KoinStarter.ensureStarted(context).get()
}
