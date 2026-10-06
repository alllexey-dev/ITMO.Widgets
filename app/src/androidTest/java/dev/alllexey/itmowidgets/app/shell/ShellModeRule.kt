package dev.alllexey.itmowidgets.app.shell

import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

/**
 * Runs a test body, with its `@Before` and `@After`, once per shell `MainActivity` can run, and names the shell in a
 * failure. The body reads the screen through `ShellProbe`, so the same assertions hold in both. Until SH-1b9 adds the
 * switch, only the legacy shell exists and a body runs once.
 */
class ShellModeRule : TestRule {

    enum class Mode { LEGACY }

    /** The shell of the pass that runs now. */
    var mode: Mode = Mode.LEGACY
        private set

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val failures = mutableListOf<Throwable>()
            val skipped = mutableListOf<AssumptionViolatedException>()
            Mode.entries.forEach { pass ->
                mode = pass
                try {
                    base.evaluate()
                } catch (assumption: AssumptionViolatedException) {
                    skipped += assumption
                } catch (failure: Throwable) {
                    failures += AssertionError("${description.methodName} in the $pass shell: ${failure.message}", failure)
                }
            }
            MultipleFailureException.assertEmpty(failures)
            if (skipped.size == Mode.entries.size) throw skipped.first()
        }
    }
}
