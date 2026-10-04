import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * String catalog checks. itmowidgets.android.app and itmowidgets.cmp.ui apply it, so a module never adds a line
 * for it. A no-op until L05's TC-16a fills it; its checks attach to the module's `itmoVerifyQuick` task.
 */
class StringsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit
}
