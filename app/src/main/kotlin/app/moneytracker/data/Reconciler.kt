package app.moneytracker.data

import app.moneytracker.cloud.AuthRepository
import app.moneytracker.parser.Util
import app.moneytracker.security.SecureLogger
import kotlinx.datetime.Instant

/**
 * Catches transactions that left no trace on the phone (bank fees, mandates,
 * NACH debits — no SMS, no notification) using the one signal that never
 * lies: the balance.
 *
 * Every SMS row that carried "Avl Bal" is a checkpoint. Between two
 * consecutive checkpoints of the same account, if the balance moved by more
 * than the captured rows explain, the difference IS a transaction — so we
 * insert it, as `source = RECON`. The raw_hash is derived from the two
 * checkpoint hashes + gap, making re-runs idempotent, and previously
 * inserted RECON rows count toward the explained amount so a gap is only
 * ever filled once.
 *
 * Manual rows carry no account; they are attributed to the checkpoint's
 * account, which is correct for single-account use (PLAN.md v0.9 does full
 * account wiring).
 */
object Reconciler {

    /** Scans [rows] (any order) and inserts missing gap transactions.
     *  Returns how many were inserted. */
    suspend fun run(rows: List<TransactionRow>): Int {
        val userId = AuthRepository.currentUserId() ?: return 0

        // Parse timestamps once; drop rows we can't place in time.
        val timed = rows.mapNotNull { r ->
            runCatching { Instant.parse(r.ts) }.getOrNull()?.let { it to r }
        }.sortedBy { it.first }

        var inserted = 0
        val checkpoints = timed.filter { (_, r) -> r.balanceAfterPaise != null && r.accountLast4 != null }
        for ((acct, cps) in checkpoints.groupBy { it.second.accountLast4 }) {
            for (i in 0 until cps.size - 1) {
                val (ta, a) = cps[i]
                val (tb, b) = cps[i + 1]
                if (tb <= ta) continue
                val explained = timed
                    .filter { (t, r) ->
                        t > ta && t <= tb && (r.accountLast4 == acct || r.accountLast4 == null)
                    }
                    .sumOf { (_, r) -> if (r.direction == "CREDIT") r.amountPaise else -r.amountPaise }
                val gap = b.balanceAfterPaise!! - (a.balanceAfterPaise!! + explained)
                if (gap == 0L) continue

                val ok = runCatching {
                    insertIgnoringDuplicate("transactions", TransactionRow(
                        userId = userId,
                        ts = tb.toString(),
                        amountPaise = if (gap > 0) gap else -gap,
                        direction = if (gap > 0) "CREDIT" else "DEBIT",
                        channel = "OTHER",
                        source = "RECON",
                        rawHash = Util.rawHash("RECON", a.rawHash, "${b.rawHash}|$gap"),
                        accountLast4 = acct,
                    ))
                }.onFailure { SecureLogger.e(it) { "recon insert failed" } }
                    .getOrDefault(false)
                if (ok) inserted++
            }
        }
        SecureLogger.d { "recon inserted=$inserted" }
        return inserted
    }
}
