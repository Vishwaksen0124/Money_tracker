package app.moneytracker.ui.dashboard

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.moneytracker.MoneyApp
import app.moneytracker.capture.NotifCapture
import app.moneytracker.capture.SmsCapture
import app.moneytracker.cloud.AuthRepository
import app.moneytracker.data.Category
import app.moneytracker.data.Reconciler
import app.moneytracker.data.TransactionCacheStore
import app.moneytracker.data.TransactionRepository
import app.moneytracker.data.TransactionRow
import app.moneytracker.ui.components.CategoryChips
import app.moneytracker.security.SecureLogger
import app.moneytracker.ui.analytics.AnalyticsScreen
import app.moneytracker.ui.formatRupees
import app.moneytracker.ui.components.SkeletonCard
import app.moneytracker.ui.entry.ManualEntrySheet
import app.moneytracker.ui.theme.bgGradient
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// 16-bit-safe request code (FragmentActivity rejects larger ones).
private const val REQ_SMS = 0x51

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(@Suppress("UNUSED_PARAMETER") app: MoneyApp) {
    val context = LocalContext.current
    // Seed from the in-process cache, falling back to the encrypted on-disk
    // cache so even a cold start paints instantly instead of waiting on the
    // (often cold-starting) Supabase fetch. Shimmer shows only when neither
    // cache has anything.
    val seed = remember { TransactionRepository.cached ?: TransactionCacheStore.load(context) }
    var rows by remember { mutableStateOf(seed ?: emptyList()) }
    var loaded by remember { mutableStateOf(seed != null) }
    var loadFailed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var entryOpen by remember { mutableStateOf(false) }
    var categorizing by remember { mutableStateOf<TransactionRow?>(null) }
    var showStats by remember { mutableStateOf(false) }
    val email = remember { AuthRepository.currentUserEmail() }
    val scope = rememberCoroutineScope()
    var smsEnabled by remember { mutableStateOf(SmsCapture.hasPermissions(context)) }
    var notifEnabled by remember { mutableStateOf(NotifCapture.accessGranted(context)) }

    // Re-check after returning from the system permission dialog / settings
    // (which background then resume the activity), so the cards disappear and
    // the backfill kicks in once granted.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        smsEnabled = SmsCapture.hasPermissions(context)
        notifEnabled = NotifCapture.accessGranted(context)
    }

    // Idempotent inbox sweep on every dashboard open (and right after the
    // permission grant): catches anything the live receiver missed.
    LaunchedEffect(smsEnabled) {
        if (smsEnabled) {
            val n = runCatching { SmsCapture.backfill(context) }
                .onFailure { SecureLogger.e(it) { "backfill failed" } }
                .getOrDefault(0)
            if (n > 0) refreshTick++
        }
    }

    LaunchedEffect(refreshTick) {
        val t0 = android.os.SystemClock.elapsedRealtime()
        SecureLogger.d { "fetch start (cacheSeed=${seed != null})" }
        runCatching { TransactionRepository.recent() }
            .onSuccess { fetched ->
                // Paint immediately — don't make the user wait on reconciliation.
                rows = fetched
                loadFailed = false
                loaded = true
                refreshing = false
                TransactionCacheStore.save(context, fetched)
                SecureLogger.d { "fetch done in ${android.os.SystemClock.elapsedRealtime() - t0}ms n=${fetched.size}" }
                // Reconcile in the background; refresh only if it added rows.
                val found = runCatching { Reconciler.run(fetched) }.getOrDefault(0)
                if (found > 0) {
                    val refreshed = runCatching { TransactionRepository.recent() }.getOrDefault(fetched)
                    rows = refreshed
                    TransactionCacheStore.save(context, refreshed)
                }
            }
            .onFailure {
                loadFailed = true
                loaded = true
                refreshing = false
                SecureLogger.e(it) { "recent fetch failed" }
            }
    }

    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(tz).date
    val weekStart = today.weekStart()
    val monthStart = LocalDate(today.year, today.month, 1)
    val agg = remember(rows, today) { aggregate(rows, tz, today, weekStart, monthStart) }

    if (showStats) {
        AnalyticsScreen(rows = rows, tz = tz, onBack = { showStats = false })
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { entryOpen = true },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add")
            }
        },
    ) { inner ->
        val contentPadding = PaddingValues(
            start = 24.dp, end = 24.dp,
            top = inner.calculateTopPadding() + 24.dp,
            bottom = inner.calculateBottomPadding() + 96.dp,
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; refreshTick++ },
            modifier = Modifier.fillMaxSize().background(bgGradient()),
        ) {
            if (!loaded && rows.isEmpty()) {
                DashboardSkeleton(contentPadding)
            } else {
                LazyColumn(
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { Header(
                        email,
                        onStats = { showStats = true },
                        onSignOut = {
                            TransactionRepository.clearCache()
                            TransactionCacheStore.clear(context)
                            scope.launch { AuthRepository.signOut() }
                        },
                    ) }
                    if (!smsEnabled) {
                        item {
                            EnableSmsCard {
                                (context as? Activity)?.let {
                                    ActivityCompat.requestPermissions(it, SmsCapture.PERMISSIONS, REQ_SMS)
                                }
                            }
                        }
                    }
                    if (!notifEnabled) {
                        item {
                            EnableNotifCard {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                    item { EnterAnimated(loaded, 0) { TodayHero(agg.todaySpent) } }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EnterAnimated(loaded, 40, Modifier.weight(1f)) {
                                StatTile("This week", agg.weekSpent)
                            }
                            EnterAnimated(loaded, 70, Modifier.weight(1f)) {
                                StatTile("This month", agg.monthSpent)
                            }
                        }
                    }
                    item { EnterAnimated(loaded, 100) { IncomeSummary(agg.monthIncome, agg.monthSpent) } }

                    if (agg.byCategory.isNotEmpty()) {
                        item {
                            EnterAnimated(loaded, 130) {
                                CategoryBreakdown(agg.byCategory, agg.monthSpent)
                            }
                        }
                    }

                    if (rows.isNotEmpty()) {
                        item {
                            Text(
                                "Recent",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            )
                        }
                        items(rows.take(20), key = { it.id ?: it.rawHash }) { row ->
                            TransactionRowItem(row, tz, onClick = { categorizing = row })
                        }
                    } else if (loaded) {
                        item { EmptyState(failed = loadFailed, onRetry = { refreshTick++ }) }
                    }
                }
            }
        }
    }

    if (entryOpen) {
        ManualEntrySheet(
            onDismiss = { entryOpen = false },
            onSaved = { newRow ->
                entryOpen = false
                // Prepend the row the insert already returned — no refetch needed.
                TransactionRepository.prepend(newRow)
                rows = listOf(newRow) + rows
            },
        )
    }

    categorizing?.let { row ->
        CategorizeSheet(
            current = Category.from(row.category),
            onDismiss = { categorizing = null },
            onPick = { picked ->
                val id = row.id
                categorizing = null
                if (id != null) scope.launch {
                    runCatching { TransactionRepository.setCategory(id, picked) }
                        .onSuccess { rows = TransactionRepository.cached ?: rows }
                        .onFailure { SecureLogger.e(it) { "setCategory failed" } }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorizeSheet(
    current: Category?,
    onDismiss: () -> Unit,
    onPick: (Category) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Categorize", style = MaterialTheme.typography.titleLarge)
            CategoryChips(selected = current, onSelect = onPick)
        }
    }
}

// ── components ──────────────────────────────────────────────────────────────

@Composable
private fun Header(email: String?, onStats: () -> Unit, onSignOut: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Welcome back",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                displayName(email),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        IconButton(onClick = onStats) {
            Icon(
                Icons.Outlined.BarChart,
                contentDescription = "Analytics",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSignOut) {
            Icon(
                Icons.AutoMirrored.Outlined.Logout,
                contentDescription = "Sign out",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Static rupee figure. (A count-up animation was removed — it re-formatted
 *  and re-laid-out the text every frame, which janked on mid-range devices.) */
@Composable
private fun AnimatedRupees(
    paise: Long,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    Text(formatRupees(paise), style = style, color = color)
}

@Composable
private fun TodayHero(paise: Long) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(cs.surface, cs.surfaceVariant))
            )
        ) {
            Column(Modifier.padding(28.dp)) {
                Text("Today's spend", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                AnimatedRupees(paise, MaterialTheme.typography.displayLarge, cs.primary)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, paise: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            AnimatedRupees(paise, MaterialTheme.typography.titleLarge)
        }
    }
}

/** Income this month plus net (income − spend), so the card answers the
 *  one question a glance should: am I ahead this month? */
@Composable
private fun IncomeSummary(incomePaise: Long, spentPaise: Long) {
    val cs = MaterialTheme.colorScheme
    val net = incomePaise - spentPaise
    val ahead = net >= 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            DirectionBadge(credit = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Income this month", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                AnimatedRupees(incomePaise, MaterialTheme.typography.titleLarge)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (ahead) "Net saved" else "Net spent", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                AnimatedRupees(
                    if (ahead) net else -net,
                    MaterialTheme.typography.titleMedium,
                    if (ahead) cs.primary else cs.error,
                )
            }
        }
    }
}

