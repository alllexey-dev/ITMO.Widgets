package dev.alllexey.itmowidgets.feature.settings.ui

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportEvent
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportViewModel
import dev.alllexey.itmowidgets.feature.settings.ui.ics.IcsExportActions
import dev.alllexey.itmowidgets.feature.settings.ui.ics.IcsExportSheetContent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * «Выгрузить в .ics»: `IcsExportSheetContent` of `:shared:feature-settings` over the Koin `IcsExportViewModel`, as
 * tall as its content. The host keeps what only Android does: the date range picker for «Свои даты» (listened to
 * again after recreation) and the share and view intents of the written file, which the FileProvider
 * `${applicationId}.files` serves from `cacheDir/ics`.
 */
@AndroidEntryPoint
class IcsExportBottomSheet : ItmoBottomSheetFragment() {

    private val viewModel: IcsExportViewModel by viewModel()

    private val actions = IcsExportActions(
        onChoose = { kind -> viewModel.choose(kind) },
        onSend = { file -> shareIcs(file) },
        onOpen = { file -> openIcs(file) },
        onChooseAnother = { viewModel.chooseAnother() },
        onRetry = { viewModel.retry() },
    )

    @Composable
    override fun SheetContent() {
        val state by viewModel.uiState.collectAsState()
        IcsExportSheetContent(state, actions, canOpen = { file -> canOpenIcs(file) })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listenToIcsDatePicker(viewModel::onDates)
        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { event ->
            when (event) {
                IcsExportEvent.PickDates -> showIcsDatePicker(viewModel::onDates)
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    companion object {
        const val TAG = "IcsExportBottomSheet"
    }
}
