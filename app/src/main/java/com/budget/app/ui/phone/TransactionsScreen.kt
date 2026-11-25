package com.budget.app.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.CategoryDto
import com.budget.app.data.remote.TxnDto
import com.budget.app.ui.common.Pill
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon

/** Recent transactions: search, a few filters, and a sheet to set a category (or mark a transfer). */
@Composable
fun TransactionsScreen(
    ui: PhoneUi,
    onBack: () -> Unit,
    onFilter: (String) -> Unit,
    onSearch: (String) -> Unit,
    onCategorize: (TxnDto, String?, Boolean, Boolean) -> Unit,
    /** Who owes you for a charge: (charge, person id or null, new person's name, cents). */
    onOwed: (TxnDto, String?, String?, Long?) -> Unit = { _, _, _, _ -> },
) {
    val c = Budget.colors
    var search by remember { mutableStateOf(ui.txnSearch) }
    var opened by remember { mutableStateOf<TxnDto?>(null) }
    var editing by remember { mutableStateOf<TxnDto?>(null) }
    val names = remember(ui.categories) { ui.categories.associate { it.id to it.name } }
    val byDay = remember(ui.transactions) { ui.transactions.groupBy { it.postedOn } }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                    PhIcon(Ph.CARET_LEFT, "Back", c.ink)
                }
                Text("Transactions", color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    if (it.isEmpty() || it.length >= 2) onSearch(it)
                },
                placeholder = { Text("Search merchant") },
                leadingIcon = { PhIcon(Ph.SEARCH, null, c.muted, size = 18.dp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            )
            Chips(
                listOf("all" to "All", "none" to "Needs a category", "pending" to "Pending", "in" to "Money in"),
                ui.txnFilter, onFilter, Modifier.padding(start = 8.dp),
            )
            ui.message?.let { Note(it, c.accent) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
            if (ui.transactions.isEmpty()) {
                item { Note("Nothing here.", c.muted) }
            }
            byDay.forEach { (day, list) ->
                item(key = "d$day") {
                    Text(
                        (Dates.dayLabel(day)).uppercase(),
                        color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
                    )
                }
                items(list, key = { it.id }) { t -> TxnRow(t, names[t.categoryId]) { if (t.splits.isNotEmpty()) opened = t else editing = t } }
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    }

    opened?.let { t -> ItemsSheet(t, onDismiss = { opened = null }) }

    editing?.let { t ->
        CategorySheet(
            txn = t,
            categories = ui.categories,
            onDismiss = { editing = null },
            onPick = { categoryId, forMerchant, transfer ->
                editing = null
                onCategorize(t, categoryId, forMerchant, transfer)
            },
            onOwed = { person, newName, cents ->
                editing = null
                onOwed(t, person, newName, cents)
            },
        )
    }
}

@Composable
private fun TxnRow(t: TxnDto, category: String?, onClick: () -> Unit) {
    val c = Budget.colors
    val ok = okColor()
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(t.merchant, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((if (t.amountCents > 0) "+" else "−") + Money.format(kotlin.math.abs(t.amountCents)), color = if (t.amountCents > 0) ok else c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.account, color = c.muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                t.pending -> Pill("Pending", c.warn, c.warnSoft)
                t.kind == "TRANSFER" -> Pill("Transfer", c.muted, c.raised)
                t.splits.isNotEmpty() && t.splits.any { it.categoryId == null } -> Pill("Needs a category", c.warn, c.warnSoft)
                t.splits.isNotEmpty() -> {
                    val names = t.splits.map { it.categoryName ?: "Needs a category" }.distinct()
                    Text((if (names.size == 1) names[0] else "${names.size} categories") + " · Amazon ▾", color = c.muted, fontSize = 12.sp)
                }
                category == null && t.kind == "NORMAL" && t.amountCents < 0 -> Pill("Needs a category", c.warn, c.warnSoft)
                else -> Text((category ?: "") + if (t.guessed) " · guessed" else "", color = c.muted, fontSize = 12.sp)
            }
        }
        t.owed?.let { o ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                Pill("Owed · ${o.personName} ${Money.format(o.cents)}", c.accent, c.accentSoft)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
}

/** The items in a charge split by item, view only: item categories are changed on the web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemsSheet(txn: TxnDto, onDismiss: () -> Unit) {
    val c = Budget.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(txn.merchant, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Note("${Dates.short(txn.postedOn)} · ${txn.account}")
                }
                Text((if (txn.amountCents > 0) "+" else "−") + Money.format(kotlin.math.abs(txn.amountCents)), color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                txn.splits.forEachIndexed { i, p ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(p.note ?: "Item", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (p.categoryId == null) Pill("Needs a category", c.warn, c.warnSoft)
                            else Text(p.categoryName ?: "", color = c.muted, fontSize = 12.sp)
                        }
                        Text(Money.format(kotlin.math.abs(p.amountCents)), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Note("Change item categories on the web.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(
    txn: TxnDto,
    categories: List<CategoryDto>,
    onDismiss: () -> Unit,
    onPick: (String?, Boolean, Boolean) -> Unit,
    onOwed: (String?, String?, Long?) -> Unit,
) {
    val c = Budget.colors
    var picked by remember { mutableStateOf(txn.categoryId) }
    var transfer by remember { mutableStateOf(txn.kind == "TRANSFER") }
    val people = categories.filter { it.managedForOthers }
    val budgetCats = categories.filterNot { it.managedForOthers }
    val charge = txn.kind == "NORMAL" && txn.amountCents < 0 && !txn.pending && people.none { it.id == txn.categoryId }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(txn.merchant, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Note("${Dates.short(txn.postedOn)} · ${txn.account}")
                }
                Text((if (txn.amountCents > 0) "+" else "−") + Money.format(kotlin.math.abs(txn.amountCents)), color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Label("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                budgetCats.forEach { cat ->
                    Choice(cat.name, !transfer && picked == cat.id) {
                        picked = cat.id
                        transfer = false
                    }
                }
                Choice("Transfer (not spending)", transfer) { transfer = true }
            }
            // Money back from someone goes in their name.
            if (!charge && people.isNotEmpty()) {
                Label("Paid back by")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    people.forEach { p ->
                        Choice(p.name, !transfer && picked == p.id) {
                            picked = p.id
                            transfer = false
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ready = transfer || picked != null
                Text(
                    "This one",
                    color = if (ready) c.ink else c.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, c.line, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, enabled = ready) { onPick(picked, false, transfer) }
                        .padding(vertical = 13.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (!transfer && !txn.peer) {
                    Text(
                        "All from ${txn.merchant}",
                        color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1.4f)
                            .background(if (ready) c.accent else c.muted, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, enabled = ready) { onPick(picked, true, false) }
                            .padding(vertical = 13.dp, horizontal = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            if (charge) OwedSection(txn, people, onOwed)
        }
    }
}

/** "Someone owes me": who, and how much of the charge. Their share leaves your spending. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwedSection(txn: TxnDto, people: List<CategoryDto>, onOwed: (String?, String?, Long?) -> Unit) {
    val c = Budget.colors
    val total = kotlin.math.abs(txn.amountCents)
    var person by remember { mutableStateOf(txn.owed?.personId) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", (txn.owed?.cents ?: total) / 100.0)) }
    val cents = amount.replace(",", "").replace("$", "").toDoubleOrNull()?.let { Math.round(it * 100) }
    val ready = (person != null || (adding && newName.isNotBlank())) && cents != null && cents in 1..total
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    Label("Someone owes me")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        people.forEach { p ->
            Choice(p.name, !adding && person == p.id) {
                person = p.id
                adding = false
            }
        }
        Choice("New person…", adding) {
            adding = true
            person = null
        }
    }
    if (adding) {
        OutlinedTextField(value = newName, onValueChange = { newName = it.take(40) }, placeholder = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            leadingIcon = { Text("$") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Choice("Half", false) { amount = String.format(java.util.Locale.US, "%.2f", Math.round(total / 2.0) / 100.0) }
        Choice("All", false) { amount = String.format(java.util.Locale.US, "%.2f", total / 100.0) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (txn.owed != null) {
            Text(
                "Remove",
                color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, c.line, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onOwed(null, null, null) }
                    .padding(vertical = 13.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Text(
            "Save who owes me",
            color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1.4f)
                .background(if (ready) c.accent else c.muted, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, enabled = ready) { onOwed(if (adding) null else person, if (adding) newName else null, cents) }
                .padding(vertical = 13.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    val c = Budget.colors
    Text(
        label,
        color = if (on) c.surface else c.ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(if (on) c.ink else c.surface, RoundedCornerShape(50))
            .border(1.dp, if (on) c.ink else c.line, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
