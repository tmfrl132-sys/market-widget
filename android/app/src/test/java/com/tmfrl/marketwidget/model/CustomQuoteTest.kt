package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class CustomQuoteTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L
    private val ticker = CustomTicker("T:AAPL", "AAPL", "애플", KIND_EQUITY, null)

    @Test fun historyDue_sixHourBoundary() {
        assertFalse(historyDue(now - 6 * hour + 60_000, now))
        assertTrue(historyDue(now - 6 * hour, now))
        assertTrue(historyDue(null, now))
    }

    @Test fun maxDue_sevenDayBoundary() {
        assertFalse(maxDue(now - 7 * 24 * hour + 1, now))
        assertTrue(maxDue(now - 7 * 24 * hour, now))
        assertTrue(maxDue(null, now))
    }

    @Test fun bigMoveBaseByKind() {
        assertEquals(3.0, customBigMoveBase(KIND_EQUITY), 0.0)
        assertEquals(2.0, customBigMoveBase(KIND_ETF), 0.0)
        assertEquals(1.5, customBigMoveBase(KIND_INDEX), 0.0)
        assertEquals(5.0, customBigMoveBase(KIND_CRYPTO), 0.0)
    }

    @Test fun firstFailureIsEmptyTileNotStale() {
        val quote = assembleCustomQuote(ticker, null, null, null, null, null, null, "t")
        assertEquals("T:AAPL", quote.id)
        assertEquals("애플", quote.name)
        assertNull(quote.price)
        assertFalse(quote.stale)
    }

    @Test fun laterFailureKeepsValuesAndIsStale() {
        val previous = Quote("T:AAPL", "애플", 200.0, 1.0, 0.5, daily = listOf(1.0, 2.0))
        val quote = assembleCustomQuote(ticker, previous, null, null, null, null, null, "t")
        assertEquals(200.0, quote.price!!, 0.0)
        assertEquals(listOf(1.0, 2.0), quote.daily)
        assertTrue(quote.stale)
    }

    @Test fun successBuildsAllFields() {
        val max = MaxFields(
            closes = listOf(50.0, 120.0, 60.0, 101.0), dates = listOf("1990-01-02", "2000-03-10", "2002-10-09", "2026-11-11"),
            depths = listOf(0.0, 0.0, -50.0, -15.8), start = 1990, peak = 120.0, mdd = -50.0,
            peakDate = "2000-03-10", troughDate = "2002-10-09", recoveryDate = null,
        )
        val quote = assembleCustomQuote(
            ticker, null, LiveQuote(102.0, 2.0, 2.0, 100.0),
            Series(listOf(90.0, 100.0, 101.0), listOf("2026-11-09", "2026-11-10", "2026-11-11")),
            Series(listOf(80.0, 101.0), listOf("2026-11-02", "2026-11-09")),
            max, "America/New_York", "2026-11-12T10:00:00+09:00",
        )
        assertEquals(102.0, quote.price!!, 0.0)
        assertEquals(listOf(90.0, 100.0, 102.0), quote.daily)
        assertEquals(listOf(80.0, 102.0), quote.weekly)
        assertEquals(listOf(50.0, 120.0, 60.0, 102.0), quote.max_closes)
        assertEquals(-15.0, quote.max_depths.last(), 0.0)
        assertEquals(-50.0, quote.mdd!!, 0.0)
        assertEquals("2002-10-09", quote.mdd_trough_date)
        assertEquals("America/New_York", quote.market_tz)
        assertEquals(3.0, quote.big_move_pct!!, 0.0)
        assertFalse(quote.always_open)
        assertFalse(quote.stale)
        assertEquals(emptyList<MaxEvent>(), quote.max_events)
        assertEquals(100.0, quote.prev_close!!, 0.0)
    }

    @Test fun cryptoIsAlwaysOpen() {
        val btc = CustomTicker("T:ETH-USD", "ETH-USD", "Ethereum USD", KIND_CRYPTO, null)
        assertTrue(assembleCustomQuote(btc, null, LiveQuote(1.0, 0.0, 0.0, 1.0), null, null, null, "UTC", "t").always_open)
    }

    @Test fun statusText() {
        assertEquals("받는 중", customStatusText(null))
        assertEquals("받는 중", customStatusText(CustomEntry("T:AAPL", Quote("T:AAPL", "애플", null, null, null))))
        assertEquals(
            "받지 못함 · 다시 시도 중",
            customStatusText(CustomEntry("T:AAPL", Quote("T:AAPL", "애플", null, null, null), last_ok_at = 1L, last_failed = true)),
        )
        val at = ZonedDateTime.of(2026, 11, 12, 23, 5, 0, 0, SEOUL).toInstant().toEpochMilli()
        assertEquals("23:05 받음", customStatusText(CustomEntry("T:AAPL", Quote("T:AAPL", "애플", 1.0, 0.0, 0.0), last_ok_at = at)))
    }

    @Test fun cache_roundTripAndBroken() {
        val cache = CustomCache(listOf(CustomEntry("T:AAPL", Quote("T:AAPL", "애플", 1.0, 0.0, 0.0), 1L, 2L, 3L, false)))
        assertEquals(cache, parseCustomCache(encodeCustomCache(cache)))
        assertEquals(CustomCache(), parseCustomCache("not json"))
    }
}
