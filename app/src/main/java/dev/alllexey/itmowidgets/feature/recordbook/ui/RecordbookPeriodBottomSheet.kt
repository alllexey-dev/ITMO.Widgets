package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSelection

/**
 * The period picker of the recordbook, drawn by `RecordbookPeriodSheetContent` of `:shared:feature-recordbook` as tall
 * as its periods. The picked period goes back as the [RESULT_KEY] Fragment result; the arguments, the tag and the
 * result keys are the View sheet's.
 */
class RecordbookPeriodBottomSheet : ItmoBottomSheetFragment() {

    private val options: List<RecordbookPeriodOption> by lazy { readOptions() }

    @Composable
    override fun SheetContent() {
        val arguments = requireArguments()
        RecordbookPeriodSheetContent(
            options = options,
            programName = arguments.getString(ARG_PROGRAM_NAME).orEmpty(),
            selectedProgram = arguments.getLong(ARG_SELECTED_PROGRAM),
            selectedSemester = arguments.getInt(ARG_SELECTED_SEMESTER),
            onSelect = ::select,
            onClose = ::onCloseRequest,
        )
    }

    private fun readOptions(): List<RecordbookPeriodOption> {
        val arguments = requireArguments()
        val programIds = arguments.getLongArray(ARG_PROGRAM_IDS) ?: longArrayOf()
        val semesters = arguments.getIntArray(ARG_SEMESTERS) ?: intArrayOf()
        val courses = arguments.getIntArray(ARG_COURSES) ?: intArrayOf()
        val years = arguments.getStringArrayList(ARG_YEARS).orEmpty()
        val actual = arguments.getBooleanArray(ARG_ACTUAL) ?: booleanArrayOf()
        val names = arguments.getStringArrayList(ARG_PROGRAM_NAMES).orEmpty()
        return semesters.indices.map { index ->
            RecordbookPeriodOption(
                programId = programIds[index],
                semester = semesters[index],
                course = courses[index],
                studyYear = years[index],
                actual = actual[index],
                programName = names.getOrElse(index) { "" }
            )
        }
    }

    private fun select(option: RecordbookPeriodOption) {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(
                RESULT_PROGRAM_ID to option.programId,
                RESULT_SEMESTER to option.semester
            )
        )
        dismiss()
    }

    companion object {
        const val RESULT_KEY = "recordbook_period_result"
        const val RESULT_PROGRAM_ID = "recordbook_program_id"
        const val RESULT_SEMESTER = "recordbook_semester"
        private const val TAG = "RecordbookPeriodBottomSheet"
        private const val ARG_PROGRAM_NAME = "program_name"
        private const val ARG_PROGRAM_NAMES = "program_names"
        private const val ARG_PROGRAM_IDS = "program_ids"
        private const val ARG_SEMESTERS = "semesters"
        private const val ARG_COURSES = "courses"
        private const val ARG_YEARS = "years"
        private const val ARG_ACTUAL = "actual"
        private const val ARG_SELECTED_PROGRAM = "selected_program"
        private const val ARG_SELECTED_SEMESTER = "selected_semester"

        fun show(
            manager: FragmentManager,
            programs: List<RecordbookProgram>,
            selection: RecordbookSelection
        ) {
            if (manager.findFragmentByTag(TAG) != null) return
            val options = recordbookPeriodOptions(programs)
            RecordbookPeriodBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROGRAM_NAME, selection.program.name)
                    putStringArrayList(ARG_PROGRAM_NAMES, ArrayList(options.map { it.programName }))
                    putLongArray(ARG_PROGRAM_IDS, options.map { it.programId }.toLongArray())
                    putIntArray(ARG_SEMESTERS, options.map { it.semester }.toIntArray())
                    putIntArray(ARG_COURSES, options.map { it.course }.toIntArray())
                    putStringArrayList(ARG_YEARS, ArrayList(options.map { it.studyYear }))
                    putBooleanArray(ARG_ACTUAL, options.map { it.actual }.toBooleanArray())
                    putLong(ARG_SELECTED_PROGRAM, selection.program.id)
                    putInt(ARG_SELECTED_SEMESTER, selection.period.semester)
                }
            }.show(manager, TAG)
        }
    }
}
