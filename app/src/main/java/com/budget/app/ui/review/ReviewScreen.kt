package com.budget.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.AutoRecordedDto
import com.budget.app.data.remote.LooksPaidDto
import com.budget.app.data.remote.RepeatDto
import com.budget.app.ui.common.Banner
import com.budget.app.ui.common.Panel
import com.budget.app.ui.common.SectionLabel
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import com.budget.app.ui.today.ExtrasViewModel

/**
 * What the bank feed noticed: charges that look like bill payments (each waits
 * for Review and record, then Confirm) and payments it recorded on its own
 * (each with Undo, listed for ten days), and charges that repeat like a bill
 * (Add as bill or Not a bill).
 */
@Composable
fun ReviewScreen(vm: ExtrasViewModel, onBack: () -> Unit) {
    val c = Budget.colors
    val ui by vm.ui.collectAsState()
    val review = ui.review
    var confirming by remember { mutableStateOf<LooksPaidDto?>(null) }

    LaunchedEffect(Unit) { vm.load() }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.statusBarsPadding().padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { PhIcon(Ph.CARET_LEFT, "Back", c.ink) }
                Text("Review", color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp))
            }
        }

        ui.message?.let { m -> item { Banner(m, c.accent, c.accentSoft, icon = Ph.CHECK_CIRCLE) } }

        if (review == null) {
            item { Text("Nothing to show. The Review list needs the server to be reachable.", color = c.muted, modifier = Modifier.padding(4.dp)) }
        } else {
            if (review.looksPaid.isEmpty() && review.recorded.isEmpty() && review.repeats == 0 && review.paybacks.isEmpty()) {
                item { Text("All caught up. New suggestions appear after each bank sync.", color = c.muted, modifier = Modifier.padding(4.dp)) }
            }

            if (review.recorded.isNotEmpty()) {
                item { SectionLabel("Done for you", c.muted) }
                item {
                    Text(
                        "Payments the bank showed for a bill, recorded on their own. Nothing to do unless one is wrong. Each stays here for ten days.",
                        color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                items(review.recorded, key = { "auto-${it.lineItemId}-${it.year}-${it.month}" }) { r ->
                    AutoRow(r, busy = ui.busy, onUndo = { vm.undo(r) })
                }
            }

            if (review.moved.isNotEmpty()) {
                item {
                    Panel {
                        review.moved.forEach { m ->
                            Text(
                                "${m.name} is now due the ${m.to ?: "—"}${m.from?.let { " (was the $it)" } ?: ""}. Keep or put back on the web.",
                                color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            if (review.paybacks.isNotEmpty()) {
                item { SectionLabel("Paid back?", c.muted) }
                items(review.paybacks, key = { "payback-${it.personId}-${it.txnId}" }) { p ->
                    PaybackRow(p, busy = ui.busy, onYes = { vm.approvePayback(p) }, onNotThis = { vm.dismissPayback(p) })
                }
            }

            if (review.looksPaid.isNotEmpty()) {
                item { SectionLabel("Bills that look paid", c.muted) }
                item {
                    Text(
                        "Recording one also ties the merchant to the bill, so next time an exact match is recorded for you.",
                        color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                items(review.looksPaid, key = { "match-${it.lineItemId}-${it.txnId}" }) { l ->
                    LooksPaidRow(l, busy = ui.busy, onReview = { confirming = l }, onNotThis = { vm.dismiss(l) })
                }
            }

            if (review.repeatList.isNotEmpty()) {
                item { SectionLabel("Repeating charges", c.muted) }
                items(review.repeatList, key = { "repeat-${it.key}" }) { r ->
                    RepeatRow(r, busy = ui.busy, onAdd = { vm.addRepeat(r) }, onDismiss = { vm.dismissRepeat(r) })
                }
            } else if (review.repeats > 0) {
                item {
                    Text(
                        "${review.repeats} repeating charge${if (review.repeats == 1) "" else "s"} could become bills. Add or dismiss them on the web.",
                        color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }

    confirming?.let { l ->
        ConfirmDialog(
            item = l,
            onDismiss = { confirming = null },
            onConfirm = { cents, day ->
                vm.confirm(l, cents, day)
                confirming = null
            },
        )
    }
}

@Composable
private fun AutoRow(r: AutoRecordedDto, busy: Boolean, onUndo: () -> Unit) {
    val c = Budget.colors
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(Dates.monthName(r.year, r.month), Money.format(r.amountCents), r.paidOn?.let { Dates.short(it) }, r.merchant).joinToString(" · "),
                    color = c.muted, fontSize = 13.sp, maxLines = 2,
                )
            }
            TextButton(onClick = onUndo, enabled = !busy) { Text("Undo", color = c.accent, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun RepeatRow(r: RepeatDto, busy: Boolean, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val c = Budget.colors
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(r.merchant, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${r.months} months running, around the ${ordinal(r.typicalDay)}",
                    color = c.muted, fontSize = 13.sp,
                )
            }
            Text(Money.format(r.typicalCents), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            listOfNotNull(r.account.takeIf { it.isNotBlank() }, Dates.short(r.lastOn)?.let { "last $it" }).joinToString(" · "),
            color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(c.raised, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAdd,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) { Text("Add as bill", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("Not a bill", color = c.ink) }
        }
    }
}

private fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

/** Money in that adds up exactly to someone's charges: link it, or not. */
@Composable
private fun PaybackRow(p: com.budget.app.data.remote.PaybackDto, busy: Boolean, onYes: () -> Unit, onNotThis: () -> Unit) {
    val c = Budget.colors
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("${p.personName} paid you back?", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(Dates.short(p.postedOn) ?: p.postedOn, p.merchant).joinToString(" · "), color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("+" + Money.format(p.cents), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            "= " + p.charges.joinToString(" + ") { "${Dates.short(it.postedOn) ?: it.postedOn} ${it.merchant} ${Money.format(it.cents)}" },
            color = c.muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(c.raised, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onYes,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) { Text("Yes, link it", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onNotThis, enabled = !busy) { Text("Not this", color = c.ink) }
        }
    }
}

@Composable
private fun LooksPaidRow(l: LooksPaidDto, busy: Boolean, onReview: () -> Unit, onNotThis: () -> Unit) {
    val c = Budget.colors
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${if (l.isIncome) "Looks received" else "Looks paid"} · ${l.reason}".uppercase(),
                    color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                )
                Text(l.billName, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Dates.monthName(l.year, l.month)} · ${if (l.isIncome) "expected" else "planned"} ${Money.format(l.budgetedCents)}",
                    color = c.muted, fontSize = 13.sp,
                )
            }
            Text(Money.format(l.amountCents), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            listOf(l.account, Dates.short(l.postedOn) ?: l.postedOn, l.merchant).filter { it.isNotBlank() }.joinToString(" · "),
            color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(c.raised, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onReview,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) { Text("Review and record", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onNotThis, enabled = !busy) { Text("Not this", color = c.ink) }
        }
    }
}

/** The confirm step: the bank's amount and date, either of which can be changed. */
@Composable
private fun ConfirmDialog(item: LooksPaidDto, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit) {
    val c = Budget.colors
    var amount by remember { mutableStateOf(Money.toDecimal(item.amountCents)) }
    var day by remember { mutableStateOf(item.postedOn) }
    val cents = Money.parse(amount)
    val dayOk = Dates.isValidDay(day)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(item.billName, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("For ${Dates.monthName(item.year, item.month)}. Change anything before confirming.", color = c.muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(if (item.isIncome) "Amount received" else "Amount paid") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amount.isNotEmpty() && cents == null,
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = !dayOk,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (cents != null && dayOk) onConfirm(cents, day) },
                enabled = cents != null && dayOk,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) { Text(cents?.let { "Confirm ${Money.format(it)}" } ?: "Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.muted) } },
    )
}
