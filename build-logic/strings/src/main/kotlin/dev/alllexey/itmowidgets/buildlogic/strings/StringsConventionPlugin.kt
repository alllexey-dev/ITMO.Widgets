package dev.alllexey.itmowidgets.buildlogic.strings

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Usage
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * The string catalog (ADR 0028). itmowidgets.android.app and itmowidgets.cmp.ui apply it, so a module never adds a
 * line for it.
 *
 * - Every module gets the `itmowidgetsStrings { androidExport(...) }` extension.
 * - A shared module publishes the declared files as its `itmowidgetsAndroidStringsElements` variant.
 * - `:app` runs `checkStringCatalog` before every build (`preBuild`) and merges the exports of its project
 *   dependencies into a generated `res` directory of each variant.
 */
class StringsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val extension = extensions.create<StringsExtension>(EXTENSION)
        pluginManager.withPlugin("com.android.kotlin.multiplatform.library") { publishAndroidExports(extension) }
        pluginManager.withPlugin("com.android.application") {
            registerCatalogCheck()
            consumeAndroidExports()
        }
    }

    private fun Project.publishAndroidExports(extension: StringsExtension) {
        val root = isolated.rootProject.projectDirectory.asFile
        val resources = layout.projectDirectory.dir(COMPOSE_RESOURCES)
        val export = tasks.register<ExportAndroidStrings>("exportAndroidStrings") {
            description = "Android copies of the composeResources string files named by androidExport(...)."
            module.set(project.name)
            paths.set(extension.androidExports)
            sourcePrefix.set(resources.asFile.relativeTo(root).invariantSeparatorsPath)
            resourcesDir.set(resources)
            sources.from(extension.androidExports.map { list -> list.map { resources.file(it) } })
            outputDir.set(layout.buildDirectory.dir("generated/itmowidgets/androidStrings"))
        }
        configurations.consumable(ELEMENTS) {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(USAGE))
            outgoing.artifact(export.flatMap { it.outputDir })
        }
    }

    private fun Project.registerCatalogCheck() {
        val root = isolated.rootProject.projectDirectory
        val check = tasks.register<CheckStringCatalog>("checkStringCatalog") {
            group = "verification"
            description = "Checks every string catalog file against the ADR 0028 rules."
            catalogFiles.from(
                layout.projectDirectory.dir("src/main/res/values").asFileTree.matching { include("strings_*.xml") },
                root.dir("shared").asFileTree.matching { include("*/$COMPOSE_RESOURCES/values*/strings*.xml") },
                root.dir("iosApp/Strings").asFileTree.matching { include("strings_ios*.xml") },
            )
            frozenKeys.set(root.file(StringCatalogRules.FROZEN_KEYS))
            repositoryRoot.set(root)
            report.set(layout.buildDirectory.file("reports/checkStringCatalog/files.txt"))
        }
        tasks.named { it == "preBuild" }.configureEach { dependsOn(check) }
    }

    private fun Project.consumeAndroidExports() {
        extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
            // Reselects the strings variant of every project in the runtime graph; external modules and modules
            // without this plugin have none and drop out.
            val exported = variant.runtimeConfiguration.incoming.artifactView {
                withVariantReselection()
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(USAGE))
                componentFilter { it is ProjectComponentIdentifier }
                lenient(true)
            }.files
            val name = "collect${variant.name.replaceFirstChar { it.uppercase() }}AndroidStrings"
            val collect = tasks.register<CollectAndroidStrings>(name) {
                description = "Merges the androidExport files of the project dependencies into a generated res dir."
                this.exported.from(exported)
                outputDir.set(layout.buildDirectory.dir("generated/itmowidgets/androidStrings/${variant.name}"))
            }
            variant.sources.res?.addGeneratedSourceDirectory(collect, CollectAndroidStrings::outputDir)
        }
    }

    private companion object {
        const val EXTENSION = "itmowidgetsStrings"
        const val ELEMENTS = "itmowidgetsAndroidStringsElements"
        const val USAGE = "itmowidgets-android-strings"
        const val COMPOSE_RESOURCES = "src/commonMain/composeResources"
    }
}
