package app.moneytracker.ui.loading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.moneytracker.ui.components.BrandBadge
import app.moneytracker.ui.theme.bgGradient

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(bgGradient()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandBadge(sizeDp = 76)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
