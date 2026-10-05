package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** The ViewModel contract of `docs/architecture.md` "Screen state and events" and what presentation may import. */
class PresentationRulesTest {

    @Test
    fun `view models expose one uiState and at most one events flow and declare no state types`() {
        val violations = productionClasses
            .filter { it.name.endsWith("ViewModel") && ": ViewModel" in it.text }
            .requireAtLeast(MIN_VIEW_MODELS, "ViewModel subclasses")
            .associate { it.ratchetKey to shapeViolations(it) }
            .filterValues { it.isNotEmpty() }
            .mapValues { (_, reasons) -> reasons.joinToString("; ") }
        Ratchet.assertOnly(RatchetRule.VIEW_MODEL_SHAPE, violations)
    }

    @Test
    fun `presentation does not import Android resources`() {
        // UiText and AppError carry text; KM-07's UiTextRulesTest covers commonMain once presentation moves there.
        val violations = presentationFiles()
            .filter { file -> file.imports.any { it.name == RESOURCES || it.name.startsWith("$RESOURCES.") } }
            .associate { it.ratchetKey to "imports $RESOURCES" }
        Ratchet.assertOnly(RatchetRule.PRESENTATION_RESOURCES, violations)
    }

    @Test
    fun `presentation imports from androidx only the ViewModel API`() {
        presentationFiles().assertFalse { file ->
            file.imports.any { it.name.startsWith("androidx.") && it.name !in allowedAndroidxImports }
        }
    }

    private fun presentationFiles(): List<KoFileDeclaration> = productionFiles
        .filter { it.packagee?.name?.contains(".presentation") == true }
        .requireAtLeast(MIN_VIEW_MODELS, "presentation files")

    private fun shapeViolations(viewModel: KoClassDeclaration): List<String> = buildList {
        val publicProperties = viewModel.properties(includeNested = false).filter { it.hasPublicOrDefaultModifier }
        val uiState = publicProperties.firstOrNull { it.name == UI_STATE }
        if (uiState?.declaredType()?.startsWith("StateFlow<") != true) add("no public `$UI_STATE: StateFlow<S>`")
        publicProperties.firstOrNull { it.name == EVENTS }?.let { events ->
            if (events.declaredType()?.startsWith("Flow<") != true) add("`$EVENTS` is not declared as `Flow<E>`")
        }
        publicProperties
            .filter { it.name != UI_STATE && it.name != EVENTS && it.isFlow() }
            .forEach { add("extra public flow `${it.name}`") }
        viewModel.functions(includeNested = false, includeLocal = false)
            .filter { it.hasPublicOrDefaultModifier && it.returnType?.text?.let(FLOW_TYPE::containsMatchIn) == true }
            .forEach { add("public function `${it.name}` returns a flow") }
        declaredTypes(viewModel).forEach { add("declares `$it` in the ViewModel file") }
    }

    /**
     * Non-private types the ViewModel file declares besides the ViewModel and its companion object. Private helpers
     * (`Inputs`, `LoadAttempt`) are implementation, not the screen's state or events.
     */
    private fun declaredTypes(viewModel: KoClassDeclaration): List<String> {
        val file = viewModel.containingFile
        val topLevel = file.classes(includeNested = false, includeLocal = false).filterNot { it.hasPrivateModifier }
            .map { it.name } +
            file.interfaces(includeNested = false).filterNot { it.hasPrivateModifier }.map { it.name } +
            file.objects(includeNested = false).filterNot { it.hasPrivateModifier }.map { it.name } +
            file.typeAliases.filterNot { it.hasPrivateModifier }.map { it.name }
        val nested = viewModel.classes(includeNested = false, includeLocal = false)
            .filterNot { it.hasPrivateModifier }.map { it.name } +
            viewModel.interfaces(includeNested = false).filterNot { it.hasPrivateModifier }.map { it.name } +
            viewModel.objects(includeNested = false)
                .filterNot { it.hasPrivateModifier || it.hasCompanionModifier }.map { it.name }
        return (topLevel - viewModel.name) + nested
    }

    private fun KoPropertyDeclaration.declaredType(): String? = type?.text

    /** A declared flow type, or for an inferred type an initializer that builds or exposes a flow. */
    private fun KoPropertyDeclaration.isFlow(): Boolean =
        declaredType()?.let(FLOW_TYPE::containsMatchIn) ?: FLOW_INITIALIZER.containsMatchIn(text)

    private companion object {
        const val UI_STATE = "uiState"
        const val EVENTS = "events"
        const val RESOURCES = "dev.alllexey.itmowidgets.R"
        val FLOW_TYPE = Regex("""^(Mutable)?(State|Shared)?Flow<|^(Mutable)?LiveData<""")
        val FLOW_INITIALIZER = Regex("""\w*Flow\b|\bstateIn\(|\bshareIn\(|LiveData\b""")
        val allowedAndroidxImports = setOf(
            "androidx.lifecycle.ViewModel",
            "androidx.lifecycle.viewModelScope",
            "androidx.lifecycle.SavedStateHandle"
        )
    }
}
