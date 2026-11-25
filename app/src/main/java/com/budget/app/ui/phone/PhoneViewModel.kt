package com.budget.app.ui.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budget.app.AppContainer
import com.budget.app.data.remote.ApiException
import com.budget.app.data.remote.BudgetResponse
import com.budget.app.data.remote.CategoryDto
import com.budget.app.data.remote.OverviewResponse
import com.budget.app.data.remote.TodayResponse
import com.budget.app.data.remote.TxnDto
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class PhoneUi(
    val today: TodayResponse? = null,
    val budget: BudgetResponse? = null,
    /** The Budget view shown: "month", "cards" or a card's id; null picks the main card's statement. */
    val budgetView: String? = null,
    val overview: OverviewResponse? = null,
    val transactions: List<TxnDto> = emptyList(),
    val txnFilter: String = "all",
    val txnSearch: String = "",
    val categories: List<CategoryDto> = emptyList(),
    val loading: Boolean = false,
    /** Set when the server is older than these views (0.101). */
    val unsupported: Boolean = false,
    val message: String? = null,
)

/**
 * Today, the Budget views, the Insights overview and Transactions, as the server works them out.
 * The last copy stays on screen while offline.
 */
class PhoneViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(PhoneUi())
    val ui: StateFlow<PhoneUi> = _ui

    fun loadToday() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val today = async { fetch { c.api.today() } }
            val overview = async { fetch { c.api.overview() } }
            val t = today.await()
            _ui.update { it.copy(today = t ?: it.today, overview = overview.await() ?: it.overview, loading = false) }
            if (_ui.value.budget == null) loadBudget()
        }
    }

    fun loadOverview() {
        viewModelScope.launch { fetch { c.api.overview() }?.let { r -> _ui.update { it.copy(overview = r) } } }
    }

    /** The Budget view; with no choice yet, the main card's statement (what Today's headline is). */
    fun loadBudget(view: String? = _ui.value.budgetView) {
        _ui.update { it.copy(budgetView = view) }
        viewModelScope.launch {
            val key = view ?: _ui.value.today?.available?.cardId ?: "month"
            fetch { c.api.budget(key) }?.let { r -> _ui.update { it.copy(budget = r) } }
        }
    }

    fun loadTransactions(filter: String = _ui.value.txnFilter, search: String = _ui.value.txnSearch) {
        _ui.update { it.copy(txnFilter = filter, txnSearch = search) }
        viewModelScope.launch {
            fetch { c.api.transactions(filter, search) }?.let { r -> _ui.update { it.copy(transactions = r.transactions) } }
            if (_ui.value.categories.isEmpty()) fetch { c.api.categories() }?.let { r -> _ui.update { it.copy(categories = r) } }
        }
    }

    /** A category for one charge, or every charge from its merchant; null categoryId with transfer=true marks it a transfer. */
    fun categorize(txn: TxnDto, categoryId: String?, forMerchant: Boolean, transfer: Boolean = false) {
        viewModelScope.launch {
            val note = try {
                if (transfer) {
                    c.api.setTransfer(txn.id, true)
                    "Marked as a transfer."
                } else {
                    val r = c.api.setCategory(txn.id, categoryId, forMerchant)
                    if (forMerchant && r.others > 0) "Saved. ${r.others} other${if (r.others == 1) "" else "s"} from ${txn.merchant} too." else "Saved."
                }
            } catch (e: ApiException) {
                e.message ?: "The server refused that."
            } catch (e: IOException) {
                "You're offline. Try again when you're back."
            }
            _ui.update { it.copy(message = note) }
            loadTransactions()
            loadToday()
        }
    }

    /**
     * Who owes you for a charge: an existing person ([personId]) or a new one ([newName]), and how
     * much. A null person clears it.
     */
    fun setOwed(txn: TxnDto, personId: String?, newName: String?, cents: Long?) {
        viewModelScope.launch {
            val note = try {
                val id = if (personId == null && !newName.isNullOrBlank()) c.api.addPerson(newName.trim()).id else personId
                c.api.setOwed(txn.id, id, cents)
                if (!newName.isNullOrBlank()) fetch { c.api.categories() }?.let { r -> _ui.update { it.copy(categories = r) } }
                if (id == null) "No one owes you for it now." else "Saved."
            } catch (e: ApiException) {
                if (e.status == 404) "Ledger on the server needs updating for this." else e.message ?: "The server refused that."
            } catch (e: IOException) {
                "You're offline. Try again when you're back."
            }
            _ui.update { it.copy(message = note) }
            loadTransactions()
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    private suspend fun <T> fetch(call: suspend () -> T): T? = try {
        call()
    } catch (e: ApiException) {
        if (e.status == 404) _ui.update { it.copy(unsupported = true) }
        null
    } catch (_: IOException) {
        null
    } catch (_: Exception) {
        null
    }
}
