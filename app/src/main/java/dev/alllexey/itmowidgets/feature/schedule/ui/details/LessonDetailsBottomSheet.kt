package dev.alllexey.itmowidgets.feature.schedule.ui.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.navigation.navigationArgs
import dev.alllexey.itmowidgets.core.navigation.putNavigationArgs
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.navigation.MapLauncher
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.designsystem.host.ItmoBottomSheetFragment
import dev.alllexey.itmowidgets.designsystem.host.SheetHeight
import dev.alllexey.itmowidgets.designsystem.host.SheetSpec
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import dev.alllexey.itmowidgets.feature.schedule.ui.shortTitle
import javax.inject.Inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * One lesson occurrence, drawn by `LessonDetailsSheetRoute` of `:shared:feature-schedule`: what the schedule card
 * shows, in full, plus the map hand-off and the viewer's friends on the same lesson. The Fragment keeps the stable
 * entry points (class name, [TAG], [newInstance], the argument keys) and performs the effects: the `geo:` map, the
 * meeting link, the profile after closing.
 */
@AndroidEntryPoint
class LessonDetailsBottomSheet : ItmoBottomSheetFragment() {

    @Inject lateinit var buildings: BuildingDirectory

    private val viewModel: LessonDetailsViewModel by viewModel()

    private val lesson: LessonDetailsArgs by lazy {
        requireNotNull(requireArguments().navigationArgs<LessonDetailsArgs>(ARG_LESSON))
    }

    /** What the body asks of the host; instrumented tests call it to check the effects without the Compose tree. */
    @VisibleForTesting
    internal val actions = LessonDetailsActions(
        onMap = { mapDestination()?.let { requireContext().openLessonMap(it, requireView()) } },
        onLink = { url -> requireContext().openLessonLink(url, requireView()) },
        onProfile = ::openProfile,
        onClose = ::onCloseRequest,
    )

    private val mapAvailable: Boolean by lazy { mapDestination() != null }

    override val spec: SheetSpec get() = SPEC

    @Composable
    override fun SheetContent() {
        LessonDetailsSheetRoute(
            lesson = lesson,
            mapAvailable = mapAvailable,
            actions = actions,
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
        )
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            it.backgroundTintList = ColorStateList.valueOf(
                requireContext().color.resolve(com.google.android.material.R.attr.colorSurfaceContainerLowest)
            )
            (it.background as? MaterialShapeDrawable)?.elevation = 0f
        }
    }

    /** The profile is a contextual screen above the tabs; the sheet has nothing to add once it opens. */
    private fun openProfile(isu: Int) {
        dismiss()
        openUserProfile(isu)
    }

    private fun mapDestination(): MapDestination? = buildings.lessonMapDestination(lesson, requireContext())

    companion object {
        const val TAG = "LessonDetailsBottomSheet"
        private const val ARG_LESSON = "arg_lesson"

        /** 90 % of the screen however short the content, as the View sheet opened. */
        private val SPEC = SheetSpec(height = SheetHeight.Tall)

        fun newInstance(lesson: Lesson, date: kotlinx.datetime.LocalDate): LessonDetailsBottomSheet =
            newInstance(lesson.toDetailsArgs(date))

        fun newInstance(args: LessonDetailsArgs): LessonDetailsBottomSheet = LessonDetailsBottomSheet().apply {
            arguments = args.viewModelArgs().apply { putNavigationArgs(ARG_LESSON, args) }
        }
    }
}

/**
 * The [LessonDetailsViewModel] arguments of this lesson: its occurrence and, when it is a valid ISU, its teacher.
 * Shared by [LessonDetailsBottomSheet] and the Compose shell's lesson entry.
 */
internal fun LessonDetailsArgs.viewModelArgs(): Bundle = Bundle().apply {
    putLong(LessonDetailsViewModel.ARG_PAIR_ID, pairId)
    putString(LessonDetailsViewModel.ARG_DATE, date)
    UserScreenArgs.profileIsu(teacherIsu)?.let { putInt(LessonDetailsViewModel.ARG_TEACHER_ISU, it) }
}

/**
 * Where the map hand-off of [lesson] points: the directory's building, else the schedule's own building text; null
 * when the lesson names no building. Shared by [LessonDetailsBottomSheet] and the Compose shell's lesson entry.
 */
internal fun BuildingDirectory.lessonMapDestination(lesson: LessonDetailsArgs, context: Context): MapDestination? {
    find(lesson.buildingId, lesson.mainBuildingId, lesson.building)?.let { return it.toMapDestination() }
    val building = lesson.building ?: return null
    return MapDestination(label = Building(building).shortTitle(context), address = building)
}

/** Opens [destination] in a `geo:` handler; without one a snackbar on [anchor] says so. */
internal fun Context.openLessonMap(destination: MapDestination, anchor: View) {
    if (!MapLauncher.open(this, destination)) {
        Snackbar.make(anchor, R.string.schedule_map_unavailable, Snackbar.LENGTH_SHORT).show()
    }
}

/** Opens the lesson's meeting [url]; without a handler a snackbar on [anchor] says so. */
internal fun Context.openLessonLink(url: String, anchor: View) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Snackbar.make(anchor, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
    }
}
