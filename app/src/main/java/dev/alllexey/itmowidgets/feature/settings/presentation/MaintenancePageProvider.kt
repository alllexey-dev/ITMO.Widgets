package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Widget refresh, the onboarding replay, diagnostics, the privacy policy and the version. */
class MaintenancePageProvider @Inject constructor(
    private val widgetRefreshRequester: WidgetRefreshRequester,
    private val onboardingRepository: OnboardingRepository,
    private val appVersion: AppVersion,
    private val diagnostics: AppDiagnostics
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.MAINTENANCE)

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
                    title = UiText.Resource(R.string.settings_refresh_widgets_title),
                    trailingIconRes = R.drawable.ic_refresh
                ),
                SettingItem.Action(
                    id = SettingRowId.RESTART_ONBOARDING,
                    title = UiText.Resource(R.string.settings_restart_onboarding_title),
                    description = UiText.Resource(R.string.settings_restart_onboarding_description),
                    trailingIconRes = R.drawable.ic_refresh
                ),
                SettingItem.Action(
                    id = SettingRowId.DIAGNOSTICS,
                    title = UiText.Resource(R.string.settings_diagnostics_title),
                    value = UiText.Resource(R.string.settings_diagnostics_count, listOf(state.diagnosticsCount)),
                    trailingIconRes = R.drawable.ic_chevron_right
                ),
                SettingItem.Action(
                    id = SettingRowId.PRIVACY_POLICY,
                    title = UiText.Resource(R.string.settings_privacy_policy_title),
                    trailingIconRes = R.drawable.ic_open_in_new
                ),
                SettingItem.Info(
                    id = SettingRowId.VERSION,
                    title = UiText.Resource(R.string.settings_version_title),
                    value = UiText.Dynamic(appVersion.name)
                )
            ),
            footer = UiText.Resource(R.string.app_unofficial_notice)
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
