package app.moneytracker.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.moneytracker.cloud.AuthRepository
import app.moneytracker.security.SecureLogger
import app.moneytracker.ui.components.BrandBadge
import app.moneytracker.ui.components.BusyButton
import app.moneytracker.ui.theme.bgGradient
import kotlinx.coroutines.launch

private enum class Mode { SIGN_IN, SIGN_UP }

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(Mode.SIGN_IN) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(bgGradient()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp)
                .widthIn(max = 480.dp),
        ) {
            BrandBadge(sizeDp = 80)
            Spacer(Modifier.height(20.dp))
            Text(
                "Money Tracker",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "subhead",
            ) { m ->
                Text(
                    if (m == Mode.SIGN_IN) "Welcome back" else "Create your account",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(28.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("Email") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            autoCorrectEnabled = false,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (min 8 chars)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(visible = error != null || info != null) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                (error ?: info).orEmpty(),
                                color = if (error != null) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    BusyButton(
                        label = if (mode == Mode.SIGN_IN) "Sign in" else "Create account",
                        busy = busy,
                        enabled = email.isNotBlank() && password.length >= 8,
                        onClick = {
                            error = null
                            info = null
                            busy = true
                            val pwChars = password.toCharArray()
                            password = ""
                            val emailSnap = email
                            val modeSnap = mode
                            scope.launch {
                                try {
                                    if (modeSnap == Mode.SIGN_IN) {
                                        AuthRepository.signIn(emailSnap, pwChars)
                                        onSignedIn()
                                    } else {
                                        when (AuthRepository.signUp(emailSnap, pwChars)) {
                                            AuthRepository.SignUpResult.SignedIn ->
                                                onSignedIn()
                                            AuthRepository.SignUpResult.ConfirmationRequired -> {
                                                info = "Check your inbox — confirm the email to continue."
                                                mode = Mode.SIGN_IN
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {
                                    SecureLogger.e(t) { "auth failed" }
                                    error = friendlyError(t)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            TextButton(
                enabled = !busy,
                onClick = { mode = if (mode == Mode.SIGN_IN) Mode.SIGN_UP else Mode.SIGN_IN },
            ) {
                Text(
                    if (mode == Mode.SIGN_IN) "Create an account instead"
                    else "I already have an account"
                )
            }
        }
    }
}

private fun friendlyError(t: Throwable): String {
    val m = t.message.orEmpty()
    return when {
        "Invalid login credentials"   in m -> "Email or password is incorrect."
        "already registered"          in m -> "An account with this email already exists. Sign in instead."
        "Password should be at least" in m -> "Password is too short."
        "rate limit"                  in m -> "Too many attempts. Try again in a minute."
        "Email not confirmed"         in m -> "Confirm your email first — check your inbox for the link."
        "Email signups are disabled"  in m -> "Sign-ups are disabled in this Supabase project."
        else                               -> "Authentication failed. Check your connection and try again."
    }
}
