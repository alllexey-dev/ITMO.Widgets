package dev.alllexey.itmowidgets.buildlogic.strings

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * `itmowidgetsStrings { }` in a module build file. A shared module lists the `composeResources` string files that
 * `:app` still needs as Android resources, one line each, and whether `:app` still needs its drawables:
 *
 * ```
 * itmowidgetsStrings {
 *     androidExport("values/strings_common.xml")
 *     androidExportDrawables()
 * }
 * ```
 */
abstract class StringsExtension {
    abstract val androidExports: ListProperty<String>

    abstract val androidDrawables: Property<Boolean>

    init {
        androidDrawables.convention(false)
    }

    /** [path] is relative to `src/commonMain/composeResources`. */
    fun androidExport(path: String) {
        AndroidStringsExport.requireExportable(path)
        androidExports.add(path)
    }

    /**
     * Every file of `src/commonMain/composeResources/drawable` under its own name, so `R.drawable.<id>` and
     * `@drawable/<id>` in `:app` keep resolving after an icon moves to `composeResources`.
     */
    fun androidExportDrawables() {
        androidDrawables.set(true)
    }
}
