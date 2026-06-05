package app.moneytracker.capture

import app.moneytracker.cloud.SupabaseProvider
import app.moneytracker.data.TransactionRow
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Cross-source dedup. The same UPI transaction can surface from both a bank
 * SMS and a payment-app notification; without this they'd become two rows and
 * double-count spend. The `unique(user_id, raw_hash)` constraint only catches
 * re-processing the *same* source text — this catches the *same money* seen
 * through different channels, matched on (amount, direction, ±3 min).
 */
object CaptureCommon {

    suspend fun nearMatch(amountPaise: Long, direction: String, tsMillis: Long): TransactionRow? {
        val t = Instant.fromEpochMilliseconds(tsMillis)
        return SupabaseProvider.client.from("transactions").select {
            filter {
                eq("amount_paise", amountPaise)
                eq("direction", direction)
                gte("ts", t.minus(3.minutes).toString())
                lte("ts", t.plus(3.minutes).toString())
            }
            limit(1)
        }.decodeList<TransactionRow>().firstOrNull()
    }

    suspend fun delete(id: String) {
        SupabaseProvider.client.from("transactions").delete { filter { eq("id", id) } }
    }
}
