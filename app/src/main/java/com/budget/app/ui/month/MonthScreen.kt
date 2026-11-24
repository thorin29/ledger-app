package com.budget.app.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.EntryStatuses
import com.budget.app.data.Money
import com.budget.app.data.MonthLogic
import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.sync.Overlay
import com.budget.app.ui.common.Banner
import com.budget.app.ui.common.SectionLabel
import com.budget.app.ui.record.toneColor
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import java.time.YearMonth

private data class Group(val key: String, val title: String, val danger: Boolean, val rows: List<MonthEntry>, val cards: Boolean = false)

/**
 * Every item in a month, grouped as on the web: owed from earlier, each half, left-over, income,
 * then card payments together in Cards (taken out of the other groups).
 */
@Composable
fun MonthScreen(vm: MonthViewModel, onRecord: (MonthEntry?) -> Unit) {
    val c = Budget.colors
    val ui by vm.ui.collectAsState()
    val pending by vm.pending.collectAsState()
    val view = remember(ui.view, pending) { ui.view?.let { Overlay.apply(it, pending) } }
    val isCurrent = YearMonth.now() == YearMonth.of(ui.year, ui.month)

    val groups = remember(view) {
        if (view == null) emptyList() else buildList {
            val bills = { rows: List<MonthEntry> -> rows.filterNot { it.isCardPayment } }
            val cards = (view.carried + view.halves.flatMap { it.entries } + view.surplusItems)
                .filter { it.isCardPayment }
                .distinctBy { "${it.lineItemId}-${it.year}-${it.month}" }
            if (bills(view.carried).isNotEmpty()) add(Group("carried", "Still owed from earlier", true, bills(view.carried)))
            view.halves.forEach { h -> add(Group("half-${h.half.index}", h.half.label.ifBlank { "Half ${h.half.index + 1}" }, false, bills(h.entries))) }
            if (bills(view.surplusItems).isNotEmpty()) add(Group("surplus", "Paid from what's left over", false, bills(view.surplusItems)))
            if (view.income.isNotEmpty()) add(Group("income", "Income", false, view.income))
            if (cards.isNotEmpty()) add(Group("cards", "Cards", false, cards, cards = true))
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Row(Modifier.statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shift(-1) }) { PhIcon(Ph.CARET_LEFT, "Previous month", c.ink) }
                    Text(
                        Dates.monthTitle(ui.year, ui.month),
                        color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.shift(1) }) { PhIcon(Ph.CARET_RIGHT, "Next month", c.ink) }
                }
                if (!isCurrent) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { vm.today() }) { Text("Back to this month", color = c.accent, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            if (ui.offline) item { Banner("Offline. Showing what was last loaded.", c.warn, c.warnSoft, icon = Ph.CLOUD_SLASH) }
            ui.error?.let { e -> item { Banner(e, c.danger, c.dangerSoft, icon = Ph.WARNING) } }

            if (view != null) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(c.surface, RoundedCornerShape(18.dp))
                            .border(1.dp, c.line, RoundedCornerShape(18.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("Paid so far", color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(Money.format(view.totalPaidCents), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(" of ${Money.format(view.totalBudgetedCents)}", color = c.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).background(c.raised, RoundedCornerShape(4.dp))) {
                            Box(Modifier.fillMaxWidth(MonthLogic.paidShare(view)).fillMaxHeight().background(c.accent, RoundedCornerShape(4.dp)))
                        }
                        if (view.incomeExpectedCents > 0) {
                            Text(
                                "Income in ${Money.format(view.incomeReceivedCents)} of ${Money.format(view.incomeExpectedCents)}",
                                color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                groups.forEach { g ->
                    item(key = "h-${g.key}") { SectionLabel(g.title, if (g.danger) c.danger else c.muted) }
                    item(key = "g-${g.key}") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(c.surface, RoundedCornerShape(18.dp))
                                .border(1.dp, c.line, RoundedCornerShape(18.dp)),
                        ) {
                            if (g.cards) CardsSummary(g.rows)
                            if (g.rows.isEmpty()) Text("Nothing here.", color = c.muted, modifier = Modifier.padding(16.dp))
                            g.rows.forEachIndexed { i, e ->
                                if (i > 0 || g.cards) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                                MonthRow(e, onClick = { onRecord(e) })
                            }
                        }
                    }
                }
            } else if (!ui.loading) {
                item { Text("Nothing loaded yet.", color = c.muted, modifier = Modifier.padding(4.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { onRecord(null) },
            containerColor = c.accent,
            contentColor = c.onAccent,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { PhIcon(Ph.PLUS, "Record a payment", c.onAccent) }
    }
}

/** "$4,789.00 paid of $4,480.00 planned" with a bar. A balance transfer's $0 line isn't planned. */
@Composable
private fun CardsSummary(rows: List<MonthEntry>) {
    val c = Budget.colors
    val paid = rows.sumOf { it.actualCents ?: 0L }
    val planned = rows.filterNot { it.paidNote != null && (it.actualCents ?: 0L) == 0L }.sumOf { it.budgetedCents }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Money.format(paid), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(" paid of ${Money.format(planned)} planned", color = c.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(c.raised, RoundedCornerShape(3.dp))) {
            val share = if (planned > 0) (paid.toFloat() / planned).coerceIn(0f, 1f) else if (paid > 0) 1f else 0f
            if (share > 0f) Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(c.accent, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun MonthRow(e: MonthEntry, onClick: () -> Unit) {
    val c = Budget.colors
    val status = EntryStatuses.of(e)
    val iconTint: Color = when {
        e.settled -> c.accent
        status.tone == com.budget.app.data.Tone.MUTED -> c.muted
        else -> toneColor(status.tone)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhIcon(if (e.settled) Ph.CHECK_CIRCLE_FILL else Ph.CIRCLE, if (e.settled) "Paid" else "Not paid", iconTint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = (if (e.isCardPayment && e.settled) EntryStatuses.cardPaid(e) else listOf(status)).map { it.text to toneColor(it.tone) }
            Text(
                buildAnnotatedString {
                    parts.forEachIndexed { i, (text, color) ->
                        if (i > 0) withStyle(SpanStyle(color = c.muted)) { append(" · ") }
                        withStyle(SpanStyle(color = color)) { append(text) }
                    }
                },
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            Money.format(e.actualCents ?: e.budgetedCents),
            color = if (e.settled) c.muted else c.ink,
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
        )
    }
}
