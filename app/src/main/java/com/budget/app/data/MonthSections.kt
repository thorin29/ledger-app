package com.budget.app.data

import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.remote.MonthView

/**
 * The Tracker's sections, as on the web: owed from earlier, then each half (or each paycheck, or
 * the whole month, per Pay Settings), left-over, income, and card payments together in Cards.
 * Each section's line ("$X paid · $Y pending") counts what it pays, card payments included, as on
 * the web. Pure, so it is unit-tested.
 */
object MonthSections {
    data class Section(
        val key: String,
        val title: String,
        val rows: List<MonthEntry>,
        val danger: Boolean = false,
        /** The bold start of the section's line, and the rest. */
        val lead: String? = null,
        val rest: String = "",
        /** The bar: done of total. */
        val done: Long = 0,
        val total: Long = 0,
    )

    private fun key(e: MonthEntry) = "${e.lineItemId}-${e.year}-${e.month}"

    fun of(view: MonthView): List<Section> = buildList {
        val bills = { rows: List<MonthEntry> -> rows.filterNot { it.isCardPayment } }
        val periodEntries = view.periods?.flatMap { it.entries }.orEmpty()
        val cards = (view.carried + view.halves.flatMap { it.entries } + periodEntries + view.surplusItems)
            .filter { it.isCardPayment }
            .distinctBy(::key)
        if (bills(view.carried).isNotEmpty()) add(Section("carried", "Still owed from earlier", bills(view.carried), danger = true))
        val periods = view.periods
        when {
            periods != null -> periods.forEachIndexed { i, p ->
                val title = when {
                    p.payday != null -> "${Dates.short(p.payday)} Paycheck"
                    p.fromPayday != null -> "${Dates.short(p.fromPayday)} Paycheck (Cont.)"
                    else -> "Before the First Payday"
                } + if (p.current) " · Now" else ""
                add(Section("period-$i", title, bills(p.entries), lead = "${Money.format(p.paidCents)} paid", rest = " · ${Money.format(p.remainingCents)} pending", done = p.budgetedCents - p.remainingCents, total = p.budgetedCents))
            }
            view.wholeMonth -> {
                val paid = view.halves.sumOf { it.paidCents }
                val left = view.halves.sumOf { it.remainingCents }
                val budgeted = view.halves.sumOf { it.budgetedCents }
                add(Section("half-0", "This Month", bills(view.halves.flatMap { it.entries }), lead = "${Money.format(paid)} paid", rest = " · ${Money.format(left)} pending", done = budgeted - left, total = budgeted))
            }
            else -> view.halves.forEach { h ->
                add(
                    Section(
                        "half-${h.half.index}", h.half.label.ifBlank { "Half ${h.half.index + 1}" }, bills(h.entries),
                        lead = "${Money.format(h.paidCents)} paid", rest = " · ${Money.format(h.remainingCents)} pending",
                        done = h.budgetedCents - h.remainingCents, total = h.budgetedCents,
                    ),
                )
            }
        }
        if (bills(view.surplusItems).isNotEmpty()) {
            add(Section("surplus", "Paid from what's left over", bills(view.surplusItems), lead = "${Money.format(view.surplusPaidCents)} paid", rest = " of ${Money.format(view.surplusBudgetedCents)} planned", done = view.surplusPaidCents, total = view.surplusBudgetedCents))
        }
        if (view.income.isNotEmpty()) {
            add(Section("income", "Income", view.income, lead = "${Money.format(view.incomeReceivedCents)} received", rest = " of ${Money.format(view.incomeExpectedCents)}", done = view.incomeReceivedCents, total = view.incomeExpectedCents))
        }
        if (cards.isNotEmpty()) {
            val paid = cards.sumOf { it.actualCents ?: 0L }
            // A balance transfer's $0 line isn't a planned payment.
            val planned = cards.filterNot { it.paidNote != null && (it.actualCents ?: 0L) == 0L }.sumOf { it.budgetedCents }
            add(Section("cards", "Cards", cards, lead = "${Money.format(paid)} paid", rest = " of ${Money.format(planned)} planned", done = paid, total = planned))
        }
    }
}
