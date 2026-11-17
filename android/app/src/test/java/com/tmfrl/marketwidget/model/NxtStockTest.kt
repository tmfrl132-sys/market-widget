package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/**
 * v1.3 국내주식 NXT 시간. 실제 자료는 2026-11-17 23:04 서울(NXT 애프터마켓 마감 뒤) 삼성전자 응답이다.
 * - naver_basic_005930: m.stock.naver.com/api/stock/005930/basic (overMarketPriceInfo: AFTER_MARKET, 189,800, -7,000, 20:00)
 * - naver_minute_005930: api.stock.naver.com/chart/domestic/item/005930/minute (KRX 09:00~15:30, 그 뒤 15:40~19:59 봉)
 * - naver_nxt_minute_005930: api.stock.naver.com/chart/domestic/NXT/item/005930/minute (NXT 08:00~19:59, 15:20~15:39 없음)
 * - naver_basic_069500: KODEX 200(NXT 비거래), overMarketPriceInfo 없음
 * 기대값은 파이썬으로 같은 규칙을 따로 계산했다(프리 50봉 + KRX 381봉 + 애프터 260봉 = 691봉, 5개마다 줄여 139개).
 */
class NxtStockTest {
    private fun resource(name: String) = NxtStockTest::class.java.classLoader!!.getResource(name)!!.readText()

    private fun seoul(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, SEOUL)

    private val basic = resource("naver_basic_005930_20261117_2304.json")
    private val krx = resource("naver_minute_005930_20261117.json")
    private val nxt = resource("naver_nxt_minute_005930_20261117.json")

    private val now = seoul(17, 23, 4)

    @Test fun real_afterMarketUsesNxtPriceAndKrxPrevClose() {
        val live = nxtStockQuote(basic, krx, nxt, now)!!
        // NXT 19:59 봉 값. 기준은 KRX 전일종가 196,800(= 189,800 − (−7,000))
        assertEquals(189800.0, live.price, 0.0)
        assertEquals(196800.0, live.prevClose, 0.0)
        assertEquals(-7000.0, live.change, 0.0)
        assertEquals(-3.56, live.changePct, 0.0)
    }

    @Test fun real_sessionIsEightToEightAndBarsJoinNxtKrxNxt() {
        val live = nxtStockQuote(basic, krx, nxt, now)!!
        assertEquals(seoul(17, 8).toEpochSecond(), live.sessionStart)
        assertEquals(seoul(17, 20).toEpochSecond(), live.sessionEnd)
        assertEquals(139, live.intraday.size)
        assertEquals(live.intraday.size, live.intradayTimes.size)
        // 첫 점은 NXT 프리마켓 08:00 봉, 마지막은 NXT 19:59 봉
        assertEquals(seoul(17, 8).toEpochSecond(), live.intradayTimes.first())
        assertEquals(191000.0, live.intraday.first(), 0.0)
        assertEquals(seoul(17, 19, 59).toEpochSecond(), live.intradayTimes.last())
        assertEquals(189800.0, live.intraday.last(), 0.0)
        // 15:30 KRX 종가 봉(NXT에는 없는 봉)이 들어 있다
        val close = live.intradayTimes.indexOf(seoul(17, 15, 30).toEpochSecond())
        assertEquals(86, close)
        assertEquals(190300.0, live.intraday[close], 0.0)
        // 프리마켓 봉은 08:50 전에 끝나고 09:00 봉부터 KRX다
        assertTrue(live.intradayTimes.none { it > seoul(17, 8, 50).toEpochSecond() && it < seoul(17, 9).toEpochSecond() })
        assertEquals(seoul(17, 9).toEpochSecond(), live.intradayTimes[10])
    }

    @Test fun real_weekendStillShowsLastNxtDay() {
        // 서울 시간 창을 따지지 않는다. 토요일에도 금요일(여기서는 11/17) NXT 마지막 값이다.
        val live = nxtStockQuote(basic, krx, nxt, seoul(21, 10))!!
        assertEquals(189800.0, live.price, 0.0)
        assertEquals(seoul(17, 20).toEpochSecond(), live.sessionEnd)
    }

    @Test fun real_noNxtBarsBehavesLikeOff() {
        // NXT 비거래 종목은 NXT 분봉이 빈 목록이다. 꺼진 것과 같다(23:04는 네이버 창 밖이라 null → Yahoo).
        assertNull(nxtStockQuote(basic, krx, "[]", now))
        assertEquals(naverStockQuote(basic, krx, now), nxtStockQuote(basic, krx, "[]", now))
        // 받은 NXT 분봉이 깨졌어도 꺼진 것과 같다
        assertNull(nxtStockQuote(basic, krx, "<html>", now))
    }

    @Test fun real_basicWithoutOverMarketInfoBehavesLikeOff() {
        val etf = resource("naver_basic_069500_20261117_2304.json")
        assertNull(nxtStockQuote(etf, krx, nxt, now))
        // overMarketPriceInfo가 null이어도 같다
        assertNull(nxtStockQuote("""{"closePrice": "189,800", "compareToPreviousClosePrice": "-7,000", "overMarketPriceInfo": null}""", krx, nxt, now))
    }

