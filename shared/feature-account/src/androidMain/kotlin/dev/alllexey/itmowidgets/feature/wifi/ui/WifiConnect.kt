package dev.alllexey.itmowidgets.feature.wifi.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiEnterpriseConfig
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.result.ActivityResult
import androidx.annotation.RequiresApi
import dev.alllexey.itmowidgets.feature.wifi.domain.ItmoWifi
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * The system's «Сохранить сеть?» dialog for ITMO-Corp (Android 11+, `Settings.ACTION_WIFI_ADD_NETWORKS`): PEAP with
 * MSCHAPv2, the user's login and password, and the server checked against the Let's Encrypt roots of the phone's own
 * CA store and the domain `wifi.it.itmo.ru`. Android 11 refuses an enterprise network that checks no server, so null
 * when the phone has no such root or the system rejects the config; the caller then copies the password instead.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal fun addItmoWifiIntent(credentials: WifiCredentials): Intent? {
    val roots = letsEncryptRoots().ifEmpty { return null }
    val suggestion = try {
        val config = WifiEnterpriseConfig().apply {
            eapMethod = WifiEnterpriseConfig.Eap.PEAP
            phase2Method = WifiEnterpriseConfig.Phase2.MSCHAPV2
            identity = credentials.login
            password = credentials.password
            caCertificates = roots.toTypedArray()
            domainSuffixMatch = ItmoWifi.SERVER_DOMAIN
        }
        WifiNetworkSuggestion.Builder().setSsid(ItmoWifi.SSID).setWpa2EnterpriseConfig(config).build()
    } catch (_: IllegalArgumentException) {
        return null
    }
    return Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
        .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
}

/** Whether the dialog saved the network (or it was there already); null when the user closed it. */
internal fun ActivityResult.networkSaved(): Boolean? {
    if (resultCode != Activity.RESULT_OK) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val codes = data?.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST).orEmpty()
    return codes.isNotEmpty() && codes.all { it == Settings.ADD_WIFI_RESULT_SUCCESS || it == Settings.ADD_WIFI_RESULT_ALREADY_EXISTS }
}

/** The ISRG roots (Let's Encrypt) the system trusts; the user's own certificates are not read. */
private fun letsEncryptRoots(): List<X509Certificate> = runCatching {
    val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
    store.aliases().toList()
        .filter { it.startsWith("system:") }
        .mapNotNull { store.getCertificate(it) as? X509Certificate }
        .filter { ISRG.containsMatchIn(it.subjectX500Principal.name) }
}.getOrDefault(emptyList())

/** `O=Internet Security Research Group` (ISRG Root X1, X2) and `O=ISRG` (Root YE, YR). */
private val ISRG = Regex("""(^|,)\s*O=(Internet Security Research Group|ISRG)\s*(,|$)""")

/**
 * Puts [text] on the clipboard; a [secret] is marked sensitive, so the keyboard and the system preview hide it.
 * Returns whether the app should confirm the copy itself: Android 13 and newer show their own overlay.
 */
internal fun Context.copyToClipboard(label: String, text: String, secret: Boolean): Boolean {
    val clip = ClipData.newPlainText(label, text)
    if (secret) {
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    }
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip) ?: return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}

/** The system Wi-Fi list, where the user picks ITMO-Corp and pastes the password. */
internal fun Context.openWifiSettings(): Boolean = start(Intent(Settings.ACTION_WIFI_SETTINGS))

/** A page in the browser. */
internal fun Context.openPage(url: String): Boolean = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun Context.start(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** `ClipDescription.EXTRA_IS_SENSITIVE` (API 33); older systems ignore the extra. */
private val EXTRA_IS_SENSITIVE =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
