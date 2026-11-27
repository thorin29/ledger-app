package com.budget.app.data.remote

import kotlinx.serialization.Serializable

/*
 * The phone views (server 0.101+): Today, Budget, the Insights overview, transactions and alerts.
 * The server works these out so the phone and the web always agree. Money is whole cents.
 */

@Serializable
data class AvailableDto(
    val cents: Long = 0,
    val endCents: Long = 0,
    val until: String = "",
    val perDayCents: Long? = null,
    /** "statement" (the main card's statement) or "month". */
    val basis: String = "month",
    val cardId: String? = null,
    val cardName: String? = null,
)

@Serializable
data class CheckingDto(val cents: Long = 0, val payComingCents: Long = 0, val nextPayOn: String? = null, val nextPayCents: Long? = null)

@Serializable
data class StatementDto(
    val cardId: String = "",
    val name: String = "",
    val from: String = "",
    val to: String = "",
    val day: Int = 1,
    val days: Int = 30,
    val chargedCents: Long = 0,
    val billsToComeCents: Long = 0,
    val projectedCents: Long = 0,
    val dueOn: String? = null,
)

@Serializable
data class NeedsYouDto(
    /** pending, overdue, review, over, short */
    val kind: String = "",
    val text: String = "",
    val cents: Long? = null,
    /** tracker, review, budget, insights */
    val target: String = "",
)

@Serializable
data class NextBillDto(val lineItemId: String = "", val name: String = "", val dueOn: String = "", val cents: Long = 0, val status: String = "due")

@Serializable
data class TodayResponse(
    val today: String = "",
    val available: AvailableDto = AvailableDto(),
    val checking: CheckingDto? = null,
    val monthLeftCents: Long = 0,
    val monthDaysLeft: Int = 0,
    val statement: StatementDto? = null,
    val needsYou: List<NeedsYouDto> = emptyList(),
    val nextBills: List<NextBillDto> = emptyList(),
    val bankAsOf: String? = null,
)

@Serializable
data class BudgetViewTab(val key: String = "", val name: String = "")

@Serializable
data class BudgetPeriod(val from: String = "", val to: String = "", val day: Int = 1, val days: Int = 30)

@Serializable
data class BudgetGroupDto(val id: String = "", val name: String = "", val spentCents: Long = 0, val budgetCents: Long? = null, val projectedCents: Long = 0)

@Serializable
data class BudgetBillDto(val name: String = "", val dueOn: String = "", val cents: Long = 0, val budgetedCents: Long = 0, val status: String = "due")

@Serializable
data class BudgetSectionDto(
    /** BILLS, ESSENTIAL, FLEXIBLE, UNPLANNED, SORT */
    val tier: String = "",
    val name: String = "",
    val spentCents: Long = 0,
    val budgetCents: Long? = null,
    val projectedCents: Long = 0,
    val groups: List<BudgetGroupDto> = emptyList(),
    val bills: List<BudgetBillDto> = emptyList(),
)

@Serializable
data class BudgetCardDto(val key: String = "", val name: String = "", val spentCents: Long = 0, val budgetCents: Long = 0, val projectedCents: Long = 0)

@Serializable
data class BudgetResponse(
    val views: List<BudgetViewTab> = emptyList(),
    val view: String = "month",
    val period: BudgetPeriod = BudgetPeriod(),
    val leftCents: Long = 0,
    val leftEndCents: Long = 0,
    val perDayCents: Long? = null,
    val sections: List<BudgetSectionDto> = emptyList(),
    val cards: List<BudgetCardDto> = emptyList(),
    val checksOk: Boolean = true,
)

@Serializable
data class OverviewPartDto(val key: String = "", val name: String = "", val spentCents: Long = 0, val budgetCents: Long? = null, val dueCents: Long = 0)

@Serializable
data class OutlookCaseDto(val beforeCents: Long? = null, val afterCents: Long = 0)

@Serializable
data class OutlookDto(
    val from: String = "",
    val to: String = "",
    val paydayOn: String? = null,
    val worst: OutlookCaseDto = OutlookCaseDto(),
    val best: OutlookCaseDto = OutlookCaseDto(),
)

@Serializable
data class IncomeTotalsDto(val totalCents: Long = 0, val receivedCents: Long = 0)

