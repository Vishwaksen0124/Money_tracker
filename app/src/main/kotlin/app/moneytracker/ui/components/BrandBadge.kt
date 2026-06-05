package app.moneytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand mark: circle with a stylised ₹. Visual anchor for the loading and
 *  auth screens. */
@Composable
fun BrandBadge(modifier: Modifier = Modifier, sizeDp: Int = 88) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(cs.primary, cs.primaryContainer))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "₹",
            color = cs.onPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (sizeDp / 2).sp,
        )
    }
}
