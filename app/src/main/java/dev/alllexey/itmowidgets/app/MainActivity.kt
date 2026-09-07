package dev.alllexey.itmowidgets.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.navOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.databinding.ActivityMainBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        enableEdgeToEdge()
        setContentView(view)

        val navView = binding.bottomNavView
        val initialBottomNavPadding = navView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = 0
            )
            navView.updatePadding(bottom = initialBottomNavPadding + systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHostFragment.navController
        navView.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (sessionRepository.state.value is SessionState.SignedIn) {
                navView.isVisible = destination.id !in destinationsWithoutBottomNavigation
            }
        }

        lifecycleScope.launch { sessionRepository.initialize() }
        sessionRepository.state
            .flowWithLifecycle(lifecycle)
            .onEach { state -> renderSession(navController, state) }
            .launchIn(lifecycleScope)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (
            intent.action == ACTION_OPEN_SCHEDULE &&
            sessionRepository.state.value is SessionState.SignedIn
        ) {
            binding.bottomNavView.selectedItemId = R.id.navigation_schedule
        }
    }

    private fun renderSession(navController: NavController, state: SessionState) {
        when (state) {
            SessionState.Initializing -> {
                binding.bottomNavView.isVisible = false
                binding.navHostFragment.isVisible = false
                binding.sessionProgress.isVisible = true
            }

            SessionState.SigningOut,
            SessionState.SignedOut,
            SessionState.ReauthenticationRequired -> {
                binding.bottomNavView.isVisible = false
                if (navController.currentDestination?.id != R.id.auth) {
                    navController.setGraph(R.navigation.main_nav_graph)
                }
                revealResolvedGraph()
            }

            is SessionState.SignedIn -> {
                val destination = if (intent.action == ACTION_OPEN_SCHEDULE) {
                    R.id.navigation_schedule
                } else {
                    R.id.navigation_home
                }
                when (navController.currentDestination?.id) {
                    null -> {
                        val graph = navController.navInflater.inflate(
                            R.navigation.main_nav_graph
                        )
                        graph.setStartDestination(destination)
                        navController.graph = graph
                    }

                    R.id.auth -> navController.navigate(
                        destination,
                        null,
                        navOptions {
                            popUpTo(R.id.auth) { inclusive = true }
                            launchSingleTop = true
                        }
                    )
                }
                binding.bottomNavView.isVisible = navController.currentDestination
                    ?.id
                    ?.let { destinationId ->
                        destinationId !in destinationsWithoutBottomNavigation
                    }
                    ?: false
                revealResolvedGraph()
            }
        }
    }

    private fun revealResolvedGraph() {
        supportFragmentManager.executePendingTransactions()
        binding.sessionProgress.isVisible = false
        binding.navHostFragment.isVisible = true
    }

    companion object {
        const val ACTION_OPEN_SCHEDULE =
            "dev.alllexey.itmowidgets.action.OPEN_SCHEDULE"

        private val destinationsWithoutBottomNavigation = setOf(
            R.id.settings,
            R.id.debug_tools,
            R.id.recordbook_subject
        )
    }
}
