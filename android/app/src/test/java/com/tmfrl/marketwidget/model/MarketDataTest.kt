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

    @Test fun parse_oldCacheWithoutV10FieldsUsesDefaults() {
        val quote = parseMarketData(
            """{"updated_at": "t", "quotes": [{"id": "KS11", "name": "코스피", "price": 1.0, "change": 0.0, "change_pct": 0.0,
                "max_closes": [1.0, 2.0], "max_drawdowns": [-1.0], "peak": 2.0, "mdd": -1.0,
                "max_events": [{"label": "외환위기", "index": 0, "depth": -64.7}]}], "events": []}""",
        ).quotes[0]
        assertEquals(emptyList<Double>(), quote.max_depths)
        assertNull(quote.mdd_peak_date)
        assertNull(quote.mdd_trough_date)
        assertNull(quote.mdd_recovery_date)
        assertNull(quote.max_events[0].date)
        assertEquals(0, quote.max_events[0].index)
    }

    @Test fun parse_readsV10Fields() {
        val quote = parseMarketData(
            """{"updated_at": "t", "quotes": [{"id": "CL", "name": "WTI", "price": 1.0, "change": 0.0, "change_pct": 0.0,
                "max_closes": [10.0, 5.0], "max_dates": ["2008-07-03", "2020-04-20"], "max_depths": [0.0, -100.0],
                "mdd_peak_date": "2008-07-03", "mdd_trough_date": "2020-04-20", "mdd_recovery_date": null,
                "max_events": [{"label": "코로나", "index": 3, "depth": -100.0, "date": "2020-04-20"}]}], "events": []}""",
        ).quotes[0]
        assertEquals(listOf(0.0, -100.0), quote.max_depths)
        assertEquals("2008-07-03", quote.mdd_peak_date)
        assertEquals("2020-04-20", quote.mdd_trough_date)
        assertNull(quote.mdd_recovery_date)
        assertEquals("2020-04-20", quote.max_events[0].date)
    }

    @Test fun parse_realV10Sample() {
        val text = MarketDataTest::class.java.classLoader!!.getResource("data_v10_max_2026_11_12.json")!!.readText()
        val quotes = parseMarketData(text).quotes.associateBy { it.id }
        val kospi = quotes.getValue("KS11")
        assertEquals(kospi.max_closes.size, kospi.max_depths.size)
        assertEquals(-64.7, kospi.mdd!!, 0.0)
        assertEquals("1998-06-16", kospi.mdd_trough_date)
        assertEquals(-64.7, kospi.max_depths[kospi.max_dates.indexOf("1998-06-16")], 0.0)
        assertEquals(emptyList<Double>(), quotes.getValue("VIX").max_depths)
        assertNull(quotes.getValue("CL").mdd_recovery_date)
    }

    @Test fun encode_keepsV10FieldsInCache() {
        val quote = Quote(
            "KS11", "코스피", 1.0, 0.0, 0.0, max_depths = listOf(0.0, -1.0), mdd_peak_date = "2020-01-02",
            mdd_trough_date = "2020-03-02", mdd_recovery_date = "2021-01-04",
            max_events = listOf(MaxEvent("코로나", 1, -1.0, "2020-03-02")),
        )
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
