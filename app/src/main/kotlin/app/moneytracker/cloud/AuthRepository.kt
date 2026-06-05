package app.moneytracker.cloud

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper over supabase-kt's Auth. Hides the SDK from feature code so
 * we can swap implementations or stub for tests.
 *
 * Passwords arrive as `CharArray` (UI clears its own copy after handing it
 * over). The Supabase SDK API takes `String`, so we cannot fully avoid the
 * String lingering in the heap — Hardening §21.5 documents this residual.
 */
object AuthRepository {

    enum class SignUpResult { SignedIn, ConfirmationRequired }

    private val auth get() = SupabaseProvider.client.auth

    val sessionStatus: Flow<SessionStatus> get() = auth.sessionStatus

    // The SDK often restores the session token without the decoded user
    // object, so currentUserOrNull() is null even with a valid session. Fall
    // back to the JWT's `sub` claim, which always carries the user id.
    fun currentUserId(): String? =
        auth.currentUserOrNull()?.id
            ?: auth.currentSessionOrNull()?.accessToken?.let(::userIdFromJwt)

    fun currentUserEmail(): String? = auth.currentUserOrNull()?.email

    /**
     * Bounded wait for the session to restore from storage, returning the user
     * id once available (or null after [timeoutMs]). Replaces the SDK's
     * awaitInitialization(), which hangs in this setup. Without it the fetch /
     * capture race an unattached session, so requests go out unauthenticated
     * and RLS returns 0 rows.
     */
    suspend fun awaitUserId(timeoutMs: Long = 5_000): String? {
        var waited = 0L
        while (auth.currentSessionOrNull() == null && waited < timeoutMs) {
            kotlinx.coroutines.delay(100)
            waited += 100
        }
        return currentUserId()
    }

    private fun userIdFromJwt(token: String): String? = runCatching {
        val payload = token.split(".")[1]
        val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))
        Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
    }.getOrNull()

    /**
     * Returns:
     *  - [SignUpResult.SignedIn] if the project has email confirmation off — a
     *    session is live and [sessionStatus] will flip to Authenticated.
     *  - [SignUpResult.ConfirmationRequired] if the project requires email
     *    confirmation; the user must click the link before signing in.
     *
     * Throws on actual errors (invalid email, weak password, network).
     */
    suspend fun signUp(emailAddr: String, password: CharArray): SignUpResult {
        try {
            auth.signUpWith(Email) {
                email = emailAddr
                this.password = password.concatToString()
            }
        } finally {
            password.fill(' ')
        }
        return if (auth.currentUserOrNull() != null) SignUpResult.SignedIn
        else SignUpResult.ConfirmationRequired
    }

    suspend fun signIn(emailAddr: String, password: CharArray) {
        try {
            auth.signInWith(Email) {
                email = emailAddr
                this.password = password.concatToString()
            }
        } finally {
            password.fill(' ')
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }
}
