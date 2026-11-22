package com.budget.app.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.NeedsYouDto
import com.budget.app.data.remote.TodayResponse
import com.budget.app.ui.common.Pill
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import java.time.LocalDate

/**
 * Today: the decision screen. Available to spend on the main card's statement, checking and the
 * month's budget, the statement itself, what needs you, and the next bills with Record.
 */
@Composable
fun TodayHome(
    t: TodayResponse,
    bankSyncing: Boolean,
    bankNote: String?,
    offline: Boolean,
    onRefreshBank: () -> Unit,
    onOpen: (String) -> Unit,
    onRecord: (String) -> Unit,
) {
    val c = Budget.colors
    val ok = okColor()
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Note(Dates.weekdayDate(LocalDate.now()))
                Text("Today", color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            }
            Row(
                Modifier
                    .background(c.surface, RoundedCornerShape(50))
                    .border(1.dp, c.line, RoundedCornerShape(50))
                    .clickable(role = Role.Button, enabled = !bankSyncing, onClick = onRefreshBank)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhIcon(Ph.SYNC, "Check the bank now", c.muted, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (bankSyncing) "Checking…" else Dates.stamp(t.bankAsOf)?.let { "Bank $it" } ?: "Check the bank",
                    color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (offline) Note("Offline · showing the last copy")
        bankNote?.let { Note(it) }

        // The headline: budget left on the main card's statement.
        val a = t.available
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.accent, RoundedCornerShape(22.dp))
                .clickable(role = Role.Button) { onOpen("budget") }
                .padding(18.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text("Available to spend", color = c.onAccent.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("until ${Dates.short(a.until) ?: a.until}", color = c.onAccent.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(leftText(a.cents), color = c.onAccent, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    if (a.basis == "statement") "${a.cardName ?: "Card"} statement" else "This month",
                    color = c.onAccent.copy(alpha = 0.9f), fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                a.perDayCents?.let { Text("about ${Money.whole(it)} a day", color = c.onAccent.copy(alpha = 0.9f), fontSize = 13.sp) }
            }
            Text(
                "At this pace: ${leftText(a.endCents)}${if (a.endCents >= 0) " left" else ""} by ${Dates.short(a.until) ?: a.until}",
                color = c.onAccent.copy(alpha = 0.9f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f), onClick = { onOpen("insights") }) {
                Label("Checking")
                Figure(t.checking?.let { Money.whole(it.cents) } ?: "—", if ((t.checking?.cents ?: 0L) < 0L) c.danger else c.ink)
                t.checking?.let { ch ->
                    val pay = ch.nextPayCents
                    Note(if (pay != null && ch.nextPayOn != null) "+${Money.whole(pay)} pay ${Dates.short(ch.nextPayOn)}" else "No pay due")
                }
            }
            Tile(Modifier.weight(1f), onClick = { onOpen("budget-month") }) {
                Label("Month budget left")
                Figure(leftText(t.monthLeftCents), if (t.monthLeftCents < 0) c.danger else ok)
                Note("${t.monthDaysLeft} days left")
            }
        }

        t.statement?.let { s ->
            Tile(Modifier.fillMaxWidth(), onClick = { onOpen("cards") }) {
                Row(Modifier.fillMaxWidth()) {
                    Label("${s.name} · closes ${Dates.short(s.to) ?: s.to}")
                    Spacer(Modifier.weight(1f))
                    Note("day ${s.day} of ${s.days}")
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Figure(Money.whole(s.chargedCents))
                    Spacer(Modifier.width(6.dp))
                    Note("charged", c.muted)
                    Spacer(Modifier.weight(1f))
                    Note("${Money.whole(s.projectedCents)} projected")
                }
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(c.raised, RoundedCornerShape(50)))
                    Box(Modifier.width(maxWidth * (s.day.toFloat() / maxOf(1, s.days)).coerceIn(0f, 1f)).height(6.dp).background(c.accent, RoundedCornerShape(50)))
                }
                if (s.billsToComeCents > 0) Note("${Money.whole(s.billsToComeCents)} in bills still to land", c.muted)
            }
        }

        if (t.needsYou.isNotEmpty()) {
            Tile(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Needs You", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Pill(t.needsYou.size.toString(), c.warn, c.warnSoft)
                }
                t.needsYou.forEach { n -> NeedRow(n) { onOpen(n.target) } }
            }
        }

        if (t.nextBills.isNotEmpty()) {
            Tile(Modifier.fillMaxWidth()) {
                Text("Next Up", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                t.nextBills.forEach { b ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Dates.dayLabel(b.dueOn), color = c.muted, fontSize = 13.sp, modifier = Modifier.width(64.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Column(Modifier.weight(1f)) {
                            Text(b.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (b.status == "pending") Text("Pending at the bank", color = c.muted, fontSize = 12.sp)
                        }
                        Text(Money.whole(b.cents), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Record",
                            color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .border(1.dp, c.line, RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button) { onRecord(b.lineItemId) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NeedRow(n: NeedsYouDto, onClick: () -> Unit) {
    val c = Budget.colors
    val (label, fg, bg) = when (n.kind) {
        "pending" -> Triple("Pending", c.accent, c.accentSoft)
        "review" -> Triple("Review", c.warn, c.warnSoft)
        "over" -> Triple("Over", c.danger, c.dangerSoft)
        "overdue" -> Triple("Overdue", c.danger, c.dangerSoft)
        "cardFile" -> Triple("Due", c.warn, c.warnSoft)
        else -> Triple("Short", c.danger, c.dangerSoft)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill(label, fg, bg)
        Spacer(Modifier.width(10.dp))
        Text(n.text, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        n.cents?.let {
            Text(Money.whole(it), color = if (n.kind == "over" || n.kind == "short") c.danger else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        } ?: PhIcon(Ph.CARET_RIGHT, null, c.muted, size = 18.dp)
    }
}
