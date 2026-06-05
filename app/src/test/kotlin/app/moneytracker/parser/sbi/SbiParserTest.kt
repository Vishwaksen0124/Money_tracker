package app.moneytracker.parser.sbi

import app.moneytracker.parser.ParsedTxn
import app.moneytracker.parser.Util
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are synthetic SBI-style messages assembled from the publicly
 * documented format templates. No real user SMS is committed.
 */
class SbiParserTest {

    private val parser = SbiParser()
    private val now = 1_700_000_000_000L

    @Test fun `matches SBI senders with and without carrier prefix`() {
        listOf("SBI", "SBIINB", "SBIPSG", "SBIUPI", "VK-SBIINB", "JD-SBIPSG", "AD-SBIUPI", "BX-SBIPSG-S")
            .forEach { assertTrue("should match $it", parser.matches(it)) }
    }

    @Test fun `does not match other banks or unrelated text`() {
        listOf("HDFCBK", "VK-HDFCBK", "AXISBK", "ICICIB", "RANDOMSENDER", "MERCHSBIYO")
            .forEach { assertFalse("should not match $it", parser.matches(it)) }
    }

    @Test fun `UPI debit modern format`() {
        val body = "Dear UPI user A/C XX1234 debited by 500.0 on date 15Apr26 trf to MERCHANT NAME Refno 410999999999. -SBI"
        val r = parser.parse("VK-SBIINB", body, now)!!
        assertEquals(ParsedTxn.Direction.DEBIT, r.direction)
        assertEquals(50000L, r.amountPaise)
        assertEquals(ParsedTxn.Channel.UPI, r.channel)
        assertEquals("1234", r.accountLast4)
        assertEquals("MERCHANT NAME", r.counterparty)
        assertNull(r.balanceAfterPaise)
    }

    @Test fun `UPI credit modern format`() {
        val body = "Dear UPI user A/C XX1234 credited by 5000.0 on date 01Apr26 trf from PERSON NAME Refno 410999999999. -SBI"
        val r = parser.parse("VK-SBIINB", body, now)!!
        assertEquals(ParsedTxn.Direction.CREDIT, r.direction)
        assertEquals(500000L, r.amountPaise)
        assertEquals(ParsedTxn.Channel.UPI, r.channel)
        assertEquals("1234", r.accountLast4)
        assertEquals("PERSON NAME", r.counterparty)
    }

    @Test fun `generic debit with balance and comma thousands`() {
        val body = "Your A/c XX1234 is debited by Rs.1,234.56 on 15-04-26 by transfer to MERCHANT. Avl Bal Rs.12,345.67"
        val r = parser.parse("SBIINB", body, now)!!
        assertEquals(ParsedTxn.Direction.DEBIT, r.direction)
        assertEquals(123456L, r.amountPaise)
        assertEquals("1234", r.accountLast4)
        assertEquals(1234567L, r.balanceAfterPaise)
        assertEquals("MERCHANT", r.counterparty)
    }

    @Test fun `NEFT credit with sender`() {
        val body = "Dear Customer, Rs.5000.00 credited to A/c No. XX1234 on 01-04-26 by NEFT from EMPLOYER_LTD. Avl Bal: Rs.18345.67."
        val r = parser.parse("SBIINB", body, now)!!
        assertEquals(ParsedTxn.Direction.CREDIT, r.direction)
        assertEquals(500000L, r.amountPaise)
        assertEquals(ParsedTxn.Channel.NEFT, r.channel)
        assertEquals("1234", r.accountLast4)
        assertEquals(1834567L, r.balanceAfterPaise)
        assertEquals("EMPLOYER_LTD", r.counterparty)
    }

    @Test fun `ATM withdrawal`() {
        val body = "Dear Customer, Rs.2000.00 withdrawn from A/c XX1234 at ATM/POS on 15-04-26. Avl Bal: Rs.10000.00"
        val r = parser.parse("SBIINB", body, now)!!
        assertEquals(ParsedTxn.Direction.DEBIT, r.direction)
        assertEquals(200000L, r.amountPaise)
        assertEquals(ParsedTxn.Channel.ATM, r.channel)
        assertEquals("1234", r.accountLast4)
        assertEquals(1000000L, r.balanceAfterPaise)
    }

