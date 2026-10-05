package dev.alllexey.itmowidgets.buildlogic.strings

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Android copies of `composeResources` string files and drawables (ADR 0028): `:app` merges them into its own `res`,
 * so `dev.alllexey.itmowidgets.R.string.<id>`, `R.drawable.<id>` and XML `@string/<id>`, `@drawable/<id>` keep
 * working under `android.nonTransitiveRClass`. A string copy is named `<module>_<file>` (`core_strings_common.xml`)
 * so it never clashes with a same-named file that is still in `app/src/main/res`; a drawable keeps its name, which
 * is its resource id.
 */
object AndroidStringsExport {

    /**
     * `values/strings_platform.xml` of `designsystem` -> `values/designsystem_strings_platform.xml`; a resource file
     * name allows no `-`, so `feature-qr` gives `feature_qr_strings_qr.xml`. `drawable/ic_qr.xml` stays as it is.
     */
    fun exportedPath(module: String, path: String): String {
        if (DRAWABLE.matches(path)) return path
        requireExportable(path)
        return path.substringBeforeLast('/') + "/" + module.replace('-', '_') + "_" + path.substringAfterLast('/')
    }

    /** Every `drawable/<id>.xml` of [resourcesDir], sorted; fails when there is none. */
    fun drawablePaths(resourcesDir: File): List<String> {
        val files = File(resourcesDir, DRAWABLE_DIR).listFiles { file -> file.isFile }.orEmpty()
            .map { "$DRAWABLE_DIR/${it.name}" }
            .sorted()
        if (files.isEmpty()) throw GradleException("androidExportDrawables(): no file in $resourcesDir/$DRAWABLE_DIR")
        val invalid = files.filterNot { DRAWABLE.matches(it) }
        if (invalid.isNotEmpty()) throw GradleException("androidExportDrawables(): not <lower_snake>.xml: $invalid")
        return files
    }

    /** Fails unless [path] is a `values[-<qualifier>]/strings*.xml` file relative to `composeResources`. */
    fun requireExportable(path: String) = require(EXPORTABLE.matches(path)) {
        "androidExport(\"$path\"): expected values[-<qualifier>]/strings_<name>.xml under composeResources"
    }

    /** [source] with a "generated from" comment after the XML declaration, if any. */
    fun render(generatedFrom: String, source: String): String {
        val header = "<!-- Generated from $generatedFrom, do not edit. -->\n"
        val prolog = PROLOG.find(source)
        return if (prolog == null) header + source else source.substring(0, prolog.range.last + 1) + header +
            source.substring(prolog.range.last + 1)
    }

    /**
     * Writes the [paths] of [resourcesDir] into [outputDir]; [sourcePrefix] is the repository-relative path of
     * [resourcesDir] for the header.
     */
    fun export(module: String, resourcesDir: File, sourcePrefix: String, paths: List<String>, outputDir: File) {
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        paths.forEach { path ->
            val source = File(resourcesDir, path)
            if (!source.isFile) throw GradleException("androidExport(\"$path\"): no file $sourcePrefix/$path")
            val target = File(outputDir, exportedPath(module, path))
            target.parentFile.mkdirs()
            target.writeText(render("$sourcePrefix/$path", source.readText()))
        }
    }

    /** Copies the exported trees of every module into one generated `res` directory of `:app`. */
    fun merge(exportedDirs: List<File>, outputDir: File) {
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        val owners = mutableMapOf<String, File>()
        exportedDirs.filter { it.isDirectory }.sortedBy { it.invariantSeparatorsPath }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile }.sortedBy { it.invariantSeparatorsPath }.forEach { file ->
                val relative = file.relativeTo(dir).invariantSeparatorsPath
                val previous = owners.put(relative, file)
                if (previous != null) throw GradleException("Two modules export $relative: $previous and $file")
                file.copyTo(File(outputDir, relative))
            }
        }
    }

    const val DRAWABLE_DIR = "drawable"
    private val DRAWABLE = Regex("""$DRAWABLE_DIR/[a-z][a-z0-9_]*\.xml""")
    private val EXPORTABLE = Regex("""values(-[A-Za-z0-9+-]+)?/strings[A-Za-z0-9_]*\.xml""")
    private val PROLOG = Regex("""\A<\?xml[^>]*\?>\r?\n?""")
}

/** Producer side: a shared module's declared `androidExport` files and drawables, ready for `:app`. */
abstract class ExportAndroidStrings : DefaultTask() {

    @get:Input
    abstract val module: Property<String>

    @get:Input
    abstract val paths: ListProperty<String>

    @get:Input
    abstract val drawables: Property<Boolean>

    @get:Input
    abstract val sourcePrefix: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val resourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun export() {
        val resources = resourcesDir.get().asFile
        val drawablePaths = if (drawables.get()) AndroidStringsExport.drawablePaths(resources) else emptyList()
        AndroidStringsExport.export(
            module.get(),
            resources,
            sourcePrefix.get(),
            paths.get() + drawablePaths,
            outputDir.get().asFile,
        )
    }
}

/** Consumer side: one generated `res` directory of `:app` with the exports of its project dependencies. */
abstract class CollectAndroidStrings : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val exported: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun collect() = AndroidStringsExport.merge(exported.files.toList(), outputDir.get().asFile)
}
