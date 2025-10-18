package me.alllexey123.itmowidgets.ui.main

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import me.alllexey123.itmowidgets.R
import me.alllexey123.itmowidgets.ui.qr.QrCodeFragment
import me.alllexey123.itmowidgets.ui.schedule.ScheduleFragment
import me.alllexey123.itmowidgets.ui.settings.SettingsFragment
import me.alllexey123.itmowidgets.ui.web.WebFragment

class MainActivity : AppCompatActivity() {

    private val fragmentManager = supportFragmentManager
    private var activeFragment: Fragment? = null

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
            val initialFragment = ScheduleFragment()
            fragmentManager.beginTransaction()
                .add(R.id.nav_host_fragment, initialFragment, R.id.navigation_schedule.toString())
                .commit()
            activeFragment = initialFragment
        }

        bottomNavView.setOnItemSelectedListener { item ->
            val fragmentTransaction = fragmentManager.beginTransaction()
            val fragmentTag = item.itemId.toString()
            var fragment = fragmentManager.findFragmentByTag(fragmentTag)

            activeFragment?.let { fragmentTransaction.hide(it) }

            if (fragment == null) {
                fragment = when (item.itemId) {
                    R.id.navigation_web -> WebFragment()
                    R.id.navigation_qr_code -> QrCodeFragment()
                    R.id.navigation_schedule -> ScheduleFragment()
                    R.id.navigation_settings -> SettingsFragment()
                    else -> throw IllegalStateException("Unknown menu item ID")
                }
                fragmentTransaction.add(R.id.nav_host_fragment, fragment, fragmentTag)
            } else {
                fragmentTransaction.show(fragment)
            }

            activeFragment = fragment
            fragmentTransaction.commit()
            true
        }

        if (savedInstanceState == null) {
            bottomNavView.selectedItemId = R.id.navigation_schedule
        }
    }
}