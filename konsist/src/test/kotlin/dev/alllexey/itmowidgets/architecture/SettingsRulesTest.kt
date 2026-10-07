package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Settings (L14): the page model that Android's Compose screen and iOS's SwiftUI `Form`s both render (IO-08a) stays
 * platform-neutral. Its types carry `UiText` and `AppIcon`, never Android, `R`, Compose or a resource int.
 */
class SettingsRulesTest {

    @Test
    fun `the settings page model is free of Android R Compose and resource ints`() {
        modelFiles().assertFalse { file ->
            file.imports.any { import -> FORBIDDEN_IMPORTS.any { import.name.startsWith(it) } || import.name == APP_R } ||
                COMPOSABLE in file.text || RESOURCE_ANNOTATION.containsMatchIn(file.text) ||
                RESOURCE_INT.containsMatchIn(file.text)
        }
    }

    /** The files that declare the model types; asserting their names makes a move or a rename fail loudly. */
    private fun modelFiles(): List<KoFileDeclaration> {
        val files = productionFiles.filter { file ->
            file.packagee?.name == PRESENTATION_PACKAGE &&
                file.declaredTypeNames().any { it in MODEL_TYPES }
        }
        val declared = files.flatMap { it.declaredTypeNames() }.filter { it in MODEL_TYPES }.toSet()
        assertEquals("Settings model types in $PRESENTATION_PACKAGE", MODEL_TYPES, declared)
        return files
    }

    private fun KoFileDeclaration.declaredTypeNames(): List<String> =
        classes().map { it.name } + interfaces().map { it.name } + objects().map { it.name }

    private companion object {
        const val PRESENTATION_PACKAGE = "dev.alllexey.itmowidgets.feature.settings.presentation"
        const val APP_R = "dev.alllexey.itmowidgets.R"
        const val COMPOSABLE = "@Composable"

        /** The types of the L14 invariant (lane file, "Invariants"). */
        val MODEL_TYPES = setOf("SettingItem", "SettingSection", "SettingsPage", "SettingRowId", "SettingsEvent")

        val FORBIDDEN_IMPORTS = listOf("android.", "androidx.compose.", "androidx.annotation.")

        /** `@StringRes`, `@DrawableRes`, `@PluralsRes`, ... */
        val RESOURCE_ANNOTATION = Regex("""@\w+Res\b""")

        /** A property or parameter that holds a resource id: `titleRes: Int`, `iconResId: Int`. */
        val RESOURCE_INT = Regex("""\b\w*Res(Id)?\s*:\s*Int\b""")
    }
}
