package com.budget.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.budget.app.data.Appearance
import com.budget.app.data.EnrollLinks
import com.budget.app.data.update.UpdateNotifier
import com.budget.app.ui.AppRoot
import com.budget.app.ui.theme.BudgetTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as BudgetApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        takeLink(intent)
        setContent {
            val appearance by container.appearance.collectAsState(initial = Appearance())
            BudgetTheme(appearance) {
                AppRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeLink(intent)
    }

    private fun takeLink(intent: Intent?) {
        EnrollLinks.parse(intent?.data?.toString())?.let { container.pendingEnrollLink.value = it }
        when (intent?.getStringExtra(EXTRA_OPEN_TAB)) {
            "cards" -> container.openCards.value = true
            "insights" -> container.openInsights.value = true
        }
        intent?.removeExtra(EXTRA_OPEN_TAB)
        intent?.getStringExtra(EXTRA_LOYALTY)?.let {
            container.openLoyalty.value = it
            intent.removeExtra(EXTRA_LOYALTY)
        }
        intent?.getStringExtra(com.budget.app.data.reminders.AlertWorker.EXTRA_OPEN_SCREEN)?.let {
            container.openScreen.value = it
            intent.removeExtra(com.budget.app.data.reminders.AlertWorker.EXTRA_OPEN_SCREEN)
        }
        if (intent?.getStringExtra(UpdateNotifier.EXTRA_OPEN) == UpdateNotifier.OPEN_UPDATE) {
            container.openUpdate.value = true
            intent.removeExtra(UpdateNotifier.EXTRA_OPEN)
        }
    }

    companion object {
        /** Set by the card reminder: open on the Cards tab. */
        const val EXTRA_OPEN_TAB = "open_tab"

        /** Set by a long-press shortcut: "" opens Loyalty Cards, a card id opens that card. */
        const val EXTRA_LOYALTY = "loyalty"
    }
}
