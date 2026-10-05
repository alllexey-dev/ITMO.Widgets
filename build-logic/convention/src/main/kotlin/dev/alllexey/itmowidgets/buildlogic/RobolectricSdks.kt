package dev.alllexey.itmowidgets.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

/**
 * The `itmoTesting` extension of [TestingConventionPlugin]. By default host tests run on the one SDK of
 * `robolectric-android-all`; a module whose tests also name other SDKs in `@Config(sdk = [...])` lists their
 * android-all jars here, e.g. `itmoTesting.extraRobolectricSdks(libs.robolectric.android.all.sdk29)`.
 */
class ItmoTestingExtension internal constructor(
    private val project: Project,
    private val defaultRuntime: Configuration,
) {
    private var depsFile: TaskProvider<RobolectricDepsFile>? = null
    private var extraSdkCount = 0

    /**
     * Adds the android-all jars [jars] (catalog entries) to the SDKs this module's host tests may run on. Gradle
     * resolves each into its own cache, and Robolectric reads every jar in place through a generated
     * `robolectric-deps.properties`, still offline (SP-13a).
     */
    fun extraRobolectricSdks(vararg jars: Provider<MinimalExternalModuleDependency>) {
        val task = depsFile ?: registerDepsFile().also { depsFile = it }
        // One configuration per jar: in one configuration Gradle would resolve the versions of the module to one.
        jars.forEach { jar ->
            val configuration = project.configurations.create("robolectricSdkJar${extraSdkCount++}") {
                isTransitive = false
            }
            project.dependencies.addProvider(configuration.name, jar)
            task.configure { this.jars.from(configuration) }
        }
    }

    private fun registerDepsFile(): TaskProvider<RobolectricDepsFile> {
        val task = project.tasks.register<RobolectricDepsFile>("robolectricDeps") {
            description = "Maps every android-all jar of this module's host tests to its file in the Gradle cache."
            jars.from(defaultRuntime)
            output.set(project.layout.buildDirectory.file("robolectric/robolectric-deps.properties"))
        }
        // Robolectric prefers robolectric-deps.properties over robolectric.dependency.dir.
        project.tasks.withType<Test>().configureEach {
            jvmArgumentProviders.add(RobolectricDepsArgument(task.flatMap { it.output }))
        }
        return task
    }
}

/** Writes `robolectric-deps.properties`: one `org.robolectric:android-all-instrumented:<version>=<jar>` per jar. */
abstract class RobolectricDepsFile : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val jars: ConfigurableFileCollection

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun write() {
        output.get().asFile.writeText(
            jars.files.sortedBy { it.name }.joinToString(separator = "") { jar ->
                val version = jar.name.removePrefix("android-all-instrumented-").removeSuffix(".jar")
                "org.robolectric\\:android-all-instrumented\\:$version=${jar.absolutePath}\n"
            },
        )
    }
}

class RobolectricDepsArgument(
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val file: Provider<RegularFile>,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Drobolectric-deps.properties=${file.get().asFile.absolutePath}")
}