    @Test fun real_overMarketInfoFromOtherDayBehavesLikeOff() {
        val yesterday = basic.replace("\"localTradedAt\":\"2026-11-17T20:00:00+09:00\"", "\"localTradedAt\":\"2026-11-16T20:00:00+09:00\"")
        assertTrue(yesterday != basic)
        assertNull(nxtStockQuote(yesterday, krx, nxt, now))
    }

    // ---- 만든 자료: 프리마켓, 정규장 ----

    private fun bar(time: String, price: Double) = """{"localDateTime": "$time", "currentPrice": $price, "openPrice": 1.0}"""

    private fun list(vararg bars: String) = bars.joinToString(",", "[", "]")

    private fun overBasic(type: String, price: String, compare: String, tradedAt: String) =
        """{"closePrice": "189,800", "compareToPreviousClosePrice": "-7,000",
           "overMarketPriceInfo": {"tradingSessionType": "$type", "overMarketStatus": "OPEN", "overPrice": "$price",
           "compareToPreviousClosePrice": "$compare", "localTradedAt": "$tradedAt"}}"""

    @Test fun preMarket_usesTodaysNxtBarsAndYesterdaysKrxClose() {
        // 11/18 08:30. KRX 분봉은 아직 어제(11/17) 것이고 NXT는 오늘 프리마켓 봉이다. 기준 = 191,000 − 700 = 190,300(어제 KRX 종가)
        val basic = overBasic("PRE_MARKET", "191,000", "700", "2026-11-18T08:29:00+09:00")
        val nxtBars = list(bar("20261117195900", 189800.0), bar("20261118080000", 190500.0), bar("20261118082900", 191000.0))
        val live = nxtStockQuote(basic, krx, nxtBars, seoul(18, 8, 30))!!
        assertEquals(191000.0, live.price, 0.0)
        assertEquals(190300.0, live.prevClose, 0.0)
        assertEquals(700.0, live.change, 0.0)
        assertEquals(0.37, live.changePct, 0.0)
        // 어제 봉은 쓰지 않는다
        assertEquals(listOf(190500.0, 191000.0), live.intraday)
        assertEquals(seoul(18, 8).toEpochSecond(), live.sessionStart)
        assertEquals(seoul(18, 20).toEpochSecond(), live.sessionEnd)
    }

    @Test fun regularSession_krxBarsReplaceNxtBetweenNineAndHalfPastThree() {
        val basic = overBasic("PRE_MARKET", "191,000", "700", "2026-11-18T08:50:00+09:00")
        // NXT: 프리 08:00 + 정규장 09:00~09:04(999, 쓰지 않음). KRX: 09:00~09:04
        val nxtBars = list(bar("20261118080000", 190500.0), *(0..4).map { bar("2026111809%02d00".format(it), 999.0) }.toTypedArray())
        val krxBars = list(*(0..4).map { bar("2026111809%02d00".format(it), 191500.0 + it * 100) }.toTypedArray())
        val live = nxtStockQuote(basic, krxBars, nxtBars, seoul(18, 9, 5))!!
        // 이은 봉 6개(프리 1 + KRX 5)를 5개마다 줄이면 첫 봉과 마지막 봉. NXT 정규장 봉이 섞였다면 11개라 3개가 남는다.
        assertEquals(listOf(190500.0, 191900.0), live.intraday)
        assertEquals(listOf(seoul(18, 8).toEpochSecond(), seoul(18, 9, 4).toEpochSecond()), live.intradayTimes)
        assertEquals(191900.0, live.price, 0.0)
        assertEquals(1600.0, live.change, 0.0)
    }

    @Test fun afterMarket_nxtBarsAfterKrxCloseAndNotPastEight() {
        val basic = overBasic("AFTER_MARKET", "190,000", "-300", "2026-11-18T16:10:00+09:00")
        // NXT 15:29(정규장 시간, 쓰지 않음), 15:40~16:10. KRX 09:00, 15:30 종가, 15:40(KRX 분봉의 장 뒤 봉, 쓰지 않음)
        val nxtBars = list(
            bar("20261118152900", 999.0), bar("20261118154000", 190200.0), bar("20261118155000", 190150.0),
            bar("20261118160000", 190050.0), bar("20261118161000", 190000.0),
        )
        val krxBars = list(bar("20261118090000", 190500.0), bar("20261118153000", 190100.0), bar("20261118154000", 888.0))
        val live = nxtStockQuote(basic, krxBars, nxtBars, seoul(18, 16, 11))!!
        // 이은 봉 6개(KRX 2 + NXT 4) → 첫 봉과 마지막 봉. 쓰지 않을 봉이 섞였다면 7개 이상이라 3개가 남는다.
        assertEquals(listOf(190500.0, 190000.0), live.intraday)
        assertEquals(190000.0, live.price, 0.0)
        assertEquals(190300.0, live.prevClose, 0.0)
        assertEquals(-0.16, live.changePct, 0.0)
    }
}
