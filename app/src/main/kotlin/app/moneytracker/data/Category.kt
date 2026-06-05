package app.moneytracker.data

import androidx.compose.ui.graphics.Color

/**
 * Fixed spend/income categories. Stored as the enum `name` in the
 * transactions.category column; UI reads label/emoji/color from here so the
 * set stays consistent everywhere and needs no per-user categories table.
 */
enum class Category(
    val label: String,
    val emoji: String,
    val color: Color,
    val income: Boolean = false,
) {
    FOOD("Food & Dining", "🍔", Color(0xFFFF8A65)),
    GROCERIES("Groceries", "🛒", Color(0xFF4DB6AC)),
    SHOPPING("Shopping", "🛍️", Color(0xFFBA68C8)),
    TRANSPORT("Transport", "🚗", Color(0xFF64B5F6)),
    BILLS("Bills & Utilities", "💡", Color(0xFFFFD54F)),
    ENTERTAINMENT("Entertainment", "🎬", Color(0xFFF06292)),
    HEALTH("Health", "💊", Color(0xFF81C784)),
    CASH("Cash / ATM", "💵", Color(0xFF90A4AE)),
    TRANSFER("Transfer", "🔁", Color(0xFF7986CB)),
    INCOME("Income", "💰", Color(0xFF22D3A5), income = true),
    OTHER("Other", "📦", Color(0xFFA1887F));

    companion object {
        fun from(name: String?): Category? = name?.let { n -> entries.firstOrNull { it.name == n } }

        /** Best-effort auto-tag from the signals captured without user input. */
        fun auto(direction: String, channel: String): Category? = when {
            direction == "CREDIT" -> INCOME
            channel == "ATM"      -> CASH
            else                  -> null
        }
    }
}