@Composable
private fun EnableSmsCard(onEnable: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Capture transactions automatically",
                style = MaterialTheme.typography.titleMedium,
                color = cs.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Bank debits and credits appear here the moment the SMS arrives. " +
                    "Only bank alerts are parsed — raw message text never leaves your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onEnable, shape = RoundedCornerShape(12.dp)) {
                Text("Enable SMS capture")
            }
        }
    }
}

@Composable
private fun EnableNotifCard(onEnable: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Catch UPI app payments", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Reads PhonePe / GPay / Paytm payment notifications, so payments " +
                    "you make from the phone are recorded even when the bank sends no SMS.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onEnable, shape = RoundedCornerShape(12.dp)) {
                Text("Enable notification access")
            }
        }
    }
}

@Composable
private fun DashboardSkeleton(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonCard(height = 36.dp, corner = 12.dp, modifier = Modifier.fillMaxWidth(0.5f))
        SkeletonCard(height = 132.dp, corner = 28.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonCard(height = 92.dp, modifier = Modifier.weight(1f))
            SkeletonCard(height = 92.dp, modifier = Modifier.weight(1f))
        }
        SkeletonCard(height = 84.dp)
        repeat(4) { SkeletonCard(height = 68.dp, corner = 18.dp) }
    }
}

@Composable
private fun TransactionRowItem(row: TransactionRow, tz: TimeZone, onClick: () -> Unit) {
    val credit = row.direction == "CREDIT"
    val category = Category.from(row.category)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(category = category, credit = credit)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category?.label ?: "Tap to categorize",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (category == null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sourceLabel(row) + " · " + formatTime(row.ts, tz),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = (if (credit) "+ " else "− ") + formatRupees(row.amountPaise),
                style = MaterialTheme.typography.titleMedium,
                color = if (credit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Category emoji in a tinted circle; falls back to the debit/credit arrow
 *  when uncategorized so the row never looks empty. */
@Composable
private fun CategoryBadge(category: Category?, credit: Boolean) {
    val cs = MaterialTheme.colorScheme
    val tint = category?.color ?: if (credit) cs.primary else cs.secondary
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (category != null) {
            Text(category.emoji, style = MaterialTheme.typography.titleMedium)
        } else {
            Icon(
                if (credit) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                contentDescription = null, tint = tint, modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DirectionBadge(credit: Boolean) {
    val cs = MaterialTheme.colorScheme
    val tint = if (credit) cs.primary else cs.secondary
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (credit) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun EmptyState(failed: Boolean, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (failed) "Couldn't load transactions" else "No transactions yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (failed) "Check your connection and retry."
                else "Tap + to add one manually. SMS capture lands next.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (failed) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun EnterAnimated(
    visible: Boolean,
    delayMs: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 220, delayMillis = delayMs)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 240, delayMillis = delayMs),
                    initialOffsetY = { it / 8 },
                ),
        modifier = modifier,
    ) { content() }
}

// ── pure helpers ────────────────────────────────────────────────────────────

private data class Aggregates(
    val todaySpent: Long,
    val weekSpent: Long,
    val monthSpent: Long,
    val monthIncome: Long,
    // This month's debit total per category, highest first. Null category
    // (uncategorized) is folded into Category.OTHER for the breakdown.
    val byCategory: List<Pair<Category, Long>>,
)

private fun aggregate(
    rows: List<TransactionRow>,
    tz: TimeZone,
    today: LocalDate,
    weekStart: LocalDate,
    monthStart: LocalDate,
): Aggregates {
    var t = 0L; var w = 0L; var m = 0L; var inc = 0L
    val cat = HashMap<Category, Long>()
    rows.forEach { r ->
        val instant = runCatching { Instant.parse(r.ts) }.getOrNull() ?: return@forEach
        val d = instant.toLocalDateTime(tz).date
        val amt = r.amountPaise
        when (r.direction) {
            "DEBIT" -> {
                if (d == today)                 t += amt
                if (d >= weekStart)     w += amt
                if (d >= monthStart) {
                    m += amt
                    val c = Category.from(r.category) ?: Category.OTHER
                    cat[c] = (cat[c] ?: 0L) + amt
                }
            }
            "CREDIT" -> {
                if (d >= monthStart)    inc += amt
            }
        }
    }
    return Aggregates(t, w, m, inc, cat.toList().sortedByDescending { it.second })
}

@Composable
private fun CategoryBreakdown(byCategory: List<Pair<Category, Long>>, monthSpent: Long) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Spending by category", style = MaterialTheme.typography.titleMedium)
            byCategory.take(6).forEach { (c, paise) ->
                val frac = if (monthSpent > 0) paise.toFloat() / monthSpent else 0f
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.emoji}  ${c.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatRupees(paise), style = MaterialTheme.typography.bodyMedium)
                    }
                    Box(
                        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(cs.surfaceVariant)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp)
                                .clip(RoundedCornerShape(3.dp)).background(c.color)
                        )
                    }
                }
            }
        }
    }
}

