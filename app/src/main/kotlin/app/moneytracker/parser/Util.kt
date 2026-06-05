package app.moneytracker.parser

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parsing primitives shared across bank packs. Kept small and pure; every
 * bank uses these so we don't duplicate money / date / hash logic.
 */
internal object Util {

    // One pattern that handles bare digits, decimals, and both Western
    // ("1,234,567.89") and Indian ("1,23,456.78") thousand grouping.
    private val AMOUNT_RX = Regex("""[0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?""")

    /** "1,234.56" → 123456; "500" → 50000; "500.5" → 50050. Null on malformed. */
    fun paise(amount: String): Long? {
        val s = amount.replace(",", "").trim()
        if (s.isEmpty()) return null
        val dot = s.indexOf('.')
        return if (dot < 0) {
            s.toLongOrNull()?.times(100L)
        } else {
            val r = s.substring(0, dot).toLongOrNull() ?: return null
            val pTxt = s.substring(dot + 1).padEnd(2, '0').take(2)
            val p = pTxt.toLongOrNull() ?: return null
            r * 100L + p
        }
    }

    fun firstAmount(text: String): Long? =
        AMOUNT_RX.find(text)?.value?.let(::paise)

    fun amountAfter(text: String, anchor: Regex): Long? {
        val m = anchor.find(text) ?: return null
        val tail = text.substring(m.range.last + 1)
        return firstAmount(tail)
    }

    /** SHA-256 hex of bank-code + sender + body. Stable across reprocessing. */
    fun rawHash(bankCode: String, sender: String, body: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$bankCode|$sender|$body".toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xff
                append(HEX[v ushr 4]); append(HEX[v and 0x0f])
            }
        }
    }

    // Every numeric separator/year/time combination DATE_RX can capture,
    // plus the month-name forms ("15-Apr-26", "15Apr26").
    private val DATE_FORMATS = buildList {
        for (date in listOf("dd-MM-yy", "dd-MM-yyyy", "dd/MM/yy", "dd/MM/yyyy"))
            for (time in listOf(" HH:mm:ss", " HH:mm", ""))
                add(DateTimeFormatter.ofPattern(date + time, Locale.ROOT))
        add(DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH))
        add(DateTimeFormatter.ofPattern("ddMMMyy", Locale.ENGLISH))
    }

    fun tryParseDate(text: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        for (fmt in DATE_FORMATS) {
            try {
                val ldt = runCatching { LocalDateTime.parse(text.trim(), fmt) }.getOrNull()
                    ?: java.time.LocalDate.parse(text.trim(), fmt).atStartOfDay()
                return ldt.atZone(zone).toInstant().toEpochMilli()
            } catch (_: Throwable) {
                continue
            }
        }
        return null
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
