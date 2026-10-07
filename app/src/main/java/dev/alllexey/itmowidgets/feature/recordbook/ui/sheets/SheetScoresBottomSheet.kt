package dev.alllexey.itmowidgets.feature.recordbook.ui.sheets

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.designsystem.host.SheetSpec
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresEvent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * «Мои баллы»: downloads a public Google Sheet, finds the own row and total and connects them, asking only when the
 * sheet leaves a choice: the row, the tab, the total. «Изменить итог» opens it at the choice of the total. The body is
 * `SheetScoresSheet` of `:shared:feature-recordbook`; the Fragment keeps the stable entry points ([TAG],
 * [newInstance]), closes itself once the view model is done and shows a failed save in a snackbar.
 */
@AndroidEntryPoint
class SheetScoresBottomSheet : ItmoBottomSheetFragment() {
    private val viewModel: SheetScoresViewModel by viewModel()

    override val spec: SheetSpec get() = SPEC

    @Composable
    override fun SheetContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SheetScoresSheet(
            subjectName = viewModel.scope.subjectName,
            state = state,
            actions = SheetScoresActions(
                onRetry = { viewModel.refresh(RefreshMode.Force) },
                onPickRow = viewModel::pickRow,
                onPickTab = viewModel::pickTab,
                onPickTotal = viewModel::pickTotal,
            ),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { if (it == SheetScoresUiState.Done) dismiss() }
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { event ->
            when (event) {
                is SheetScoresEvent.SaveFailed ->
                    Snackbar.make(view, event.text.resolve(requireContext()), Snackbar.LENGTH_SHORT).show()
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    companion object {
        const val TAG = "SheetScoresBottomSheet"

        /** As tall as its content up to 90 % of the screen, as the View sheet expanded; the search takes the keyboard. */
        private val SPEC = SheetSpec(textInput = true)

        fun newInstance(args: SheetScoresArgs) = SheetScoresBottomSheet().apply { arguments = args.toBundle() }
    }
}
