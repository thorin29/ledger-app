package com.budget.app.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.OwedMatchDto
import com.budget.app.data.remote.OwedPersonDto
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon

/**
 * Owed to You: who owes what, and for one person their open charges, payments that add up to
 * some of them (Link), and what's been paid back (Unlink).
 */
@Composable
fun OwedScreen(
    ui: PhoneUi,
    onBack: () -> Unit,
    onLink: (personId: String, txnId: String, keys: List<String>) -> Unit,
    onUnlink: (txnId: String) -> Unit,
    onDismiss: (personId: String, txnId: String) -> Unit,
) {
    val c = Budget.colors
    var openId by remember { mutableStateOf<String?>(null) }
    val person = ui.owed?.firstOrNull { it.id == openId }
    BackHandler { if (openId != null) openId = null else onBack() }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button) { if (openId != null) openId = null else onBack() }, contentAlignment = Alignment.Center) {
                PhIcon(Ph.CARET_LEFT, "Back", c.ink)
            }
            Text(person?.name ?: "Owed to You", color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ui.message?.let { Note(it, c.accent) }
            when {
                ui.owed == null -> Note("Loading…")
                person != null -> Person(person, onLink, onUnlink, onDismiss)
                ui.owed.isEmpty() -> Note("Nothing yet. Open a charge in Transactions and use Someone owes me.")
                else -> {
                    PageCard {
                        Text("Open", color = c.muted, fontSize = 13.sp)
                        Text(Money.format(ui.owed.sumOf { maxOf(0L, it.openCents) }), color = c.ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.fillMaxWidth().background(c.surface, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))) {
                        ui.owed.forEachIndexed { i, p ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Button) { openId = p.id }.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(p.name, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (p.openCents < 0) "${Money.format(-p.openCents)} ahead" else Money.format(p.openCents),
                                        color = if (p.openCents > 0) c.ink else c.muted, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    )
                                    val open = p.charges.count { it.open }
                                    Text(
                                        when {
                                            p.matches.isNotEmpty() -> "${p.matches.size} payment${if (p.matches.size == 1) "" else "s"} to link"
                                            open > 0 -> "$open charge${if (open == 1) "" else "s"}"
                                            else -> "paid up"
                                        },
                                        color = if (p.matches.isNotEmpty()) okColor() else c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun Person(
    p: OwedPersonDto,
    onLink: (String, String, List<String>) -> Unit,
    onUnlink: (String) -> Unit,
    onDismiss: (String, String) -> Unit,
) {
    val c = Budget.colors
    val ok = okColor()
    val open = p.charges.filter { it.open }
    var ticked by remember(p) { mutableStateOf(setOf<String>()) }
    var pick by remember(p) { mutableStateOf<String?>(null) }
    val tickedCents = open.filter { it.key in ticked }.sumOf { it.leftCents }
    val payments: List<OwedMatchDto> = p.matches + p.maybe
    val exact = payments.firstOrNull { it.cents == tickedCents }?.txnId

    PageCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Owes", color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (p.openCents < 0) "${Money.format(-p.openCents)} ahead" else Money.format(p.openCents), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text("${Money.format(p.markedCents)} owed · ${Money.format(p.paidCents)} paid back", color = c.muted, fontSize = 13.sp)
    }

    if (p.matches.isNotEmpty()) {
        Label("Payments That Match")
        PageCard {
            p.matches.forEach { m ->
                val names = p.charges.filter { it.key in m.chargeKeys }.joinToString(" and ") { "${Dates.short(it.postedOn)} ${it.merchant}" }
                Column {
                    Text("${Dates.short(m.postedOn)} · ${m.merchant} · +${Money.format(m.cents)}", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("adds up to $names", color = c.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Not this", color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(role = Role.Button) { onDismiss(p.id, m.txnId) }.padding(10.dp))
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.weight(1.4f)) { ActionButton("Link") { onLink(p.id, m.txnId, m.chargeKeys) } }
                    }
                }
            }
        }
    }

    Label("Open Charges")
    PageCard {
        if (open.isEmpty()) Text("Nothing open.", color = c.muted, fontSize = 14.sp)
        open.forEach { ch ->
            Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { ticked = if (ch.key in ticked) ticked - ch.key else ticked + ch.key }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ch.key in ticked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.accent))
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(ch.merchant, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(Dates.short(ch.postedOn), ch.categoryName).joinToString(" · "), color = c.muted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(ch.cents), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (ch.coveredCents > 0) Text("${Money.format(ch.leftCents)} left", color = c.muted, fontSize = 12.sp)
                }
            }
        }
        if (ticked.isNotEmpty()) {
            Text("Ticked: ${Money.format(tickedCents)}", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            DropdownField(
                value = pick ?: exact,
                placeholder = "Pick the payment",
                options = payments.map { it.txnId to "${Dates.short(it.postedOn)} · ${it.merchant} · +${Money.format(it.cents)}${if (it.cents == tickedCents) " (exact)" else ""}" },
                onPick = { pick = it },
            )
            val chosen = pick ?: exact
            ActionButton("Link ${Money.format(tickedCents)} to that payment", enabled = chosen != null) { onLink(p.id, chosen!!, ticked.toList()) }
        }
    }

    val paid = p.charges.filter { !it.open }
    if (p.paybacks.isNotEmpty() || paid.isNotEmpty()) {
        Label("Paid")
        PageCard {
            p.paybacks.reversed().forEach { b ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${Dates.short(b.postedOn)} · ${b.merchant}", color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("+${Money.format(b.cents)}", color = ok, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Unlink", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(role = Role.Button) { onUnlink(b.txnId) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                }
            }
            paid.takeLast(8).reversed().forEach { ch ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${Dates.short(ch.postedOn)} · ${ch.merchant}", color = c.muted, fontSize = 13.sp, textDecoration = TextDecoration.LineThrough, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Money.format(ch.cents), color = c.muted, fontSize = 13.sp, textDecoration = TextDecoration.LineThrough)
                }
            }
        }
    }
}
