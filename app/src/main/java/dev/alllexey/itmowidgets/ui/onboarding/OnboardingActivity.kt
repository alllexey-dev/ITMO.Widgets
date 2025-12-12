package dev.alllexey.itmowidgets.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.main.MainActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: Button
    private lateinit var btnSkip: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.onboarding_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewPager = findViewById(R.id.view_pager)
        btnNext = findViewById(R.id.btn_next)
        btnSkip = findViewById(R.id.btn_skip)
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)

        val adapter = OnboardingAdapter(this)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = false

        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()

        btnNext.setOnClickListener {
            val current = viewPager.currentItem
            val total = viewPager.adapter?.itemCount ?: 0
            if (!skippedToEnd && current < total - 1) {
                viewPager.currentItem = current + 1
                skippedToEnd = skippedToEnd || current == total - 2
                updateButtons()
            } else {
                finishOnboarding()
            }
        }

        btnSkip.setOnClickListener {
            finishOnboarding()
        }

        updateButtons()
    }

    fun nextPage() {
        if (viewPager.currentItem < (viewPager.adapter?.itemCount ?: 0) - 1) {
            viewPager.currentItem = viewPager.currentItem + 1
            updateButtons()
        } else {
            finishOnboarding()
        }
    }

    private var skippedToEnd = false

    private fun updateButtons() {
        if (skippedToEnd) {
            btnNext.text = "Завершить"
            btnSkip.visibility = View.INVISIBLE
        }
    }

    private fun finishOnboarding() {
        val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
        appContainer.storage.utility.setOnboardingCompleted(true)

        val mainActivityIntent = Intent(this, MainActivity::class.java).putExtra("onboarding", true)
        startActivity(mainActivityIntent)
        finish()
    }
}