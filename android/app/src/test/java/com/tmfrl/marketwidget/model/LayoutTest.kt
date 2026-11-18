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

    @Test fun topPartHeight_addsEventLine() {
        assertEquals(248f, topPartHeightDp(2, eventLine = true), 0f)
        assertEquals(238f, detailMinHeightDp(2, eventLine = true), 0f)
    }

    @Test fun widgetLayout_noEventsKeepsOldHeights() {
        assertEquals(WidgetLayout(showEventLine = false, showDetail = true, detailHeightDp = 170f), widgetLayout(400f, 2, hasEvents = false))
        assertEquals(true, widgetLayout(220f, 2, hasEvents = false).showDetail)
        assertEquals(false, widgetLayout(219f, 2, hasEvents = false).showDetail)
    }

    @Test fun widgetLayout_eventLineTakesHeightFromChart() {
        assertEquals(WidgetLayout(showEventLine = true, showDetail = true, detailHeightDp = 152f), widgetLayout(400f, 2, hasEvents = true))
    }

    @Test fun widgetLayout_detailHeightNeverBelowMinimum() {
        assertEquals(60f, widgetLayout(238f, 2, hasEvents = true).detailHeightDp, 0f)
    }

    @Test fun widgetLayout_chartThresholdStaysV13WithEvents() {
        for (rows in 1..4) {
            val threshold = detailMinHeightDp(rows)
            assertEquals(true, widgetLayout(threshold, rows, hasEvents = true).showDetail)
            assertEquals(false, widgetLayout(threshold - 1f, rows, hasEvents = true).showDetail)
        }
    }

    @Test fun widgetLayout_eventsNeverChangeChartVisibility() {
        for (rows in 1..4) for (height in 100..600) {
            assertEquals(
                widgetLayout(height.toFloat(), rows, hasEvents = false).showDetail,
                widgetLayout(height.toFloat(), rows, hasEvents = true).showDetail,
            )
        }
    }

    @Test fun widgetLayout_boundaryBandKeepsChartAndHidesLine() {
        // QA 재현 크기: 타일 3줄, 높이 310dp. v1.3에서 작은 차트가 보이던 구간이다
        assertEquals(WidgetLayout(showEventLine = false, showDetail = true, detailHeightDp = 60f), widgetLayout(310f, 3, hasEvents = true))
        assertEquals(WidgetLayout(showEventLine = false, showDetail = true, detailHeightDp = 60f), widgetLayout(322f, 3, hasEvents = true))
        assertEquals(true, widgetLayout(323f, 3, hasEvents = true).showEventLine)
        assertEquals(false, widgetLayout(237f, 2, hasEvents = true).showEventLine)
        assertEquals(true, widgetLayout(237f, 2, hasEvents = true).showDetail)
    }

    @Test fun widgetLayout_lineOnlyBelowChartThresholdWhenTilesFit() {
        val layout = widgetLayout(300f, 3, hasEvents = true)
        assertEquals(true, layout.showEventLine)
        assertEquals(false, layout.showDetail)
        assertEquals(false, widgetLayout(294f, 3, hasEvents = true).showEventLine)
    }

    @Test fun widgetLayout_hidesLineWhenTilesWouldBeCut() {
        val layout = widgetLayout(219f, 2, hasEvents = true)
        assertEquals(false, layout.showEventLine)
        assertEquals(false, layout.showDetail)
        assertEquals(false, widgetLayout(110f, 1, hasEvents = true).showEventLine)
    }

    @Test fun widgetLayout_oneRowKeepsChartWhenLineDoesNotFit() {
        // 한 줄 타일은 차트 기준이 135dp이고, 줄은 153dp부터 함께 놓인다
        val layout = widgetLayout(140f, 1, hasEvents = true)
        assertEquals(false, layout.showEventLine)
        assertEquals(true, layout.showDetail)
        assertEquals(false, widgetLayout(152f, 1, hasEvents = true).showEventLine)
        assertEquals(true, widgetLayout(153f, 1, hasEvents = true).showEventLine)
    }

    @Test fun widgetLayoutFor_usesRowsFromWidth() {
        assertEquals(widgetLayout(310f, 3, hasEvents = true), widgetLayoutFor(480f, 310f, 11, hasEvents = true))
        assertEquals(widgetLayout(310f, 2, hasEvents = true), widgetLayoutFor(712f, 310f, 11, hasEvents = true))
    }

    @Test fun orientedSize_followsGlanceExactRule() {
        assertEquals(800f to 300f, orientedWidgetSizeDp(landscape = true, minWidth = 500, maxWidth = 800, minHeight = 300, maxHeight = 520))
        assertEquals(500f to 520f, orientedWidgetSizeDp(landscape = false, minWidth = 500, maxWidth = 800, minHeight = 300, maxHeight = 520))
    }

    @Test fun orientedSize_unknownIsNull() {
        assertEquals(null, orientedWidgetSizeDp(landscape = true, minWidth = 0, maxWidth = 0, minHeight = 0, maxHeight = 0))
    }

    @Test fun eventListRows_staysWithinGlanceChildLimit() {
        assertEquals(2, eventListRows(availableDp = 30f, rowDp = 24f))
        assertEquals(5, eventListRows(availableDp = 120f, rowDp = 24f))
        assertEquals(8, eventListRows(availableDp = 960f, rowDp = 24f))
    }
}
