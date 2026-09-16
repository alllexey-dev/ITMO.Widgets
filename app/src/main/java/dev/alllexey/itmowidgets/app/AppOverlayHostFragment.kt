package dev.alllexey.itmowidgets.app

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import androidx.transition.Slide
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.ScreenTransitionHost

/** One full-screen surface above the unchanged root host and bottom navigation. */
class AppOverlayHostFragment : NavHostFragment(), ScreenTransitionHost {
    override fun onCreate(savedInstanceState: Bundle?) {
        enterTransition = Slide(Gravity.END).apply { duration = 220 }
        returnTransition = Slide(Gravity.END).apply { duration = 220 }
        val screen = AppScreen.valueOf(requireArguments().getString(SCREEN)!!)

        // Restore the controller before creating its child Fragments. setGraph consumes its
        // pending saved back stack, so configuration changes retain the exact overlay level.
        val graph = navController.navInflater.inflate(R.navigation.overlay_nav_graph)
        graph.setStartDestination(screen.destinationId)
        navController.setGraph(graph, requireArguments().getBundle(SCREEN_ARGUMENTS))
        // Use the restored top destination, not the original entry screen.
        if (navController.currentDestination?.id == R.id.settings) postponeEnterTransition()
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return checkNotNull(super.onCreateView(inflater, container, savedInstanceState)).apply {
            setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            (this as ViewGroup).isTransitionGroup = true
            // Empty space must not pass taps or focus through to the covered root/navigation bar.
            isClickable = true
            isFocusableInTouchMode = true
        }
    }

    override fun onContentReady() {
        startPostponedEnterTransition()
    }

    companion object {
        private const val SCREEN = "screen"
        private const val SCREEN_ARGUMENTS = "screen_arguments"

        fun create(screen: AppScreen, arguments: Bundle?) = AppOverlayHostFragment().apply {
            val initialArguments = Bundle(arguments ?: Bundle.EMPTY).apply {
                putBoolean(ScreenTransitionHost.ARG_OVERLAY_ROOT, true)
            }
            this.arguments = bundleOf(SCREEN to screen.name, SCREEN_ARGUMENTS to initialArguments)
        }
    }
}

internal val AppScreen.destinationId: Int
    get() = when (this) {
        AppScreen.SETTINGS -> R.id.settings
        AppScreen.DEBUG_TOOLS -> R.id.debug_tools
        AppScreen.RECORDBOOK_SUBJECT -> R.id.recordbook_subject
        AppScreen.APP_UPDATE -> R.id.app_update
        AppScreen.QR_PASS -> R.id.qr_pass
        AppScreen.FRIENDS -> R.id.friends
        AppScreen.USER_FRIENDS -> R.id.user_friends
        AppScreen.USER_SEARCH -> R.id.user_search
        AppScreen.USER_PROFILE -> R.id.user_profile
        AppScreen.USER_SCHEDULE -> R.id.user_schedule
        AppScreen.USER_SPORT -> R.id.user_sport
    }
