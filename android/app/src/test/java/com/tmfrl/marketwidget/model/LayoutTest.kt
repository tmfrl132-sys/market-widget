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

    @Test fun tileGrid_thirteenQuotesByWidth() {
        assertEquals(TileGrid(7, 2), tileGrid(1300f, 13))
        assertEquals(TileGrid(7, 2), tileGrid(712f, 13))
        assertEquals(TileGrid(5, 3), tileGrid(691f, 13))
        assertEquals(TileGrid(5, 3), tileGrid(500f, 13))
        assertEquals(TileGrid(4, 4), tileGrid(400f, 13))
    }

    @Test fun tileGrid_tenQuotesKeepFiveAndFive() {
        // 수집기 배포 전(10개)에는 폭 500dp 이상이면 v0.8.0과 같은 5·5다
        assertEquals(TileGrid(5, 2), tileGrid(712f, 10))
        assertEquals(TileGrid(5, 2), tileGrid(500f, 10))
        assertEquals(TileGrid(4, 3), tileGrid(400f, 10))
    }

    @Test fun tileGrid_noQuotesHasNoRows() {
        assertEquals(0, tileGrid(712f, 0).rows)
    }

    @Test fun topPartHeight_twoRowsMatchV080() {
        assertEquals(230f, topPartHeightDp(2), 0f)
        assertEquals(220f, detailMinHeightDp(2), 0f)
        assertEquals(315f, topPartHeightDp(3), 0f)
        assertEquals(305f, detailMinHeightDp(3), 0f)
        assertEquals(400f, topPartHeightDp(4), 0f)
    }
}
