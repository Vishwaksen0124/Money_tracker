package app.moneytracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.moneytracker.cloud.AuthRepository
import app.moneytracker.security.SecureLogger
import app.moneytracker.ui.auth.AuthScreen
import app.moneytracker.ui.dashboard.DashboardScreen
import app.moneytracker.ui.loading.LoadingScreen
import app.moneytracker.ui.theme.MoneyTrackerTheme
import io.github.jan.supabase.auth.status.SessionStatus

private enum class Screen { Loading, Auth, Dashboard }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hardening §21.7 — FLAG_SECURE on the host Activity covers every Compose screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { MoneyTrackerTheme { Root() } }
    }

    @Composable
    private fun Root() {
        val session by AuthRepository.sessionStatus
            .collectAsStateWithLifecycle(initialValue = null)

        val screen = when {
            session == null || session is SessionStatus.Initializing -> Screen.Loading
            session is SessionStatus.Authenticated                  -> Screen.Dashboard
            else                                                    -> Screen.Auth
        }

        SecureLogger.d { "route screen=$screen session=${session?.let { it::class.simpleName }}" }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "root",
            ) { s ->
                when (s) {
                    Screen.Loading   -> LoadingScreen()
                    Screen.Auth      -> AuthScreen(onSignedIn = { /* session flow drives routing */ })
                    Screen.Dashboard -> DashboardScreen(application as MoneyApp)
                }
            }
        }
    }
}
