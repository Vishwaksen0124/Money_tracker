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

    fun currentUserId(): String? = auth.currentUserOrNull()?.id

    fun currentUserEmail(): String? = auth.currentUserOrNull()?.email

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
