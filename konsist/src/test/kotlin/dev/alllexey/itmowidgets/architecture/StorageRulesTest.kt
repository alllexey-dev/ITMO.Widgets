package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

class StorageRulesTest {

    @Test
    fun `session-cleared repositories are singletons`() {
        // Sign-out must clear the instance every screen reads: each such repository is a Koin single.
        val koinSingles = productionFiles
            .flatMap { file -> KOIN_SINGLE.findAll(file.text).map { it.groupValues[1] }.toList() }
            .toSet()
        productionClasses
            .filter { it.name.endsWith("RepositoryImpl") && it.hasParentWithName("SessionDataCleaner") }
            .requireNonEmpty("session-cleared repositories")
            .assertTrue { it.name in koinSingles }
    }

    @Test
    fun `production code stores preferences in DataStore, not SharedPreferences`() {
        productionFiles.requireNonEmpty("production files").assertFalse { file ->
            "SharedPreferences" in file.text ||
                "PreferenceManager" in file.text ||
                "SharedPreferencesMigration" in file.text
        }
    }

    private companion object {
        val KOIN_SINGLE = Regex("""\bsingleOf\(::(\w+)\)""")
    }
}
