package dev.alllexey.itmowidgets.feature.dormitory.presentation

import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.text.BalanceLine
import dev.alllexey.itmowidgets.core.text.PaymentPill
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

    /** my.itmo knows no application and no Договор найма. */
    data object NoApplication : DormitoryContent

    /**
     * The application: its stage (null with a Договор найма but no application), the dormitory once assigned, the
     * documents while they matter, the Договоры найма when there are any.
     */
    data class Shown(
        val stage: DormitoryStageCard?,
        val dormitory: DormitoryPlace?,
        val documents: DormitoryDocuments?,
        val contracts: DormitoryContracts? = null,
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

/**
 * The Договоры найма: [current] this academic year's and any Долг, [pastYears] the other earlier years folded into
 * `Прошлые годы` (null without any).
 */
data class DormitoryContracts(val current: List<ContractCard>, val pastYears: PastYears?)

/** `Прошлые годы`: [years] newest first, shown while [expanded]. */
data class PastYears(val expanded: Boolean, val years: List<ContractYearGroup>)

/** One earlier academic year, `2025/2026 учебный год`. */
data class ContractYearGroup(val title: UiText, val contracts: List<ContractCard>)

/**
 * One Договор: [title] its kind, [subtitle] the number and dates, [balance] the Долг, overpayment or zero balance,
 * [payments] the schedule in ITMO's order; [pay] adds `Оплатить`, which opens [DormitoryWeb.PAY_URL] in the browser.
 */
data class ContractCard(
    val id: String,
    val title: UiText,
    val subtitle: UiText,
    val balance: BalanceLine,
    val pay: Boolean,
    val payments: List<PaymentRow>,
)

/** One Платёж: ITMO's [period] label, the [amount], `до 10 октября`, `оплачено 500 ₽` when paid in part, its state. */
data class PaymentRow(val period: String, val amount: UiText, val due: UiText, val paid: UiText?, val pill: PaymentPill?)

/** The dormitory section of my.itmo, where dates are picked and documents uploaded, in the in-app view. */
object DormitoryWeb {
    const val PATH = "/services/dormitories"
    val route: AppRoutes.MyItmoWeb get() = AppRoutes.MyItmoWeb(PATH)

    /**
     * `Оплатить`: the same section in the system browser. The in-app view does not follow the redirect to the bank,
     * and its navigation policy stays as it is.
     */
    const val PAY_URL = "https://my.itmo.ru$PATH"
}
