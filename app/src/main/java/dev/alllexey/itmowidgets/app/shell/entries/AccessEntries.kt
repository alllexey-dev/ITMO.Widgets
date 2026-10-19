package dev.alllexey.itmowidgets.app.shell.entries

import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.ActivityRoutes
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ProjectLinks
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.permission.RequestNotificationPermission
import dev.alllexey.itmowidgets.core.ui.permission.openAppNotificationSettings
import dev.alllexey.itmowidgets.core.ui.permission.openNotificationSettingsIfLocked
import dev.alllexey.itmowidgets.core.ui.permission.requestNotifications
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.ConnectionEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.connection.ConnectionViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationSettingsEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.notifications.NotificationSettingsViewModel
import dev.alllexey.itmowidgets.feature.settings.ui.connection.ConnectionActions
import dev.alllexey.itmowidgets.feature.settings.ui.connection.ConnectionScreen
import dev.alllexey.itmowidgets.feature.settings.ui.notifications.NotificationSettingsActions
import dev.alllexey.itmowidgets.feature.settings.ui.notifications.NotificationSettingsScreen
import dev.alllexey.itmowidgets.feature.settings.ui.openBackgroundWorkSettings
import org.koin.core.parameter.parametersOf

/**
 * «Подключение и данные» under its settings keys (`SERVICES`, and `PRIVACY`, whose audiences live here now): the
 * rows open their sections, BARS its sign-in activity, the devices their screens; the site pages open in the browser.
 */
@Composable
internal fun ConnectionEntry(key: AppRoutes.Settings, navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val view = LocalView.current
    val platform = LocalPlatformActions.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: ConnectionViewModel = accessViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is ConnectionEvent.ShowError -> Snackbar.make(view, event.error.messageRes(), Snackbar.LENGTH_LONG).show()
                    is ConnectionEvent.OpenWebPage -> openLink(platform, view, BuildConfig.WIDGETS_BASE_URL + event.path)
                }
            }
        }
    }
    val actions = remember(viewModel, navigator, key, platform, view, context) {
        ConnectionActions(
            onBack = { navigator.close(key) },
            onConnectedChange = viewModel::setConnected,
            onAudience = viewModel::setAudience,
            onRetryPrivacy = viewModel::retryPrivacy,
            onFriends = { navigator.open(AppRoutes.Friends) },
            onQueue = { navigator.select(AppTab.SPORT) },
            onLinks = { navigator.select(AppTab.RECORDBOOK) },
            onSource = { openLink(platform, view, ProjectLinks.SERVICES_SOURCE_URL) },
            onBars = { context.startActivity(ActivityRoutes.barsLogin(context)) },
            onPairDevice = { navigator.open(AppRoutes.WebLogin()) },
            onDevices = { navigator.open(AppRoutes.Connections) },
            onDeleteAccount = viewModel::deleteAccount,
        )
    }
    ConnectionScreen(state, actions)
}

/**
 * «Уведомления» under its settings key: the host reads what only Android knows on every resume (the permission,
 * each channel's switch, the background restriction) and answers the page's events with the permission dialog and
 * the system pages. The background work hint survives recreation in the entry's saved state.
 */
@Composable
internal fun NotificationSettingsEntry(key: AppRoutes.Settings, navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: NotificationSettingsViewModel = accessViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var hint by rememberSaveable { mutableStateOf(false) }
    val readSystem = {
        val manager = NotificationManagerCompat.from(context)
        viewModel.onPermissionChanged(manager.areNotificationsEnabled())
        viewModel.onChannelsChanged(
            AppNotificationChannels.ALL.associateWith { channel ->
                manager.getNotificationChannelCompat(channel)?.importance != NotificationManagerCompat.IMPORTANCE_NONE
            },
        )
    }
    val permission = rememberLauncherForActivityResult(RequestNotificationPermission()) { granted ->
        activity?.openNotificationSettingsIfLocked(granted)
        readSystem()
    }
    LifecycleResumeEffect(viewModel) {
        // Back from a system page, the rows follow what Android allows now.
        readSystem()
        viewModel.onBackgroundWorkChanged()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    NotificationSettingsEvent.RequestPermission -> context.requestNotifications(permission)
                    NotificationSettingsEvent.OpenSystemSettings -> context.openAppNotificationSettings()
                    NotificationSettingsEvent.OpenConnection -> navigator.open(AppRoutes.Settings(SettingsPage.SERVICES.name))
                    NotificationSettingsEvent.ShowBackgroundWorkHint -> hint = true
                    NotificationSettingsEvent.OpenBackgroundWorkSettings -> activity?.openBackgroundWorkSettings()
                    is NotificationSettingsEvent.ShowError ->
                        Snackbar.make(view, event.error.messageRes(), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }
    val actions = remember(viewModel, navigator, key, activity) {
        NotificationSettingsActions(
            onBack = { navigator.close(key) },
            onMarks = viewModel::setMarks,
            onMarkSource = viewModel::setMarkSource,
            onScheduleChanges = viewModel::setScheduleChanges,
            onPush = viewModel::setPush,
            onConnection = viewModel::openConnection,
            onRequestPermission = viewModel::requestPermission,
            onSystemSettings = viewModel::openSystemSettings,
            onBackgroundWork = viewModel::openBackgroundWorkSettings,
            onAllowBackgroundWork = {
                hint = false
                activity?.openBackgroundWorkSettings()
            },
            onDismissHint = { hint = false },
        )
    }
    NotificationSettingsScreen(state, actions, hintShown = hint)
}

private fun openLink(platform: PlatformActions, anchor: View, url: String) {
    if (!platform.openLink(url)) Snackbar.make(anchor, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
}

/** Koin's definition of [VM] in this entry's own store, with the entry's `SavedStateHandle`. */
@Composable
private inline fun <reified VM : ViewModel> accessViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}
