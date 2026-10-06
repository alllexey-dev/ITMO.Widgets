package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.settings.WidgetAppearance
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeCustomSpoilerRepository
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.feature.me.di.meModule
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingModule
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.definition.indexKey
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class AccountDebugFixturesTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `a fixture builds the Me and onboarding view models until its host unloads it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val fakes = Fakes()

        val fixture = AccountDebugFixtures.load(application, fakes)
        assertSame(fakes.me, koin.get<MeViewModel>())
        val handle = SavedStateHandle()
        assertSame(fakes.onboarding, koin.get<OnboardingViewModel> { parametersOf(handle) })
        assertSame(handle, fakes.handle)

        AccountDebugFixtures.unload(application, fixture)
        assertReleaseDefinition(koin, MeViewModel::class, meModule)
        assertReleaseDefinition(koin, OnboardingViewModel::class, onboardingModule)
    }

    @Test
    fun `a replaced fixture is left to the host that replaced it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val fakes = Fakes()

        val first = AccountDebugFixtures.load(application, fakes)
        val second = AccountDebugFixtures.load(application, fakes)
        AccountDebugFixtures.unload(application, first)
        assertSame(fakes.me, koin.get<MeViewModel>())

        AccountDebugFixtures.unload(application, second)
        assertReleaseDefinition(koin, MeViewModel::class, meModule)
    }

    /** The definition Koin resolves [type] with is the one [module] declares, not the fixture's. */
    @OptIn(KoinInternalApi::class)
    private fun assertReleaseDefinition(koin: Koin, type: KClass<*>, module: Module) {
        val key = indexKey(type, null, koin.scopeRegistry.rootScope.scopeQualifier)
        assertTrue("${type.simpleName} is not declared by its release module", key in module.mappings)
        assertSame(module.mappings[key], koin.instanceRegistry.instances[key])
    }

    /** One instance of each ViewModel over in-memory fakes; [handle] is the one the last onboarding call got. */
    private class Fakes : AccountDebugFixtures.Fakes {
        var handle: SavedStateHandle? = null
        val me = MeViewModel(
            FakeSessionRepository(SessionState.SignedOut),
            FakeSocialRepository(),
            FakeCustomServicesRepository(false),
        )
        val onboarding = OnboardingViewModel(
            FakeOnboardingRepository(),
            FakeCustomServicesRepository(false),
            DefaultAppearance,
            FakeCustomSpoilerRepository(),
            SavedStateHandle(),
        )

        override fun me() = me

        override fun onboarding(savedStateHandle: SavedStateHandle): OnboardingViewModel {
            handle = savedStateHandle
            return onboarding
        }
    }

    /** A fresh install's widget appearance that is never written. */
    private object DefaultAppearance : WidgetAppearanceRepository {
        override fun observeAppearance() = flowOf(WidgetAppearance())
        override suspend fun setCompactNextLessonEarly(enabled: Boolean) = Unit
        override suspend fun setCompactTeacherHidden(hidden: Boolean) = Unit
        override suspend fun setFullTeacherHidden(hidden: Boolean) = Unit
        override suspend fun setFullPastLessonsHidden(hidden: Boolean) = Unit
        override suspend fun setFullTomorrowEnabled(enabled: Boolean) = Unit
        override suspend fun setCompactTextSize(size: WidgetTextSize) = Unit
        override suspend fun setFullTextSize(size: WidgetTextSize) = Unit
        override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) = Unit
        override suspend fun setQrSpoilerEnabled(enabled: Boolean) = Unit
    }

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin and Hilt are up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
