package com.budget.app.data

import com.budget.app.data.remote.ApiClient
import com.budget.app.data.remote.ApiException
import com.budget.app.data.secure.TokenStore
import com.budget.app.data.sync.LocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

/** Where the app stands. One state gates the whole interface. */
sealed interface SessionState {
    data object Loading : SessionState
    /** Never set up on this phone. */
    data class SignedOut(val server: String?) : SessionState
    data class SignedIn(val server: String, val username: String, val deviceName: String) : SessionState
    /** Was signed in; the server no longer accepts this phone (revoked, password changed, expired). */
    data class NeedsSignIn(val server: String, val username: String?, val reason: String) : SessionState
}

class Session(
    private val settings: Settings,
    private val tokens: TokenStore,
    private val local: LocalStore,
    private val api: ApiClient,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state

    /** The configured server, for the API client. Kept in memory once known. */
    @Volatile
    var server: String? = null
        private set

    suspend fun load() {
        server = settings.get(Settings.SERVER)
        val token = tokens.load()
        val user = settings.get(Settings.USERNAME)
        val device = settings.get(Settings.DEVICE_NAME) ?: "Phone"
        val s = server
        _state.value = when {
            s == null -> SessionState.SignedOut(null)
            token == null && user != null -> SessionState.NeedsSignIn(s, user, "Sign in again to continue.")
            token == null -> SessionState.SignedOut(s)
            else -> SessionState.SignedIn(s, user ?: "", device)
        }
    }

    /** Step 1: check the address is a Ledger server. Returns its version. */
    suspend fun checkServer(server: String): String {
        val meta = api.meta(server)
        if (meta.app != "budget") throw IOException("That address isn't a Ledger server.")
        if (meta.api != 1) throw IOException("This app needs a newer or older server (API ${meta.api}).")
        return meta.version
    }

    /** Steps 2 and 3: password → proof; proof + web code → this phone's token. */
    suspend fun enroll(server: String, username: String, password: String, code: String, deviceName: String) {
        val proof = api.login(server, username.trim().lowercase(), password).proof
        val enrolled = api.enroll(server, proof, code, deviceName)
        // A new sign-in starts clean: nothing queued or cached under another one.
        val previous = settings.get(Settings.SERVER)
        val previousUser = settings.get(Settings.USERNAME)
        if (previous != server || previousUser != username.trim().lowercase()) local.clearAll()
        tokens.save(enrolled.token)
        settings.set(Settings.SERVER, server)
        settings.set(Settings.USERNAME, username.trim().lowercase())
        settings.set(Settings.DEVICE_NAME, enrolled.device.name)
        this.server = server
        _state.value = SessionState.SignedIn(server, username.trim().lowercase(), enrolled.device.name)
    }

    /** The second person: invite code + a username and password of their own → their account, signed in on this phone. */
    suspend fun join(server: String, code: String, username: String, password: String, deviceName: String) {
        val joined = api.join(server, code, username.trim().lowercase(), password, deviceName)
        local.clearAll()
        tokens.save(joined.token)
        settings.set(Settings.SERVER, server)
        settings.set(Settings.USERNAME, joined.user.username)
        settings.set(Settings.DEVICE_NAME, joined.device.name)
        this.server = server
        _state.value = SessionState.SignedIn(server, joined.user.username, joined.device.name)
    }

    /**
     * Any call came back 401. Confirm with /auth/me before giving up, so a
     * single odd response can't sign the phone out.
     */
    suspend fun onUnauthorized() {
        val s = server ?: return
        try {
            api.me()
            return
        } catch (e: ApiException) {
            if (e.status != 401) return
            tokens.clear()
            _state.value = SessionState.NeedsSignIn(s, settings.get(Settings.USERNAME), reasonFor(e.code))
        } catch (_: IOException) {
            // Offline: decide later.
        }
    }

    suspend fun signOut() {
        runCatching { api.logout() }
        tokens.clear()
        local.clearAll()
        settings.set(Settings.USERNAME, null)
        settings.set(Settings.DEVICE_NAME, null)
        _state.value = SessionState.SignedOut(server)
    }

    private fun reasonFor(code: String): String = when (code) {
        "device_revoked" -> "This phone was signed out from Settings on the web."
        "reauth_required" -> "The password changed. Sign in again."
        "device_expired" -> "This phone's sign-in expired. Sign in again."
        else -> "Sign in again to continue."
    }
}
