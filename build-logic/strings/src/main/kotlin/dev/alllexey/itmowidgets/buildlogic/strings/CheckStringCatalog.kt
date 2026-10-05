package dev.alllexey.itmowidgets.buildlogic.strings

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Runs [StringCatalogRules] over every catalog file of the repository: `:app`'s `strings_*.xml`, the shared
 * modules' `composeResources` and L18's Apple-only `iosApp/Strings/strings_ios*.xml`.
 */
abstract class CheckStringCatalog : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val frozenKeys: RegularFileProperty

    /** Only shortens paths in messages. */
    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    /** Lists the checked files; lets Gradle skip the task while no catalog file changes. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val root = repositoryRoot.get().asFile
        val files = catalogFiles.files.sortedBy { it.invariantSeparatorsPath }
            .map { CatalogFile.parse(it.relativeTo(root).invariantSeparatorsPath, it) }
        val frozen = frozenKeys.get().asFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val violations = StringCatalogRules.check(files, frozen)
        if (violations.isNotEmpty()) {
            throw GradleException(
                "String catalog check failed (ADR 0028):\n" + violations.joinToString("\n") { "  - $it" },
            )
        }
        report.get().asFile.writeText(files.joinToString("\n", postfix = "\n") { it.path })
    }
}
