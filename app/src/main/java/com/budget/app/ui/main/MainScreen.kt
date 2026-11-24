package com.budget.app.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import com.budget.app.data.Settings
import com.budget.app.ui.settings.UpdateScreen
import com.budget.app.ui.theme.UpdateDot
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budget.app.AppContainer
import com.budget.app.data.Money
import com.budget.app.data.MonthLogic
import com.budget.app.data.SessionState
import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.sync.Overlay
import com.budget.app.ui.month.MonthScreen
import com.budget.app.ui.month.MonthViewModel
import com.budget.app.ui.plan.PlanScreen
import com.budget.app.ui.plan.PlanViewModel
import com.budget.app.ui.record.RecordSheet
import com.budget.app.ui.settings.MoreScreen
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import com.budget.app.ui.phone.InsightsHome
import com.budget.app.ui.phone.NotificationsScreen
import com.budget.app.ui.phone.PhoneViewModel
import com.budget.app.ui.phone.StatementHeader
import com.budget.app.ui.phone.TodayHome
import com.budget.app.ui.phone.TransactionsScreen

enum class Tab(val label: String, val icon: Ph, val iconOn: Ph) {
    TODAY("Today", Ph.HOUSE, Ph.HOUSE_FILL),
    MONTH("Tracker", Ph.CALENDAR, Ph.CALENDAR_FILL),
    CARDS("Cards", Ph.CARD, Ph.CARD_FILL),
    INSIGHTS("Insights", Ph.PIE, Ph.PIE_FILL),
    MORE("More", Ph.LIST, Ph.LIST_FILL),
}

/** Which month a record sheet works on, and what it opened on. */
private data class RecordRequest(val vm: MonthViewModel, val entry: MonthEntry?)

/** What's open over More: one of its screens, or More itself. */
private enum class MoreScreenKind { NONE, REVIEW, TRANSACTIONS, NOTIFICATIONS, UPDATE, PLAN }

