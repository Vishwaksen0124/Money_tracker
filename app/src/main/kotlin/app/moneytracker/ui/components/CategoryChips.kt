package app.moneytracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.moneytracker.data.Category

/** Wrapping grid of selectable category chips. Shared by the manual-entry
 *  sheet and the tap-to-categorize sheet so the set looks identical in both. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(
    selected: Category?,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
    incomeOnly: Boolean? = null,   // null = all; true/false filters by income
) {
    val items = Category.entries.filter { incomeOnly == null || it.income == incomeOnly }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { c ->
            FilterChip(
                selected = selected == c,
                onClick = { onSelect(c) },
                label = { Text("${c.emoji}  ${c.label}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = c.color.copy(alpha = 0.22f),
                    selectedLabelColor = Color.Unspecified,
                ),
            )
        }
    }
}
