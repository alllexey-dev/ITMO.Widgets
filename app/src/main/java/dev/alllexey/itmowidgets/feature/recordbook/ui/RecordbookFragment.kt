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
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSelection
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The recordbook tab (`navigation_recordbook`), kept by name for the main graph and the notifications. The screen is
 * `RecordbookRoute` from `:shared:feature-recordbook`; this host opens the period picker and the subject page, takes
 * the picked period back and returns from the BARS sign-in with a refresh.
 */
@AndroidEntryPoint
class RecordbookFragment : Fragment() {
    /** The route's ViewModel: `koinViewModel()` in this Fragment's ComposeView resolves the same instance. */
    private val viewModel: RecordbookViewModel by viewModel()
    private val barsLogin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.refresh(RefreshMode.Force)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView {
            RecordbookRoute(
                onOpenPeriods = { programs, selection ->
                    RecordbookPeriodBottomSheet.show(parentFragmentManager, programs, selection)
                },
                onOpenSubject = ::openSubject,
                onBarsLogin = { barsLogin.launch(Intent(requireContext(), BarsLoginActivity::class.java)) },
                viewModel = viewModel,
            )
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        parentFragmentManager.setFragmentResultListener(RecordbookPeriodBottomSheet.RESULT_KEY, viewLifecycleOwner) { _, result ->
            viewModel.selectPeriod(
                result.getLong(RecordbookPeriodBottomSheet.RESULT_PROGRAM_ID),
                result.getInt(RecordbookPeriodBottomSheet.RESULT_SEMESTER)
            )
        }
        // The route starts the load too; starting it here does not wait for the first composition frame, as the
        // View host did not (a second Silent refresh does nothing).
        viewModel.refresh(RefreshMode.Silent)
    }

    private fun openSubject(selection: RecordbookSelection, subject: RecordbookSubject) {
        val arguments = RecordbookSubjectArgs(
            entryId = subject.entryId,
            programId = selection.program.id,
            semester = selection.period.semester,
            studyYear = selection.period.studyYear,
            barsPlan = subject.barsJournal?.planId,
            barsType = subject.barsJournal?.type,
            barsIdentifier = subject.barsJournal?.identifier
        ).toBundle()
        openScreen(AppScreen.RECORDBOOK_SUBJECT, arguments)
    }
}