    @Test fun `card POS swipe`() {
        val body = "Rs.500.00 spent on your SBI Debit Card XX1234 at AMAZON on 15-04-26. Avl Bal Rs.12345.67"
        val r = parser.parse("SBIINB", body, now)!!
        assertEquals(ParsedTxn.Direction.DEBIT, r.direction)
        assertEquals(50000L, r.amountPaise)
        assertEquals(ParsedTxn.Channel.CARD, r.channel)
        assertEquals("1234", r.accountLast4)
        assertEquals("AMAZON", r.counterparty)
    }

    @Test fun `VPA UPI debit captures the address`() {
        val body = "Rs.500.00 debited from A/c XX1234 on 15-04-26 to VPA merchant@oksbi (UPI Ref no 4109XXX). Avl Bal: Rs.12345.67."
        val r = parser.parse("SBIUPI", body, now)!!
        assertEquals(ParsedTxn.Direction.DEBIT, r.direction)
        assertEquals(50000L, r.amountPaise)
        assertEquals(ParsedTxn.Channel.UPI, r.channel)
        assertEquals("merchant@oksbi", r.counterparty)
    }

    @Test fun `bare amount with trailing Rs-prefixed balance is not mistaken for the balance`() {
        val body = "Dear UPI user A/C XX1234 debited by 49.0 on date 15Apr26 trf to MERCHANT Refno 4109. Avl Bal Rs.1,234.56 -SBI"
        val r = parser.parse("SBIUPI", body, now)!!
        assertEquals(4900L, r.amountPaise)
        assertEquals(123456L, r.balanceAfterPaise)
    }

    @Test fun `real-world SBI credit with hyphen before verb and ddMMMyy date`() {
        val body = "Dear SBI User, your A/c X4524-credited by Rs.40 on 04Jun26 transfer from GANJA YUVA NAGA  VENKATA NILESH Ref No 615577897186 -SBI"
        val r = parser.parse("AD-SBIUPI-S", body, now)!!
        assertEquals(ParsedTxn.Direction.CREDIT, r.direction)
        assertEquals(4000L, r.amountPaise)
        assertEquals("4524", r.accountLast4)
    }

    @Test fun `raw hash is body+sender derived, not time derived`() {
        val body = "Your A/c XX1234 is debited by Rs.100.00. Avl Bal Rs.500.00"
        val a = parser.parse("SBIINB", body, now)!!
        val b = parser.parse("SBIINB", body, now + 5_000)!!
        assertEquals(a.rawHash, b.rawHash)
        assertEquals(64, a.rawHash.length) // SHA-256 hex
    }

    @Test fun `rejects promotional and zero-amount messages`() {
        assertNull(parser.parse("SBIINB", "Promotional content about a new SBI scheme. -SBI", now))
        assertNull(parser.parse("SBIINB", "Your A/c XX1234 is debited by Rs.0.00 due to charge reversal", now))
    }

    @Test fun `falls back to receivedAt when date unparseable`() {
        val body = "Your A/c XX1234 is debited by Rs.100.00."
        val r = parser.parse("SBIINB", body, now)!!
        assertEquals(now, r.tsMillis)
    }

    @Test fun `Util tryParseDate handles minute-precision and date-only forms`() {
        assertNotNull(Util.tryParseDate("15-04-26 13:45"))
        assertNotNull(Util.tryParseDate("15-04-26 13:45:09"))
        assertNotNull(Util.tryParseDate("15/04/2026"))
        assertNotNull(Util.tryParseDate("15Apr26"))
        assertNull(Util.tryParseDate("not a date"))
    }

    @Test fun `Util paise parses common forms`() {
        assertEquals(50000L, Util.paise("500"))
        assertEquals(50000L, Util.paise("500.00"))
        assertEquals(50050L, Util.paise("500.5"))
        assertEquals(123456L, Util.paise("1,234.56"))
        assertEquals(123400L, Util.paise("1,234"))
        assertNull(Util.paise(""))
        assertNull(Util.paise("not-a-number"))
    }
}
