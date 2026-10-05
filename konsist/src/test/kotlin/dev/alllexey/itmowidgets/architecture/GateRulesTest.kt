package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import org.junit.Test

/**
 * Who may hold a network client. Clients are matched by fully qualified name, read from each file's imports, so the
 * app's own `feature.recordbook.data.bars.BarsClient` is not MyItmoApi's `BarsClient`. A lane that adds or removes a
 * client name (KM-10a1/a2, CO-01a, ML-01b, KM-10i) edits one line below in its own PR, with L06 as reviewer.
 */
class GateRulesTest {

    @Test
    fun `network clients are gated by demo mode`() {
        val gated = productionClasses.filter { declaration ->
            !declaration.isInAny(CLIENT_BUILDER_PACKAGES) && declaration.holds(::callsNetworkClient)
        }
        // A rule that matches nothing proves nothing.
        org.junit.Assert.assertTrue("Only ${gated.size} classes take a network client", gated.size > MIN_GATED_CLASSES)
        gated.assertTrue { declaration -> declaration.passes(DEMO_MODE, ::callsNetworkClient) }
    }

    @Test
    fun `backend clients are gated by the opt-in`() {
        // DemoMode keeps the demo session off the network; BackendGate.mayCallBackend() also needs the opt-in.
        productionClasses
            .filter { declaration ->
                !declaration.isInAny(CLIENT_BUILDER_PACKAGES + DI_PACKAGE) && declaration.holds(::isBackendClient)
            }
            .requireAtLeast(MIN_BACKEND_GATED_CLASSES, "classes that take the Backend client")
            .assertTrue { declaration -> declaration.passes(BACKEND_GATE, ::isBackendClient) }
    }

    /** A constructor of this class takes a parameter of a [client] type. */
    private fun KoClassDeclaration.holds(client: (KoParameterDeclaration) -> Boolean): Boolean =
        constructors.any { constructor -> constructor.parameters.any(client) }

    /** Every constructor that takes a [client] also takes [gate]. */
    private fun KoClassDeclaration.passes(gate: String, client: (KoParameterDeclaration) -> Boolean): Boolean =
        constructors
            .filter { constructor -> constructor.parameters.any(client) }
            .all { constructor -> constructor.parameters.any { it.type.name == gate } }

    private fun KoClassDeclaration.isInAny(packages: List<String>): Boolean {
        val packageName = packagee?.name.orEmpty()
        return packages.any { packageName == it || packageName.startsWith("$it.") }
    }

    private fun isNetworkClient(parameter: KoParameterDeclaration): Boolean {
        val names = parameter.typeNames()
        return names.any { it in networkClients || it.isAreaApi() } ||
            (OK_HTTP_CLIENT in names && parameter.hasAnnotationWithName(PUBLIC_WEB_CLIENT))
    }

    private fun callsNetworkClient(parameter: KoParameterDeclaration): Boolean =
        isNetworkClient(parameter) && !parameter.onlyWritesTokens()

    /**
     * MyItmoApi's `TokenManager.replaceTokens` writes the local `TokenStorage` under the refresh lock and sends no
     * request, so a class whose every use of its `MyItmoClient` is `<client>.tokens.replaceTokens(` (LA-1d's
     * `SessionTransitions`: sign-in, sign-out, demo start) is a token writer, not a network caller. Any other use of
     * the parameter, a KDoc link included, brings the class back under the gate.
     */
    private fun KoParameterDeclaration.onlyWritesTokens(): Boolean {
        if (MY_ITMO_CLIENT !in typeNames()) return false
        val text = containingFile.text
        val uses = Regex("""\b${Regex.escape(name)}\b""").findAll(text).count()
        val declarations = Regex("""\b${Regex.escape(name)}\s*:""").findAll(text).count()
        val tokenWrites = Regex("""\b${Regex.escape(name)}\.tokens\.replaceTokens\(""").findAll(text).count()
        return tokenWrites > 0 && uses == declarations + tokenWrites
    }

    private fun isBackendClient(parameter: KoParameterDeclaration): Boolean =
        parameter.typeNames().any { it in backendClients || it.isBackendAreaApi() }

    private fun KoParameterDeclaration.typeNames(): Set<String> = containingFile.candidateNames(type.name)

    /** An area of MyItmoApi 2.x or of Core 2.0 (`users`, `sport`, ...): holding one is holding the client. */
    private fun String.isAreaApi(): Boolean = isBackendAreaApi() || startsWith("$MY_ITMO_AREAS.") && endsWith("Api")

    private fun String.isBackendAreaApi(): Boolean = startsWith("$BACKEND_CLIENT_PACKAGE.") && endsWith("Api")

    private companion object {
        const val BACKEND_CLIENT_PACKAGE = "dev.alllexey.itmowidgets.client"
        const val MY_ITMO_AREAS = "dev.alllexey.itmoapi.myitmo"
        const val MY_ITMO_CLIENT = "dev.alllexey.itmoapi.myitmo.MyItmoClient"
        const val DEMO_MODE = "DemoMode"
        const val BACKEND_GATE = "BackendGate"
        const val OK_HTTP_CLIENT = "okhttp3.OkHttpClient"
        const val PUBLIC_WEB_CLIENT = "PublicWebClient"

        /** Build the clients themselves; every user of a client is gated instead. */
        val CLIENT_BUILDER_PACKAGES = listOf("dev.alllexey.itmowidgets.core.network", BACKEND_CLIENT_PACKAGE)

        /** Backend: Core 1.x and Core 2.0; every `*Api` of the 2.0 client counts through [isBackendAreaApi]. */
        val backendClients = setOf(
            "dev.alllexey.itmowidgets.core.ItmoWidgetsApi",
            "$BACKEND_CLIENT_PACKAGE.BackendClient"
        )

        /** My ITMO, BARS, ITMO.ID, Backend and Ktor; the public web OkHttp client is matched by its qualifier. */
        val networkClients = backendClients + setOf(
            "api.myitmo.MyItmo",
            "api.myitmo.MyItmoApi",
            "api.bars.Bars",
            MY_ITMO_CLIENT,
            "dev.alllexey.itmoapi.bars.BarsClient",
            "dev.alllexey.itmoapi.itmoid.ItmoIdClient",
            "io.ktor.client.HttpClient"
        )
    }
}
