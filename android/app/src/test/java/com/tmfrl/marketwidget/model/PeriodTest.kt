package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodTest {
    private val daily = (1..250).map { it.toDouble() }
    private val weekly = (1..260).map { it * 10.0 }

    @Test fun periodValues_slicesDailyAndUsesWeeklyForFiveYears() {
        assertEquals(daily.takeLast(22), periodValues(daily, weekly, Period.ONE_MONTH))
        assertEquals(daily.takeLast(63), periodValues(daily, weekly, Period.THREE_MONTHS))
        assertEquals(daily, periodValues(daily, weekly, Period.ONE_YEAR))
        assertEquals(weekly, periodValues(daily, weekly, Period.FIVE_YEARS))
    }

    @Test fun periodValues_slicesDatesLikeValues() {
        val dates = daily.map { it.toString() }
        val weeks = weekly.map { it.toString() }
        assertEquals(dates.takeLast(22), periodValues(dates, weeks, Period.ONE_MONTH))
        assertEquals(weeks, periodValues(dates, weeks, Period.FIVE_YEARS))
        assertEquals(emptyList<String>(), periodValues(dates, weeks, Period.MAX))
    }

    @Test fun parsePeriod_defaultsToThreeMonths() {
        assertEquals(Period.THREE_MONTHS, parsePeriod(null))
        assertEquals(Period.THREE_MONTHS, parsePeriod("bogus"))
        assertEquals(Period.FIVE_YEARS, parsePeriod("FIVE_YEARS"))
    }

    @Test fun period_labels() {
        assertEquals(listOf("오늘", "1개월", "3개월", "1년", "5년", "MAX"), Period.entries.map { it.label })
        assertEquals("1년 전", Period.ONE_YEAR.agoLabel)
    }

    @Test fun chartValues_todayUsesIntradayAndMaxUsesFullHistory() {
        val quote = Quote(
            "KS11", "코스피", 1.0, 0.0, 0.0, daily = daily, weekly = weekly,
            intraday = listOf(1.0, 2.0), max_closes = listOf(3.0, 4.0),
        )
        assertEquals(listOf(1.0, 2.0), chartValues(quote, Period.TODAY))
        assertEquals(listOf(3.0, 4.0), chartValues(quote, Period.MAX))
        assertEquals(daily, chartValues(quote, Period.ONE_YEAR))
    }

    @Test fun chartValues_oldCacheHasNoTodayOrMax() {
        val quote = Quote("KS11", "코스피", 1.0, 0.0, 0.0, chart = listOf(1.0, 2.0))
        assertEquals(emptyList<Double>(), chartValues(quote, Period.TODAY))
        assertEquals(emptyList<Double>(), chartValues(quote, Period.MAX))
        assertEquals(listOf(1.0, 2.0), chartValues(quote, Period.THREE_MONTHS))
    }
}
