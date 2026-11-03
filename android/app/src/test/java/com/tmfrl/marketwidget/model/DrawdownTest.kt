package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun format_drawdownAndAxis() {
        assertEquals("-38.0%", formatDrawdown(-38.04))
        assertEquals("0.0%", formatDrawdown(-0.04))
        assertEquals("0.0%", formatDrawdown(0.0))
        assertEquals("-20%", formatAxisPct(-20.0))
        assertEquals("0%", formatAxisPct(0.0))
        assertEquals("-2.5%", formatAxisPct(-2.5))
    }
}
