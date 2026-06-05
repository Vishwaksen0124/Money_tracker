package app.moneytracker.ui

import kotlin.math.abs

/** "₹ 1,23,456.78", Indian digit grouping, negatives prefixed with "−".
 *  Shared by every amount-bearing screen so formatting never drifts. */
fun formatRupees(paise: Long): String {
    val neg = paise < 0
    val a = abs(paise)
    val grouped = formatIndianGrouping(a / 100)
    val rem = a % 100
    val body = if (rem == 0L) "₹ $grouped" else "₹ $grouped.${rem.toString().padStart(2, '0')}"
    return if (neg) "−$body" else body
}

/** Indian grouping: 1,23,456 not 123,456. */
private fun formatIndianGrouping(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val tail = s.takeLast(3)
    val head = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "$head,$tail"
}
