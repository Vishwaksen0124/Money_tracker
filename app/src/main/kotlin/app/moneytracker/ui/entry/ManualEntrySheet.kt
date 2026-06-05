package app.moneytracker.ui.entry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.moneytracker.data.Category
import app.moneytracker.data.TransactionRepository
import app.moneytracker.data.TransactionRow
import app.moneytracker.parser.Util
import app.moneytracker.ui.components.CategoryChips
import app.moneytracker.security.SecureLogger
import app.moneytracker.ui.components.BusyButton
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntrySheet(
    onDismiss: () -> Unit,
    onSaved: (TransactionRow) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(TransactionRepository.Direction.DEBIT) }
    var category by remember { mutableStateOf<Category?>(null) }
    var dateMillis by remember { mutableStateOf<Long?>(null) }   // null = now
    var datePickerOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("New transaction", style = MaterialTheme.typography.titleLarge)

            // Hero amount input — large, centered, gradient surface
            Card(
                colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Amount (₹)",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = amount,
                        onValueChange = { v ->
                            if (v.matches(AMOUNT_PATTERN)) amount = v
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.displayMedium.copy(
                            color = cs.primary,
                            textAlign = TextAlign.Center,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(cs.primary),
                        decorationBox = { inner ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (amount.isEmpty()) {
                                    Text(
                                        "0",
                                        style = MaterialTheme.typography.displayMedium,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
            }

            // Direction toggle
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = direction == TransactionRepository.Direction.DEBIT,
                    onClick = { direction = TransactionRepository.Direction.DEBIT },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Spent") }
                SegmentedButton(
                    selected = direction == TransactionRepository.Direction.CREDIT,
                    onClick = { direction = TransactionRepository.Direction.CREDIT },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Received") }
            }

            Text("Category", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            CategoryChips(selected = category, onSelect = { category = it })

            // When it happened — defaults to now; tap to backdate (e.g. a debit
            // you discovered later with no SMS).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Date",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { datePickerOpen = true }) {
                    Text(dateMillis?.let(::formatDate) ?: "Today")
                }
            }

            AnimatedVisibility(visible = error != null) {
                Text(error.orEmpty(), color = cs.error, style = MaterialTheme.typography.bodySmall)
            }

            BusyButton(
                label = if (direction == TransactionRepository.Direction.DEBIT) "Save spend" else "Save income",
                busy = busy,
                busyLabel = "Saving…",
                enabled = amount.isNotBlank(),
                onClick = {
                    val paise = Util.paise(amount)
                    if (paise == null || paise <= 0) {
                        error = "Enter a valid amount"
                        return@BusyButton
                    }
                    error = null
                    busy = true
                    scope.launch {
                        try {
                            onSaved(TransactionRepository.addManual(paise, direction, dateMillis, category))
                        } catch (t: Throwable) {
                            SecureLogger.e(t) { "addManual failed" }
                            error = "Couldn't save — check your connection and try again."
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }

    if (datePickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateMillis ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    dateMillis = pickerState.selectedDateMillis
                    datePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Picker millis are UTC-midnight of the chosen day; format in UTC so the
 *  label always shows exactly the day that was tapped. */
private fun formatDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date.toString()

private val AMOUNT_PATTERN = Regex("""^\d{0,8}(?:\.\d{0,2})?$""")
