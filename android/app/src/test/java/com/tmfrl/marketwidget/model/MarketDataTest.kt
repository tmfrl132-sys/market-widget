package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDataTest {
    @Test fun parse_ignoresUnknownKeysAndAcceptsNullPrice() {
        val json = """
            {"updated_at": "2026-10-30T10:30:00+09:00", "extra": 1,
             "quotes": [{"id": "KS11", "name": "코스피", "price": null, "change": null,
                         "change_pct": null, "history": [], "stale": true, "new_field": "x"}],
             "events": [{"date": "2026-11-13", "title": "미국 CPI", "country": "US"}]}
        """.trimIndent()
        val data = parseMarketData(json)
        assertEquals("2026-10-30T10:30:00+09:00", data.updated_at)
        assertNull(data.quotes[0].price)
        assertTrue(data.quotes[0].stale)
        assertEquals("미국 CPI", data.events[0].title)
    }

    @Test fun parse_oldFormatHasEmptyAxisFields() {
        val quote = parseMarketData(
            """{"updated_at": "t", "quotes": [{"id": "KS11", "name": "코스피", "price": 1.0, "change": 0.0,
                "change_pct": 0.0, "daily": [1.0], "intraday": [1.0]}], "events": []}""",
        ).quotes[0]
        assertEquals(emptyList<String>(), quote.daily_dates)
        assertEquals(emptyList<String>(), quote.weekly_dates)
        assertEquals(emptyList<String>(), quote.max_dates)
        assertEquals(emptyList<Long>(), quote.intraday_times)
        assertNull(quote.session_start)
        assertNull(quote.session_end)
    }

    @Test fun parse_readsCollectorDateFields() {
        val quote = parseMarketData(
            """{"updated_at": "t", "quotes": [{"id": "KS11", "name": "코스피", "price": 1.0, "change": 0.0, "change_pct": 0.0,
                "daily": [1.0], "daily_dates": ["2026-11-05"], "weekly": [1.0], "weekly_dates": ["2026-11-02"],
                "max_closes": [1.0], "max_dates": ["1996-12-11"]}], "events": []}""",
        ).quotes[0]
        assertEquals(listOf("2026-11-05"), quote.daily_dates)
        assertEquals(listOf("2026-11-02"), quote.weekly_dates)
        assertEquals(listOf("1996-12-11"), quote.max_dates)
    }

    @Test fun parse_oldCacheWithoutV09FieldsUsesDefaults() {
        val quote = parseMarketData(
            """{"updated_at": "2026-11-06T10:00:00+09:00", "quotes": [{"id": "KS11", "name": "코스피", "price": 1.0,
                "change": 0.0, "change_pct": 0.0, "intraday_times": [1793840400]}], "events": []}""",
        ).quotes[0]
        assertNull(quote.big_move_pct)
        assertNull(quote.market_tz)
        assertFalse(quote.always_open)
    }

    @Test fun parse_readsV09Fields() {
        val quotes = parseMarketData(
            """{"updated_at": "t", "quotes": [
                {"id": "KS11", "name": "코스피", "price": 1.0, "change": 0.0, "change_pct": 0.0,
                 "big_move_pct": 4.3, "market_tz": "Asia/Seoul", "always_open": false},
                {"id": "BTC", "name": "비트코인", "price": 1.0, "change": 0.0, "change_pct": 0.0,
                 "big_move_pct": 5.0, "market_tz": "UTC", "always_open": true}], "events": []}""",
        ).quotes
        assertEquals(4.3, quotes[0].big_move_pct!!, 0.0)
        assertEquals("Asia/Seoul", quotes[0].market_tz)
        assertFalse(quotes[0].always_open)
        assertEquals("UTC", quotes[1].market_tz)
        assertTrue(quotes[1].always_open)
    }

    @Test fun encode_keepsV09FieldsInCache() {
        val quote = Quote("BTC", "비트코인", 1.0, 0.0, 0.0, big_move_pct = 5.0, market_tz = "UTC", always_open = true)
        val data = MarketData("t", listOf(quote), emptyList())
        assertEquals(data, parseMarketData(encodeMarketData(data)))
    }

    @Test fun encode_keepsDeviceFieldsInCache() {
        val quote = Quote(
            "KS11", "코스피", 1.0, 0.0, 0.0, intraday = listOf(1.0), intraday_times = listOf(1793840400L),
            session_start = 1793840400L, session_end = 1793863800L, daily_dates = listOf("2026-11-05"),
        )
        val data = MarketData("t", listOf(quote), emptyList())
        assertEquals(data, parseMarketData(encodeMarketData(data)))
    }
}
