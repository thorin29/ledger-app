package com.budget.app.data.sync

import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.remote.MonthView
import kotlinx.serialization.Serializable

/**
 * A change made on the phone, waiting to reach the server. Every change goes
 * through the queue — online or not — so there is one path, and an offline
 * change is just one that waits longer.
 *
 * [id] doubles as the Idempotency-Key, so a resend is applied once.
 * [enteredAt] is when Confirm was pressed; the server refuses the write if the
 * entry changed after that.
 */
@Serializable
data class PendingWrite(
    val id: String,
    val type: String,
    val year: Int,
    val month: Int,
    val lineItemId: String? = null,
    val amountCents: Long? = null,
    val paidOn: String? = null,
    val enteredAt: String,
    /** What to call it in the waiting list, e.g. "Electric $172.40". */
    val label: String,
) {
    companion object {
        const val RECORD = "record"
        const val CLEAR = "clear"
        const val BALANCE = "balance"
    }
}

/** A queued change the server refused (changed elsewhere, or no longer valid). */
@Serializable
data class SyncProblem(val id: String, val label: String, val message: String, val at: String)

/**
 * Shows queued changes on top of the last month the server sent, so the
 * screen reflects what was entered even before it syncs. Pure, so it is
 * unit-tested. Totals that depend on the server's planning rules (free after
 * each half) are left as the server last computed them.
 */
object Overlay {
    fun apply(view: MonthView, pending: List<PendingWrite>): MonthView {
        if (pending.isEmpty()) return view
        fun patch(entry: MonthEntry): MonthEntry {
            var result = entry
            for (w in pending) {
                if (w.lineItemId != entry.lineItemId || w.year != entry.year || w.month != entry.month) continue
                result = when (w.type) {
                    PendingWrite.RECORD -> result.copy(actualCents = w.amountCents, paidOn = w.paidOn ?: result.paidOn)
                    PendingWrite.CLEAR -> result.copy(actualCents = null, paidOn = null)
                    else -> result
                }
            }
            return result
        }

        val halves = view.halves.map { half ->
            val entries = half.entries.map(::patch)
            half.copy(
                entries = entries,
                paidCents = entries.sumOf { it.actualCents ?: 0L },
                remainingCents = entries.filter { it.actualCents == null }.sumOf { it.budgetedCents },
            )
        }
        val periods = view.periods?.map { p ->
            val entries = p.entries.map(::patch)
            p.copy(
                entries = entries,
                paidCents = entries.sumOf { it.actualCents ?: 0L },
                remainingCents = entries.filter { it.actualCents == null }.sumOf { it.budgetedCents },
            )
        }
        val balance = pending.lastOrNull { it.type == PendingWrite.BALANCE && it.year == view.year && it.month == view.month }

        return view.copy(
            halves = halves,
            periods = periods,
            carried = view.carried.map(::patch),
            income = view.income.map(::patch),
            surplusItems = view.surplusItems.map(::patch),
            billsBalanceCents = balance?.amountCents ?: view.billsBalanceCents,
        )
    }
}

/** What to do with a queued write after trying to send it. Pure; unit-tested. */
enum class SendOutcome { DONE, REFUSED, RETRY_LATER, SIGNED_OUT }

object SyncRules {
    /** [status] is the HTTP status, or null when the network failed. */
    fun classify(status: Int?): SendOutcome = when {
        status == null -> SendOutcome.RETRY_LATER
        status in 200..299 -> SendOutcome.DONE
        status == 401 -> SendOutcome.SIGNED_OUT
        // Changed elsewhere, invalid, or gone: retrying can't help. Show it.
        status == 409 || status == 422 || status == 404 || status == 400 -> SendOutcome.REFUSED
        // Rate limited, server trouble, anything unexpected: try again later.
        else -> SendOutcome.RETRY_LATER
    }
}
