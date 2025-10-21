package dev.alllexey.itmowidgets.ui.main

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.qr.QrCodeFragment
import dev.alllexey.itmowidgets.ui.schedule.ScheduleFragment
import dev.alllexey.itmowidgets.ui.settings.SettingsFragment
import dev.alllexey.itmowidgets.ui.web.WebFragment

class MainActivity : AppCompatActivity() {

    private val fragmentManager = supportFragmentManager
    private var activeFragment: Fragment? = null

    private lateinit var scheduleFragment: ScheduleFragment
    private lateinit var webFragment: WebFragment
    private lateinit var qrCodeFragment: QrCodeFragment
    private lateinit var settingsFragment: SettingsFragment

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

        if (savedInstanceState == null) {
            scheduleFragment = ScheduleFragment()
            webFragment = WebFragment()
            qrCodeFragment = QrCodeFragment()
            settingsFragment = SettingsFragment()

            fragmentManager.beginTransaction().apply {
                add(R.id.nav_host_fragment, settingsFragment, R.id.navigation_settings.toString()).hide(settingsFragment)
                add(R.id.nav_host_fragment, qrCodeFragment, R.id.navigation_qr_code.toString()).hide(qrCodeFragment)
                add(R.id.nav_host_fragment, webFragment, R.id.navigation_web.toString()).hide(webFragment)
                add(R.id.nav_host_fragment, scheduleFragment, R.id.navigation_schedule.toString())
            }.commit()
            activeFragment = scheduleFragment
        } else {
            scheduleFragment = fragmentManager.findFragmentByTag(R.id.navigation_schedule.toString()) as ScheduleFragment
            webFragment = fragmentManager.findFragmentByTag(R.id.navigation_web.toString()) as WebFragment
            qrCodeFragment = fragmentManager.findFragmentByTag(R.id.navigation_qr_code.toString()) as QrCodeFragment
            settingsFragment = fragmentManager.findFragmentByTag(R.id.navigation_settings.toString()) as SettingsFragment

            val lastActiveTag = savedInstanceState.getString("ACTIVE_FRAGMENT_TAG")
            activeFragment = lastActiveTag?.let { fragmentManager.findFragmentByTag(it) } ?: scheduleFragment
        }

        bottomNavView.setOnItemSelectedListener { item ->
            val fragmentToShow = when (item.itemId) {
                R.id.navigation_schedule -> scheduleFragment
                R.id.navigation_web -> webFragment
                R.id.navigation_qr_code -> qrCodeFragment
                R.id.navigation_settings -> settingsFragment
                else -> throw IllegalStateException("Unknown menu item ID")
            }

            if (fragmentToShow !== activeFragment) {
                fragmentManager.beginTransaction().apply {
                    activeFragment?.let { hide(it) }
                    show(fragmentToShow)
                    commit()
                }
                activeFragment = fragmentToShow
            }
            true
        }

        bottomNavView.selectedItemId = R.id.navigation_schedule
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activeFragment?.let { outState.putString("ACTIVE_FRAGMENT_TAG", it.tag) }
    }
}