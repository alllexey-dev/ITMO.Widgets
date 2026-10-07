package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** The sport tab (L11): its rules over `feature.sport` in `:app` and `:shared:feature-sport`. */
class SportRulesTest {

    private val sportFiles by lazy {
        productionFiles.filter { file ->
            file.packagee?.name.orEmpty().let { it == SPORT_PACKAGE || it.startsWith("$SPORT_PACKAGE.") }
        }
    }

    @Test
    fun `no sport ViewModel is scoped to the activity`() {
        // The tab's ViewModels live in the host's store (the Fragment's, a Nav3 entry's, the SwiftUI host's); what
        // the feed and the schedule share is the singleton `SportBookingsHolder`, never an activity-scoped ViewModel.
        sportFiles
            .requireAtLeast(MIN_SPORT_FILES, "sport files")
            .assertFalse { file -> ACTIVITY_SCOPES.any { it in file.text } }
    }

    @Test
    fun `only a route obtains the sport ViewModels`() {
        // Screens stay stateless for the Fragment hosts, the Nav3 shell and the SwiftUI host alike (KN-02b).
        sportFiles
            .flatMap { it.functions() }
            .filter { function -> KOIN_VIEW_MODEL in function.text }
            .requireNonEmpty("sport functions obtaining a Koin ViewModel")
            .assertTrue { function -> function.name.endsWith("Route") }
    }

    private companion object {
        const val SPORT_PACKAGE = "dev.alllexey.itmowidgets.feature.sport"
        const val KOIN_VIEW_MODEL = "koinViewModel()"
        val ACTIVITY_SCOPES = listOf("activityViewModel", "activityViewModels", "koinActivityViewModel")

        /** The sport sources in `:app` and `:shared:feature-sport` after LP-6; drops only with the integrator's OK. */
        const val MIN_SPORT_FILES = 90
    }
}
