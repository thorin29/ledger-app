package com.budget.app.data

import com.budget.app.data.remote.CardDto
import com.budget.app.data.remote.CardStatusDto
import com.budget.app.data.remote.CardsResponse

/**
 * The card stack: the summary first, then each card. Pure, so it is
 * unit-tested; the screen only draws it.
 */
object CardLogic {
    /** One face of the stack. [card] is null for the summary. */
    data class Face(val key: String, val card: CardDto?)

    fun faces(r: CardsResponse): List<Face> = listOf(Face("summary", null)) + r.cards.map { Face(it.id, it) }

    /** Swiping sends the front face to the back. */
    fun <T> next(order: List<T>): List<T> = if (order.size < 2) order else order.drop(1) + order.first()

    /** Swiping the other way brings the back face to the front. */
    fun <T> previous(order: List<T>): List<T> = if (order.size < 2) order else listOf(order.last()) + order.dropLast(1)

    /** The card's name without the trailing account digits, which the face shows on their own. */
    fun faceName(name: String): String {
        val tail = Regex("""[\s\-–]*\(?\.{0,3}\d{3,4}\)?\s*$""")
        var out = name
        // Feeds often repeat the digits ("Card - 1234 (1234)").
        repeat(2) { out = out.replace(tail, "").trim() }
        return out.ifEmpty { name }
    }

    /** "#2b2f36" as an ARGB value for Color(...). */
    fun argb(hex: String): Long {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return 0xFF2B2F36
        return (0xFF000000 or clean.toLong(16))
    }

    /** The line above the big number. */
    fun lead(card: CardDto): Pair<String, Long> {
        val s = card.status ?: return "Balance" to card.owedNowCents
        val due = Dates.short(s.nextDue) ?: s.nextDue
        return when (s.plan) {
            "paid" -> "Nothing due · next $due" to 0L
            "payoff" -> "Payment by $due" to s.suggestedCents
            else -> "Pay by $due" to s.suggestedCents
        }
    }

    /** On a 0% card: when the offer ends and how many payments are left. */
    fun promoLine(s: CardStatusDto): String? {
        if (s.plan != "payoff" || s.promoEndsOn == null || s.paymentsLeft == null) return null
        val end = runCatching { java.time.LocalDate.parse(s.promoEndsOn) }.getOrNull() ?: return null
        val month = end.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
        val pace = s.monthlyCents?.let { " · ${Money.whole(it)}/mo clears it" } ?: ""
        return "0% until $month ${end.dayOfMonth}, ${end.year} · ${s.paymentsLeft} payments left$pace"
    }

    /** Across cards: on current statements, and what's new since they closed. */
    fun onStatements(r: CardsResponse): Long = r.cards.sumOf { it.status?.dueCents ?: 0L }
    fun sinceStatements(r: CardsResponse): Long = r.cards.sumOf { it.status?.newChargesCents ?: 0L }

    /** "Pay this cycle", as on the web's card table: the amount (null when nothing is due) and its note. */
    fun payThisCycle(s: CardStatusDto): Pair<Long?, String> = when {
        s.plan == "paid" || s.suggestedCents <= 0L -> null to "nothing due"
        s.promoEndsOn != null && s.monthlyCents != null -> s.suggestedCents to "${Money.whole(s.monthlyCents)}/mo clears it by ${Dates.monthYear(s.promoEndsOn)}"
        s.plan == "statement" -> s.suggestedCents to "in full, no interest"
        else -> s.suggestedCents to "0% payment"
    }
}
