package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartTest {
    @Test fun chartPoints_spreadsXAndInvertsY() {
        val points = chartPoints(listOf(10.0, 20.0, 15.0), width = 100f, height = 50f, pad = 0f)
        assertEquals(listOf(0f to 50f, 50f to 0f, 100f to 25f), points)
    }

    @Test fun chartPoints_keepsPadding() {
        val points = chartPoints(listOf(1.0, 2.0), width = 100f, height = 50f, pad = 5f)
        assertEquals(listOf(5f to 45f, 95f to 5f), points)
    }

    @Test fun chartPoints_flatSeriesSitsInMiddle() {
        val points = chartPoints(listOf(3.0, 3.0, 3.0), width = 100f, height = 50f, pad = 0f)
        assertEquals(listOf(25f, 25f, 25f), points.map { it.second })
    }

    @Test fun chartPoints_needsTwoValues() {
        assertEquals(emptyList<Pair<Float, Float>>(), chartPoints(listOf(1.0), 100f, 50f, 0f))
    }

    @Test fun chartPoints_usesAxisRangeWhenGiven() {
        val axis = YAxis(0.0, 100.0, listOf(0.0, 100.0))
        val points = chartPoints(listOf(25.0, 50.0), width = 100f, height = 100f, pad = 0f, axis = axis)
        assertEquals(listOf(0f to 75f, 100f to 50f), points)
    }

    @Test fun yAxis_roundTicksCoverValues() {
        val axis = yAxisFor(listOf(5859.08, 5700.0, 5648.79))!!
        assertEquals(listOf(5600.0, 5700.0, 5800.0, 5900.0), axis.ticks)
        assertEquals(5600.0, axis.min, 0.0)
        assertEquals(5900.0, axis.max, 0.0)
    }

    @Test fun yAxis_smallValuesKeepCleanFractions() {
        assertEquals(listOf(4.0, 4.25, 4.5, 4.75, 5.0), yAxisFor(listOf(4.2, 4.9))!!.ticks)
    }

    @Test fun yAxis_flatSeriesGetsRange() {
        assertEquals(listOf(99.0, 100.0, 101.0), yAxisFor(listOf(100.0, 100.0))!!.ticks)
    }

    @Test fun yAxis_alwaysThreeToFiveTicksAroundValues() {
        val samples = listOf(
            listOf(0.8, 1.3), listOf(20.8, 31.5), listOf(1325.5, 1401.0),
            listOf(61000.0, 124000.0), listOf(3969.6, 4210.0), listOf(5000.0, 5650.0),
        )
        samples.forEach { values ->
            val axis = yAxisFor(values)!!
            assertEquals("$values ${axis.ticks}", true, axis.ticks.size in 3..5)
            assertEquals("$values ${axis.ticks}", true, axis.min <= values.min() && axis.max >= values.max())
        }
    }

    @Test fun yAxis_nullWithoutValues() {
        assertNull(yAxisFor(emptyList()))
    }

    @Test fun chartSummary_minMaxAndPeriodChange() {
        val summary = chartSummary(listOf(100.0, 90.0, 120.0, 110.0))!!
        assertEquals(90.0, summary.min, 0.0)
        assertEquals(120.0, summary.max, 0.0)
        assertEquals(10.0, summary.change, 1e-9)
        assertEquals(10.0, summary.changePct, 1e-9)
    }

    @Test fun formatSignedAmount_signAndDigits() {
        assertEquals("-211", formatSignedAmount(-210.5))
        assertEquals("+1,250", formatSignedAmount(1250.0))
        assertEquals("-0.42", formatSignedAmount(-0.42))
        assertEquals("0.00", formatSignedAmount(0.0))
    }

    @Test fun chartSummary_nullForTooFewValues() {
        assertNull(chartSummary(listOf(1.0)))
        assertNull(chartSummary(emptyList()))
    }

    @Test fun formatChangeBadge_arrowAndAbsoluteValue() {
        assertEquals("▲0.47%", formatChangeBadge(0.47))
        assertEquals("▼1.20%", formatChangeBadge(-1.2))
        assertEquals("0.00%", formatChangeBadge(0.0))
        assertEquals("-", formatChangeBadge(null))
    }

    @Test fun formatDday_todayTomorrowAndCount() {
        val today = java.time.LocalDate.of(2026, 10, 31)
        assertEquals("오늘", formatDday(today, today))
        assertEquals("내일", formatDday(today.plusDays(1), today))
        assertEquals("D-6", formatDday(today.plusDays(6), today))
    }

    @Test fun flagOf_country() {
        assertEquals("🇺🇸", flagOf("US"))
        assertEquals("🇰🇷", flagOf("KR"))
        assertEquals("", flagOf("JP"))
    }

    @Test fun toggleSelection_selectsAndClears() {
        assertEquals("KS11", toggleSelection(current = null, tapped = "KS11"))
        assertEquals("BTC", toggleSelection(current = "KS11", tapped = "BTC"))
        assertNull(toggleSelection(current = "KS11", tapped = "KS11"))
    }
}
