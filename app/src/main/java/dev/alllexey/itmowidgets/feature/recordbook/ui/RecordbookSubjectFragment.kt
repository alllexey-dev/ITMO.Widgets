package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkActions
import dev.alllexey.itmowidgets.core.ui.navigation.openLinkEditor
import dev.alllexey.itmowidgets.core.ui.navigation.openSheetScores
import dev.alllexey.itmowidgets.core.ui.navigation.openSubjectLinks
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.core.ui.openLink
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.RecordbookSubjectExits
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.RecordbookSubjectRoute
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The subject page (`recordbook_subject` in the overlay graph), kept by name for the graph and the notifications. The
 * screen is `RecordbookSubjectRoute` from `:shared:feature-recordbook`; this host opens links, the link sheets,
 * `Мои баллы`, teacher profiles and the BARS sign-in, and refreshes the page after a completed sign-in.
 */
@AndroidEntryPoint
class RecordbookSubjectFragment : Fragment() {
    /** The route's ViewModel, created with this Fragment's arguments as its saved state. */
    private val viewModel: RecordbookSubjectViewModel by viewModel()
    private val barsLogin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.refresh(RefreshMode.Force)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val exits = RecordbookSubjectExits(
            onBack = { closeScreen() },
            onOpenLink = { url -> openLink(url, requireView()) },
            onLinkActions = { args, linkId -> openLinkActions(args, linkId) },
            onAllLinks = { openSubjectLinks(it) },
            onAddLink = { openLinkEditor(it) },
            onOpenTeacher = { openUserProfile(it) },
            onOpenSheetScores = { openSheetScores(it) },
            onBarsLogin = { barsLogin.launch(Intent(requireContext(), BarsLoginActivity::class.java)) },
        )
        val semester = requireArguments().getInt(RecordbookSubjectArgs.SEMESTER)
        return itmoComposeView { RecordbookSubjectRoute(semester, exits, viewModel) }
    }
}
