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

    @Test fun chartSummary_minMaxAndPeriodChange() {
        val summary = chartSummary(listOf(100.0, 90.0, 120.0, 110.0))!!
        assertEquals(90.0, summary.min, 0.0)
        assertEquals(120.0, summary.max, 0.0)
        assertEquals(10.0, summary.changePct, 1e-9)
    }

    @Test fun chartSummary_nullForTooFewValues() {
        assertNull(chartSummary(listOf(1.0)))
        assertNull(chartSummary(emptyList()))
    }

    @Test fun toggleSelection_selectsAndClears() {
        assertEquals("KS11", toggleSelection(current = null, tapped = "KS11"))
        assertEquals("BTC", toggleSelection(current = "KS11", tapped = "BTC"))
        assertNull(toggleSelection(current = "KS11", tapped = "KS11"))
    }
}
