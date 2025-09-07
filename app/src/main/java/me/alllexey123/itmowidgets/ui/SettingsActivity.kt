package me.alllexey123.itmowidgets.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.alllexey123.itmowidgets.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
}