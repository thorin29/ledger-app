package com.budget.app.data

import com.budget.app.data.remote.MonthEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How loudly a status reads: plain, needs attention soon, or late. */
enum class Tone { MUTED, WARN, DANGER }

data class EntryStatus(val text: String, val tone: Tone)

/** The short line under an entry's name. Pure, so it is unit-tested. */
object EntryStatuses {
    /** Recorded on its own from a bank charge. */
    private fun auto(e: MonthEntry): String = if (e.autoPaid) " · auto" else ""

    fun of(e: MonthEntry, today: LocalDate = LocalDate.now()): EntryStatus {
        val due = Dates.dueDate(e.year, e.month, e.dueDay)
        val dueText = due?.let { Dates.short(it.toString()) }
        if (e.isIncome) {
            return if (e.settled) EntryStatus((Dates.short(e.paidOn)?.let { "Received $it" } ?: "Received") + auto(e), Tone.MUTED)
            else EntryStatus(dueText?.let { "Expected $it" } ?: "Expected", Tone.MUTED)
        }
        if (e.settled) return EntryStatus((Dates.short(e.paidOn)?.let { "Paid $it" } ?: "Paid") + auto(e), Tone.MUTED)
        // Its payment is already at the bank: pending, or posted and waiting to be matched.
        e.pendingCharge?.let { return EntryStatus(if (it.pending) "Pending at the bank" else "At the bank, waiting to match", Tone.MUTED) }
        if (e.carriedFrom != null) return EntryStatus("Still owed from ${Dates.monthName(e.year, e.month)}", Tone.DANGER)
        if (due == null || dueText == null) return EntryStatus("Not paid yet", Tone.MUTED)
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            // Overdue only two days past due: the bank feed is often a day or two behind.
            days < -2 -> EntryStatus("Overdue · was due $dueText", Tone.DANGER)
            days == -1L -> EntryStatus("Due yesterday", Tone.WARN)
            days < 0 -> EntryStatus("Due ${-days} days ago", Tone.WARN)
            days == 0L -> EntryStatus("Due today", Tone.WARN)
            days <= 3 -> EntryStatus("Due $dueText", Tone.WARN)
            else -> EntryStatus("Due $dueText", Tone.MUTED)
        }
    }

    /**
     * A paid card payment's line, in parts joined with " · ": the date, what was planned, how much
     * over, and why ("balance transfer", "after bills", "auto"). Only the "over" part is [Tone.WARN].
     */
    fun cardPaid(e: MonthEntry): List<EntryStatus> {
        val paid = e.actualCents ?: return emptyList()
        val over = paid - e.budgetedCents
        return listOfNotNull(
            EntryStatus(Dates.short(e.paidOn)?.let { "Paid $it" } ?: "Paid", Tone.MUTED),
            if (e.budgetedCents > 0) EntryStatus("of ${Money.format(e.budgetedCents)}", Tone.MUTED) else null,
            if (e.budgetedCents > 0 && over > 0) EntryStatus("${Money.format(over)} over", Tone.WARN) else null,
            when {
                !e.paidNote.isNullOrBlank() -> e.paidNote.lowercase()
                e.paidFromSurplus -> "after bills"
                e.autoPaid -> "auto"
                else -> null
            }?.let { EntryStatus(it, Tone.MUTED) },
        )
    }
}
