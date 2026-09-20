package dev.alllexey.itmowidgets.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.navigation.FloatingWindow
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.databinding.ActivityMainBinding

class MainNavigationCoordinator(
    private val binding: ActivityMainBinding,
    private val fragments: FragmentManager,
    val rootHost: NavHostFragment
) : AppNavigator {
    val overlayHost: AppOverlayHostFragment?
        get() = (fragments.findFragmentByTag(OVERLAY) as? AppOverlayHostFragment)?.takeUnless { it.isRemoving }

    init {
        // Material navigation bars may have elevation; sibling order alone is not enough.
        binding.overlayContainer.translationZ = binding.bottomNavView.z + 1f
        binding.bottomNavView.setupWithNavController(rootHost.navController)
        binding.bottomNavView.setOnItemSelectedListener { selectRoot(it.itemId) }
        binding.bottomNavView.setOnItemReselectedListener { selectRoot(it.itemId) }
        fragments.addOnBackStackChangedListener(::updateAccessibility)
        // The root host must not reclaim Back from a restored overlay via defaultNavHost.
        fragments.beginTransaction().setPrimaryNavigationFragment(overlayHost ?: rootHost).commit()
        updateAccessibility()
    }

    override fun openScreen(screen: AppScreen, arguments: Bundle?) {
        if (fragments.isStateSaved) return
        fragments.executePendingTransactions()
        overlayHost?.let {
            it.navController.navigate(screen.destinationId, arguments)
            return
        }
        openOverlay(screen, arguments)
    }

    private fun openOverlay(screen: AppScreen, arguments: Bundle?) {
        val host = AppOverlayHostFragment.create(screen, arguments)
        fragments.beginTransaction()
            .setReorderingAllowed(true)
            .add(R.id.overlay_container, host, OVERLAY)
            .setMaxLifecycle(rootHost, Lifecycle.State.STARTED)
            .setPrimaryNavigationFragment(host)
            .addToBackStack(OVERLAY)
            .commit()
    }

    /** All root changes, including widget intents and re-selection, discard contextual history. */
    fun selectRoot(destination: Int): Boolean {
        if (destination !in ROOTS || fragments.isStateSaved) return false
        dismissOverlays()
        val controller = rootHost.navController
        while (controller.currentDestination is FloatingWindow) {
            if (!controller.popBackStack()) break
        }
        if (controller.currentDestination?.id == destination) return true
        val item = binding.bottomNavView.menu.findItem(destination) ?: return false
        return NavigationUI.onNavDestinationSelected(item, controller)
    }

    override fun openRoot(root: AppRoot) {
        selectRoot(root.destinationId)
    }

    private val AppRoot.destinationId: Int
        get() = when (this) {
            AppRoot.RECORDBOOK -> R.id.navigation_recordbook
            AppRoot.SCHEDULE -> R.id.navigation_schedule
            AppRoot.HOME -> R.id.navigation_home
            AppRoot.SPORT -> R.id.navigation_sport
            AppRoot.ME -> R.id.navigation_me
        }

    override fun dismissOverlays() {
        if (fragments.isStateSaved) return
        fragments.executePendingTransactions()
        fragments.popBackStackImmediate(OVERLAY, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        updateAccessibility()
    }

    private fun updateAccessibility() {
        val importance = if (overlayHost != null) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        binding.navHostFragment.importantForAccessibility = importance
        binding.bottomNavView.importantForAccessibility = importance
    }

    companion object {
        private const val OVERLAY = "app-overlay"
        val ROOTS = setOf(
            R.id.navigation_recordbook,
            R.id.navigation_schedule,
            R.id.navigation_home,
            R.id.navigation_sport,
            R.id.navigation_me
        )
    }
}
