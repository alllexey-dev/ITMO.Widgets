package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/**
 * Common code names text through `UiText.Res`/`UiText.Plural` and icons through `AppIcon`, never an Android `R` id
 * (ADR 0028, recipe uitext-migration). `UiText.Resource` stays only for unported Android presentation.
 */
class UiTextRulesTest {

    @Test
    fun `common code references no Android R`() {
        commonMainFiles().assertFalse { file ->
            file.imports.any { it.name.endsWith(".R") || ".R." in it.name } || ANDROID_R.containsMatchIn(file.text)
        }
    }

    @Test
    fun `common code builds no UiText Resource`() {
        commonMainFiles().assertFalse { file -> "UiText.Resource(" in file.text }
    }

    private fun commonMainFiles(): List<KoFileDeclaration> = productionFiles
        .filter { "/src/commonMain/" in it.projectPath }
        .requireAtLeast(MIN_COMMON_MAIN_FILES, "commonMain files")

    private companion object {
        const val MIN_COMMON_MAIN_FILES = 100

        /** `R.string.x`, `R.plurals.x`, `R.drawable.x` and the like; `Res.string.x` is the CMP accessor. */
        val ANDROID_R = Regex("""(?<![\w.])R\.(string|plurals|drawable|color|dimen|layout|id|style|attr|raw|xml)\.""")
    }
}
