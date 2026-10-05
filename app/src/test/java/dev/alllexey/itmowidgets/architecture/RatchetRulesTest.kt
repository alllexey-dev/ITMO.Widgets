package dev.alllexey.itmowidgets.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The allowlist files themselves: what each rule reads must be well formed and owned by one lane. */
class RatchetRulesTest {

    @Test
    fun `ratchet lines name a known rule and a key in its owner's file`() {
        assertTrue("No ratchet directory at ${Ratchet.directory}", Ratchet.directory.isDirectory)
        val problems = Ratchet.entries.mapNotNull { entry ->
            val where = "$APP_MODULE/${Ratchet.DIRECTORY}/${entry.file}:${entry.line}"
            when {
                RatchetRule.byId(entry.ruleId) == null -> "$where: unknown rule ${entry.ruleId}"
                !KEY.matches(entry.key) -> "$where: `${entry.key}` is not one fully qualified name"
                Ratchet.fileFor(entry.key) != entry.file ->
                    "$where: ${entry.key} belongs in ${Ratchet.fileFor(entry.key)}"
                else -> null
            }
        }
        assertEquals("Malformed ratchet lines", emptyList<String>(), problems)
    }

    @Test
    fun `ratchet lines are unique`() {
        val duplicates = Ratchet.entries
            .groupBy { it.ruleId to it.key }
            .filterValues { it.size > 1 }
            .keys
        assertEquals("Duplicated ratchet lines", emptySet<Pair<String, String>>(), duplicates)
    }

    @Test
    fun `ratchet files belong to an existing feature core or app`() {
        val features = ArchitectureScope.productionFiles.mapNotNull { featureOf(it.packagee?.name.orEmpty()) }.toSet()
        val owners = features.map { "$it.txt" }.toSet() + "core.txt" + "app.txt"
        val strays = Ratchet.directory.listFiles().orEmpty().map { it.name }.filterNot { it in owners }
        assertEquals("Ratchet files without an owner", emptyList<String>(), strays)
    }

    private companion object {
        val KEY = Regex("""^dev\.alllexey\.itmowidgets(\.\w+)+$""")
    }
}