@Serializable
data class LastMonthDto(val month: String = "", val resultCents: Long = 0)

@Serializable
data class OverviewResponse(
    val month: String = "",
    val income: IncomeTotalsDto = IncomeTotalsDto(),
    val parts: List<OverviewPartDto> = emptyList(),
    val leftCents: Long = 0,
    val overCents: Long = 0,
    val expectedOverCents: Long = 0,
    val outlook: OutlookDto? = null,
    val lastMonth: LastMonthDto? = null,
    val debtCents: Long? = null,
)

@Serializable
data class TxnDto(
    val id: String = "",
    val postedOn: String = "",
    val merchant: String = "",
    val account: String = "",
    val amountCents: Long = 0,
    val pending: Boolean = false,
    /** NORMAL, TRANSFER, IGNORED */
    val kind: String = "NORMAL",
    val categoryId: String? = null,
    val guessed: Boolean = false,
    val peer: Boolean = false,
    /** An Amazon charge split by item (server 0.114.1+). */
    val splits: List<TxnSplitDto> = emptyList(),
    /** Who owes you part or all of this charge (server 0.115+). */
    val owed: OwedDto? = null,
)

@Serializable
data class OwedDto(val personId: String = "", val personName: String = "", val cents: Long = 0)

@Serializable
data class PersonDto(val id: String = "", val name: String = "")

/** Owed to You (server 0.116+). */
@Serializable
data class OwedResponse(val people: List<OwedPersonDto> = emptyList())

@Serializable
data class OwedPersonDto(
    val id: String = "",
    val name: String = "",
    val charges: List<OwedChargeDto> = emptyList(),
    val paybacks: List<OwedPaybackDto> = emptyList(),
    val markedCents: Long = 0,
    val paidCents: Long = 0,
    /** Still owed; negative when they've paid ahead. */
    val openCents: Long = 0,
    /** Money in that adds up exactly to some open charges. */
    val matches: List<OwedMatchDto> = emptyList(),
    /** Other money in that could be theirs. */
    val maybe: List<OwedMatchDto> = emptyList(),
)

@Serializable
data class OwedChargeDto(
    val key: String = "",
    val txnId: String = "",
    val postedOn: String = "",
    val merchant: String = "",
    val categoryName: String? = null,
    val cents: Long = 0,
    val coveredCents: Long = 0,
) {
    val open: Boolean get() = coveredCents < cents
    val leftCents: Long get() = cents - coveredCents
}

@Serializable
data class OwedPaybackDto(val txnId: String = "", val postedOn: String = "", val merchant: String = "", val cents: Long = 0, val linkedCents: Long = 0)

@Serializable
data class OwedMatchDto(val txnId: String = "", val postedOn: String = "", val merchant: String = "", val cents: Long = 0, val chargeKeys: List<String> = emptyList())

@Serializable
data class TxnSplitDto(val categoryId: String? = null, val categoryName: String? = null, val amountCents: Long = 0, val note: String? = null)

@Serializable
data class TxnListResponse(val transactions: List<TxnDto> = emptyList())

@Serializable
data class CategoryDto(
    val id: String = "",
    val name: String = "",
    val active: Boolean = true,
    /** A person who owes you, or money held for others: kept out of your spending. */
    val managedForOthers: Boolean = false,
)

@Serializable
data class CategoriesResponse(val categories: List<CategoryDto> = emptyList())

@Serializable
data class CategoryChangeResponse(val ok: Boolean = true, val others: Int = 0)

@Serializable
data class AlertDto(
    val key: String = "",
    val kind: String = "",
    val title: String = "",
    val body: String = "",
    val open: String = "today",
    val cents: Long? = null,
)

@Serializable
data class AlertsResponse(val alerts: List<AlertDto> = emptyList())

/** A store loyalty card the household shares (server 0.117+). */
@Serializable
data class LoyaltyCardDto(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    /** ZXing's name for the barcode type: CODE_128, UPC_A, QR_CODE, ... */
    val format: String = "CODE_128",
    val color: String = "#334155",
    val memberName: String? = null,
    /** 0 when there's no image. */
    val imageVersion: Int = 0,
    val useCount: Int = 0,
)

@Serializable
data class LoyaltyListResponse(val cards: List<LoyaltyCardDto> = emptyList())
