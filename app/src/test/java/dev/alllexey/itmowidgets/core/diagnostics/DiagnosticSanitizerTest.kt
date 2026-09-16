package dev.alllexey.itmowidgets.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSanitizerTest {

    private val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJpc3UiOjUwMjU4N30.c2lnbmF0dXJlLXNpZ25hdHVyZQ"

    @Test
    fun `bearer headers, jwts and named secrets are redacted`() {
        val text = "Authorization: Bearer $jwt; refresh_token=abc123&code=xyz; token: \"$jwt\""

        val sanitized = DiagnosticSanitizer.sanitize(text)

        assertFalse(sanitized.contains(jwt))
        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("xyz"))
        assertTrue(sanitized.contains("[redacted]"))
    }

    @Test
    fun `oversized messages keep only their head`() {
        val sanitized = DiagnosticSanitizer.sanitize("x".repeat(DiagnosticSanitizer.MAX_MESSAGE_LENGTH + 500))

        assertEquals(DiagnosticSanitizer.MAX_MESSAGE_LENGTH + 1, sanitized.length)
        assertTrue(sanitized.endsWith("…"))
    }

    @Test
    fun `ordinary messages are untouched`() {
        val text = "HTTP 403 GET /api/users/me/data after 1200 ms"

        assertEquals(text, DiagnosticSanitizer.sanitize(text))
    }

    @Test
    fun `stack traces keep the cause chain, bound frames and sanitize messages`() {
        val cause = IllegalStateException("token=$jwt")
        val error = RuntimeException("outer", cause)

        val trace = DiagnosticSanitizer.stackTrace(error)

        assertTrue(trace.startsWith("java.lang.RuntimeException: outer"))
        assertTrue(trace.contains("Caused by: java.lang.IllegalStateException: token=[redacted]"))
        assertFalse(trace.contains(jwt))
        val frames = trace.lines().count { it.startsWith("\tat ") }
        assertTrue(frames <= 2 * DiagnosticSanitizer.MAX_STACK_FRAMES)
    }
}
