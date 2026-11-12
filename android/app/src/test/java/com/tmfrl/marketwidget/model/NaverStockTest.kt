package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZonedDateTime

/** 설계서 4.2절. 2026-11-12 실측: KRX 종가 208,000(15:30 봉), NXT 20:00 값 206,500, 전일 KRX 종가 206,000. */
class NaverStockTest {
    private fun seoul(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, SEOUL)

    private val basic = """{"stockName": "삼성전자", "closePrice": "206,500", "compareToPreviousClosePrice": "500",
        "fluctuationsRatio": "0.24", "marketStatus": "CLOSE"}"""

    private fun bar(time: String, price: String) = """{"localDateTime": "$time", "currentPrice": $price, "openPrice": 1.0}"""

    /** 09:00~09:06 일곱 봉, 15:30 KRX 종가 봉, 그 뒤 NXT 봉들. */
    private fun minutes(day: String = "20261112") = (
        (0..6).map { bar("${day}09%02d00".format(it), "${207000 + it * 100}.0") } +
            listOf(bar("${day}153000", "208000.0"), bar("${day}153100", "206500.0"), bar("${day}195900", "206500.0"))
        ).joinToString(",", "[", "]")

    @Test fun afterCloseUsesKrxCloseNotNxt() {
        val live = naverStockQuote(basic, minutes(), seoul(12, 15, 45))!!
        assertEquals(208000.0, live.price, 0.0)
        assertEquals(206000.0, live.prevClose, 0.0)
        assertEquals(2000.0, live.change, 0.0)
        assertEquals(0.97, live.changePct, 0.0)
    }

    @Test fun sessionIsNineToHalfPastThree() {
        val live = naverStockQuote(basic, minutes(), seoul(12, 15, 45))!!
        assertEquals(seoul(12, 9).toEpochSecond(), live.sessionStart)
        assertEquals(seoul(12, 15, 30).toEpochSecond(), live.sessionEnd)
        // 정규장 봉 8개 중 0, 5번째와 마지막(15:30)
        assertEquals(listOf(207000.0, 207500.0, 208000.0), live.intraday)
        assertEquals(seoul(12, 15, 30).toEpochSecond(), live.intradayTimes.last())
    }

    @Test fun eveningGoesToYahoo() {
        assertNull(naverStockQuote(basic, minutes(), seoul(12, 20, 20)))
        assertNull(naverStockQuote(basic, minutes(), seoul(12, 16, 0)))
        assertNull(naverStockQuote(basic, minutes(), seoul(12, 8, 59)))
    }

    @Test fun weekendGoesToYahoo() {
        // 2026-11-14는 토요일
        assertNull(naverStockQuote(basic, minutes("20261114"), seoul(14, 10)))
    }

    @Test fun yesterdaysBarsGoToYahoo() {
        assertNull(naverStockQuote(basic, minutes("20261111"), seoul(12, 9, 30)))
    }

    @Test fun duringSessionUsesLatestRegularBar() {
        val text = listOf(bar("20261112090000", "207000.0"), bar("20261112100000", "209000.0")).joinToString(",", "[", "]")
        val live = naverStockQuote(basic, text, seoul(12, 10, 1))!!
        assertEquals(209000.0, live.price, 0.0)
        assertEquals(3000.0, live.change, 0.0)
    }

    @Test fun missingBasicValuesGoToYahoo() {
        assertNull(naverStockQuote("""{"closePrice": "206,500"}""", minutes(), seoul(12, 15, 45)))
        assertNull(naverStockQuote(basic, "[]", seoul(12, 15, 45)))
    }

    @Test fun negativeCompareIsSigned() {
        val down = """{"closePrice": "170,000", "compareToPreviousClosePrice": "-36,000"}"""
        val live = naverStockQuote(down, minutes(), seoul(12, 15, 45))!!
        assertEquals(206000.0, live.prevClose, 0.0)
    }

    @Test fun window_weekdayNineToFour() {
        assertEquals(true, naverStockWindow(seoul(12, 9)))
        assertEquals(true, naverStockWindow(seoul(12, 15, 59)))
        assertEquals(false, naverStockWindow(seoul(12, 16)))
        assertEquals(false, naverStockWindow(seoul(14, 10)))
    }
}