private fun LocalDate.weekStart(): LocalDate {
    // Monday-anchored. DayOfWeek's ordinal is 0=Mon..6=Sun on the JVM.
    val daysFromMonday = dayOfWeek.ordinal
    return LocalDate.fromEpochDays(toEpochDays() - daysFromMonday)
}

/** "SMS · UPI", "Manual", "Auto-detected · A/c 4524" — what + where from. */
private fun sourceLabel(row: TransactionRow): String {
    val source = when (row.source) {
        "SMS"   -> "SMS"
        "NOTIF" -> "App alert"
        "RECON" -> "Auto-detected"
        else    -> "Manual"
    }
    val channel = when (row.channel) {
        "OTHER" -> null
        "CARD"  -> "Card"
        else    -> row.channel                      // UPI / NEFT / IMPS / ATM / POS
    }
    val acct = row.accountLast4?.let { "A/c $it" }
    return listOfNotNull(source, channel ?: acct).joinToString(" · ")
}

/** Friendly name from an email: "rakesh.kumar@x.com" → "Rakesh Kumar". */
private fun displayName(email: String?): String {
    val local = email?.substringBefore("@")?.takeIf { it.isNotBlank() } ?: return "there"
    return local.split('.', '_', '-', '+')
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        .ifBlank { local }
}

private fun formatTime(iso: String, tz: TimeZone): String =
    runCatching {
        val dt = Instant.parse(iso).toLocalDateTime(tz)
        val mm = dt.minute.toString().padStart(2, '0')
        val hh12 = ((dt.hour + 11) % 12) + 1
        val ampm = if (dt.hour < 12) "AM" else "PM"
        "${dt.date} · $hh12:$mm $ampm"
    }.getOrElse { iso }
