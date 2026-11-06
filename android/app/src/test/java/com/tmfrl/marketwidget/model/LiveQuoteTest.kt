package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.util.TimeZone

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
        assertEquals(listOf(100.0, 105.0, 106.0), parseNaverMinutes(text).values)
    }

    @Test fun naverMinutes_skipsEmptyAndHandlesNoData() {
        val text = listOf(minute("20261104090000", "100.0"), minute("20261104090100", "null")).joinToString(",", "[", "]")
        assertEquals(listOf(100.0), parseNaverMinutes(text).values)
        assertEquals(IntradaySeries(), parseNaverMinutes("[]"))
    }

    private fun seoul(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(SEOUL).toEpochSecond()

    private fun minutes(vararg items: String) = items.joinToString(",", "[", "]")

    @Test fun naverMinutes_timesFollowSameFilterAsValues() {
        val today = (0..6).map { minute("2026110509%02d00".format(it), "${100 + it}.0") }
        val text = (listOf(minute("20261104153000", "1.0")) + today).joinToString(",", "[", "]")
        val series = parseNaverMinutes(text)
        assertEquals(listOf(100.0, 105.0, 106.0), series.values)
        assertEquals(listOf(seoul(5, 9), seoul(5, 9, 5), seoul(5, 9, 6)), series.times)
    }

    @Test fun naverMinutes_sessionIsNineToHalfPastThreeSeoul() {
        val series = parseNaverMinutes(minutes(minute("20261105090000", "1.0"), minute("20261105100000", "2.0")))
        assertEquals(seoul(5, 9), series.sessionStart)
        assertEquals(seoul(5, 15, 30), series.sessionEnd)
    }

    @Test fun naverMinutes_closingAuctionBarKeepsSessionEnd() {
        val series = parseNaverMinutes(minutes(minute("20261105090000", "1.0"), minute("20261105153200", "2.0")))
        assertEquals(seoul(5, 15, 30), series.sessionEnd)
        assertEquals(1f, sessionPosition(series.times.last(), series.sessionStart!!, series.sessionEnd!!), 0f)
    }

    @Test fun naverMinutes_barAtLimitKeepsSessionEnd() {
        val series = parseNaverMinutes(minutes(minute("20261105090000", "1.0"), minute("20261105153500", "2.0")))
        assertEquals(seoul(5, 15, 30), series.sessionEnd)
    }

    @Test fun naverMinutes_delayedOpenStretchesSessionEnd() {
        // 수능일처럼 개장이 늦춰진 날은 15:35 뒤에도 봉이 있다
        val series = parseNaverMinutes(minutes(minute("20261112100000", "1.0"), minute("20261112163000", "2.0")))
        assertEquals(seoul(12, 9), series.sessionStart)
        assertEquals(seoul(12, 16, 30), series.sessionEnd)
    }

    @Test fun naverMinutes_unreadableTimeKeepsValuesWithoutAxis() {
        val partly = parseNaverMinutes(minutes(minute("20261105090000", "1.0"), minute("20261105xx0100", "2.0")))
        assertEquals(IntradaySeries(listOf(1.0, 2.0)), partly)
        val broken = parseNaverMinutes(minutes(minute("2026110509", "1.0")))
        assertEquals(IntradaySeries(listOf(1.0)), broken)
    }

    @Test fun naverMinutes_doesNotDependOnDeviceTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val series = parseNaverMinutes(minutes(minute("20261105090000", "1.0"), minute("20261105100000", "2.0")))
            assertEquals(seoul(5, 9), series.times.first())
            assertEquals(seoul(5, 15, 30), series.sessionEnd)
        } finally {
            TimeZone.setDefault(original)
        }
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
        assertEquals(listOf(99.0, 97.13), parseIntraday(chart("97.0", day2, listOf(day1, day2, day2 + 300), "[99.0, null, 97.126]")).values)
        assertEquals(IntradaySeries(), parseIntraday("""{"chart": {"result": null}}"""))
    }

    private fun intradayChart(periods: String?, stamps: List<Long>, closes: String): String {
        val field = if (periods == null) "" else ""","tradingPeriods": $periods"""
        return """{"chart": {"result": [{
            "meta": {"regularMarketPrice": 1.0, "gmtoffset": 32400$field},
            "timestamp": $stamps,
            "indicators": {"quote": [{"close": $closes}]}
        }]}}"""
    }

    private fun span(start: Long, end: Long) =
        """{"timezone": "KST", "start": $start, "end": $end, "gmtoffset": 32400}"""

    @Test fun parseIntraday_timesDropSameBarsAsValues() {
        val series = parseIntraday(intradayChart(null, listOf(day1, day1 + 300, day1 + 600), "[99.0, null, 97.0]"))
        assertEquals(listOf(99.0, 97.0), series.values)
        assertEquals(listOf(day1, day1 + 600), series.times)
    }

    @Test fun parseIntraday_readsTradingPeriods() {
        val series = parseIntraday(intradayChart("[[${span(day1, day1 + 21600)}]]", listOf(day1, day1 + 300), "[1.0, 2.0]"))
        assertEquals(day1, series.sessionStart)
        assertEquals(day1 + 21600, series.sessionEnd)
    }

    @Test fun parseIntraday_manyPeriodsUseFirstStartAndLastEnd() {
        val periods = "[[${span(day1, day1 + 100)},${span(day1 + 200, day1 + 300)}],[${span(day1 + 400, day1 + 500)}]]"
        val series = parseIntraday(intradayChart(periods, listOf(day1, day1 + 300), "[1.0, 2.0]"))
        assertEquals(day1, series.sessionStart)
        assertEquals(day1 + 500, series.sessionEnd)
    }

    @Test fun parseIntraday_otherPeriodShapesMeanUnknownSession() {
        val shapes = listOf(
            """{"regular": [[${span(day1, day1 + 100)}]]}""",
            "[${span(day1, day1 + 100)}]",
            "[[]]",
            "[]",
            "null",
            "\"closed\"",
            """[[{"start": $day1}]]""",
            """[[{"start": "x", "end": {}}]]""",
        )
        shapes.forEach { shape ->
            val series = parseIntraday(intradayChart(shape, listOf(day1, day1 + 300), "[1.0, 2.0]"))
            assertEquals(shape, listOf(1.0, 2.0), series.values)
            assertEquals(shape, listOf(day1, day1 + 300), series.times)
            assertNull(shape, series.sessionStart)
            assertNull(shape, series.sessionEnd)
        }
    }

    @Test fun parseLiveQuote_survivesOtherPeriodShape() {
        val text = intradayChart("""{"regular": []}""", listOf(day1, day2), "[100.0, 95.0]")
        assertEquals(1.0, parseLiveQuote(text)!!.price, 0.0)
    }

    @Test fun parseIntraday_timestampLengthMismatchKeepsValues() {
        val series = parseIntraday(intradayChart(null, listOf(day1), "[1.0, null, 2.0]"))
        assertEquals(listOf(1.0, 2.0), series.values)
        assertEquals(emptyList<Long>(), series.times)
    }

    @Test fun withIntraday_copiesAllFourFields() {
        val live = LiveQuote(1.0, 0.0, 0.0, 1.0).withIntraday(IntradaySeries(listOf(1.0), listOf(5L), 2L, 9L))
        assertEquals(listOf(1.0), live.intraday)
        assertEquals(listOf(5L), live.intradayTimes)
        assertEquals(2L, live.sessionStart)
        assertEquals(9L, live.sessionEnd)
    }

    @Test fun apply_replacesIntradayFieldsTogetherAndKeepsLength() {
        val base = withMax(peak = 200.0, mdd = -60.0).copy(
            intraday = listOf(1.0), intraday_times = listOf(1L), session_start = 1L, session_end = 2L,
        )
        val live = LiveQuote(
            95.0, -5.0, -5.0, 100.0,
            intraday = listOf(99.0, 96.0), intradayTimes = listOf(10L, 20L), sessionStart = 5L, sessionEnd = 50L,
        )
        val quote = merged(base, live)
        assertEquals(listOf(99.0, 95.0), quote.intraday)
        assertEquals(listOf(10L, 20L), quote.intraday_times)
        assertEquals(5L, quote.session_start)
        assertEquals(50L, quote.session_end)
    }

    @Test fun apply_keepsIntradayFieldsTogetherWhenNoneReceived() {
        val base = withMax(peak = 200.0, mdd = -60.0).copy(
            intraday = listOf(1.0, 2.0), intraday_times = listOf(1L, 2L), session_start = 1L, session_end = 3L,
        )
        val quote = merged(base, LiveQuote(95.0, -5.0, -5.0, 100.0))
        assertEquals(listOf(1.0, 2.0), quote.intraday)
        assertEquals(listOf(1L, 2L), quote.intraday_times)
        assertEquals(1L, quote.session_start)
        assertEquals(3L, quote.session_end)
    }

    @Test fun apply_newIntradayWithoutTimesClearsOldTimes() {
        // 값만 새로 받으면 옛 시각은 다른 봉의 시각이다
        val base = withMax(peak = 200.0, mdd = -60.0).copy(
            intraday = listOf(1.0, 2.0), intraday_times = listOf(1L, 2L), session_start = 1L, session_end = 3L,
        )
        val quote = merged(base, LiveQuote(95.0, -5.0, -5.0, 100.0, intraday = listOf(9.0, 8.0)))
        assertEquals(emptyList<Long>(), quote.intraday_times)
        assertNull(quote.session_start)
        assertNull(quote.session_end)
    }

    private fun withDates(daily: List<Double>, dates: List<String>, live: LiveQuote, updatedAt: String): Quote {
        val base = Quote("KS11", "코스피", 100.0, 1.0, 0.1, daily = daily, daily_dates = dates)
        return applyLiveQuotes(MarketData("old", listOf(base), emptyList()), mapOf("KS11" to live), updatedAt).quotes[0]
    }

    private val appendLive = LiveQuote(95.0, -5.0, -5.0, 100.0)

    @Test fun apply_appendsUpdatedDateWhenDailyGrows() {
        val quote = withDates(listOf(90.0, 100.0), listOf("2026-11-03", "2026-11-04"), appendLive, "2026-11-05T10:00:00+09:00")
        assertEquals(listOf(90.0, 100.0, 95.0), quote.daily)
        assertEquals(listOf("2026-11-03", "2026-11-04", "2026-11-05"), quote.daily_dates)
    }

    @Test fun apply_keepsDatesWhenDailyLastIsReplaced() {
        val dates = listOf("2026-11-04", "2026-11-05")
        val quote = withDates(listOf(100.0, 98.0), dates, appendLive, "2026-11-05T10:00:00+09:00")
        assertEquals(listOf(100.0, 95.0), quote.daily)
        assertEquals(dates, quote.daily_dates)
    }

    @Test fun apply_doesNotAppendDateToMisalignedOrEmptyDates() {
        val empty = withDates(listOf(90.0, 100.0), emptyList(), appendLive, "2026-11-05T10:00:00+09:00")
        assertEquals(emptyList<String>(), empty.daily_dates)
        val short = withDates(listOf(90.0, 100.0), listOf("2026-11-04"), appendLive, "2026-11-05T10:00:00+09:00")
        assertEquals(listOf("2026-11-04"), short.daily_dates)
    }

    @Test fun apply_keepsWeeklyAndMaxDatesAligned() {
        val base = Quote(
            "KS11", "코스피", 100.0, 1.0, 0.1,
            weekly = listOf(1.0, 2.0), weekly_dates = listOf("2026-10-26", "2026-11-02"),
            max_closes = listOf(3.0, 4.0), max_dates = listOf("1996-12-11", "2026-11-05"),
        )
        val quote = merged(base, appendLive)
        assertEquals(quote.weekly.size, quote.weekly_dates.size)
        assertEquals(quote.max_closes.size, quote.max_dates.size)
        assertEquals(base.weekly_dates, quote.weekly_dates)
        assertEquals(base.max_dates, quote.max_dates)
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

    @Test fun symbols_coverAllThirteenQuotesInCollectorOrder() {
        assertEquals(
            listOf("KS11", "KQ11", "GSPC", "ES", "IXIC", "NQ", "VIX", "KRW", "DXY", "TNX", "CL", "GC", "BTC"),
            LIVE_SYMBOLS.keys.toList(),
        )
        assertEquals("ES=F", LIVE_SYMBOLS["ES"])
        assertEquals("NQ=F", LIVE_SYMBOLS["NQ"])
        assertEquals("DX-Y.NYB", LIVE_SYMBOLS["DXY"])
    }

    /** 뉴욕 시간(gmtoffset -18000) range=5d 응답. 수집기 parse_chart 테스트와 같은 자료다. */
    private fun newYorkChart(price: String, time: Long, stamps: List<Long>, closes: String) = """
        {"chart": {"result": [{
          "meta": {"regularMarketPrice": $price, "regularMarketTime": $time, "gmtoffset": -18000, "symbol": "DX-Y.NYB"},
          "timestamp": $stamps,
          "indicators": {"quote": [{"close": $closes}]}
        }], "error": null}}
    """

    @Test fun parse_skipsWeekendEmptyBarForMondayDollarIndex() {
        // 뉴욕 11-06 금, 11-08 일(빈 봉), 11-09 월 0시. 시세는 월 10:00
        val live = parseLiveQuote(
            newYorkChart("102.5", 1794236400L, listOf(1793941200L, 1794114000L, 1794200400L), "[102.236, null, 102.5]"),
        )!!
        assertEquals(102.236, live.prevClose, 0.0)
        assertEquals(0.26, live.change, 0.0)
        assertEquals(0.26, live.changePct, 0.0)
    }

    @Test fun parse_weekdayEmptyBarStillMeansNoChange() {
        // 뉴욕 11-04 수, 11-05 목(빈 봉), 11-06 금 0시. 시세는 금 10:00. 평일 빈 봉은 지금 규칙대로 비운다.
        assertNull(
            parseLiveQuote(
                newYorkChart("102.236", 1793977200L, listOf(1793768400L, 1793854800L, 1793941200L), "[102.0, null, 102.236]"),
            ),
        )
    }

    @Test fun apply_keepsCollectorOnlyFields() {
        val base = Quote(
            "KS11", "코스피", 100.0, 1.0, 1.0, daily = listOf(99.0, 100.0),
            big_move_pct = 2.0, market_tz = "Asia/Seoul", always_open = false,
        )
        val btc = Quote("BTC", "비트코인", 1.0, 0.0, 0.0, big_move_pct = 5.0, market_tz = "UTC", always_open = true)
        val merged = applyLiveQuotes(
            MarketData("old", listOf(base, btc), emptyList()),
            mapOf("KS11" to LiveQuote(95.0, -5.0, -5.0, 100.0), "BTC" to LiveQuote(2.0, 1.0, 100.0, 1.0)),
            "new",
        ).quotes
        assertEquals(2.0, merged[0].big_move_pct!!, 0.0)
        assertEquals("Asia/Seoul", merged[0].market_tz)
        assertFalse(merged[0].always_open)
        assertEquals(5.0, merged[1].big_move_pct!!, 0.0)
        assertEquals("UTC", merged[1].market_tz)
        assertEquals(true, merged[1].always_open)
    }
}
