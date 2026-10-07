package dev.alllexey.itmowidgets.feature.sport.data.golden

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import java.io.File
import org.junit.Assert.assertEquals

/**
 * Golden domain values of the sport data layer, in `src/androidHostTest/resources/sport/golden/`. They were recorded through the
 * MyItmoApi 1.x and Core 1.x path before KM-10c swapped it, so the same files prove the 2.x path keeps every value.
 * `SPORT_GOLDEN_RECORD=1` rewrites them; only a deliberate behaviour change may do that.
 */
object SportGolden {

    fun assertGolden(name: String, vararg values: Pair<String, Any?>) {
        val rendered = values.joinToString("\n\n") { (label, value) -> "# $label\n${render(value)}" }
            .replace(IDENTITY_TO_STRING, "$1") + "\n"
        val file = File("src/androidHostTest/resources/sport/golden/$name.txt")
        if (System.getenv("SPORT_GOLDEN_RECORD") == "1") {
            file.parentFile?.mkdirs()
            file.writeText(rendered)
            return
        }
        check(file.exists()) { "No golden ${file.path}; record it through the 1.x path" }
        assertEquals("Golden $name", file.readText(), rendered)
    }

    /** One element per line; failures by their [AppError] case, never by the cause, which differs between clients. */
    fun render(value: Any?): String = when (value) {
        is AppResult.Success<*> -> render(value.value)
        is AppResult.Failure -> "Failure(${render(value.error)})"
        is LoadState.Content<*> -> render(value.value) + (value.error?.let { "\npartial: ${render(it)}" } ?: "")
        is LoadState.Error -> "Error(${render(value.error)})"
        is AppError -> value::class.simpleName.orEmpty()
        is Map<*, *> -> value.entries.joinToString("\n") { (key, item) -> "$key: ${render(item)}" }
        is Iterable<*> -> value.joinToString("\n") { render(it) }.ifEmpty { "[]" }
        else -> value.toString()
    }

    /** `UnavailableReason$Full@1b2c3d`: an object without its own toString, named by its class instead. */
    private val IDENTITY_TO_STRING = Regex("""[\w.]+\$(\w+)@\p{XDigit}+""")
}
