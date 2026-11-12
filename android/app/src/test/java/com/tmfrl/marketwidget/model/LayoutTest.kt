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

    @Test fun tileGrid_v10FixedExpectations() {
        assertEquals(TileGrid(4, 1), tileGrid(1300f, 1))
        assertEquals(TileGrid(4, 1), tileGrid(1300f, 3))
        assertEquals(TileGrid(5, 1), tileGrid(1300f, 5))
        assertEquals(TileGrid(4, 2), tileGrid(1300f, 8))
        assertEquals(TileGrid(5, 3), tileGrid(1300f, 15))
        assertEquals(TileGrid(7, 3), tileGrid(1300f, 21))
        assertEquals(TileGrid(5, 5), tileGrid(500f, 21))
        assertEquals(TileGrid(4, 6), tileGrid(400f, 21))
        assertEquals(TileGrid(4, 1), tileGrid(400f, 2))
    }

    @Test fun sparkPixels_shareBudgetAcrossTiles() {
        assertEquals(258 to 29, fitBitmap(316, 36, sparkMaxPixels(21)))
        assertEquals(316 to 36, fitBitmap(316, 36, sparkMaxPixels(13)))
        assertEquals(160_000, sparkMaxPixels(0))
    }

    @Test fun commonGutter_widestPlusMargin() {
        assertEquals(48f, commonGutter(listOf(30f, 42f), 10f), 0.001f)
        assertEquals(6f, commonGutter(emptyList(), 10f), 0.001f)
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
