package dev.alllexey.itmowidgets.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackendOriginTest {

    @Test
    fun takesTheHttpsOriginOfTheBuild() {
        assertEquals("https://dev.widgets.alllexey.dev", BackendOrigin.from("https://dev.widgets.alllexey.dev"))
    }

    @Test
    fun refusesAMissingUnexpandedOrPlainHttpOrigin() {
        listOf(null, "", "$(BACKEND_BASE_URL)", "http://dev.widgets.alllexey.dev", "https:", 42).forEach { value ->
            assertFailsWith<IllegalArgumentException>(value.toString()) { BackendOrigin.from(value) }
        }
    }
}
