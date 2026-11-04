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

    @Test fun parse_nullWhenPreviousDayBarIsEmpty() {
        // 11/4 10:37 실제 사례: 전일 봉이 비어 있어 전전일 종가와 비교해 -4.09%가 나왔다
        val day0 = day1 - 86400
        assertNull(parseLiveQuote(chart("5619.4", day2 + 3600, listOf(day0, day1, day2), "[5859.08, null, 5619.4]")))
    }

    @Test fun parse_nullWhenTodayBarAbsentAndLastBarEmpty() {
        assertNull(parseLiveQuote(chart("5619.4", day2 + 3600, listOf(day1 - 86400, day1), "[5859.08, null]")))
    }

    @Test fun parse_usesLastBarWhenTodayBarAbsent() {
        val live = parseLiveQuote(chart("5619.4", day2 + 3600, listOf(day1 - 86400, day1), "[5859.08, 5648.79]"))!!
        assertEquals(5648.79, live.prevClose, 0.0)
    }

    private val naverIndex = """{"stockName": "코스피", "closePrice": "5,648.79",
        "compareToPreviousClosePrice": "-210.29", "fluctuationsRatio": "-3.59", "marketStatus": "CLOSE"}"""

    @Test fun naverIndex_readsCommaNumbersAndSign() {
        val live = parseNaverIndex(naverIndex)!!
        assertEquals(5648.79, live.price, 0.0)
        assertEquals(-210.29, live.change, 0.0)
        assertEquals(-3.59, live.changePct, 0.0)
        assertEquals(5859.08, live.prevClose, 0.0)
    }

    @Test fun naverIndex_nullWhenValueMissing() {
        assertNull(parseNaverIndex("""{"closePrice": "5,648.79"}"""))
        assertNull(parseNaverIndex("""{"closePrice": "-", "compareToPreviousClosePrice": "1", "fluctuationsRatio": "1"}"""))
    }

    private fun minute(time: String, price: String) = """{"localDateTime": "$time", "currentPrice": $price}"""

    @Test fun naverMinutes_everyFifthLatestDayKeepsLast() {
        val today = (0..6).map { minute("2026110409%02d00".format(it), "${100 + it}.0") }
        val text = (listOf(minute("20261103153000", "1.0")) + today).joinToString(",", "[", "]")
        assertEquals(listOf(100.0, 105.0, 106.0), parseNaverMinutes(text))
    }

    @Test fun naverMinutes_skipsEmptyAndHandlesNoData() {
        val text = listOf(minute("20261104090000", "100.0"), minute("20261104090100", "null")).joinToString(",", "[", "]")
        assertEquals(listOf(100.0), parseNaverMinutes(text))
        assertEquals(emptyList<Double>(), parseNaverMinutes("[]"))
    }

    @Test fun naverCodes_onlyKoreanIndexes() {
        assertEquals(setOf("KS11", "KQ11"), NAVER_CODES.keys)
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

    private fun withMax(peak: Double?, mdd: Double?) = Quote(
        "KS11", "코스피", 100.0, 1.0, 0.1,
        max_closes = listOf(50.0, 100.0), max_drawdowns = if (peak == null) emptyList() else listOf(-60.0, -20.0),
        peak = peak, mdd = mdd,
    )

    private fun merged(base: Quote, live: LiveQuote) =
        applyLiveQuotes(MarketData("old", listOf(base), emptyList()), mapOf("KS11" to live), "new").quotes[0]

    @Test fun apply_updatesMaxChartAndDrawdown() {
        val quote = merged(withMax(peak = 200.0, mdd = -60.0), LiveQuote(90.0, -10.0, -10.0, 100.0))
        assertEquals(listOf(50.0, 90.0), quote.max_closes)
        assertEquals(listOf(-60.0, -55.0), quote.max_drawdowns)
        assertEquals(200.0, quote.peak!!, 0.0)
        assertEquals(-60.0, quote.mdd!!, 0.0)
    }

    @Test fun apply_newLowDeepensMdd() {
        val quote = merged(withMax(peak = 200.0, mdd = -60.0), LiveQuote(40.0, -60.0, -60.0, 100.0))
        assertEquals(-80.0, quote.mdd!!, 0.0)
    }

    @Test fun apply_newHighRaisesPeak() {
        val quote = merged(withMax(peak = 200.0, mdd = -60.0), LiveQuote(250.0, 150.0, 150.0, 100.0))
        assertEquals(250.0, quote.peak!!, 0.0)
        assertEquals(listOf(-60.0, 0.0), quote.max_drawdowns)
    }

    @Test fun apply_keepsDrawdownEmptyForExcludedSymbols() {
        val quote = merged(withMax(peak = null, mdd = null), LiveQuote(90.0, -10.0, -10.0, 100.0))
        assertNull(quote.peak)
        assertNull(quote.mdd)
        assertEquals(emptyList<Double>(), quote.max_drawdowns)
        assertEquals(listOf(50.0, 90.0), quote.max_closes)
    }

    @Test fun apply_setsIntradayEndingAtPriceAndPrevClose() {
        val live = LiveQuote(95.0, -5.0, -5.0, 100.123, intraday = listOf(99.0, 97.0, 96.0))
        val quote = merged(withMax(peak = 200.0, mdd = -60.0), live)
        assertEquals(listOf(99.0, 97.0, 95.0), quote.intraday)
        assertEquals(100.12, quote.prev_close!!, 0.0)
    }

    @Test fun apply_keepsOldIntradayWhenNoneReceived() {
        val base = withMax(peak = 200.0, mdd = -60.0).copy(intraday = listOf(1.0, 2.0))
        assertEquals(listOf(1.0, 2.0), merged(base, LiveQuote(95.0, -5.0, -5.0, 100.0)).intraday)
    }

    @Test fun parseIntraday_dropsEmptyBars() {
        assertEquals(listOf(99.0, 97.13), parseIntraday(chart("97.0", day2, listOf(day1, day2, day2 + 300), "[99.0, null, 97.126]")))
        assertEquals(emptyList<Double>(), parseIntraday("""{"chart": {"result": null}}"""))
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
