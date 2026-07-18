package dev.alllexey.itmowidgets.core.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.ActivityMainBinding

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        enableEdgeToEdge()
        setContentView(view)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right
            )
            insets
        }

        val navView = binding.bottomNavView
        if (savedInstanceState == null) {
            navView.selectedItemId = R.id.navigation_home
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHostFragment.navController
        navView.setupWithNavController(navController)
    }
}
