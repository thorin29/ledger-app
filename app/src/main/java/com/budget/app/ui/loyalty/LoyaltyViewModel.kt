package com.budget.app.ui.loyalty

import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budget.app.AppContainer
import com.budget.app.MainActivity
import com.budget.app.R
import com.budget.app.data.Settings
import com.budget.app.data.remote.ApiException
import com.budget.app.data.remote.LoyaltyCardDto
import com.budget.app.data.remote.LoyaltyListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class LoyaltyUi(
    val cards: List<LoyaltyCardDto> = emptyList(),
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * The household's loyalty cards. The list and each card's image are kept on the phone, so cards
 * show at the register with no signal; they refresh from Ledger whenever it's reachable.
 */
class LoyaltyViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(LoyaltyUi())
    val ui: StateFlow<LoyaltyUi> = _ui
    private val dir = File(c.filesDir, "loyalty").apply { mkdirs() }

    init {
        viewModelScope.launch {
            c.settings.get(Settings.LOYALTY_CACHE)?.let { raw ->
                runCatching { c.api.json.decodeFromString(LoyaltyListResponse.serializer(), raw) }.getOrNull()?.let { r ->
                    _ui.update { it.copy(cards = r.cards, loaded = true) }
                }
            }
            refresh()
        }
    }

    /** The image saved on this phone for a card, if it has one. */
    fun imageFile(card: LoyaltyCardDto): File? = if (card.imageVersion > 0) File(dir, "${card.id}-${card.imageVersion}.img").takeIf { it.exists() } else null

    fun refresh() {
        viewModelScope.launch {
            try {
                val r = c.api.loyaltyCards()
                c.settings.set(Settings.LOYALTY_CACHE, c.api.json.encodeToString(LoyaltyListResponse.serializer(), r))
                _ui.update { it.copy(cards = r.cards, loaded = true) }
                fetchImages(r.cards)
                updateShortcuts(r.cards)
            } catch (_: Exception) {
                _ui.update { it.copy(loaded = true) }
            }
        }
    }

    private suspend fun fetchImages(cards: List<LoyaltyCardDto>) = withContext(Dispatchers.IO) {
        val keep = cards.filter { it.imageVersion > 0 }.map { "${it.id}-${it.imageVersion}.img" }.toSet()
        dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        for (card in cards.filter { it.imageVersion > 0 }) {
            val f = File(dir, "${card.id}-${card.imageVersion}.img")
            if (f.exists()) continue
            runCatching { f.writeBytes(c.api.loyaltyImage(card.id)) }
        }
        // A fresh copy of the list so the images show.
        _ui.update { it.copy(cards = it.cards.toList()) }
    }

    /** Long-press the app icon: Loyalty Cards, then the three cards used most. */
    private fun updateShortcuts(cards: List<LoyaltyCardDto>) {
        val ctx = c.appContext
        val all = ShortcutInfoCompat.Builder(ctx, "loyalty")
            .setShortLabel("Loyalty Cards")
            .setRank(0)
            .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_shortcut_card))
            .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_LOYALTY, ""))
            .build()
        val top = listOf(all) + cards.take(3).mapIndexed { i, card ->
            ShortcutInfoCompat.Builder(ctx, "loyalty-${card.id}")
                .setShortLabel(card.name.take(25))
                .setRank(i + 1)
                .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_shortcut_card))
                .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_LOYALTY, card.id))
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(ctx, top) }
    }

    fun used(card: LoyaltyCardDto) {
        viewModelScope.launch { runCatching { c.api.markLoyaltyUsed(card.id) } }
    }

    /** Saves a card, and its new image when [jpeg] is set ([removeImage] takes it off). Calls [done] on success. */
    fun save(card: LoyaltyCardDto, jpeg: ByteArray?, removeImage: Boolean, done: (LoyaltyCardDto) -> Unit) {
        act {
            var saved = c.api.saveLoyaltyCard(card)
            if (jpeg != null) saved = c.api.setLoyaltyImage(saved.id, jpeg)
            else if (removeImage && card.imageVersion > 0) saved = c.api.setLoyaltyImage(saved.id, null)
            if (jpeg != null) withContext(Dispatchers.IO) { File(dir, "${saved.id}-${saved.imageVersion}.img").writeBytes(jpeg) }
            refresh()
            done(saved)
        }
    }

    fun delete(card: LoyaltyCardDto, done: () -> Unit) {
        act {
            c.api.deleteLoyaltyCard(card.id)
            refresh()
            done()
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    private fun act(call: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            val note = try {
                call()
                null
            } catch (e: ApiException) {
                if (e.status == 404) "Ledger on the server needs updating for loyalty cards." else e.message ?: "The server refused that."
            } catch (e: IOException) {
                "You're offline. Cards can be added once you're back."
            }
            _ui.update { it.copy(busy = false, message = note) }
        }
    }
}
