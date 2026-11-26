package com.budget.app.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.CategoryDto
import com.budget.app.data.remote.TxnDto
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import java.util.Locale

private const val NEW = "__new"

/**
 * One transaction, full screen: its category (a dropdown) for this charge or all from the merchant,
 * whether it's a transfer, and who owes you for it (or, for money in, who paid you back).
 */
@Composable
fun TransactionPage(
    txn: TxnDto,
    categories: List<CategoryDto>,
    onBack: () -> Unit,
    /** (category id, all from the merchant, transfer) */
    onPick: (String?, Boolean, Boolean) -> Unit,
    /** (person id, new person's name, cents); a null person clears it. */
    onOwed: (String?, String?, Long?) -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = Budget.colors
    val people = categories.filter { it.managedForOthers }
    val budgetCats = categories.filterNot { it.managedForOthers }
    val moneyIn = txn.amountCents > 0
    val charge = txn.kind == "NORMAL" && txn.amountCents < 0 && !txn.pending && people.none { it.id == txn.categoryId }
    var picked by remember { mutableStateOf(txn.categoryId?.takeIf { id -> budgetCats.any { it.id == id } }) }
    var payer by remember { mutableStateOf(txn.categoryId?.takeIf { id -> people.any { it.id == id } }) }
    var allFrom by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf(txn.kind == "TRANSFER") }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                PhIcon(Ph.CARET_LEFT, "Back", c.ink)
            }
            Text("Transaction", color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PageCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(txn.merchant, color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text((if (moneyIn) "+" else "−") + Money.format(kotlin.math.abs(txn.amountCents)), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("${Dates.short(txn.postedOn)} · ${txn.account}${if (txn.pending) " · pending" else ""}", color = c.muted, fontSize = 13.sp)
            }

            Label("Category")
            PageCard {
                DropdownField(
                    value = if (transfer) null else picked,
                    placeholder = if (transfer) "Transfer (not spending)" else "Pick a category",
                    options = budgetCats.map { it.id to it.name },
                    onPick = {
                        picked = it
                        payer = null
                        transfer = false
                    },
                )
                if (!txn.peer && !transfer) {
                    Row(Modifier.fillMaxWidth().border(1.5.dp, c.line, RoundedCornerShape(12.dp))) {
                        Segment("This charge", !allFrom, Modifier.weight(1f)) { allFrom = false }
                        Segment("All from ${txn.merchant}", allFrom, Modifier.weight(1f)) { allFrom = true }
                    }
                }
                ActionButton("Save category", enabled = transfer || picked != null) { onPick(if (transfer) null else picked, allFrom && !transfer, transfer) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Not spending (transfer)", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = transfer,
                        onCheckedChange = { transfer = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
                    )
                }
            }

            if (moneyIn && people.isNotEmpty()) {
                Label("Paid back by")
                PageCard {
                    DropdownField(value = payer, placeholder = "Who paid you back", options = people.map { it.id to it.name }, onPick = { payer = it })
                    ActionButton("Save · paid back by ${people.firstOrNull { it.id == payer }?.name ?: "…"}", enabled = payer != null) { onPick(payer, allFrom, false) }
                }
            }

            if (charge) {
                Label("Someone owes me")
                OwedPanel(txn, people, onOwed)
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun OwedPanel(txn: TxnDto, people: List<CategoryDto>, onOwed: (String?, String?, Long?) -> Unit) {
    val c = Budget.colors
    val total = kotlin.math.abs(txn.amountCents)
    var person by remember { mutableStateOf(txn.owed?.personId) }
    var newName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(dollars(txn.owed?.cents ?: total)) }
    val cents = amount.replace(",", "").replace("$", "").trim().toDoubleOrNull()?.let { Math.round(it * 100) }
    val adding = person == NEW
    val name = if (adding) newName.trim() else people.firstOrNull { it.id == person }?.name
    val ready = !name.isNullOrBlank() && cents != null && cents in 1..total
    PageCard {
        Text("Who", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        DropdownField(value = person, placeholder = "Who owes you", options = people.map { it.id to it.name } + (NEW to "New person…"), onPick = { person = it })
        if (adding) {
            OutlinedTextField(value = newName, onValueChange = { newName = it.take(40) }, placeholder = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                leadingIcon = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Chip("Half") { amount = dollars(Math.round(total / 2.0)) }
            Chip("All") { amount = dollars(total) }
        }
        ActionButton(if (ready) "Save · $name owes ${Money.format(cents!!)}" else "Save", enabled = ready) {
            onOwed(if (adding) null else person, if (adding) newName.trim() else null, cents)
        }
        if (txn.owed != null) {
            Text(
                "Remove",
                color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, c.line, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onOwed(null, null, null) }
                    .padding(vertical = 13.dp),
            )
        }
    }
}

private fun dollars(cents: Long) = String.format(Locale.US, "%.2f", cents / 100.0)

/** A dropdown: the choice in a bordered field, the list in a menu. */
@Composable
internal fun DropdownField(value: String?, placeholder: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val c = Budget.colors
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.5.dp, c.line, RoundedCornerShape(12.dp))
                .background(c.surface, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = options.firstOrNull { it.first == value }?.second
            Text(label ?: placeholder, color = if (label != null) c.ink else c.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("▾", color = c.muted, fontSize = 14.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name, fontWeight = if (id == value) FontWeight.Bold else FontWeight.Normal) }, onClick = {
                    onPick(id)
                    open = false
                })
            }
        }
    }
}

@Composable
internal fun PageCard(content: @Composable () -> Unit) {
    val c = Budget.colors
    Column(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
internal fun ActionButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Budget.colors
    Text(
        label,
        color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (enabled) c.accent else c.muted, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 10.dp),
    )
}

@Composable
private fun Segment(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Budget.colors
    Text(
        label,
        color = if (on) c.surface else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(if (on) c.ink else c.surface, RoundedCornerShape(10.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 8.dp),
    )
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    val c = Budget.colors
    Text(
        label,
        color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .border(1.5.dp, c.line, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
    )
}
