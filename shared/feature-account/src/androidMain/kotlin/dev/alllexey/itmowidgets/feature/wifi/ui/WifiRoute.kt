package dev.alllexey.itmowidgets.feature.wifi.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.feature.wifi.domain.ItmoWifi
import dev.alllexey.itmowidgets.feature.wifi.presentation.WifiViewModel
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Wi-Fi sheet with its Koin ViewModel, which reads ISU each time the sheet starts. `Подключиться` opens the
 * system's dialog that saves ITMO-Corp with the login and password (Android 11+); an older system, a phone without the
 * Let's Encrypt roots or a dialog that fails copies the password and opens the Wi-Fi settings instead. Android hosts it
 * in the shell's `Wifi` sheet; iOS draws the same view model in SwiftUI (`WifiSheet`).
 */
@Composable
fun WifiRoute(onClose: () -> Unit, viewModel: WifiViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val texts = WifiTexts(
        copied = text(AccountStrings.wifi_copied),
        saved = text(AccountStrings.wifi_saved),
        saveFailed = text(AccountStrings.wifi_save_failed),
        manual = text(AccountStrings.wifi_copied_open_settings),
        linkFailed = text(CoreStrings.link_open_failed),
        password = text(AccountStrings.wifi_password),
        login = text(AccountStrings.wifi_login),
    )
    val show: (String) -> Unit = { message -> scope.launch { snackbars.showSnackbar(message, duration = SnackbarDuration.Long) } }
    val addNetwork = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.networkSaved()) {
            true -> show(texts.saved)
            false -> state.credentials?.let {
                context.copyToClipboard(texts.password, it.password, secret = true)
                show(texts.saveFailed)
            }
            null -> Unit
        }
    }

    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { }
    }

    val actions = WifiActions(
        onClose = onClose,
        onConnect = { credentials ->
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) addItmoWifiIntent(credentials) else null
            val launched = intent != null && runCatching { addNetwork.launch(intent) }.isSuccess
            if (!launched) {
                context.copyToClipboard(texts.password, credentials.password, secret = true)
                context.openWifiSettings()
                show(texts.manual)
            }
        },
        onCopyLogin = { if (context.copyToClipboard(texts.login, it, secret = false)) show(texts.copied) },
        onCopyPassword = { if (context.copyToClipboard(texts.password, it, secret = true)) show(texts.copied) },
        onTogglePassword = viewModel::togglePassword,
        onOpenIsu = { if (!context.openPage(ItmoWifi.ISU_PAGE_URL)) show(texts.linkFailed) },
        onRetry = viewModel::start,
    )
    WifiSheetContent(state, actions, snackbarHostState = snackbars)
}

private class WifiTexts(
    val copied: String,
    val saved: String,
    val saveFailed: String,
    val manual: String,
    val linkFailed: String,
    val password: String,
    val login: String,
)

@Composable
private fun text(key: String): String = UiText.Res(key).asString()
