package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/**
 * Common code names text through `UiText.Res`/`UiText.Plural` and icons through `AppIcon`, never an Android `R` id
 * (ADR 0028). Outside Compose UI a text is a catalog key of the generated `<Area>Strings`/`<Area>Plurals`, never a CMP
 * resource (ADR 0031); iOS code resolves it through the app's string tables (ADR 0035).
 */
class UiTextRulesTest {

    @Test
    fun `common code references no Android R`() {
        commonMainFiles().assertFalse { file ->
            file.imports.any { it.name.endsWith(".R") || ".R." in it.name } || ANDROID_R.containsMatchIn(file.text)
        }
    }

    @Test
    fun `core, ios and non-UI code import no Compose resources and no Compose resolver`() {
        // The CMP Res, its accessors and the resolvers live in :shared:compose-strings and the screens; core, data,
        // domain, presentation and iOS code stay compilable for SwiftUI without them.
        val violations = productionFiles
            .filter { it.projectPath.startsWith(CORE_MODULE) || it.isNonUi() || it.isIosOutsideComposeKit() }
            .requireAtLeast(MIN_NON_UI_FILES, "core and non-UI files")
            .associate { file -> file.qualifiedKey to file.imports.map { it.name }.filter(::isComposeText) }
            .filterValues { it.isNotEmpty() }
            .mapValues { (_, imports) -> "imports ${imports.joinToString()}" }
        assertNoViolations("text without Compose resources outside the UI", violations)
    }

    private fun KoFileDeclaration.isNonUi(): Boolean = NON_UI_PACKAGE.containsMatchIn(packagee?.name.orEmpty())

    private fun isComposeText(import: String): Boolean =
        import.startsWith(COMPOSE_RESOURCES) || GENERATED_RES.matches(import) || import in COMPOSE_RESOLVERS

    private fun commonMainFiles(): List<KoFileDeclaration> = productionFiles
        .filter { "/src/commonMain/" in it.projectPath }
        .requireAtLeast(MIN_COMMON_MAIN_FILES, "commonMain files")

    private companion object {
        const val MIN_COMMON_MAIN_FILES = 100
        const val MIN_NON_UI_FILES = 400
        const val CORE_MODULE = "/shared/core/"
        const val COMPOSE_RESOURCES = "org.jetbrains.compose.resources."

        /** A `data`, `domain` or `presentation` package of any module. */
        val NON_UI_PACKAGE = Regex("""\.(data|domain|presentation)(\.|$)""")

        /** A module's CMP `Res` or one of its accessors; the key objects (`CoreStrings`) start with a capital. */
        val GENERATED_RES = Regex("""dev\.alllexey\.itmowidgets\.shared\.[a-z.]+\.(Res|[a-z]\w*)""")

        /** `:shared:compose-strings`' resolvers, which load the CMP resources. */
        val COMPOSE_RESOLVERS = setOf(
            "dev.alllexey.itmowidgets.core.text.asString",
            "dev.alllexey.itmowidgets.core.text.resolve"
        )

        /** `R.string.x`, `R.plurals.x`, `R.drawable.x` and the like; `Res.string.x` is the CMP accessor. */
        val ANDROID_R = Regex("""(?<![\w.])R\.(string|plurals|drawable|color|dimen|layout|id|style|attr|raw|xml)\.""")
    }
}
