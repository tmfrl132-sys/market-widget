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

    @Test fun parsePeriod_defaultsToThreeMonths() {
        assertEquals(Period.THREE_MONTHS, parsePeriod(null))
        assertEquals(Period.THREE_MONTHS, parsePeriod("bogus"))
        assertEquals(Period.FIVE_YEARS, parsePeriod("FIVE_YEARS"))
    }

    @Test fun period_labels() {
        assertEquals(listOf("1개월", "3개월", "1년", "5년"), Period.entries.map { it.label })
        assertEquals("1년 전", Period.ONE_YEAR.agoLabel)
    }
}
