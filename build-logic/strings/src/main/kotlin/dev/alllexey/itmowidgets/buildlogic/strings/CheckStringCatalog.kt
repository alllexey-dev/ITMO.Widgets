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
 * modules' `composeResources` and L18's Apple-only `iosApp/Strings/strings_ios*.xml`. Then compares the committed
 * `iosApp/Shared` files with a fresh [AppleExport].
 */
abstract class CheckStringCatalog : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val frozenKeys: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appleTables: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val iconRegistry: RegularFileProperty

    /** The committed tables and `AppSymbol.swift`; a hand edit reruns the check. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appleOutputs: ConfigurableFileCollection

    @get:Internal
    abstract val sharedDir: DirectoryProperty

    /** Only shortens paths in messages. */
    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    /** Lists the checked files; lets Gradle skip the task while no catalog file changes. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val root = repositoryRoot.get().asFile
        val files = CatalogFile.parseAll(catalogFiles.files, root)
        val frozen = frozenKeys.get().asFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        fail(StringCatalogRules.check(files, frozen), "")
        val shared = sharedDir.get().asFile
        val outputs = AppleExport.render(
            files,
            appleTables.get().asFile.readLines(),
            iconRegistry.get().asFile.readLines(),
        )
        fail(
            AppleExport.staleOutputs(outputs, shared, shared.relativeTo(root).invariantSeparatorsPath),
            "\nRun `${AppleExport.COMMAND}` and commit the result; never edit these files by hand.",
        )
        report.get().asFile.writeText(files.joinToString("\n", postfix = "\n") { it.path })
    }

    private fun fail(violations: List<String>, hint: String) {
        if (violations.isEmpty()) return
        throw GradleException(
            "String catalog check failed (ADR 0028):\n" + violations.joinToString("\n") { "  - $it" } + hint,
        )
    }
}
