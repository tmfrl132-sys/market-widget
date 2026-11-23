package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {
    @Test fun rsi_wilderSmoothing() {
        // 변화 +1, -1 → 평균 0.5, 0.5 → 50. 다음 +1 → (0.5+1)/2=0.75, 0.5/2=0.25 → 75
        assertEquals(listOf(null, null, 50.0, 75.0), rsi(listOf(1.0, 2.0, 1.0, 2.0), period = 2))
    }

    @Test fun rsi_edgeCases() {
        assertEquals(100.0, rsi((1..20).map { it.toDouble() }).last()!!, 0.0)
        assertEquals(50.0, rsi(List(20) { 5.0 }).last()!!, 0.0)
        assertEquals(List<Double?>(14) { null }, rsi(List(14) { 1.0 }))
    }

    @Test fun bollinger_populationStdDev() {
        val bands = bollinger(listOf(1.0, 2.0, 3.0), period = 3)
        assertEquals(listOf(null, null, 2.0), bands.middle)
        assertEquals(listOf(null, null, 3.63), bands.upper)
        assertEquals(listOf(null, null, 0.37), bands.lower)
    }

    @Test fun historicalVolatility_sampleStdDevOfLogReturns() {
        val hv = historicalVolatility(listOf(100.0, 110.0, 99.0, 108.9), periodsPerYear = 1, period = 2)
        assertEquals(listOf(null, null, 14.19, 14.19), hv)
        assertEquals(0.0, historicalVolatility(listOf(1.0, 2.0, 4.0, 8.0), 252, period = 2).last()!!, 0.0)
        // 0 이하가 섞이면 그 구간은 비운다
        assertNull(historicalVolatility(listOf(1.0, -1.0, 2.0), 252, period = 2)[2])
    }

    @Test fun availabilityByPeriod() {
        assertTrue(supportsMomentum(Period.TODAY))
        assertFalse(supportsMomentum(Period.MAX))
        assertTrue(supportsVolatility(Period.FIVE_YEARS))
        assertFalse(supportsVolatility(Period.WEEK))
        assertTrue(supportsCandles(Period.ONE_YEAR))
        assertFalse(supportsCandles(Period.FIVE_YEARS))
    }

    @Test fun periodsPerYear_weeklyDailyAndCrypto() {
        val q = Quote("KS11", "코스피", 1.0, 0.0, 0.0)
        assertEquals(52, periodsPerYear(q, Period.FIVE_YEARS))
        assertEquals(252, periodsPerYear(q, Period.ONE_YEAR))
        assertEquals(365, periodsPerYear(q.copy(always_open = true), Period.ONE_MONTH))
    }

    private val daily = (1..30).map { it.toDouble() }

    @Test fun indicatorSeries_warmsUpOneMonthWithFullDaily() {
        val q = Quote("KS11", "코스피", 1.0, 0.0, 0.0, daily = daily)
        val shown = chartValues(q, Period.ONE_MONTH)
        assertEquals(daily, indicatorSeries(q, Period.ONE_MONTH, shown))
        assertEquals(shown, indicatorSeries(q, Period.ONE_YEAR, shown).let { it!!.takeLast(shown.size) })
        assertNull(indicatorSeries(q, Period.MAX, shown))
        assertEquals(listOf(1.0, 2.0), indicatorSeries(q, Period.TODAY, listOf(1.0, 2.0)))
        assertEquals(listOf(3, 4), tailOf(listOf(1, 2, 3, 4), 2))
    }

    @Test fun candlesFor_slicesLikeDailyAndNeedsMatchingLengths() {
        val q = Quote(
            "KS11", "코스피", 1.0, 0.0, 0.0, daily = daily,
            daily_open = daily.map { it - 0.5 }, daily_high = daily.map { it + 1 }, daily_low = daily.map { it - 1 },
        )
        val candles = candlesFor(q, Period.ONE_MONTH)!!
        assertEquals(22, candles.open.size)
        assertEquals(daily.takeLast(22).map { it + 1 }, candles.high)
        assertNull(candlesFor(q, Period.FIVE_YEARS))
        assertNull(candlesFor(q.copy(daily_low = emptyList()), Period.ONE_MONTH))
    }

    @Test fun latestOhlc_appendsOrWidensLastBar() {
        val q = Quote("KS11", "코스피", 1.0, 0.0, 0.0, daily = listOf(10.0, 11.0), daily_open = listOf(9.0, 10.0),
            daily_high = listOf(10.5, 11.5), daily_low = listOf(8.5, 9.5))
        assertEquals(Triple(listOf(9.0, 10.0, 12.0), listOf(10.5, 11.5, 12.0), listOf(8.5, 9.5, 12.0)), latestOhlc(q, listOf(10.0, 11.0, 12.0), 12.0))
        assertEquals(Triple(listOf(9.0, 10.0), listOf(10.5, 12.0), listOf(8.5, 9.5)), latestOhlc(q, listOf(10.0, 12.0), 12.0))
        assertEquals(Triple(listOf(9.0, 10.0), listOf(10.5, 11.5), listOf(8.5, 9.0)), latestOhlc(q, listOf(10.0, 9.0), 9.0))
        val old = q.copy(daily_open = emptyList(), daily_high = emptyList(), daily_low = emptyList())
        assertEquals(Triple(emptyList<Double>(), emptyList<Double>(), emptyList<Double>()), latestOhlc(old, listOf(10.0, 12.0), 12.0))
    }

    @Test fun apply_keepsOhlcInStepWithDaily() {
        val q = Quote("KS11", "코스피", 100.0, 0.0, 0.0, daily = listOf(90.0, 100.0), daily_open = listOf(89.0, 95.0),
            daily_high = listOf(91.0, 101.0), daily_low = listOf(88.0, 94.0))
        val merged = applyLiveQuotes(MarketData("old", listOf(q), emptyList()), mapOf("KS11" to LiveQuote(103.0, 3.0, 3.0, 100.0)), "new").quotes[0]
        assertEquals(listOf(90.0, 100.0, 103.0), merged.daily)
        assertEquals(listOf(89.0, 95.0, 103.0), merged.daily_open)
        assertEquals(3, merged.daily_high.size)
    }

    @Test fun toggleChartOption_flipsOnlyThatKey() {
        val s = WidgetSettings()
        assertTrue(toggleChartOption(s, OPTION_RSI).show_rsi)
        assertFalse(toggleChartOption(s, OPTION_DRAWDOWN).show_drawdown)
        assertTrue(toggleChartOption(s, OPTION_CANDLES).show_candles)
        assertTrue(toggleChartOption(s, OPTION_BOLLINGER).show_bollinger)
        assertTrue(toggleChartOption(s, OPTION_HV).show_hv)
        assertEquals(s, toggleChartOption(s, "bogus"))
    }

    @Test fun splitAtMissing_dropsNaNPoints() {
        val values = listOf(Double.NaN, Double.NaN, 1.0, 2.0, Double.NaN, 3.0)
        assertEquals(listOf(2..3, 5..5), splitAtMissing(listOf(0..5), values))
        assertEquals(listOf(0..1, 2..2), splitAtMissing(listOf(0..1, 2..2), listOf(1.0, 2.0, 3.0)))
        assertTrue(nullsAsNaN(listOf(null, 1.0))[0].isNaN())
    }

    @Test fun chartOhlc_matchesCollectorRules() {
        val text = """{"chart": {"result": [{"meta": {}, "timestamp": [1, 2, 3, 4],
            "indicators": {"quote": [{"close": [10.0, null, 12.0, 13.0], "open": [9.0, 1.0, null, 13.5],
            "high": [11.0, 1.0, 12.5, null], "low": [8.5, 1.0, null, 12.0]}]}}]}}"""
        val (o, h, l) = chartOhlc(parseYahooChart(text)!!)
        assertEquals(listOf(9.0, 12.0, 13.5), o)
        assertEquals(listOf(11.0, 12.5, 13.5), h)
        assertEquals(listOf(8.5, 12.0, 12.0), l)
    }
}
