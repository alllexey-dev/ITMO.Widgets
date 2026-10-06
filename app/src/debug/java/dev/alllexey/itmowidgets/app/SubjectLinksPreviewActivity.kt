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
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.ui.navigation.AppNavigator
import dev.alllexey.itmowidgets.core.ui.navigation.NoOpAppNavigator
import dev.alllexey.itmowidgets.di.bridge.ResourcesDebugFixtures
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.LinkEditorBottomSheet
import dev.alllexey.itmowidgets.feature.resources.ui.ReportLinkDialogFragment
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksBottomSheet
import org.koin.core.module.Module

/**
 * The real links sheets over an empty window, fed by a test-supplied in-memory repository through
 * [ResourcesDebugFixtures]; never reads a session. [EXTRA_SCREEN] picks the first sheet: `links`, `editor` or
 * `actions` (with [EXTRA_LINK_ID]).
 */
@AndroidEntryPoint
class SubjectLinksPreviewActivity : AppCompatActivity(), AppNavigator by NoOpAppNavigator {
    private lateinit var linksFixture: Module

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = appearance.fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (appearance.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate(): a restored sheet obtains its ViewModel from Koin there.
        linksFixture = ResourcesDebugFixtures.load(this) { repository }
        delegate.localNightMode = if (appearance.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        supportFragmentManager.registerFragmentLifecycleCallbacks(NarrowWindows(), false)
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

    override fun onDestroy() {
        super.onDestroy()
        ResourcesDebugFixtures.unload(this, linksFixture)
    }

    override fun openSubjectLinks(args: SubjectLinksArgs) =
        SubjectLinksBottomSheet.newInstance(args).show(supportFragmentManager, SubjectLinksBottomSheet.TAG)

    override fun openLinkEditor(args: SubjectLinksArgs, linkId: String?) =
        LinkEditorBottomSheet.newInstance(args, linkId).show(supportFragmentManager, LinkEditorBottomSheet.TAG)

    override fun openLinkActions(args: SubjectLinksArgs, linkId: String) =
        LinkActionsBottomSheet.newInstance(args, linkId).show(supportFragmentManager, LinkActionsBottomSheet.TAG)

    override fun openSheetScores(args: SheetScoresArgs) {
        sheetRequests += args
    }

    /** Narrows every sheet's window to the appearance's width. */
    private class NarrowWindows : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, fragment: Fragment) {
            val width = appearance.widthDp.takeIf { it > 0 } ?: return
            val window = (fragment as? DialogFragment)?.dialog?.window ?: return
            window.setLayout((width * fragment.resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_LINK_ID = "link_id"
        const val SCREEN_LINKS = "links"
        const val SCREEN_EDITOR = "editor"
        const val SCREEN_ACTIONS = "actions"
        val ARGS = SubjectLinksArgs(42L, "Математический анализ", "2026-1")

        @Volatile var appearance = PreviewAppearance()
        @Volatile var repository: SubjectLinksRepository = MemorySubjectLinksRepository()
        /** «Мои баллы» the sheets asked for; the sheet itself belongs to the recordbook. */
        val sheetRequests: MutableList<SheetScoresArgs> = java.util.Collections.synchronizedList(mutableListOf())
    }
}
