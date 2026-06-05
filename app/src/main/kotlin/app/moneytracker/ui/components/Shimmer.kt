package app.moneytracker.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A horizontally sweeping gradient used to indicate loading. One animation
 *  drives every skeleton on screen, so placeholders shimmer in sync. */
@Composable
fun shimmerBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    val stops = listOf(
        cs.surfaceVariant.copy(alpha = 0.55f),
        cs.onSurfaceVariant.copy(alpha = 0.18f),
        cs.surfaceVariant.copy(alpha = 0.55f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "x",
    )
    return Brush.linearGradient(
        colors = stops,
        start = Offset(x, 0f),
        end = Offset(x + 600f, 0f),
    )
}

/** A rounded shimmering block sized like a real card, for loading states. */
@Composable
fun SkeletonCard(height: Dp, modifier: Modifier = Modifier, corner: Dp = 20.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(shimmerBrush()),
    )
}
