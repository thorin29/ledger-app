package com.budget.app.ui.phone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.BudgetResponse
import com.budget.app.data.remote.BudgetSectionDto
import com.budget.app.data.remote.OverviewResponse
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon

/** Insights on the phone: Overview (income vs expenses, the outlook) and Budget (the month, each card's statement, by card). */
@Composable
fun InsightsHome(
    overview: OverviewResponse?,
    budget: BudgetResponse?,
    part: String,
    onPart: (String) -> Unit,
    onBudgetView: (String) -> Unit,
) {
    val c = Budget.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Insights", right = {
            overview?.month?.let { m -> Note(Dates.monthName(m.take(4).toInt(), m.drop(5).take(2).toInt())) }
        })
        Segments(listOf("overview" to "Overview", "budget" to "Budget"), part, onPart)
        if (part == "budget") {
            if (budget == null) Note("Loading…") else BudgetPart(budget, onBudgetView)
        } else {
            if (overview == null) Note("Loading…") else OverviewPart(overview)
        }
    }
}

@Composable
private fun OverviewPart(o: OverviewResponse) {
    val c = Budget.colors
    val ok = okColor()
    Tile(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text("Income vs Expenses", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Note("of ${Money.whole(o.income.totalCents)}")
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Ring(o, Modifier.size(140.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                o.parts.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(tierColor(p.key), RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(p.name, color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(Money.whole(p.spentCents), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        p.budgetCents?.let { Text(" / ${Money.whole(it)}", color = c.muted, fontSize = 13.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(c.ink, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("Income", color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${Money.whole(o.income.receivedCents)} in", color = c.muted, fontSize = 13.sp)
                }
            }
        }
    }

    o.outlook?.let { out ->
        var worst by rememberSaveable { mutableStateOf(true) }
        val case = if (worst) out.worst else out.best
        Tile(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Text("Outlook", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Note("${Dates.short(out.from)} – ${Dates.short(out.to)}")
            }
            Spacer(Modifier.height(10.dp))
            Segments(listOf("worst" to "Worst case", "best" to "Best case"), if (worst) "worst" else "best") { worst = it == "worst" }
            Spacer(Modifier.height(8.dp))
            out.paydayOn?.let { pay ->
                case.beforeCents?.let { OutRow("Before ${Dates.short(pay)} pay", it) }
                OutRow("After ${Dates.short(pay)} pay", case.afterCents)
            } ?: OutRow("By ${Dates.short(out.to)}", case.afterCents)
            Note(if (worst) "Worst case: this month's budgets used in full" else "Best case: spending stops now")
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        o.lastMonth?.let { l ->
            Tile(Modifier.weight(1f)) {
                Label("${Dates.monthName(l.month.take(4).toInt(), l.month.drop(5).take(2).toInt())} result")
                Figure((if (l.resultCents < 0) "−" else "+") + Money.whole(kotlin.math.abs(l.resultCents)), if (l.resultCents < 0) c.danger else ok, 20)
                Note(if (l.resultCents < 0) "spent more than came in" else "came in more than spent")
            }
        }
        o.debtCents?.let { d ->
            Tile(Modifier.weight(1f)) {
                Label("Debt")
                Figure(Money.whole(d), size = 20)
                Note("loans and cards")
            }
        }
    }
}

@Composable
private fun OutRow(label: String, cents: Long) {
    val c = Budget.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, color = c.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(if (cents < 0) "${Money.whole(-cents)} short" else Money.whole(cents), color = if (cents < 0) c.danger else okColor(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** The month's income as a ring: what's spent and paid (solid), still due (pale), and what's left. */
@Composable
private fun Ring(o: OverviewResponse, modifier: Modifier) {
    val c = Budget.colors
    val danger = c.danger
    val track = c.raised
    val line = c.line
    val ink = c.ink
    val income = maxOf(1L, o.income.totalCents).toFloat()
    val colors = o.parts.map { tierColor(it.key) }
    val left = o.leftCents
    Box(modifier.semantics { contentDescription = "${leftText(left)} of ${Money.whole(o.income.totalCents)} income remaining" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // As on the web: the thick inner ring is where income went; the thin ring outside it is
            // income received so far (dark on a pale track); the outer red ring is past income.
            val s = size.minDimension
            val mid = Offset(size.width / 2, size.height / 2)
            fun box(r: Float) = Offset(mid.x - r, mid.y - r) to Size(r * 2, r * 2)
            val stroke = s * 0.11f
            val (inTl, inSz) = box(s * 0.34f)
            drawArc(track, 0f, 360f, false, inTl, inSz, style = Stroke(stroke))
            var at = -90f
            o.parts.forEachIndexed { i, p ->
                val solid = p.spentCents / income * 360f
                if (solid > 0) drawArc(colors[i], at, solid, false, inTl, inSz, style = Stroke(stroke))
                at += solid
            }
            o.parts.forEachIndexed { i, p ->
                val pale = p.dueCents / income * 360f
                if (pale > 0) drawArc(colors[i].copy(alpha = 0.3f), at, pale, false, inTl, inSz, style = Stroke(stroke))
                at += pale
            }
            val (incTl, incSz) = box(s * 0.425f)
            drawArc(line, 0f, 360f, false, incTl, incSz, style = Stroke(s * 0.045f))
            val received = minOf(360f, o.income.receivedCents / income * 360f)
            if (received > 0) drawArc(ink, -90f, received, false, incTl, incSz, style = Stroke(s * 0.045f))
            // Past income: solid red only for what's actually spent; pale red for what may come.
            val (outTl, outSz) = box(s * 0.48f)
            if (o.expectedOverCents > o.overCents) drawArc(danger.copy(alpha = 0.25f), -90f, minOf(360f, o.expectedOverCents / income * 360f), false, outTl, outSz, style = Stroke(s * 0.025f, cap = StrokeCap.Round))
            if (o.overCents > 0) drawArc(danger, -90f, minOf(360f, o.overCents / income * 360f), false, outTl, outSz, style = Stroke(s * 0.025f, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (o.overCents > 0) "${Money.whole(o.overCents)} over" else leftText(left), color = if (o.overCents > 0) c.danger else c.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (o.overCents > 0) "past income" else "remaining", color = c.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BudgetPart(b: BudgetResponse, onView: (String) -> Unit) {
    val c = Budget.colors
    val ok = okColor()
    if (b.views.size > 1) Chips(b.views.map { it.key to it.name }, b.view, onView)
    PeriodLine(b.period.from, b.period.to, b.period.day, b.period.days)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(Modifier.weight(1f)) {
            Label("Budget left today")
            Figure(leftText(b.leftCents), if (b.leftCents < 0) c.danger else ok)
            b.perDayCents?.let { Note("about ${Money.whole(it)} a day") }
        }
        Tile(Modifier.weight(1f)) {
            Label("By ${Dates.short(b.period.to) ?: b.period.to}")
            Figure(leftText(b.leftEndCents), if (b.leftEndCents < 0) c.warn else ok)
            Note("at this pace")
        }
    }
    if (b.view == "cards") {
        Tile(Modifier.fillMaxWidth()) {
            b.cards.forEachIndexed { i, card ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Column(Modifier.padding(vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(card.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(Money.whole(card.spentCents), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(" / ${Money.whole(card.budgetCents)}", color = c.muted, fontSize = 14.sp)
                    }
                    SpendBar(card.spentCents, card.projectedCents, card.budgetCents, c.accent, Modifier.padding(top = 6.dp))
                    Note("${Money.whole(card.projectedCents)} projected")
                }
            }
        }
    } else {
        Tile(Modifier.fillMaxWidth()) {
            b.sections.forEachIndexed { i, s ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                SectionRows(s)
            }
        }
        Note("Solid = spent · pale = projected · line = budget")
    }
    if (!b.checksOk) Note("A check didn't add up. See Budget on the web.", c.warn)
}

@Composable
private fun SectionRows(s: BudgetSectionDto) {
    val c = Budget.colors
    val ok = okColor()
    val color = tierColor(s.tier)
    var open by remember(s.tier) { mutableStateOf(s.groups.any { g -> g.budgetCents != null && g.spentCents > g.budgetCents }) }
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PhIcon(if (open) Ph.CARET_DOWN else Ph.CARET_RIGHT, null, c.muted, size = 16.dp)
            Spacer(Modifier.width(4.dp))
            Text(s.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(Money.whole(s.spentCents), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            s.budgetCents?.let { Text(" / ${Money.whole(it)}", color = c.muted, fontSize = 14.sp) }
        }
        SpendBar(s.spentCents, s.projectedCents, s.budgetCents, color, Modifier.padding(top = 6.dp))
        s.budgetCents?.let { budget ->
            val end = budget - s.projectedCents
            Row(Modifier.fillMaxWidth()) {
                Note(if (s.tier == "BILLS") "${s.bills.count { it.status != "paid" }} still to come" else if (s.tier == "UNPLANNED") "${leftText(budget - s.spentCents)} reserve left" else "${leftText(budget - s.spentCents)} left")
                Spacer(Modifier.weight(1f))
                if (s.tier != "UNPLANNED") Note(if (end < 0) "${Money.whole(-end)} over" else "${Money.whole(end)} under", if (end < 0) c.warn else ok)
            }
        }
        if (open) {
            Column(Modifier.padding(start = 20.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.groups.forEach { g ->
                    val over = g.budgetCents != null && g.spentCents > g.budgetCents
                    Row(Modifier.fillMaxWidth()) {
                        Text(g.name, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Money.whole(g.spentCents), color = if (over) c.danger else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        g.budgetCents?.let { Text(" / ${Money.whole(it)}", color = c.muted, fontSize = 14.sp) }
                    }
                }
                s.bills.forEach { bill ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(Dates.short(bill.dueOn) ?: "", color = c.muted, fontSize = 13.sp, modifier = Modifier.width(52.dp))
                        Text(bill.name, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (bill.status) {
                                "paid" -> "charged"
                                "pending" -> "pending"
                                "overdue" -> "overdue"
                                else -> "due"
                            },
                            color = when (bill.status) {
                                "paid" -> ok
                                "overdue" -> c.danger
                                else -> c.muted
                            },
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(Money.whole(bill.cents), color = c.ink, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
