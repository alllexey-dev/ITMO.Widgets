package dev.alllexey.itmowidgets.di.bridge

import org.junit.rules.ExternalResource
import org.koin.core.context.stopKoin

/**
 * Stops the global Koin graph before and after a test. Robolectric creates a new Application for every test, but
 * Koin's global context lives as long as the JVM: without this rule the next test would reuse a graph whose
 * `androidContext()` and Hilt instances belong to the previous Application.
 */
class StopKoinRule : ExternalResource() {
    override fun before() {
        stopKoin()
    }

    override fun after() {
        stopKoin()
    }
}
