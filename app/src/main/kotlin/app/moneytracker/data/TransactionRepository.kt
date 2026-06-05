package app.moneytracker.data

import app.moneytracker.cloud.AuthRepository
import app.moneytracker.cloud.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Reads and writes `transactions` directly against Supabase. RLS scopes
 * everything to the current user automatically — we still set `user_id`
 * explicitly so the `with check` policy is satisfied.
 *
 * No local persistent cache yet — every screen fetches fresh; an in-process
 * cache keeps re-entry instant. v0.4 introduces the Room mirror +
 * offline-first sync queue from PLAN.md §5.
 */
object TransactionRepository {

    /** Last successful fetch, kept in-process so re-entering the dashboard
     *  paints instantly while a fresh fetch runs in the background. Cleared
     *  on sign-out so the next user never sees the previous one's rows. */
    @Volatile
    var cached: List<TransactionRow>? = null
        private set

    suspend fun recent(limit: Long = 200): List<TransactionRow> = withContext(Dispatchers.IO) {
        SupabaseProvider.client
            .from("transactions")
            .select {
                order(column = "ts", order = Order.DESCENDING)
                limit(limit)
            }
            .decodeList<TransactionRow>()
            // Cache only non-empty results: the SDK's session sometimes fails to
            // attach on a given launch and the query returns 0 rows even though
            // data exists. Keeping the last non-empty snapshot means a glitched
            // fetch never wipes the user's view.
            .also { if (it.isNotEmpty()) cached = it }
    }

    /** Optimistically reflect a just-inserted row in the cache. */
    fun prepend(row: TransactionRow) {
        cached = listOf(row) + (cached ?: emptyList())
    }

    fun clearCache() {
        cached = null
    }

    suspend fun addManual(
        amountPaise: Long,
        direction: Direction,
        tsMillis: Long? = null,   // null = now; set for backdated entries
        category: Category? = null,
    ): TransactionRow {
        require(amountPaise > 0) { "Amount must be positive" }
        val userId = requireNotNull(AuthRepository.currentUserId()) {
            "No signed-in user"
        }
        val row = TransactionRow(
            userId = userId,
            ts = (tsMillis?.let(Instant::fromEpochMilliseconds) ?: Clock.System.now()).toString(),
            amountPaise = amountPaise,
            direction = direction.name,
            channel = "OTHER",
            source = "MANUAL",
            rawHash = "manual-${UUID.randomUUID()}",
            category = (category ?: Category.auto(direction.name, "OTHER"))?.name,
        )
        return SupabaseProvider.client
            .from("transactions")
            .insert(row) { select() }
            .decodeSingle()
    }

    /** Set/clear a transaction's category and reflect it in the cache. */
    suspend fun setCategory(id: String, category: Category?) {
        SupabaseProvider.client.from("transactions").update(
            { set("category", category?.name) }
        ) { filter { eq("id", id) } }
        cached = cached?.map { if (it.id == id) it.copy(category = category?.name) else it }
    }

    enum class Direction { DEBIT, CREDIT }
}

/** Insert tolerating the unique(user_id, raw_hash) constraint: re-processing
 *  the same source row is a silent no-op. Returns true only on a new insert. */
internal suspend inline fun <reified T : Any> insertIgnoringDuplicate(table: String, row: T): Boolean =
    try {
        SupabaseProvider.client.from(table).insert(row)
        true
    } catch (t: Throwable) {
        val benign = t.message.orEmpty().let { "duplicate key" in it || "23505" in it }
        if (!benign) throw t
        false
    }
