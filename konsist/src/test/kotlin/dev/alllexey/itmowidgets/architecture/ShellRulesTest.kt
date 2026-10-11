package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** The Navigation 3 shell (L17): one Compose shell in `:app`, no Fragment host left beside it. */
class ShellRulesTest {

    @Test
    fun `the app's main sources use no Fragment or Fragment navigation`() {
        // SH-1d deleted every Fragment, sheet and dialog host and the nav graphs; screens are shell entries now.
        appMainFiles
            .requireNonEmpty("app main files")
            .assertFalse { file -> file.imports.any { import -> FRAGMENT_IMPORTS.any { import.name.startsWith(it) } } }
    }

    @Test
    fun `NavDisplay and entry registration live only in the shell`() {
        val navigating = productionFiles
            .filter { it.projectPath.startsWith(APP_SOURCES) }
            .filter { file -> NAV_DISPLAY_CALL.containsMatchIn(file.text) || REGISTRATION in file.text }
        navigating
            .filter { it.inShell }
            .requireNonEmpty("shell files with NavDisplay or entry registrations")
        navigating.assertTrue { it.inShell }
    }

    @Test
    fun `every route is registered by a shell entry`() {
        // A route without a registration would show the shell's placeholder; each lane's <Tab>Entries.kt and the
        // shell's own host entries name their keys as entry<...> type arguments.
        val registered = appMainFiles
            .filter { it.inShell }
            .flatMap { file -> ENTRY_REGISTRATION.findAll(file.text).map { it.groupValues[1].substringAfterLast('.') } }
            .toSet()
        productionFiles
            .flatMap { file ->
                file.classes(includeNested = true, includeLocal = false).filter { it.hasParentWithName(APP_ROUTE) } +
                    file.objects(includeNested = true).filter { it.hasParentWithName(APP_ROUTE) }
            }
            .requireNonEmpty("AppRoute implementations")
            .assertTrue { it.name in registered }
    }

    private val KoFileDeclaration.inShell: Boolean get() = projectPath.startsWith(SHELL_SOURCES)

    private val appMainFiles: List<KoFileDeclaration>
        get() = productionFiles.filter { it.projectPath.startsWith(APP_MAIN_SOURCES) }

    private companion object {
        const val APP_SOURCES = "/app/src/"
        const val APP_MAIN_SOURCES = "/app/src/main/"
        const val SHELL_SOURCES = "/app/src/main/java/dev/alllexey/itmowidgets/app/shell/"
        const val APP_ROUTE = "AppRoute"
        const val REGISTRATION = "EntryRegistry"
        val NAV_DISPLAY_CALL = Regex("""\bNavDisplay\(""")
        val ENTRY_REGISTRATION = Regex("""\bentry<([\w.]+)>""")
        val FRAGMENT_IMPORTS = listOf("androidx.fragment.", "androidx.navigation.fragment.", "androidx.navigation.ui.")
    }
}
