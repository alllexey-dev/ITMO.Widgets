package dev.alllexey.itmowidgets.buildlogic.strings

import org.gradle.api.provider.ListProperty

/**
 * `itmowidgetsStrings { }` in a module build file. A shared module lists the `composeResources` string files that
 * `:app` still needs as Android resources, one line each:
 *
 * ```
 * itmowidgetsStrings {
 *     androidExport("values/strings_common.xml")
 * }
 * ```
 */
abstract class StringsExtension {
    abstract val androidExports: ListProperty<String>

    /** [path] is relative to `src/commonMain/composeResources`. */
    fun androidExport(path: String) {
        AndroidStringsExport.requireExportable(path)
        androidExports.add(path)
    }
}
