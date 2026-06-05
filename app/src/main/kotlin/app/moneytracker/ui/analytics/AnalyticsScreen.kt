package app.moneytracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.moneytracker.data.Category
import app.moneytracker.data.TransactionRow
import app.moneytracker.ui.formatRupees
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(rows: List<TransactionRow>, tz: TimeZone, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = remember { kotlinx.datetime.Clock.System.now().toLocalDateTime(tz).date }
    var period by remember { mutableStateOf(PeriodFilter.MONTH) }
    var catFilter by remember { mutableStateOf<Category?>(null) }
    val data = remember(rows, period) { analytics(rows, tz, today, period) }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PeriodChips(period) { period = it } }
            item { PeriodStatsCard(data, period.label) }
            item { DailyChartCard(data.daily, catFilter) { catFilter = it } }
            item { TrendCard(data.months) }
            if (data.categories.isNotEmpty()) item {
                CategoriesCard(data.categories, data.rangeSpent, data.txnCount, data.avgTxn)
            }
            if (data.byChannel.isNotEmpty()) item { ChannelCard(data.byChannel, data.rangeSpent) }
            if (data.topExpenses.isNotEmpty()) item { TopExpensesCard(data.topExpenses) }
        }
    }
}

@Composable
private fun PeriodChips(selected: PeriodFilter, onSelect: (PeriodFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PeriodFilter.entries.forEach { p ->
            androidx.compose.material3.FilterChip(
                selected = selected == p,
                onClick = { onSelect(p) },
                label = { Text(p.label) },
            )
        }
    }
}

// ── cards ─────────────────────────────────────────────────────────────────

