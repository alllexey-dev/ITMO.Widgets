package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** The connection to ITMO.Widgets services and the privacy choices that need it. */
class ServicesPageProvider @Inject constructor(
    private val repository: SettingsRepository,
    private val customServicesRepository: CustomServicesRepository,
    private val widgetRefreshRequester: WidgetRefreshRequester
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.SERVICES, SettingsPage.PRIVACY)

    override val rows = setOf(SettingRowId.CUSTOM_SERVICES, SettingRowId.DELETE_ACCOUNT, SettingRowId.RETRY_PRIVACY) +
        SHARING_ROWS

    override fun sections(page: SettingsPage, state: SettingsPageState): List<SettingSection> = when (page) {
        SettingsPage.SERVICES -> servicesSections(state)
        else -> if (state.local.customServicesEnabled && state.sharing == SharingSettingsState.Loading) {
            emptyList()
        } else {
            privacySections(state)
        }
    }

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        if (id == SettingRowId.CUSTOM_SERVICES) updateCustomServices(scope, checked)
    }

    override fun onChoiceChanged(scope: SettingsPageScope, id: SettingRowId, optionKey: String) {
        if (id !in SHARING_ROWS) return
        val visibility = SharingVisibility.entries.firstOrNull { it.name == optionKey } ?: return
        val item = scope.currentItems.filterIsInstance<SettingItem.Choice>().firstOrNull { it.id == id } ?: return
        // A dialog can outlive the state that opened it. Reject stale and duplicate actions.
        if (!item.enabled || item.selectedOptionKey == null || scope.privacyUpdateInProgress) return
        if (item.selectedOptionKey == optionKey) return
        scope.updateSharing {
            when (id) {
                SettingRowId.SCHEDULE_SHARING -> repository.setScheduleVisibility(visibility)
                SettingRowId.FRIENDS_SHARING -> repository.setFriendsVisibility(visibility)
                else -> repository.setSportVisibility(visibility)
            }
        }
    }

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        when (id) {
            SettingRowId.DELETE_ACCOUNT -> scope.send(SettingsEvent.OpenWebPage(DELETE_ACCOUNT_PATH))
            SettingRowId.RETRY_PRIVACY -> scope.refreshPrivacy()
            else -> Unit
        }
    }

    private fun updateCustomServices(scope: SettingsPageScope, enabled: Boolean) {
        scope.launch {
            if (!customServicesRepository.isChangeable()) {
                scope.emit(SettingsEvent.ShowError(AppError.DemoUnavailable))
                return@launch
            }
            try {
                customServicesRepository.setEnabled(enabled)
                widgetRefreshRequester.refreshAll()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                scope.emit(SettingsEvent.ShowError(AppError.Unknown(error)))
            }
        }
    }

    private fun servicesSections(state: SettingsPageState) = listOf(
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.CUSTOM_SERVICES,
                    // The screen is already titled by the page; the switch names the action.
                    title = UiText.Resource(R.string.settings_custom_services_toggle),
                    checked = state.local.customServicesEnabled
                )
            ),
            footer = UiText.Resource(R.string.settings_services_footer)
        ),
        // Shown with the switch off too: an account may remain from an earlier connection.
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Action(
                    id = SettingRowId.DELETE_ACCOUNT,
                    title = UiText.Resource(R.string.settings_delete_account_title),
                    description = UiText.Resource(R.string.settings_delete_account_description),
                    trailingIcon = AppIcon.OPEN_IN_NEW
                )
            )
        )
    )

    private fun privacySections(state: SettingsPageState): List<SettingSection> {
        val local = state.local
        val sharing = state.sharing
        val content = (sharing as? SharingSettingsState.Content)
            ?.takeIf { local.customServicesEnabled }
        val editable = content != null && !content.updating
        val status = when {
            !local.customServicesEnabled -> R.string.settings_privacy_services_required
            sharing is SharingSettingsState.Error -> R.string.settings_privacy_load_error
            content == null -> R.string.settings_privacy_loading
            else -> null
        }?.let(UiText::Resource)
        val items = mutableListOf<SettingItem>(
            sharingChoice(
                id = SettingRowId.SCHEDULE_SHARING,
                title = UiText.Resource(R.string.settings_schedule_sharing_title),
                visibility = content?.settings?.scheduleVisibility,
                enabled = editable
            ),
            sharingChoice(
                id = SettingRowId.SPORT_SHARING,
                title = UiText.Resource(R.string.settings_sport_sharing_title),
                visibility = content?.settings?.sportVisibility,
                enabled = editable
            ),
            sharingChoice(
                id = SettingRowId.FRIENDS_SHARING,
                title = UiText.Resource(R.string.settings_friends_sharing_title),
                visibility = content?.settings?.friendsVisibility,
                enabled = editable
            )
        )
        if (!local.customServicesEnabled) {
            items += SettingRows.navigation(SettingsPage.SERVICES)
        } else if (sharing is SharingSettingsState.Error) {
            items += SettingItem.Action(
                id = SettingRowId.RETRY_PRIVACY,
                title = UiText.Resource(R.string.settings_privacy_retry)
            )
        }
        return listOf(
            SettingSection(
                title = null,
                items = items,
                footer = status ?: UiText.Resource(R.string.settings_privacy_footer)
            )
        )
    }

    private fun sharingChoice(
        id: SettingRowId,
        title: UiText,
        visibility: SharingVisibility?,
        enabled: Boolean
    ) = SettingItem.Choice(
        id = id,
        title = title,
        value = visibility?.label() ?: UiText.Resource(R.string.settings_privacy_unknown),
        options = SharingVisibility.entries.map { ChoiceOption(it.name, it.label()) },
        selectedOptionKey = visibility?.name,
        enabled = enabled
    )

    private fun SharingVisibility.label() = UiText.Resource(
        when (this) {
            SharingVisibility.ALL -> R.string.settings_privacy_all
            SharingVisibility.FRIENDS -> R.string.settings_privacy_friends
            SharingVisibility.NOBODY -> R.string.settings_privacy_nobody
        }
    )

    companion object {
        const val DELETE_ACCOUNT_PATH = "/delete-account"
        private val SHARING_ROWS = setOf(
            SettingRowId.SCHEDULE_SHARING,
            SettingRowId.SPORT_SHARING,
            SettingRowId.FRIENDS_SHARING
        )
    }
}
