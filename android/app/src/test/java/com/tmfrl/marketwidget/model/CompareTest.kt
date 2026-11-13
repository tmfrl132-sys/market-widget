package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CompareTest {
    // 설계서 3.5절 상태 표의 10행
    @Test fun compareButton_startsAndCancelsPicking() {
        assertEquals(ChartSelection("KS11", null, true), afterCompareButton(ChartSelection("KS11", null, false)))
        assertEquals(ChartSelection("KS11", null, false), afterCompareButton(ChartSelection("KS11", null, true)))
    }

    @Test fun picking_otherTileBecomesCompare() {
        assertEquals(ChartSelection("KS11", "NDX", false), afterTileTap(ChartSelection("KS11", null, true), "NDX", auto = false))
    }

    @Test fun picking_sameTileCancels() {
        assertEquals(ChartSelection("KS11", null, false), afterTileTap(ChartSelection("KS11", null, true), "KS11", auto = false))
    }

    @Test fun comparing_buttonTurnsOff() {
        assertEquals(ChartSelection("KS11", null, false), afterCompareButton(ChartSelection("KS11", "NDX", false)))
    }

    @Test fun comparing_otherTileKeepsCompare() {
        assertEquals(ChartSelection("GSPC", "NDX", false), afterTileTap(ChartSelection("KS11", "NDX", false), "GSPC", auto = false))
    }

    @Test fun comparing_compareTileBecomesSingleChart() {
        assertEquals(ChartSelection("NDX", null, false), afterTileTap(ChartSelection("KS11", "NDX", false), "NDX", auto = false))
    }

    @Test fun comparing_selectedTileOrChartCloses() {
        assertEquals(ChartSelection(null, null, false), afterTileTap(ChartSelection("KS11", "NDX", false), "KS11", auto = false))
    }

    @Test fun autoOn_clearsCompareAndPicking() {
        assertEquals(ChartSelection("KS11", null, false), afterAutoOn(ChartSelection("KS11", "NDX", false)))
        assertEquals(ChartSelection("KS11", null, false), afterAutoOn(ChartSelection("KS11", null, true)))
    }

    @Test fun auto_tileJumpsWithoutCompare() {
        assertEquals(ChartSelection("BTC", null, false), afterTileTap(ChartSelection("KS11", null, false), "BTC", auto = true))
        assertEquals(ChartSelection("KS11", null, false), afterTileTap(ChartSelection("KS11", null, false), "KS11", auto = true))
    }

    @Test fun normal_tapTogglesLikeBefore() {
        assertEquals(ChartSelection("KS11", null, false), afterTileTap(ChartSelection(), "KS11", auto = false))
        assertEquals(ChartSelection(null, null, false), afterTileTap(ChartSelection("KS11"), "KS11", auto = false))
        assertEquals(ChartSelection("BTC", null, false), afterTileTap(ChartSelection("KS11"), "BTC", auto = false))
    }

    @Test fun compareButton_withoutChartDoesNothing() {
        assertEquals(ChartSelection(), afterCompareButton(ChartSelection()))
    }

    private fun daily(id: String, dates: List<String>, values: List<Double>) =
        Quote(id, id, values.last(), 0.0, 0.0, daily = values, daily_dates = dates)

    private fun max(id: String, dates: List<String>, values: List<Double>) =
        Quote(id, id, values.last(), 0.0, 0.0, max_closes = values, max_dates = dates)

    private fun assertValues(expected: List<Double>, actual: List<Double>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 1e-9) }
    }

    private val oneYearPrimary = daily(
        "KS11", listOf("2026-01-05", "2026-01-06", "2026-01-07", "2026-01-08"), listOf(200.0, 210.0, 220.0, 230.0),
    )
    private val oneYearOther = daily("NDX", listOf("2026-01-02", "2026-01-06", "2026-01-08"), listOf(50.0, 55.0, 60.0))

    @Test fun series_oneYearCarriesLastKnownValue() {
        val series = compareSeries(oneYearPrimary, oneYearOther, Period.ONE_YEAR)!!
        assertEquals(listOf("2026-01-05", "2026-01-06", "2026-01-07", "2026-01-08"), series.dates)
        assertValues(listOf(100.0, 105.0, 110.0, 115.0), series.primary)
        assertValues(listOf(100.0, 110.0, 110.0, 120.0), series.other)
    }

    private val maxPrimary = max(
        "KS11", listOf("1996-01-03", "2000-01-04", "2014-09-17", "2026-11-06"), listOf(100.0, 200.0, 300.0, 600.0),
    )
    private val maxOther = max("BTC", listOf("2014-09-17", "2020-01-02", "2026-11-06"), listOf(400.0, 7000.0, 80000.0))

    @Test fun series_maxStartsAtLaterFirstDate() {
        val series = compareSeries(maxPrimary, maxOther, Period.MAX)!!
        assertEquals(listOf("2014-09-17", "2026-11-06"), series.dates)
        assertValues(listOf(100.0, 200.0), series.primary)
        assertValues(listOf(100.0, 20000.0), series.other)
    }

    @Test fun series_todayIsNotSupported() {
        assertNull(compareSeries(oneYearPrimary, oneYearOther, Period.TODAY))
    }

    @Test fun series_nullWhenOtherDatesReversed() {
        val reversed = daily("NDX", listOf("2026-01-08", "2026-01-06", "2026-01-02"), listOf(60.0, 55.0, 50.0))
        assertNull(compareSeries(oneYearPrimary, reversed, Period.ONE_YEAR))
    }

    @Test fun series_nullWhenLengthsDiffer() {
        val broken = oneYearOther.copy(daily_dates = listOf("2026-01-02", "2026-01-06"))
        assertNull(compareSeries(oneYearPrimary, broken, Period.ONE_YEAR))
    }

    @Test fun series_nullWhenOnePointLeft() {
        val late = daily("NDX", listOf("2026-01-08"), listOf(60.0))
        assertNull(compareSeries(oneYearPrimary, late, Period.ONE_YEAR))
    }

    @Test fun series_nullWhenBaseIsZero() {
        val zero = daily("CL", listOf("2026-01-02", "2026-01-06"), listOf(0.0, 10.0))
        assertNull(compareSeries(oneYearPrimary, zero, Period.ONE_YEAR))
    }

    @Test fun series_nullWhenDateUnreadable() {
        val bad = oneYearOther.copy(daily_dates = listOf("2026-01-02", "x", "2026-01-08"))
        assertNull(compareSeries(oneYearPrimary, bad, Period.ONE_YEAR))
    }

    @Test fun log_maxExample() {
        val log = compareLogChart(listOf(100.0, 200.0), listOf(100.0, 20000.0))!!
        assertValues(listOf(0.0, 0.30103), log.primary.map { Math.round(it * 1e5) / 1e5 })
        assertValues(listOf(0.0, 2.30103), log.other.map { Math.round(it * 1e5) / 1e5 })
        assertEquals(0.0, log.axis.min, 1e-9)
        assertEquals(2.30103, log.axis.max, 1e-5)
        assertEquals(listOf(0.0, 1.0, 2.0), log.axis.ticks)
    }

    @Test fun log_nullWhenNotPositive() {
        assertNull(compareLogChart(listOf(100.0, 50.0), listOf(100.0, -5.0)))
        assertNotNull(compareLogChart(listOf(100.0, 50.0), listOf(100.0, 5.0)))
    }

    @Test fun indexAxisLabel() {
        assertEquals("110", formatIndex(110.0))
        assertEquals("102.5", formatIndex(102.5))
    }

    @Test fun legendValueAndChange() {
        assertEquals("108.2", formatLegendValue(108.24))
        assertEquals("+8.2%", formatLegendChange(108.24))
        assertEquals("96.1", formatLegendValue(96.06))
        assertEquals("-3.9%", formatLegendChange(96.06))
        assertEquals("0.0%", formatLegendChange(99.97))
    }

    @Test fun startLabelByPeriod() {
        assertEquals("1/5 = 100", formatCompareStart("2026-01-05", Period.ONE_YEAR))
        assertEquals("2014.9.17 = 100", formatCompareStart("2014-09-17", Period.MAX))
        assertEquals("2021.11.8 = 100", formatCompareStart("2021-11-08", Period.FIVE_YEARS))
    }
}
