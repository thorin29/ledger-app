package com.budget.app

import com.budget.app.data.CardLogic
import com.budget.app.data.MonthSections
import com.budget.app.data.ReviewDot
import com.budget.app.data.remote.CardStatusDto
import com.budget.app.data.remote.HalfInfo
import com.budget.app.data.remote.HalfSummary
import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.remote.MonthView
import com.budget.app.data.remote.PayPeriodDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerMatchTest {
    private fun bill(id: String, budget: Long, paid: Long? = null, card: String? = null, note: String? = null) =
        MonthEntry(lineItemId = id, name = id, year = 2026, month = 11, kind = "BILL", budgetedCents = budget, actualCents = paid, paysCardId = card, paidNote = note)

    @Test fun reviewDotOnlyForNewItems() {
        assertEquals(5, ReviewDot.count("5 to review"))
        assertTrue(ReviewDot.waiting(5, null))
        assertFalse(ReviewDot.waiting(5, 5))
        assertTrue(ReviewDot.waiting(6, 5))
        assertEquals(5, ReviewDot.nextSeen(5, 2, reviewOpen = true))
        assertEquals(3, ReviewDot.nextSeen(3, 5, reviewOpen = false))
        assertNull(ReviewDot.nextSeen(6, 5, reviewOpen = false))
    }

    @Test fun payThisCycleReadsLikeTheWeb() {
        assertEquals(18_820L to "in full, no interest", CardLogic.payThisCycle(CardStatusDto(suggestedCents = 18_820, plan = "statement")))
        assertEquals(null to "nothing due", CardLogic.payThisCycle(CardStatusDto(plan = "paid")))
        assertEquals(
            50_000L to "$1,233/mo clears it by Feb 2028",
            CardLogic.payThisCycle(CardStatusDto(suggestedCents = 50_000, plan = "payoff", promoEndsOn = "2028-02-11", monthlyCents = 123_300)),
        )
    }

    @Test fun cardsLeaveTheHalvesAndSectionsCountWhatTheyPay() {
        val view = MonthView(
            year = 2026, month = 11,
            halves = listOf(
                HalfSummary(HalfInfo(0, "Through the 15th"), listOf(bill("rent", 100_000, 100_000), bill("store", 6_000, 5_810, card = "k")), 106_000, 105_810, 0),
                HalfSummary(HalfInfo(1, "After the 15th"), listOf(bill("net", 7_000)), 7_000, 0, 7_000),
            ),
            surplusItems = listOf(bill("everyday", 0, 0, card = "v", note = "Balance transfer")),
        )
        val s = MonthSections.of(view)
        assertEquals(listOf("half-0", "half-1", "cards"), s.map { it.key })
        assertEquals(listOf("rent"), s[0].rows.map { it.lineItemId })
        assertEquals("$1,058.10 paid", s[0].lead)
        assertEquals(listOf("store", "everyday"), s[2].rows.map { it.lineItemId })
        assertEquals(" of $60.00 planned", s[2].rest)
    }

    @Test fun paycheckSectionsFollowPaySettings() {
        val view = MonthView(
            year = 2026, month = 11,
            halves = listOf(HalfSummary(HalfInfo(0, "x"), listOf(bill("rent", 100_000)), 100_000, 0, 100_000)),
            periods = listOf(
                PayPeriodDto(start = "2026-11-01", end = "2026-11-13", fromPayday = "2026-10-31", entries = listOf(bill("rent", 100_000)), budgetedCents = 100_000, remainingCents = 100_000),
                PayPeriodDto(start = "2026-11-14", end = "2026-11-27", payday = "2026-11-14", current = true),
            ),
        )
        assertEquals(listOf("Oct 31 Paycheck (Cont.)", "Nov 14 Paycheck · Now"), MonthSections.of(view).map { it.title })
    }
}
