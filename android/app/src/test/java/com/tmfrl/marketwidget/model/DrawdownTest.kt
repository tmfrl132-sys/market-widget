package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawdownTest {
    private fun quote(
        daily: List<Double> = listOf(100.0, 50.0, 200.0, 150.0),
        price: Double? = 150.0,
        peak: Double? = 300.0,
        mdd: Double? = -70.0,
    ) = Quote("KS11", "코스피", price, 0.0, 0.0, daily = daily, peak = peak, mdd = mdd)

    @Test fun pct_fromPeakAndStopsAtMinus100() {
        assertEquals(-25.0, drawdownPct(150.0, 200.0), 1e-9)
        assertEquals(0.0, drawdownPct(200.0, 200.0), 1e-9)
        assertEquals(-100.0, drawdownPct(-37.63, 100.0), 1e-9)
    }

    @Test fun summary_usesSelectedPeriod() {
        val summary = drawdownSummary(quote(), Period.ONE_YEAR)!!
        assertEquals(-25.0, summary.current, 1e-9)
        assertEquals(-50.0, summary.mdd, 1e-9)
    }

    @Test fun summary_maxUsesCollectorValues() {
        val summary = drawdownSummary(quote(), Period.MAX)!!
        assertEquals(-50.0, summary.current, 1e-9)
        assertEquals(-70.0, summary.mdd, 0.0)
    }

    @Test fun summary_nullForToday() {
        assertNull(drawdownSummary(quote(), Period.TODAY))
    }

    @Test fun summary_nullForSymbolsWithoutDrawdown() {
        assertNull(drawdownSummary(quote(peak = null, mdd = null), Period.ONE_YEAR))
        assertNull(drawdownSummary(quote(peak = null, mdd = null), Period.MAX))
    }

    @Test fun summary_nullWithoutValues() {
        assertNull(drawdownSummary(quote(daily = emptyList()), Period.FIVE_YEARS))
        assertNull(drawdownSummary(quote(price = null), Period.MAX))
    }

    // ---- v1.0 잠수 차트 (설계서 3.4절, 작업 A4) ----

    private val sample = listOf(100.0, 110.0, 105.0, 120.0, 90.0, 95.0, 121.0, 80.0, 100.0)
    private val sampleDates = (0 until 9).map { java.time.LocalDate.of(2026, 10, 1).plusDays(it.toLong()).toString() }

    private val real: Map<String, Quote> by lazy {
        val text = DrawdownTest::class.java.classLoader!!.getResource("data_v10_max_2026_11_12.json")!!.readText()
        parseMarketData(text).quotes.associateBy { it.id }
    }

    @Test fun chart_oneMonthFixedExpectations() {
        val q = quote(daily = sample).copy(daily_dates = sampleDates)
        val chart = drawdownChart(q, Period.ONE_MONTH)!!
        assertEquals(DrawdownEpisode(6, 7, null, -33.9), chart.episode)
        assertEquals(-17.4, chart.current, 0.0)
        assertEquals(-33.9, chart.mdd, 0.0)
        assertEquals(listOf(0.0, 0.0, -4.5, 0.0, -25.0, -20.8, 0.0, -33.9, -17.4), chart.depths)
        assertEquals(listOf(7 to "MDD -33.9%", 8 to "현재 -17.4%"), chart.marks)
        assertEquals("10/7 고점 → 10/8 바닥 → 미회복", formatEpisode(chartDates(q, Period.ONE_MONTH), chart.episode, Period.ONE_MONTH))
    }

    @Test fun chart_troughAtEndIsOneMark() {
        val q = quote(daily = listOf(100.0, 120.0, 90.0))
        assertEquals(listOf(2 to "MDD·현재 -25.0%"), drawdownChart(q, Period.ONE_YEAR)!!.marks)
    }

    @Test fun chart_noDrawdownHasOnlyCurrent() {
        val q = quote(daily = listOf(100.0, 101.0, 102.0))
        val chart = drawdownChart(q, Period.ONE_YEAR)!!
        assertEquals(null, chart.episode)
        assertEquals(listOf(2 to "현재 0.0%"), chart.marks)
        assertEquals(null, formatEpisode(emptyList(), chart.episode, Period.ONE_YEAR))
    }

    @Test fun chart_maxUsesCollectorDepthsAndMergesEventAtTrough() {
        val kospi = real.getValue("KS11")
        val chart = drawdownChart(kospi, Period.MAX)!!
        assertEquals(kospi.max_depths, chart.depths)
        assertEquals(kospi.max_closes.size, chart.depths.size)
        val episode = chart.episode!!
        assertEquals(kospi.max_dates.indexOf("1997-06-17"), episode.peak)
        assertEquals(kospi.max_dates.indexOf("1998-06-16"), episode.trough)
        assertEquals(kospi.max_dates.indexOf("1999-04-27"), episode.recovery)
        assertEquals(-64.7, chart.depths[episode.trough], 0.0)
        assertEquals(-64.7, chart.mdd, 0.0)
        assertEquals("외환위기 -64.7% · MDD", chart.marks.single { it.first == episode.trough }.second)
        assertEquals(1, chart.marks.count { it.second.startsWith("외환위기") })
        assertTrue(chart.marks.any { it.second == "닷컴버블 -55.7%" })
        assertEquals("1997.6 고점 → 1998.6 바닥 → 1999.4 회복", formatEpisode(chartDates(kospi, Period.MAX), episode, Period.MAX))
    }

    @Test fun chart_maxUnrecoveredRunsToEnd() {
        val oil = real.getValue("CL")
        val episode = drawdownChart(oil, Period.MAX)!!.episode!!
        assertNull(episode.recovery)
        assertEquals("2008.7 고점 → 2020.4 바닥 → 미회복", formatEpisode(chartDates(oil, Period.MAX), episode, Period.MAX))
    }

    @Test fun chart_nullForVixAndToday() {
        assertNull(drawdownChart(real.getValue("VIX"), Period.MAX))
        assertNull(drawdownChart(quote(peak = null, mdd = null), Period.ONE_YEAR))
        assertNull(drawdownChart(real.getValue("KS11"), Period.TODAY))
    }

    @Test fun chart_maxMismatchedDepthsRecomputes() {
        val q = quote().copy(
            max_closes = listOf(100.0, 120.0, 60.0, 90.0), max_depths = listOf(0.0, -1.0),
            max_dates = listOf("2000-01-03", "2001-01-02", "2002-01-02", "2003-01-02"),
        )
        val chart = drawdownChart(q, Period.MAX)!!
        assertEquals(listOf(0.0, 0.0, -50.0, -25.0), chart.depths)
        // 날짜 필드가 없는 옛 캐시는 줄인 점으로 구간을 다시 계산한다
        assertEquals(DrawdownEpisode(1, 2, null, -50.0), chart.episode)
    }

    @Test fun chart_oldEventsWithoutDateAreNotDrawn() {
        val q = quote().copy(
            max_closes = listOf(100.0, 50.0, 80.0), max_dates = listOf("2000-01-03", "2001-01-02", "2002-01-02"),
            max_events = listOf(MaxEvent("닷컴버블", 0, -50.0)),
        )
        assertTrue(drawdownChart(q, Period.MAX)!!.marks.none { it.second.startsWith("닷컴버블") })
    }

    @Test fun indexAtOrBefore_lastDateNotAfter() {
        val dates = listOf("2020-01-02", "2020-03-02", "2026-11-11")
        assertEquals(2, indexAtOrBefore(dates, "2026-11-12"))
        assertEquals(1, indexAtOrBefore(dates, "2020-03-02"))
        assertEquals(0, indexAtOrBefore(dates, "2020-02-01"))
        assertNull(indexAtOrBefore(dates, "2019-12-31"))
        assertNull(indexAtOrBefore(dates, null))
    }

    @Test fun formatEpisode_dateFormatsByPeriod() {
        val dates = listOf("2024-07-10", "2024-08-05", "2024-11-03")
        val episode = DrawdownEpisode(0, 1, 2, -34.2)
        assertEquals("7/10 고점 → 8/5 바닥 → 11/3 회복", formatEpisode(dates, episode, Period.THREE_MONTHS))
        assertEquals("24.7.10 고점 → 24.8.5 바닥 → 24.11.3 회복", formatEpisode(dates, episode, Period.ONE_YEAR))
        assertEquals("2024.7 고점 → 2024.8 바닥 → 2024.11 회복", formatEpisode(dates, episode, Period.FIVE_YEARS))
    }

    @Test fun format_drawdownAndAxis() {
        assertEquals("-38.0%", formatDrawdown(-38.04))
        assertEquals("0.0%", formatDrawdown(-0.04))
        assertEquals("0.0%", formatDrawdown(0.0))
        assertEquals("-20%", formatAxisPct(-20.0))
        assertEquals("0%", formatAxisPct(0.0))
        assertEquals("-2.5%", formatAxisPct(-2.5))
    }
}
