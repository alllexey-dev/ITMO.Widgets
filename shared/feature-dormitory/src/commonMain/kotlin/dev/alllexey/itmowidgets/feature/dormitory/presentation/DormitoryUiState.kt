package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.text.RichText
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * The `Общежитие` screen (docs/features/dormitory.md). [stale] is `Обновлено 2 ч назад` over the stored answer after a
 * refresh failed; [refreshing] a pull in flight.
 */
data class DormitoryUiState(
    val content: DormitoryContent = DormitoryContent.Loading,
    val refreshing: Boolean = false,
    val stale: UiText? = null,
)

sealed interface DormitoryContent {
    /** The first answer is on its way and none is stored. */
    data object Loading : DormitoryContent

    /** Nothing stored and the refresh failed: [text] says why, the screen offers `Повторить`. */
    data class Failed(val text: UiText) : DormitoryContent

    /** my.itmo knows no application. */
    data object NoApplication : DormitoryContent

    /** The application: its stage, the dormitory once assigned, the documents while they matter. */
    data class Shown(
        val stage: DormitoryStageCard,
        val dormitory: DormitoryPlace?,
        val documents: DormitoryDocuments?,
    ) : DormitoryContent
}

/** How a stage reads: [ATTENTION] the student must act (`Место предоставлено`), [DONE] settled in. */
enum class DormitoryTone { NEUTRAL, ATTENTION, DONE }

/**
 * The Этап общежития: [title] the stage, [text] what it means, [queuePlace] `Место в очереди: 42`; [chooseDate] adds
 * `Выбрать дату заселения`, which opens [DormitoryWeb.route]; [seen] is since when the app has seen `Место
 * предоставлено`, never a deadline.
 */
data class DormitoryStageCard(
    val title: UiText,
    val text: UiText,
    val tone: DormitoryTone,
    val queuePlace: UiText? = null,
    val chooseDate: Boolean = false,
    val seen: UiText? = null,
)

/** The assigned dormitory with its address for `На карте` and the settlement instructions as safe text. */
data class DormitoryPlace(val name: String, val address: String, val map: MapDestination, val instructions: RichText)

/** The Документы для заселения; [upload] adds `Загрузить` while one is not confirmed. */
data class DormitoryDocuments(val rows: List<DormitoryDocumentRow>, val upload: Boolean)

/** One document: its name, ITMO's status label, and whether ITMO confirmed it. */
data class DormitoryDocumentRow(val name: String, val status: String, val confirmed: Boolean)

/** The dormitory section of my.itmo, where dates are picked and documents uploaded, in the in-app view. */
object DormitoryWeb {
    const val PATH = "/services/dormitories"
    val route: AppRoutes.MyItmoWeb get() = AppRoutes.MyItmoWeb(PATH)
}
