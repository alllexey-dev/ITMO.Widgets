package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.platform.AppBuild
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * «О приложении»: widget refresh, the onboarding replay, diagnostics, the privacy policy, the licences and the version,
 * which copies the build's details on a tap.
 */
class AboutPageProvider(
    private val widgetRefreshRequester: WidgetRefreshRequester,
    private val onboardingRepository: OnboardingRepository,
    private val appBuild: AppBuild,
    private val diagnostics: AppDiagnostics
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.ABOUT)

    override val rows = setOf(
        SettingRowId.REFRESH_WIDGETS,
        SettingRowId.RESTART_ONBOARDING,
        SettingRowId.DIAGNOSTICS,
        SettingRowId.PRIVACY_POLICY,
        SettingRowId.VERSION
    )

    /** The count starts at zero, so the page never waits for the diagnostics log. */
    override fun observeState(page: SettingsPage, state: Flow<SettingsPageState>): Flow<SettingsPageState> =
        combine(state, diagnostics.observe().map { it.size }.onStart { emit(0) }) { pageState, count ->
            pageState.copy(diagnosticsCount = count)
        }

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOf(
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Action(
                    id = SettingRowId.REFRESH_WIDGETS,
                    title = UiText.Res(SettingsStrings.settings_refresh_widgets_title),
                    icon = AppIcon.REFRESH
                ),
                SettingItem.Action(
                    id = SettingRowId.RESTART_ONBOARDING,
                    title = UiText.Res(SettingsStrings.settings_restart_onboarding_title),
                    description = UiText.Res(SettingsStrings.settings_restart_onboarding_description),
                    icon = AppIcon.HISTORY
                ),
                SettingItem.Action(
                    id = SettingRowId.DIAGNOSTICS,
                    title = UiText.Res(SettingsStrings.settings_diagnostics_title),
                    value = UiText.Res(SettingsStrings.settings_diagnostics_count, listOf(state.diagnosticsCount)),
                    trailingIcon = AppIcon.CHEVRON_RIGHT,
                    icon = AppIcon.ERROR
                ),
                SettingItem.Action(
                    id = SettingRowId.PRIVACY_POLICY,
                    title = UiText.Res(SettingsStrings.settings_privacy_policy_title),
                    trailingIcon = AppIcon.OPEN_IN_NEW,
                    icon = AppIcon.LOCK
                ),
                SettingRows.navigation(SettingsPage.LICENSES, icon = AppIcon.MENU_BOOK),
                SettingItem.Action(
                    id = SettingRowId.VERSION,
                    title = UiText.Res(SettingsStrings.settings_version_title),
                    description = versionSummary(appBuild),
                    icon = AppIcon.INFO
                )
            ),
            footer = UiText.Res(CoreStrings.app_unofficial_notice)
        )
    )

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        when (id) {
            SettingRowId.REFRESH_WIDGETS -> {
                widgetRefreshRequester.refreshAll()
                scope.send(SettingsEvent.WidgetsRefreshStarted)
            }
            SettingRowId.DIAGNOSTICS -> scope.send(SettingsEvent.OpenDiagnostics)
            SettingRowId.PRIVACY_POLICY -> scope.send(SettingsEvent.OpenWebPage(PRIVACY_POLICY_PATH))
            SettingRowId.VERSION -> scope.send(
                SettingsEvent.CopyText(appBuild.details(), UiText.Res(SettingsStrings.settings_version_copied))
            )
            SettingRowId.RESTART_ONBOARDING -> scope.launch {
                // The stored flag is what the root gate reads; the overlay only has to get out of the way.
                onboardingRepository.reset()
                scope.emit(SettingsEvent.CloseOverlays)
            }
            else -> Unit
        }
    }

    companion object {
        const val PRIVACY_POLICY_PATH = "/privacy.html"
    }
}

/**
 * The version row's line: `2.3.0 (20292) · a1b2c3d`, then for a custom build where it comes from, the repository or
 * «своя сборка» for a local one (docs/building.md, build identity). An unknown commit is left out.
 */
internal fun versionSummary(build: AppBuild): UiText {
    val source = build.source
    val origin = when (source) {
        null, AppBuild.LOCAL -> UiText.Res(SettingsStrings.settings_version_own_build)
        else -> UiText.Dynamic(source)
    }
    return UiText.Joined(
        listOfNotNull(
            UiText.Dynamic("${build.version} (${build.build})"),
            build.shortCommit?.let(UiText::Dynamic),
            origin.takeUnless { build.official }
        ),
        separator = " · "
    )
}
