package dev.alllexey.itmowidgets.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getString
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.qr.QrCodeFragment
import dev.alllexey.itmowidgets.ui.schedule.ScheduleFragment
import dev.alllexey.itmowidgets.ui.settings.SettingsFragment
import dev.alllexey.itmowidgets.ui.web.WebFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val fragmentManager by lazy { supportFragmentManager }
    private var activeFragment: Fragment? = null

    private val fragmentTags = mapOf(
        R.id.navigation_schedule to "schedule",
        R.id.navigation_web to "web",
        R.id.navigation_qr_code to "qr",
        R.id.navigation_settings to "settings"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right
            )
            insets
        }

        val bottomNavView = findViewById<BottomNavigationView>(R.id.bottom_nav_view)

        if (savedInstanceState != null) {
            val lastActiveTag = savedInstanceState.getString("ACTIVE_FRAGMENT_TAG")
            activeFragment = fragmentManager.findFragmentByTag(lastActiveTag)
        }

        bottomNavView.setOnItemSelectedListener { item ->
            val tag = fragmentTags[item.itemId]
            val existingFragment = fragmentManager.findFragmentByTag(tag)

            val fragmentToShow = existingFragment ?: when (item.itemId) {
                R.id.navigation_schedule -> ScheduleFragment()
                R.id.navigation_web -> WebFragment()
                R.id.navigation_qr_code -> QrCodeFragment()
                R.id.navigation_settings -> SettingsFragment()
                else -> throw IllegalStateException("Unknown menu item ID")
            }

            fragmentManager.beginTransaction().apply {
                fragmentManager.fragments.forEach {
                    hide(it)
                }
                if (existingFragment == null) {
                    add(R.id.nav_host_fragment, fragmentToShow, tag)
                } else {
                    show(fragmentToShow)
                }
                commit()
            }

            activeFragment = fragmentToShow
            true
        }

        if (savedInstanceState == null) {
            bottomNavView.selectedItemId = R.id.navigation_schedule
        }

        checkVersion()
    }

    fun checkVersion() {
        val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
        CoroutineScope(Dispatchers.IO).launch {
            if (!appContainer.storage.settings.getCustomServicesState()) return@launch
            try {
                val latestVersion = appContainer.itmoWidgets.api().latestAppVersion().data!!
                val currentVersion = getString(applicationContext, R.string.app_version)
                withContext(Dispatchers.Main) {
                    if (latestVersion > currentVersion && latestVersion > appContainer.storage.utility.getSkippedVersion()) {
                        showVersionPopup(latestVersion, currentVersion)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun showVersionPopup(latestVersion: String, currentVersion: String) {
        MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme_FilledButton)
            .setTitle("Новая версия \uD83D\uDE80")
            .setMessage("У приложения вышло обновление! Возможно, там будет что-то интересное для тебя \uD83D\uDC40\n${currentVersion} ➜ ${latestVersion} ")
            .setNeutralButton("Напомнить позже") { dialog, which ->

            }
            .setNegativeButton("Пропустить версию") { dialog, which ->
                val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
                appContainer.storage.utility.setSkippedVersion(latestVersion)
                Toast.makeText(applicationContext, "Хорошо, пропустим версию \uD83D\uDC4C", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Обновить") { dialog, which ->
                val url = getString(applicationContext, R.string.latest_release_url)
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(intent)
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activeFragment?.let { outState.putString("ACTIVE_FRAGMENT_TAG", it.tag) }
    }
}