@Composable
private fun TrendCard(months: List<MonthStat>) {
    val cs = MaterialTheme.colorScheme
    val maxVal = max(1L, months.maxOf { max(it.spent, it.income) })
    SectionCard("Last 6 months") {
        Row(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            months.forEach { m ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Bar(m.spent.toFloat() / maxVal, cs.secondary)
                        Bar(m.income.toFloat() / maxVal, cs.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(m.label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(cs.secondary, "Spent")
            LegendDot(cs.primary, "Income")
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .width(11.dp)
            .height((fraction.coerceIn(0f, 1f) * 120).dp.coerceAtLeast(2.dp))
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(color),
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PeriodStatsCard(d: AnalyticsData, label: String) {
    SectionCard(label) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Spent", formatRupees(d.rangeSpent), Modifier.weight(1f))
            Stat("Income", formatRupees(d.rangeIncome), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Avg / day", formatRupees(d.avgPerDay), Modifier.weight(1f))
            Stat(if (d.projectedSpend > 0) "Projected" else "Transactions",
                if (d.projectedSpend > 0) formatRupees(d.projectedSpend) else d.txnCount.toString(),
                Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        val net = d.rangeIncome - d.rangeSpent
        val cs = MaterialTheme.colorScheme
        Stat(
            if (net >= 0) "Net saved" else "Net spent",
            formatRupees(net),
            valueColor = if (net >= 0) cs.primary else cs.error,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyChartCard(daily: List<DayData>, catFilter: Category?, onCat: (Category?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Day totals after applying the category filter; scale bars to the busiest day.
    fun dayTotal(d: DayData) = if (catFilter == null) d.total
        else d.segments.firstOrNull { it.first == catFilter }?.second ?: 0L
    val maxV = max(1L, daily.maxOfOrNull { dayTotal(it) } ?: 1L)

    SectionCard("Daily spending") {
        // Category filter — All + each category present in the range.
        val present = daily.flatMap { it.segments.map { s -> s.first } }.distinct()
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            androidx.compose.material3.FilterChip(
                selected = catFilter == null, onClick = { onCat(null) }, label = { Text("All") },
            )
            present.forEach { c ->
                androidx.compose.material3.FilterChip(
                    selected = catFilter == c,
                    onClick = { onCat(if (catFilter == c) null else c) },
                    label = { Text("${c.emoji} ${c.label}") },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // LazyRow so only on-screen bars compose — a 6-month view is ~180 days.
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(daily) { day ->
                val segs = if (catFilter == null) day.segments
                           else day.segments.filter { it.first == catFilter }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    // Stacked segments, tallest category on the bottom.
                    Column(verticalArrangement = Arrangement.Bottom) {
                        segs.forEach { (c, paise) ->
                            val frac = (paise.toFloat() / maxV).coerceIn(0f, 1f)
                            Box(
                                Modifier.width(14.dp)
                                    .height((frac * 130).dp.coerceAtLeast(if (paise > 0) 2.dp else 0.dp))
                                    .background(c.color),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(day.label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** Donut: each category's arc is proportional to its share of month spend.
 *  Sweeps in on entry. Total sits in the hole. */
@Composable
private fun CategoryDonut(categories: List<Pair<Category, Long>>, total: Long, txnCount: Int, avgTxn: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(150.dp)) {
                val stroke = 26.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                var start = -90f
                categories.forEach { (c, paise) ->
                    val sweep = if (total > 0) 360f * (paise.toFloat() / total) else 0f
                    drawArc(
                        color = c.color,
                        startAngle = start,
                        sweepAngle = (sweep - 2f).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatRupees(total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat("Transactions", txnCount.toString())
            Stat("Avg / transaction", formatRupees(avgTxn))
        }
    }
}

@Composable
private fun CategoriesCard(categories: List<Pair<Category, Long>>, total: Long, txnCount: Int, avgTxn: Long) {
    val cs = MaterialTheme.colorScheme
    SectionCard("Spending by category") {
        CategoryDonut(categories, total, txnCount, avgTxn)
        Spacer(Modifier.height(16.dp))
        categories.forEach { (c, paise) ->
            val frac = if (total > 0) paise.toFloat() / total else 0f
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.emoji}  ${c.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(formatRupees(paise), style = MaterialTheme.typography.bodyMedium)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(cs.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.color))
                }
            }
        }
    }
}

@Composable
private fun TopExpensesCard(items: List<Pair<TransactionRow, Category?>>) {
    SectionCard("Biggest expenses this month") {
        items.forEach { (row, cat) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text((cat?.emoji ?: "📦") + "  " + (cat?.label ?: "Other"),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(formatRupees(row.amountPaise), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ChannelCard(byChannel: List<Pair<String, Long>>, total: Long) {
    val cs = MaterialTheme.colorScheme
    SectionCard("By payment method") {
        byChannel.forEach { (ch, paise) ->
            val frac = if (total > 0) paise.toFloat() / total else 0f
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(channelLabel(ch), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(formatRupees(paise), style = MaterialTheme.typography.bodyMedium)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(cs.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(cs.secondary))
                }
            }
        }
    }
}

// ── small building blocks ───────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

// ── pure computation ──────────────────────────────────────────────────────

enum class PeriodFilter(val label: String, val days: Int?) {
    WEEK("Week", 7), MONTH("Month", null), M3("3 months", 90), M6("6 months", 180)
}

private data class MonthStat(val label: String, val spent: Long, val income: Long)

/** One bar in the daily chart: spend split by category for that day. */
private data class DayData(val label: String, val segments: List<Pair<Category, Long>>, val total: Long)

private data class AnalyticsData(
    val months: List<MonthStat>,
    val rangeSpent: Long,
    val rangeIncome: Long,
    val avgPerDay: Long,
    val projectedSpend: Long,
    val categories: List<Pair<Category, Long>>,
    val topExpenses: List<Pair<TransactionRow, Category?>>,
    val byChannel: List<Pair<String, Long>>,
    val txnCount: Int,
    val avgTxn: Long,
    val daily: List<DayData>,
)

private val MONTH_ABBR = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val WEEKDAY_ABBR = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun analytics(rows: List<TransactionRow>, tz: TimeZone, today: LocalDate, period: PeriodFilter): AnalyticsData {
    // ── 6-month trend is independent of the period filter ──
    val curKey = today.year * 12 + (today.monthNumber - 1)
    val mSpent = HashMap<Int, Long>(); val mIncome = HashMap<Int, Long>()

    // ── range = the selected period ──
    val rangeStart = when (period.days) {
        null -> LocalDate(today.year, today.monthNumber, 1)
        else -> LocalDate.fromEpochDays(today.toEpochDays() - (period.days - 1))
    }
    val cat = HashMap<Category, Long>()
    val channel = HashMap<String, Long>()
    val perDayCat = HashMap<LocalDate, HashMap<Category, Long>>()
    val rangeRows = ArrayList<TransactionRow>()
    var rangeSpent = 0L; var rangeIncome = 0L

    rows.forEach { r ->
        val d = runCatching { Instant.parse(r.ts) }.getOrNull()?.toLocalDateTime(tz)?.date ?: return@forEach
        val key = d.year * 12 + (d.monthNumber - 1)
        if (r.direction == "CREDIT") mIncome[key] = (mIncome[key] ?: 0L) + r.amountPaise
        else mSpent[key] = (mSpent[key] ?: 0L) + r.amountPaise

        if (d in rangeStart..today) {
            if (r.direction == "CREDIT") rangeIncome += r.amountPaise
            else {
                rangeSpent += r.amountPaise
                val c = Category.from(r.category) ?: Category.OTHER
                cat[c] = (cat[c] ?: 0L) + r.amountPaise
                channel[r.channel] = (channel[r.channel] ?: 0L) + r.amountPaise
                perDayCat.getOrPut(d) { HashMap() }.merge(c, r.amountPaise, Long::plus)
                rangeRows += r
            }
        }
    }

    val months = (curKey - 5..curKey).map { k -> MonthStat(MONTH_ABBR[k % 12], mSpent[k] ?: 0L, mIncome[k] ?: 0L) }

    // Daily series across every day in the range (gaps shown as empty bars).
    val daily = (rangeStart.toEpochDays()..today.toEpochDays()).map { ed ->
        val d = LocalDate.fromEpochDays(ed)
        val segs = perDayCat[d]?.toList()?.sortedByDescending { it.second } ?: emptyList()
        val label = if (period == PeriodFilter.WEEK) WEEKDAY_ABBR[d.dayOfWeek.ordinal] else d.dayOfMonth.toString()
        DayData(label, segs, segs.sumOf { it.second })
    }

    val daysElapsed = (today.toEpochDays() - rangeStart.toEpochDays() + 1).coerceAtLeast(1)
    val avgPerDay = rangeSpent / daysElapsed
    val projected = if (period == PeriodFilter.MONTH) {
        val next = if (today.monthNumber == 12) LocalDate(today.year + 1, 1, 1) else LocalDate(today.year, today.monthNumber + 1, 1)
        avgPerDay * (next.toEpochDays() - rangeStart.toEpochDays())
    } else 0L
    val txnCount = rangeRows.size

    return AnalyticsData(
        months = months,
        rangeSpent = rangeSpent,
        rangeIncome = rangeIncome,
        avgPerDay = avgPerDay,
        projectedSpend = projected,
        categories = cat.toList().sortedByDescending { it.second },
        topExpenses = rangeRows.sortedByDescending { it.amountPaise }.take(5)
            .map { it to Category.from(it.category) },
        byChannel = channel.toList().sortedByDescending { it.second },
        txnCount = txnCount,
        avgTxn = if (txnCount > 0) rangeSpent / txnCount else 0L,
        daily = daily,
    )
}

private fun channelLabel(c: String): String = when (c) {
    "UPI" -> "UPI"
    "CARD" -> "Card"
    "NEFT" -> "NEFT"
    "IMPS" -> "IMPS"
    "ATM" -> "ATM"
    "POS" -> "POS"
    else -> "Other"
}
