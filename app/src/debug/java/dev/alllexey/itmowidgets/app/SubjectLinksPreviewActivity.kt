package dev.alllexey.itmowidgets.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.LinkEditorBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.ReportLinkDialogFragment
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksBottomSheet

/**
 * The real links sheets over an empty window, fed by a test-supplied in-memory repository; never
 * reads a session. [EXTRA_SCREEN] picks the first sheet: `links`, `editor` or `actions` (with [EXTRA_LINK_ID]).
 */
@AndroidEntryPoint
class SubjectLinksPreviewActivity : AppCompatActivity(), AppNavigator {
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = appearance.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        supportFragmentManager.registerFragmentLifecycleCallbacks(PreviewModels(), false)
        super.onCreate(savedInstanceState)
        appearance.colorSeed?.let {
            DynamicColors.applyToActivityIfAvailable(this, DynamicColorsOptions.Builder().setContentBasedSource(it).build())
        }
        setContentView(FrameLayout(this))
        if (savedInstanceState != null) return
        val linkId = intent.getStringExtra(EXTRA_LINK_ID)
        when (intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_LINKS) {
            SCREEN_EDITOR -> openLinkEditor(ARGS, linkId)
            SCREEN_ACTIONS -> openLinkActions(ARGS, checkNotNull(linkId))
            else -> openSubjectLinks(ARGS)
        }
    }

    override fun openSubjectLinks(args: SubjectLinksArgs) =
        SubjectLinksBottomSheet.newInstance(args).show(supportFragmentManager, SubjectLinksBottomSheet.TAG)

    override fun openLinkEditor(args: SubjectLinksArgs, linkId: String?) =
        LinkEditorBottomSheet.newInstance(args, linkId).show(supportFragmentManager, LinkEditorBottomSheet.TAG)

    override fun openLinkActions(args: SubjectLinksArgs, linkId: String) =
        LinkActionsBottomSheet.newInstance(args, linkId).show(supportFragmentManager, LinkActionsBottomSheet.TAG)

    override fun openScreen(screen: AppScreen, arguments: Bundle?) = Unit

    override fun openWebLogin() = Unit

    override fun openRoot(root: AppRoot) = Unit

    override fun dismissOverlays() = Unit

    override fun openLessonDetails(args: LessonDetailsArgs) = Unit

    override fun openPendingSportDetails(args: PendingSportDetailsArgs) = Unit

    /** Hands every sheet a view model over [repository] before Hilt could create one, and narrows its window. */
    private class PreviewModels : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentPreCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
            val arguments = fragment.arguments ?: return
            @Suppress("DEPRECATION")
            val handle = SavedStateHandle(arguments.keySet().associateWith { arguments.get(it) })
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                    LinkEditorViewModel::class.java -> LinkEditorViewModel(handle, repository)
                    else -> SubjectLinksViewModel(handle, repository)
                } as T
            }
            when (fragment) {
                is LinkEditorBottomSheet -> ViewModelProvider(fragment, factory)[LinkEditorViewModel::class.java]
                is SubjectLinksBottomSheet, is LinkActionsBottomSheet, is ReportLinkDialogFragment ->
                    ViewModelProvider(fragment, factory)[SubjectLinksViewModel::class.java]
            }
        }

        override fun onFragmentStarted(fm: FragmentManager, fragment: Fragment) {
            val width = appearance.widthDp.takeIf { it > 0 } ?: return
            val window = (fragment as? DialogFragment)?.dialog?.window ?: return
            window.setLayout((width * fragment.resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    data class Appearance(val fontScale: Float = 1f, val dark: Boolean = false, val widthDp: Int = 0, val colorSeed: Int? = null)

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_LINK_ID = "link_id"
        const val SCREEN_LINKS = "links"
        const val SCREEN_EDITOR = "editor"
        const val SCREEN_ACTIONS = "actions"
        val ARGS = SubjectLinksArgs(42L, "Математический анализ", "2026-1")

        @Volatile var appearance = Appearance()
        @Volatile var repository: SubjectLinksRepository = MemorySubjectLinksRepository()
    }
}
