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
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.databinding.ActivityMainBinding
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.LinkEditorBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksBottomSheet
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.ui.common.SportCommonDetailsBottomSheet
import dev.alllexey.itmowidgets.feature.weblogin.ui.WebLoginBottomSheet

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

    override fun openSubjectLinks(args: SubjectLinksArgs) {
        if (fragments.isStateSaved || fragments.findFragmentByTag(SubjectLinksBottomSheet.TAG) != null) return
        SubjectLinksBottomSheet.newInstance(args).show(fragments, SubjectLinksBottomSheet.TAG)
    }

    override fun openLinkEditor(args: SubjectLinksArgs, linkId: String?) {
        if (fragments.isStateSaved || fragments.findFragmentByTag(LinkEditorBottomSheet.TAG) != null) return
        LinkEditorBottomSheet.newInstance(args, linkId).show(fragments, LinkEditorBottomSheet.TAG)
    }

    override fun openLinkActions(args: SubjectLinksArgs, linkId: String) {
        if (fragments.isStateSaved || fragments.findFragmentByTag(LinkActionsBottomSheet.TAG) != null) return
        LinkActionsBottomSheet.newInstance(args, linkId).show(fragments, LinkActionsBottomSheet.TAG)
    }

    override fun openWebLogin() {
        if (fragments.isStateSaved || fragments.findFragmentByTag(WebLoginBottomSheet.TAG) != null) return
        WebLoginBottomSheet().show(fragments, WebLoginBottomSheet.TAG)
    }

    override fun openLessonDetails(args: LessonDetailsArgs) {
        if (fragments.isStateSaved || fragments.findFragmentByTag(LessonDetailsBottomSheet.TAG) != null) return
        LessonDetailsBottomSheet.newInstance(args).show(fragments, LessonDetailsBottomSheet.TAG)
    }

    /** The schedule's own fallback sheet, for a queue the sport data does not know yet. */
    override fun openPendingSportDetails(args: PendingSportDetailsArgs) {
        if (fragments.isStateSaved || sportSheetShown()) return
        PendingSportDetailsBottomSheet.newInstance(args).show(fragments, PendingSportDetailsBottomSheet.TAG)
    }

    /** The sport tab's full sheet with its actions; results arrive on the Activity's FragmentManager. */
    fun openSportDetails(item: SportCommon) {
        if (fragments.isStateSaved || sportSheetShown()) return
        SportCommonDetailsBottomSheet.newInstance(item, actionsEnabled = true).show(fragments, SportCommonDetailsBottomSheet.TAG)
    }

    private fun sportSheetShown(): Boolean =
        fragments.findFragmentByTag(PendingSportDetailsBottomSheet.TAG) != null ||
            fragments.findFragmentByTag(SportCommonDetailsBottomSheet.TAG) != null

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
