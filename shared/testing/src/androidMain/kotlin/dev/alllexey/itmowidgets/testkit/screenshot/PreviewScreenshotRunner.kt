package dev.alllexey.itmowidgets.testkit.screenshot

import org.junit.runner.Runner
import org.junit.runners.Suite
import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.internal.SandboxTestRunner

/**
 * Runs a [PreviewScreenshotTest] subclass as one Robolectric test per [PreviewCase] plus a `baselines` test. The
 * cases come from the subclass itself (its package or [PreviewScreenshots]), which a JUnit `@Parameters` method
 * declared once in the base class could not see. Each instance learns its case by name, since the sandbox loads its
 * own copy of every class (as `ParameterizedRobolectricTestRunner` recomputes its parameters there).
 */
class PreviewScreenshotRunner(testClass: Class<*>) : Suite(testClass, runners(testClass)) {

    private class CaseRunner(testClass: Class<*>, private val caseName: String?) : RobolectricTestRunner(testClass) {

        override fun getName(): String = caseName ?: BASELINES

        override fun testName(method: FrameworkMethod): String =
            if (caseName == null) method.name else "${method.name}[$caseName]"

        override fun getChildren(): List<FrameworkMethod> =
            super.getChildren().filter { (it.name == BASELINES) == (caseName == null) }

        override fun getHelperTestRunner(bootstrappedTestClass: Class<*>): SandboxTestRunner.HelperTestRunner =
            object : HelperTestRunner(bootstrappedTestClass) {
                override fun createTest(): Any = super.createTest().also { test ->
                    test.javaClass.getMethod(BIND, String::class.java).invoke(test, caseName)
                }
            }
    }

    private companion object {
        const val BASELINES = "baselines"
        const val BIND = "bindCase"

        fun runners(testClass: Class<*>): List<Runner> =
            PreviewSuite.of(testClass).cases.map { CaseRunner(testClass, it.name) } + CaseRunner(testClass, null)
    }
}
