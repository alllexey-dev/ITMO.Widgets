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
 * - A shared module publishes the declared files (and its drawables after `androidExportDrawables()`) as its
 *   `itmowidgetsAndroidStringsElements` variant.
 * - `:app` runs `checkStringCatalog` before every build (`preBuild`) and merges the exports of its project
 *   dependencies into a generated `res` directory of each variant.
 * - `:app:exportAppleStrings` writes the committed `iosApp/Shared` tables and `AppSymbol.swift` ([AppleExport]).
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
            description = "Android copies of the composeResources files named by androidExport(...) and of the " +
                "drawables after androidExportDrawables()."
            module.set(project.name)
            paths.set(extension.androidExports)
            drawables.set(extension.androidDrawables)
            sourcePrefix.set(resources.asFile.relativeTo(root).invariantSeparatorsPath)
            resourcesDir.set(resources)
            sources.from(extension.androidExports.map { list -> list.map { resources.file(it) } })
            sources.from(
                extension.androidDrawables.map { export ->
                    if (export) listOf(resources.dir(AndroidStringsExport.DRAWABLE_DIR)) else emptyList()
                },
            )
            outputDir.set(layout.buildDirectory.dir("generated/itmowidgets/androidStrings"))
        }
        configurations.consumable(ELEMENTS) {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(USAGE))
            outgoing.artifact(export.flatMap { it.outputDir })
        }
    }

    private fun Project.registerCatalogCheck() {
        val root = isolated.rootProject.projectDirectory
        val catalog = files(
            layout.projectDirectory.dir("src/main/res/values").asFileTree.matching { include("strings_*.xml") },
            root.dir("shared").asFileTree.matching { include("*/$COMPOSE_RESOURCES/values*/strings*.xml") },
            root.dir("iosApp/Strings").asFileTree.matching { include("strings_ios*.xml") },
        )
        val shared = root.dir(APPLE_SHARED)
        val export = tasks.register<ExportAppleStrings>("exportAppleStrings") {
            group = "build"
            description = "Writes the committed iosApp/Shared string tables and AppSymbol.swift (ADR 0028)."
            catalogFiles.from(catalog)
            appleTables.set(root.file(AppleExport.TABLES_FILE))
            iconRegistry.set(root.file(AppleExport.ICONS_FILE))
            repositoryRoot.set(root)
            sharedDir.set(shared)
        }
        val check = tasks.register<CheckStringCatalog>("checkStringCatalog") {
            group = "verification"
            description = "Checks every string catalog file against the ADR 0028 rules and the Apple export."
            catalogFiles.from(catalog)
            frozenKeys.set(root.file(StringCatalogRules.FROZEN_KEYS))
            appleTables.set(root.file(AppleExport.TABLES_FILE))
            iconRegistry.set(root.file(AppleExport.ICONS_FILE))
            appleOutputs.from(
                shared.asFileTree.matching {
                    include("${AppleExport.STRINGS_DIR}/*.xcstrings", AppleExport.SYMBOLS_FILE)
                },
            )
            sharedDir.set(shared)
            repositoryRoot.set(root)
            report.set(layout.buildDirectory.file("reports/checkStringCatalog/files.txt"))
            mustRunAfter(export)
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
                description = "Merges the Android exports of the project dependencies into a generated res dir."
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
        const val APPLE_SHARED = "iosApp/Shared"
    }
}
