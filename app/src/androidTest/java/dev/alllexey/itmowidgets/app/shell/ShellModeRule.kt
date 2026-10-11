package dev.alllexey.itmowidgets.app.shell

import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Runs a test body of `MainActivity`'s navigation and names the shell in a failure ("in the NAV3 shell"), the form
 * `scripts/ui-report.py` reads. `MainActivity` has one shell, the Navigation 3 one; the body reads the screen through
 * `ShellProbe`.
 */
class ShellModeRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } catch (assumption: AssumptionViolatedException) {
                throw assumption
            } catch (failure: Throwable) {
                throw AssertionError("${description.methodName} in the $SHELL shell: ${failure.message}", failure)
            }
        }
    }

    private companion object {
        const val SHELL = "NAV3"
    }
}
