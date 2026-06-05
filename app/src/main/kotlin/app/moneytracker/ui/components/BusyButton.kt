package app.moneytracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Primary CTA that swaps its label for a spinner while [busy]. Used by the
 *  auth form and the manual-entry sheet so the pattern lives in one place. */
@Composable
fun BusyButton(
    label: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busyLabel: String = "Working…",
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().height(54.dp),
    ) {
        AnimatedContent(
            targetState = busy,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "cta",
        ) { b ->
            if (b) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(busyLabel)
                }
            } else {
                Text(label)
            }
        }
    }
}
