package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import org.junit.Test

class GateRulesTest {

    @Test
    fun `network clients are gated by demo mode`() {
        val gated = productionClasses.filter { declaration ->
            declaration.packagee?.name != NETWORK_PACKAGE &&
                declaration.constructors.any { constructor -> constructor.parameters.any(::isNetworkClient) }
        }
        // A rule that matches nothing proves nothing.
        org.junit.Assert.assertTrue("Only ${gated.size} classes take a network client", gated.size > MIN_GATED_CLASSES)
        gated.assertTrue { declaration ->
            declaration.constructors
                .filter { constructor -> constructor.parameters.any(::isNetworkClient) }
                .all { constructor -> constructor.parameters.any { it.type.name == DEMO_MODE } }
        }
    }

    @Test
    fun `backend clients are gated by the opt-in`() {
        // DemoMode keeps the demo session off the network; BackendGate.mayCallBackend() also needs the opt-in.
        productionClasses
            .filter { declaration ->
                val packageName = declaration.packagee?.name.orEmpty()
                val buildsClients = packageName == NETWORK_PACKAGE || packageName == DI_PACKAGE ||
                    packageName.startsWith("$DI_PACKAGE.")
                !buildsClients &&
                    declaration.constructors.any { constructor -> constructor.parameters.any(::isBackendClient) }
            }
            .requireAtLeast(MIN_BACKEND_GATED_CLASSES, "classes that take the Backend client")
            .assertTrue { declaration ->
                declaration.constructors
                    .filter { constructor -> constructor.parameters.any(::isBackendClient) }
                    .all { constructor -> constructor.parameters.any { it.type.name == BACKEND_GATE } }
            }
    }

    private fun isBackendClient(parameter: KoParameterDeclaration): Boolean = parameter.type.name == BACKEND_CLIENT

    private fun isNetworkClient(parameter: KoParameterDeclaration): Boolean =
        parameter.type.name in networkClients ||
            (parameter.type.name == OK_HTTP_CLIENT && parameter.hasAnnotationWithName(PUBLIC_WEB_CLIENT))

    private companion object {
        /** Builds the clients themselves; every user of a client is gated instead. */
        const val NETWORK_PACKAGE =
            "dev.alllexey.itmowidgets.core.network"
        const val DEMO_MODE = "DemoMode"
        const val BACKEND_CLIENT = "ItmoWidgetsApi"
        const val BACKEND_GATE = "BackendGate"
        const val OK_HTTP_CLIENT = "OkHttpClient"
        const val PUBLIC_WEB_CLIENT = "PublicWebClient"

        /** My ITMO, BARS and Backend clients; the public web client is matched by its qualifier. */
        val networkClients = setOf("ItmoWidgetsApi", "MyItmo", "MyItmoApi", "Bars")
    }
}
