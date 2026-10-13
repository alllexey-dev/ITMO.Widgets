package dev.alllexey.itmowidgets.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Frame timing of tab taps and of a push and pop, each in a fresh process (killed in the setup: a `startupMode`
 * would kill it again after the setup), without the app's profile and with it
 * (`BaselineProfileMode.Require` also fails when the APK ships no profile). Runs nightly (android-nightly.yml).
 */
@RunWith(Parameterized::class)
class NavigationBenchmark(private val compilation: CompilationMode) {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun tabTaps() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        iterations = ITERATIONS,
        setupBlock = {
            killProcess()
            startInDemo()
        },
    ) {
        visitTabs()
    }

    @Test
    fun pushAndPop() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        iterations = ITERATIONS,
        setupBlock = {
            killProcess()
            startInDemo()
            selectTab("ME")
        },
    ) {
        repeat(3) { pushAndPopFriends() }
    }

    companion object {
        private const val ITERATIONS = 5

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun compilations(): List<CompilationMode> =
            listOf(CompilationMode.None(), CompilationMode.Partial(BaselineProfileMode.Require))
    }
}
