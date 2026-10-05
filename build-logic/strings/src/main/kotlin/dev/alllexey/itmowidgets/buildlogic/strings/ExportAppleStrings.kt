package dev.alllexey.itmowidgets.buildlogic.strings

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/** Writes the committed `iosApp/Shared` files of [AppleExport]; run it after changing a catalog file. */
@UntrackedTask(because = "It writes committed files under iosApp/Shared, which checkStringCatalog compares")
abstract class ExportAppleStrings : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appleTables: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val iconRegistry: RegularFileProperty

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    /** `iosApp/Shared`; other files there (asset catalogs, Swift sources) stay untouched. */
    @get:Internal
    abstract val sharedDir: DirectoryProperty

    @TaskAction
    fun export() {
        val catalog = CatalogFile.parseAll(catalogFiles.files, repositoryRoot.get().asFile)
        val outputs = AppleExport.render(
            catalog,
            appleTables.get().asFile.readLines(),
            iconRegistry.get().asFile.readLines(),
        )
        AppleExport.write(outputs, sharedDir.get().asFile)
    }
}
