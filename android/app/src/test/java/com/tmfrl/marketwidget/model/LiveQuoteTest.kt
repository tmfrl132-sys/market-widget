package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LiveQuoteTest {
    // 2026-11-02, 2026-11-03 09:00 (UTC+9)
    private val day1 = 1793577600L
    private val day2 = 1793664000L

    private fun chart(price: String, time: Long, stamps: List<Long>, closes: String) = """
        {"chart": {"result": [{
          "meta": {"regularMarketPrice": $price, "regularMarketTime": $time, "gmtoffset": 32400, "symbol": "^KS11"},
          "timestamp": $stamps,
          "indicators": {"quote": [{"close": $closes}]}
        }], "error": null}}
    """

    private fun quote(id: String, price: Double?, stale: Boolean = false) = Quote(
        id, id, price, change = 1.0, change_pct = 0.1, daily = listOf(1.0, 2.0), stale = stale,
    )

    @Test fun parse_usesPreviousBarWhenLastBarIsToday() {
        val live = parseLiveQuote(chart("95.0", day2 + 3600, listOf(day1, day2), "[100.0, 95.0]"))!!
        assertEquals(95.0, live.price, 0.0)
        assertEquals(-5.0, live.change, 0.0)
        assertEquals(-5.0, live.changePct, 0.0)
        assertEquals(100.0, live.prevClose, 0.0)
    }

    @Test fun parse_usesLastBarWhenTodayBarIsEmpty() {
        val live = parseLiveQuote(chart("103.0", day2 + 3600, listOf(day1 - 86400, day1, day2), "[90.0, 100.0, null]"))!!
        assertEquals(3.0, live.change, 0.0)
        assertEquals(3.0, live.changePct, 0.0)
    }

    @Test fun parse_roundsToTwoDecimals() {
        val live = parseLiveQuote(chart("101.234", day2 + 3600, listOf(day1, day2), "[99.0, 101.0]"))!!
        assertEquals(2.23, live.change, 0.0)
        assertEquals(2.26, live.changePct, 0.0)
    }

    @Test fun parse_nullWhenTooFewBarsOrNoResult() {
        assertNull(parseLiveQuote(chart("95.0", day2, listOf(day2), "[95.0]")))
        assertNull(parseLiveQuote("""{"chart": {"result": null, "error": {"code": "Not Found"}}}"""))
    }

    @Test fun apply_overwritesReceivedAndKeepsOthers() {
        val data = MarketData("old", listOf(quote("KS11", 100.0, stale = true), quote("BTC", 50.0)), emptyList())
        val merged = applyLiveQuotes(data, mapOf("KS11" to LiveQuote(95.0, -5.0, -5.0, 100.0)), "new")
        assertEquals("new", merged.updated_at)
        assertEquals(95.0, merged.quotes[0].price!!, 0.0)
        assertEquals(-5.0, merged.quotes[0].change_pct!!, 0.0)
        assertFalse(merged.quotes[0].stale)
        assertEquals(data.quotes[1], merged.quotes[1])
    }

    private fun applied(daily: List<Double>, weekly: List<Double>, live: LiveQuote): Quote {
        val base = Quote("KS11", "코스피", 100.0, 1.0, 0.1, chart = daily, daily = daily, weekly = weekly)
        return applyLiveQuotes(MarketData("old", listOf(base), emptyList()), mapOf("KS11" to live), "new").quotes[0]
    }

    @Test fun apply_replacesTodayBarWithLivePrice() {
        val quote = applied(listOf(90.0, 100.0, 98.0), listOf(80.0, 98.0), LiveQuote(95.126, -4.87, -4.87, 100.0))
        assertEquals(listOf(90.0, 100.0, 95.13), quote.daily)
        assertEquals(listOf(90.0, 100.0, 95.13), quote.chart)
        assertEquals(listOf(80.0, 95.13), quote.weekly)
    }

    @Test fun apply_appendsWhenLastBarIsPreviousClose() {
        val quote = applied(listOf(90.0, 100.0), listOf(80.0, 100.0), LiveQuote(95.0, -5.0, -5.0, 100.0))
        assertEquals(listOf(90.0, 100.0, 95.0), quote.daily)
        assertEquals(listOf(80.0, 95.0), quote.weekly)
    }

    @Test fun apply_leavesMissingChartsEmpty() {
        val quote = applied(emptyList(), emptyList(), LiveQuote(95.0, -5.0, -5.0, 100.0))
        assertEquals(emptyList<Double>(), quote.daily)
        assertEquals(emptyList<Double>(), quote.weekly)
    }

    @Test fun apply_nothingReceivedKeepsData() {
        val data = MarketData("old", listOf(quote("KS11", 100.0)), emptyList())
        assertEquals(data, applyLiveQuotes(data, emptyMap(), "new"))
    }

    @Test fun symbols_coverAllTenQuotes() {
        assertEquals(10, LIVE_SYMBOLS.size)
    }
}
