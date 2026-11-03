package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogScaleTest {
    @Test fun firstValueIsTenToTheZero() {
        val chart = logChartFor(listOf(50.0, 500.0, 5000.0))!!
        assertEquals(listOf(0.0, 1.0, 2.0), chart.values)
    }

    @Test fun ticksArePowersOfTenInsideRange() {
        val chart = logChartFor(listOf(100.0, 1000.0, 12500.0))!!
        assertEquals(listOf(0.0, 1.0, 2.0), chart.axis.ticks)
        assertEquals(0.0, chart.axis.min, 1e-9)
        assertEquals(2.0969, chart.axis.max, 1e-3)
    }

    @Test fun belowStartGetsNegativePower() {
        val chart = logChartFor(listOf(700.0, 60.0, 5000.0))!!
        assertEquals(listOf(-1.0, 0.0), chart.axis.ticks.filter { it < 0.5 })
    }

    @Test fun narrowRangeUsesFractionalPowers() {
        // 원/달러처럼 10배가 안 되는 값
        val chart = logChartFor(listOf(1000.0, 900.0, 1700.0))!!
        assertTrue(chart.axis.ticks.size in 2..MAX_TICKS_FOR_TEST)
        assertTrue(0.0 in chart.axis.ticks)
    }

    @Test fun nullForNonPositiveOrTooFewValues() {
        assertNull(logChartFor(listOf(100.0, -37.63, 50.0)))
        assertNull(logChartFor(listOf(100.0, 0.0)))
        assertNull(logChartFor(listOf(100.0)))
    }

    @Test fun flatSeriesKeepsOneTick() {
        assertEquals(listOf(0.0), logChartFor(listOf(5.0, 5.0))!!.axis.ticks)
    }

    @Test fun formatsPowers() {
        assertEquals("10⁰", formatPowerOfTen(0.0))
        assertEquals("10¹", formatPowerOfTen(1.0))
        assertEquals("10²", formatPowerOfTen(2.0))
        assertEquals("10¹²", formatPowerOfTen(12.0))
        assertEquals("10⁻¹", formatPowerOfTen(-1.0))
        assertEquals("10^0.2", formatPowerOfTen(0.2))
        assertEquals("10^-0.05", formatPowerOfTen(-0.05))
    }

    @Test fun onlyFiveYearsAndMaxSupportLog() {
        assertTrue(Period.FIVE_YEARS.supportsLog)
        assertTrue(Period.MAX.supportsLog)
        assertFalse(Period.TODAY.supportsLog)
        assertFalse(Period.ONE_YEAR.supportsLog)
    }

    private companion object {
        const val MAX_TICKS_FOR_TEST = 5
    }
}
