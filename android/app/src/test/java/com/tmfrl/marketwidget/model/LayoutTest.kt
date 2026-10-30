package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutTest {
    @Test fun eventRows_fillAvailableHeight() {
        assertEquals(10, eventRows(availableDp = 240f, rowDp = 24f, min = 3))
        assertEquals(3, eventRows(availableDp = 30f, rowDp = 24f, min = 3))
    }

    @Test fun fitBitmap_keepsSizeWhenSmall() {
        assertEquals(600 to 300, fitBitmap(600, 300, maxPixels = 400_000))
    }

    @Test fun fitBitmap_scalesDownUniformlyToPixelBudget() {
        val (w, h) = fitBitmap(1200, 900, maxPixels = 270_000)
        assertEquals(600, w)
        assertEquals(450, h)
    }
}
