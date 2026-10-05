package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/**
 * Compose Multiplatform screens (master plan 3.1, ADR 0020) and the design-system boundary (ADR 0021, DS-02c's
 * hand-in). `@Preview` functions and files under a `preview/` directory are exempt from every literal rule: long
 * Russian synthetic text and fixed sizes are their purpose. View and RemoteViews code keeps its own `dp` and colour
 * helpers, so the literal rules read only files that import Compose.
 */
class ComposeRulesTest {

    @Test
    fun `screen sheet and dialog composables are stateless`() {
        // Only `<Name>Route` or the Android host obtains the ViewModel and passes state and callbacks down.
        statelessCandidates()
            .requireNonEmpty("shared Screen, SheetContent and Dialog composables")
            .assertFalse { composable ->
                composable.parameters.any { parameter ->
                    val type = parameter.type.name
                    type.endsWith(VIEW_MODEL) || type == CONTEXT
                } || VIEW_MODEL_LOOKUP.containsMatchIn(composable.text) || LOCAL_CONTEXT in composable.text
            }
    }

    @Test
    fun `every public screen and sheet content has a preview in its module`() {
        // DS-02b's harness renders every commonMain @Preview, so a preview is also the screen's screenshot test.
        val screens = statelessCandidates().filter { it.hasPublicOrDefaultModifier && SCREEN.matches(it.name) }
        if (sharedFeatureUiFiles().isNotEmpty()) screens.requireNonEmpty("public shared screens")
        if (screens.isEmpty()) return
        val previewsByModule = sharedFiles()
            .flatMap { file -> file.composables().filter { it.isPreview }.map { file.moduleDirectory to it.text } }
            .groupBy({ it.first }, { it.second })
        screens.assertTrue { screen ->
            val call = Regex("""\b${screen.name}\b""")
            previewsByModule[screen.containingFile.moduleDirectory].orEmpty().any(call::containsMatchIn)
        }
    }

    @Test
    fun `compose code takes colours font sizes and spacing from the design system`() {
        composeFilesOutsideDesignSystem().assertFalse { file ->
            val code = file.withoutPreviews()
            RAW_COLOR.containsMatchIn(code) || RAW_FONT_SIZE.any { it.containsMatchIn(code) } ||
                RAW_SPACING.containsMatchIn(code)
        }
    }

    @Test
    fun `compose code keeps Russian text in resources`() {
        composeFilesOutsideDesignSystem().assertFalse { file ->
            RUSSIAN_LITERAL.containsMatchIn(file.withoutPreviews())
        }
    }

    @Test
    fun `only the design system opts in to the expressive api`() {
        productionFiles
            .filterNot { it.projectPath.startsWith(DESIGN_SYSTEM) }
            .requireAtLeast(MIN_DOMAIN_FILES, "files outside the design system")
            .assertFalse { file -> EXPRESSIVE_API in file.text }
    }

    /** `<Name>Screen`, `<Name>SheetContent` and `<Name>Dialog` composables of every shared module. */
    private fun statelessCandidates(): List<KoFunctionDeclaration> = sharedFiles()
        .flatMap { it.composables() }
        .filter { !it.isPreview && STATELESS.matches(it.name) }

    /** Once a shared feature has a `ui` file (LH-3's QR pass screen is the first), its screens must be in scope. */
    private fun sharedFeatureUiFiles(): List<KoFileDeclaration> =
        productionFiles.filter { SHARED_FEATURE_UI_FILE.matches(it.projectPath) }

    private fun sharedFiles(): List<KoFileDeclaration> =
        productionFiles.filter { it.projectPath.startsWith("/shared/") }

    private fun composeFilesOutsideDesignSystem(): List<KoFileDeclaration> = productionFiles
        .filter { file -> file.imports.any { it.name.startsWith(COMPOSE) } }
        .filterNot { it.projectPath.startsWith(DESIGN_SYSTEM) || PREVIEW_DIRECTORY in it.projectPath }
        .requireNonEmpty("Compose files outside the design system")

    private fun KoFileDeclaration.composables(): List<KoFunctionDeclaration> =
        functions(includeNested = true, includeLocal = false).filter { it.hasAnnotationWithName(COMPOSABLE) }

    private val KoFunctionDeclaration.isPreview: Boolean get() = hasAnnotationWithName(PREVIEW)

    /** The file's text without its `@Preview` functions. */
    private fun KoFileDeclaration.withoutPreviews(): String =
        functions(includeNested = true, includeLocal = false)
            .filter { it.isPreview }
            .fold(text) { code, preview -> code.replace(preview.text, "") }

    /** `/shared/feature-qr` for `/shared/feature-qr/src/commonMain/...`. */
    private val KoFileDeclaration.moduleDirectory: String get() = projectPath.substringBefore("/src/")

    private companion object {
        const val COMPOSE = "androidx.compose."
        const val COMPOSABLE = "Composable"
        const val PREVIEW = "Preview"
        const val VIEW_MODEL = "ViewModel"
        const val CONTEXT = "Context"
        const val LOCAL_CONTEXT = "LocalContext"
        const val DESIGN_SYSTEM = "/shared/designsystem/"
        const val PREVIEW_DIRECTORY = "/preview/"
        const val EXPRESSIVE_API = "ExperimentalMaterial3ExpressiveApi"

        val STATELESS = Regex("""[A-Z]\w*(Screen|SheetContent|Dialog)""")
        val SCREEN = Regex("""[A-Z]\w*(Screen|SheetContent)""")
        val SHARED_FEATURE_UI_FILE = Regex("""/shared/feature-[\w-]+/src/commonMain/.*/ui/[^/]+\.kt""")

        /** `koinViewModel()`, `viewModel()` and their reified forms. */
        val VIEW_MODEL_LOOKUP = Regex("""\b(koinViewModel|viewModel)\s*[<(]""")

        val RAW_COLOR = Regex("""\bColor\(\s*0x""")
        val RAW_FONT_SIZE = listOf(
            Regex("""\bTextStyle\([^)]*\bfontSize\s*="""),
            Regex("""(?<![\w.])\d+(\.\d+)?f?\.sp\b""")
        )

        /** A literal `.dp` inside `padding(`, `spacedBy(` or `Spacer(`; sizes (`size(240.dp)`) stay legal. */
        val RAW_SPACING = Regex("""\b(padding|spacedBy|Spacer)\([^)]*?(?<![\w.])\d+(\.\d+)?f?\.dp\b""")

        val RUSSIAN_LITERAL = Regex("\"[^\"\\n]*[\\u0400-\\u04FF][^\"\\n]*\"")
    }
}
