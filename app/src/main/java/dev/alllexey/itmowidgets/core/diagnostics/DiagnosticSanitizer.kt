package dev.alllexey.itmowidgets.core.diagnostics

/**
 * Strips credentials from free text before it reaches the journal. The journal is
 * the one place where an exception message can be copied out of the app, so the
 * filter is deliberately broad: a false positive costs a few characters of
 * context, a false negative leaks a session.
 */
object DiagnosticSanitizer {

    const val MAX_STACK_FRAMES = 40
    /** Platform messages can embed whole theme dumps; the journal keeps the head only. */
    const val MAX_MESSAGE_LENGTH = 1_000
    private const val REDACTED = "[redacted]"

    private val bearer = Regex("""(?i)bearer\s+[A-Za-z0-9\-._~+/]+=*""")
    private val jwt = Regex("""eyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}(\.[A-Za-z0-9_-]*)?""")
    private val namedSecret = Regex(
        """(?i)(refresh_token|access_token|id_token|token|code|password|secret|authorization|cookie)""" +
            """(["']?\s*[:=]\s*["']?)([^\s"'&,;}]+)"""
    )

    fun sanitize(text: String): String = text
        .replace(bearer) { "Bearer $REDACTED" }
        .replace(jwt, REDACTED)
        .replace(namedSecret) { "${it.groupValues[1]}${it.groupValues[2]}$REDACTED" }
        .let { if (it.length > MAX_MESSAGE_LENGTH) it.take(MAX_MESSAGE_LENGTH) + "…" else it }

    /** Cause chain with bounded frames; messages are sanitized, frames carry no data. */
    fun stackTrace(error: Throwable): String = buildString {
        var current: Throwable? = error
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            if (isNotEmpty()) append("Caused by: ")
            append(current.javaClass.name)
            current.message?.let { append(": ").append(sanitize(it)) }
            append('\n')
            val frames = current.stackTrace
            frames.take(MAX_STACK_FRAMES).forEach { append("\tat ").append(it).append('\n') }
            if (frames.size > MAX_STACK_FRAMES) append("\t... ").append(frames.size - MAX_STACK_FRAMES).append(" more\n")
            current = current.cause
        }
    }.trimEnd()
}
