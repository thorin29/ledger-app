package com.budget.app.data

import com.budget.app.data.remote.AlertDto
import java.time.DayOfWeek
import java.time.LocalDateTime

/** The notifications a person can turn on, grouped as on the Notifications screen. All start off. */
enum class AlertKind(val key: String, val group: String, val label: String, val detail: String) {
    BILL_DUE("billDue", "Bills", "Bill due", "8 AM on the due day"),
    BILL_OVERDUE("billOverdue", "Bills", "Bill overdue", "2 days past due, no payment seen"),
    BILL_AUTO("billAuto", "Bills", "Paid automatically", "When a bill is recorded from the bank"),
    CARD_DUE("cardDue", "Cards", "Card payment due", "2 days before, with the amount to pay"),
    STATEMENT("statementClosed", "Cards", "Statement closed", "The new balance and its due date"),
    PAYDAY("payday", "Money", "Payday", "When pay lands"),
    SHORT("short", "Money", "Checking may run short", "Lowest point under the cushion"),
    LARGE("largeCharge", "Money", "Large charge", "Any charge over \$250"),
    OVER("budgetOver", "Budget", "A budget goes over", "Once per budget each month"),
    RESERVE("reserveUsed", "Budget", "Reserve used up", "Unplanned spending passes the reserve"),
    WEEKLY("weekly", "Budget", "Weekly summary", "Sunday at 6 PM"),
    REVIEW("review", "Ledger", "Items to review", "At most once a day"),
    SYNC("syncStopped", "Ledger", "Bank sync stopped", "No new bank data for 24 hours"),
    AMAZON("amazonSync", "Ledger", "Amazon sync stopped", "A failed sync, or none in 3 days"),
    CARD_FILE("cardFile", "Cards", "Card file due", "Monthly, after the statement closes"),
    UPDATE("update", "Ledger", "App update", "Once per new version");

    val setting: String get() = "alert_$key"

    companion object {
        fun from(key: String): AlertKind? = entries.firstOrNull { it.key == key }
        const val QUIET = "alert_quiet"
        const val SEEN = "alert_seen"
        const val LARGE_CENTS = 25_000L
    }
}

/** Which alerts to post now. Pure, so it is unit-tested. */
object AlertRules {
    /** Quiet hours: 9 PM to 7 AM. */
    fun quiet(now: LocalDateTime): Boolean = now.hour >= 21 || now.hour < 7

    fun pick(
        alerts: List<AlertDto>,
        enabled: Set<String>,
        seen: Set<String>,
        now: LocalDateTime,
        quietHours: Boolean,
    ): List<AlertDto> {
        if (quietHours && quiet(now)) return emptyList()
        return alerts.filter { a ->
            a.kind in enabled && a.key !in seen && when (a.kind) {
                "billDue" -> now.hour >= 8
                "weekly" -> now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18
                "largeCharge" -> (a.cents ?: 0L) >= AlertKind.LARGE_CENTS
                else -> true
            }
        }
    }

    /** Keys to remember: the newest ones, so the list never grows without end. */
    fun remember(seen: List<String>, posted: List<String>, keep: Int = 400): List<String> = (seen + posted).distinct().takeLast(keep)
}
