package dev.alllexey.itmowidgets.app.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

/**
 * Runs a test body, with its `@Before` and `@After`, once per shell `MainActivity` can run, and names the shell in a
 * failure. Each pass writes its mode to the debug override [ShellMode.overrideFile] before the body launches the
 * activity and deletes the file afterwards, so a test outside the rule runs [ShellMode.DEFAULT]. The body reads the
 * screen through `ShellProbe`, so the same assertions hold in both.
 */
class ShellModeRule : TestRule {

    enum class Mode(val shell: ShellMode) {
        LEGACY(ShellMode.LEGACY),
        NAV3(ShellMode.NAV3),
    }

    /** The shell of the pass that runs now. */
    var mode: Mode = Mode.LEGACY
        private set

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val failures = mutableListOf<Throwable>()
            val skipped = mutableListOf<AssumptionViolatedException>()
            val file = ShellMode.overrideFile(ApplicationProvider.getApplicationContext<Context>())
            Mode.entries.forEach { pass ->
                mode = pass
                file.parentFile?.mkdirs()
                file.writeText(pass.shell.name)
                try {
                    base.evaluate()
                } catch (assumption: AssumptionViolatedException) {
                    skipped += assumption
                } catch (failure: Throwable) {
                    failures += AssertionError("${description.methodName} in the $pass shell: ${failure.message}", failure)
                } finally {
                    file.delete()
                }
            }
            MultipleFailureException.assertEmpty(failures)
            if (skipped.size == Mode.entries.size) throw skipped.first()
        }
    }
}
