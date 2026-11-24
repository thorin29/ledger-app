package com.budget.app

import com.budget.app.data.CardLogic
import com.budget.app.data.EntryStatuses
import com.budget.app.data.remote.CardDto
import com.budget.app.data.remote.CardStatusDto
import com.budget.app.data.remote.CardsResponse
import com.budget.app.data.remote.MonthEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CardLogicTest {
    private val promo = CardDto(
        id = "a", name = "Platinum - 3307 (3307)", owedNowCents = 1_500_000,
        status = CardStatusDto(nextDue = "2026-12-08", dueCents = 1_500_000, statementCents = 1_500_000, suggestedCents = 100_000, plan = "payoff", paymentsLeft = 15),
    )
    private val plain = CardDto(
        id = "b", name = "Rewards (5521)", owedNowCents = 64_000,
        status = CardStatusDto(nextDue = "2026-12-09", dueCents = 20_806, suggestedCents = 20_806, newChargesCents = 43_194, plan = "statement"),
    )
    private val r = CardsResponse(totalOwedCents = 1_564_000, payThisCycleCents = 120_806, nextDue = "2026-12-08", cards = listOf(promo, plain))

    @Test fun summaryComesFirst() {
        assertEquals(listOf("summary", "a", "b"), CardLogic.faces(r).map { it.key })
    }

    @Test fun swipingCyclesTheStack() {
        val order = listOf(1, 2, 3)
        assertEquals(listOf(2, 3, 1), CardLogic.next(order))
        assertEquals(listOf(3, 1, 2), CardLogic.previous(order))
        assertEquals(listOf(1), CardLogic.next(listOf(1)))
    }

    @Test fun facesLeadWithWhatToPay() {
        assertEquals("Payment by Dec 8" to 100_000L, CardLogic.lead(promo))
        assertEquals("Pay by Dec 9" to 20_806L, CardLogic.lead(plain))
        assertEquals("Balance" to 500L, CardLogic.lead(CardDto(id = "c", owedNowCents = 500)))
    }

    @Test fun promoLine() {
        assertEquals("0% until Feb 11, 2028 · 15 payments left", CardLogic.promoLine(promo.status!!.copy(promoEndsOn = "2028-02-11")))
        assertEquals(null, CardLogic.promoLine(plain.status!!))
    }

    @Test fun tallies() {
        assertEquals(1_520_806L, CardLogic.onStatements(r))
        assertEquals(43_194L, CardLogic.sinceStatements(r))
    }

    @Test fun namesAndColors() {
        assertEquals("Platinum", CardLogic.faceName("Platinum - 3307 (3307)"))
        assertEquals("Rewards", CardLogic.faceName("Rewards (5521)"))
        assertEquals(0xFF0A1428L, CardLogic.argb("#0a1428"))
    }

    @Test fun autoPaidReadsInTheStatus() {
        val e = MonthEntry(lineItemId = "x", name = "Rent", year = 2026, month = 11, kind = "BILL", actualCents = 100, paidOn = "2026-11-01", autoPaid = true)
        assertTrue(EntryStatuses.of(e, LocalDate.of(2026, 11, 16)).text.endsWith("· auto"))
    }

    @Test fun cardPaymentsReadLikeTheWeb() {
        val over = MonthEntry(lineItemId = "v", name = "Everyday", year = 2026, month = 11, kind = "BILL", budgetedCents = 390_000, actualCents = 421_560, paidOn = "2026-11-16", paidFromSurplus = true, paysCardId = "a")
        assertEquals(listOf("Paid Nov 16", "of $3,900.00", "$315.60 over", "after bills"), EntryStatuses.cardPaid(over).map { it.text })
        val moved = MonthEntry(lineItemId = "v", name = "Everyday", year = 2026, month = 11, kind = "BILL", budgetedCents = 0, actualCents = 0, paidOn = "2026-11-06", paidNote = "Balance transfer", paysCardId = "a")
        assertEquals(listOf("Paid Nov 6", "balance transfer"), EntryStatuses.cardPaid(moved).map { it.text })
    }
}