/** Signed in: five tabs along the bottom, and the record sheet over any of them. */
@Composable
fun MainScreen(container: AppContainer, session: SessionState.SignedIn) {
    val c = Budget.colors
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val todayVm: MonthViewModel = viewModel(key = "today") { MonthViewModel(container) }
    val monthVm: MonthViewModel = viewModel(key = "month") { MonthViewModel(container) }
    val planVm: PlanViewModel = viewModel(key = "plan") { PlanViewModel(container) }
    val extrasVm: com.budget.app.ui.today.ExtrasViewModel = viewModel(key = "extras") { com.budget.app.ui.today.ExtrasViewModel(container) }
    val phoneVm: PhoneViewModel = viewModel(key = "phone") { PhoneViewModel(container) }
    val extras by extrasVm.ui.collectAsState()
    val phone by phoneVm.ui.collectAsState()
    val update by container.updates.available.collectAsState()

    var record by remember { mutableStateOf<RecordRequest?>(null) }
    var editingBalanceOf by remember { mutableStateOf<MonthViewModel?>(null) }
    var more by rememberSaveable { mutableStateOf(MoreScreenKind.NONE) }
    // Insights shows Overview or Budget.
    var insightsPart by rememberSaveable { mutableStateOf("overview") }

    fun go(screen: String) {
        when (screen) {
            "tracker" -> tab = Tab.MONTH
            "cards" -> tab = Tab.CARDS
            "insights" -> {
                insightsPart = "overview"
                tab = Tab.INSIGHTS
            }
            "budget" -> {
                insightsPart = "budget"
                phoneVm.loadBudget(null)
                tab = Tab.INSIGHTS
            }
            "budget-month" -> {
                insightsPart = "budget"
                phoneVm.loadBudget("month")
                tab = Tab.INSIGHTS
            }
            "review" -> {
                more = MoreScreenKind.REVIEW
                tab = Tab.MORE
            }
            "transactions" -> {
                more = MoreScreenKind.TRANSACTIONS
                phoneVm.loadTransactions()
                tab = Tab.MORE
            }
            else -> tab = Tab.TODAY
        }
    }

    // Opened from a notification: straight to its screen.
    val openScreen by container.openScreen.collectAsState()
    LaunchedEffect(openScreen) {
        openScreen?.let {
            go(it)
            container.openScreen.value = null
        }
    }
    val openInsights by container.openInsights.collectAsState()
    LaunchedEffect(openInsights) {
        if (openInsights) {
            tab = Tab.INSIGHTS
            container.openInsights.value = false
        }
    }
    val openCards by container.openCards.collectAsState()
    LaunchedEffect(openCards) {
        if (openCards) {
            tab = Tab.CARDS
            container.openCards.value = false
        }
    }
    val openUpdate by container.openUpdate.collectAsState()
    LaunchedEffect(openUpdate) {
        if (openUpdate) {
            tab = Tab.MORE
            more = MoreScreenKind.UPDATE
            container.openUpdate.value = false
        }
    }

    fun reload(t: Tab) {
        when (t) {
            Tab.TODAY -> {
                todayVm.load()
                extrasVm.load()
                phoneVm.loadToday()
            }
            Tab.MONTH -> monthVm.load()
            Tab.CARDS -> {
                extrasVm.load()
                phoneVm.loadToday()
            }
            Tab.INSIGHTS -> {
                extrasVm.load()
                phoneVm.loadOverview()
                phoneVm.loadBudget()
            }
            Tab.MORE -> when (more) {
                MoreScreenKind.PLAN -> planVm.load()
                MoreScreenKind.TRANSACTIONS -> phoneVm.loadTransactions()
                MoreScreenKind.REVIEW -> extrasVm.load()
                else -> extrasVm.load()
            }
        }
    }

    // Showing a tab refreshes it, so a change made on one shows on the others.
    LaunchedEffect(tab) { reload(tab) }

    BackHandler(enabled = tab != Tab.TODAY && more == MoreScreenKind.NONE) { tab = Tab.TODAY }
    BackHandler(enabled = tab == Tab.MORE && more != MoreScreenKind.NONE) { more = MoreScreenKind.NONE }

    // Pull down on a tab to reload it from the server (not the bank).
    var pulled by remember { mutableStateOf(false) }
    val todayLoading by todayVm.ui.collectAsState()
    val monthLoading by monthVm.ui.collectAsState()
    val busy = extras.refreshing || todayLoading.loading || monthLoading.loading || phone.loading
    LaunchedEffect(pulled, busy) {
        if (pulled && !busy) {
            kotlinx.coroutines.delay(500)
            pulled = false
        }
    }
    // A server older than the phone views: the earlier screens, as before.
    val legacy = phone.unsupported && phone.today == null

    Column(Modifier.fillMaxSize().background(c.background)) {
        com.budget.app.ui.common.PullRefresh(
            refreshing = pulled,
            onRefresh = {
                pulled = true
                reload(tab)
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when (tab) {
                Tab.TODAY -> {
                    val t = phone.today
                    if (t != null && !legacy) {
                        TodayHome(
                            t = t,
                            bankSyncing = extras.bankSyncing,
                            bankNote = extras.bankNote,
                            offline = todayLoading.offline,
                            onRefreshBank = { extrasVm.syncBank(onDone = { phoneVm.loadToday() }) },
                            onOpen = { go(it) },
                            onRecord = { id ->
                                val view = todayLoading.view
                                val entry = view?.let { v -> MonthLogic.everything(v).firstOrNull { it.lineItemId == id } }
                                record = RecordRequest(todayVm, entry)
                            },
                        )
                    } else {
                        com.budget.app.ui.today.TodayScreen(
                            vm = todayVm,
                            onRecord = { record = RecordRequest(todayVm, it) },
                            onEditBalance = { editingBalanceOf = todayVm },
                            onOpenMonth = { tab = Tab.MONTH },
                            onOpenPlan = {
                                more = MoreScreenKind.PLAN
                                tab = Tab.MORE
                                planVm.load()
                            },
                            onOpenMore = { tab = Tab.MORE },
                            extras = extras,
                            onOpenReview = { go("review") },
                            onOpenCards = { tab = Tab.CARDS },
                            onOpenInsights = { tab = Tab.INSIGHTS },
                            onRefreshBank = { extrasVm.syncBank(onDone = { todayVm.load() }) },
                        )
                    }
                }
                Tab.MONTH -> MonthScreen(monthVm, onRecord = { record = RecordRequest(monthVm, it) })
                Tab.CARDS -> {
                    val today = phone.today
                    com.budget.app.ui.cards.CardsScreen(
                        container, extras,
                        header = if (today?.statement != null) ({ StatementHeader(today, onOpenBudget = { go("budget") }) }) else null,
                    )
                }
                Tab.INSIGHTS -> if (legacy) {
                    com.budget.app.ui.insights.InsightsScreen(
                        extras,
                        onMonth = { extrasVm.showInsightsMonth(it) },
                        onOpenCategory = { extrasVm.openCategory(it) },
                        onMakeBill = { id, name -> extrasVm.chargeAsBill(id, name) },
                    )
                } else {
                    InsightsHome(
                        overview = phone.overview,
                        budget = phone.budget,
                        part = insightsPart,
                        onPart = {
                            insightsPart = it
                            if (it == "budget" && phone.budget == null) phoneVm.loadBudget()
                        },
                        onBudgetView = { phoneVm.loadBudget(it) },
                    )
                }
                Tab.MORE -> when (more) {
                    MoreScreenKind.UPDATE -> UpdateScreen(container, onBack = { more = MoreScreenKind.NONE })
                    MoreScreenKind.PLAN -> PlanScreen(planVm, onEditBalance = { editingBalanceOf = todayVm }, onBack = { more = MoreScreenKind.NONE })
                    MoreScreenKind.REVIEW -> com.budget.app.ui.review.ReviewScreen(extrasVm, onBack = {
                        more = MoreScreenKind.NONE
                        phoneVm.loadToday()
                    })
                    MoreScreenKind.TRANSACTIONS -> TransactionsScreen(
                        ui = phone,
                        onBack = { more = MoreScreenKind.NONE },
                        onFilter = { phoneVm.loadTransactions(filter = it) },
                        onSearch = { phoneVm.loadTransactions(search = it) },
                        onCategorize = { t, cat, all, transfer -> phoneVm.categorize(t, cat, all, transfer) },
                    )
                    MoreScreenKind.NOTIFICATIONS -> NotificationsScreen(container, onBack = { more = MoreScreenKind.NONE })
                    MoreScreenKind.NONE -> MoreScreen(
                        container, session,
                        onOpenUpdate = { more = MoreScreenKind.UPDATE },
                        onOpenPlan = {
                            more = MoreScreenKind.PLAN
                            planVm.load()
                        },
                        onOpenReview = {
                            more = MoreScreenKind.REVIEW
                            extrasVm.load()
                        },
                        onOpenTransactions = {
                            more = MoreScreenKind.TRANSACTIONS
                            phoneVm.loadTransactions()
                        },
                        onOpenNotifications = { more = MoreScreenKind.NOTIFICATIONS },
                        reviewCount = phone.today?.needsYou?.firstOrNull { it.kind == "review" }?.let { n -> n.text.takeWhile { ch -> ch.isDigit() }.toIntOrNull() } ?: 0,
                    )
                }
            }
        }
        val reviewWaiting = phone.today?.needsYou?.any { it.kind == "review" } == true
        BottomBar(tab, updateAvailable = update != null, reviewWaiting = reviewWaiting, onSelect = {
            if (it == Tab.MORE && it == tab) more = MoreScreenKind.NONE
            tab = it
        })
    }

    record?.let { r ->
        val ui by r.vm.ui.collectAsState()
        val pending by r.vm.pending.collectAsState()
        val view = remember(ui.view, pending) { ui.view?.let { Overlay.apply(it, pending) } }
        RecordSheet(
            candidates = view?.let { MonthLogic.dueSoon(it) } ?: emptyList(),
            everything = view?.let { MonthLogic.everything(it) } ?: emptyList(),
            initial = r.entry,
            defaultPaidOn = r.vm::defaultPaidOn,
            onDismiss = { record = null },
            onConfirm = { entry, cents, day ->
                r.vm.record(entry, cents, day)
                record = null
                phoneVm.loadToday()
            },
            onClear = { entry ->
                r.vm.clear(entry)
                record = null
                phoneVm.loadToday()
            },
        )
    }

    editingBalanceOf?.let { vm ->
        val ui by vm.ui.collectAsState()
        BalanceDialog(
            current = ui.view?.billsBalanceCents,
            accountName = ui.view?.billsAccountName,
            onDismiss = { editingBalanceOf = null },
            onSave = {
                vm.setBalance(it)
                editingBalanceOf = null
                if (more == MoreScreenKind.PLAN) planVm.load()
            },
        )
    }
}

@Composable
private fun BottomBar(current: Tab, updateAvailable: Boolean, reviewWaiting: Boolean = false, onSelect: (Tab) -> Unit) {
    val c = Budget.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp)) {
            Tab.entries.forEach { t ->
                val on = t == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(role = Role.Tab) { onSelect(t) }
                        .semantics { selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(32.dp)
                            .background(if (on) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PhIcon(if (on) t.iconOn else t.icon, null, if (on) c.accent else c.muted)
                        // More's dots, on every screen: amber for items to review, light blue for an
                        // update, each with a ring in the bar color. With both, the update sits on top
                        // and the review dot peeks out below it.
                        if (t == Tab.MORE) {
                            val both = updateAvailable && reviewWaiting
                            if (reviewWaiting) {
                                MoreDot(
                                    Modifier.align(Alignment.TopEnd).padding(top = if (both) 7.dp else 2.dp, end = if (both) 7.dp else 12.dp),
                                    c.warn,
                                    "Items to review",
                                )
                            }
                            if (updateAvailable) {
                                MoreDot(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 12.dp), UpdateDot, "Update available")
                            }
                        }
                    }
                    Text(
                        t.label,
                        color = if (on) c.ink else c.muted,
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreDot(modifier: Modifier, color: androidx.compose.ui.graphics.Color, label: String) {
    Box(
        modifier
            .size(14.dp)
            .background(Budget.colors.surface, CircleShape)
            .padding(2.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun BalanceDialog(current: Long?, accountName: String?, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val c = Budget.colors
    var text by remember { mutableStateOf(current?.let { Money.toDecimal(maxOf(it, 0L)) } ?: "") }
    val cents = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("In ${accountName ?: "the bills account"} now", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = text.isNotEmpty() && cents == null,
            )
        },
        confirmButton = {
            Button(
                onClick = { cents?.let(onSave) },
                enabled = cents != null,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.muted) } },
    )
}
