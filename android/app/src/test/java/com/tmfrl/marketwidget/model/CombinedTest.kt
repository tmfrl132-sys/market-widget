package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.round

/**
 * 합친 24시간 차트 고정 기대값(설계서 v1.1).
 * 실제 자료는 2026-11-13 23:05 서울(뉴욕 금 09:05, 개장 전) Yahoo range=5d interval=5m 응답을 줄인 것이다.
 * 기대값은 같은 규칙을 따로 짠 파이썬 계산으로 구했다.
 */
class CombinedTest {

    // ---- 도우미 ----

    private fun ny(date: String, time: String): Long = LocalDateTime.parse("${date}T$time").atZone(NEW_YORK).toEpochSecond()

    private val seoulFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    private fun seoul(epoch: Long): String = Instant.ofEpochSecond(epoch).atZone(SEOUL).format(seoulFormat)

    private fun session(date: String, from: String = "09:30", to: String = "16:00") = TradingSpan(ny(date, from), ny(date, to))

    /** from~to(포함) 5분 간격 시각. */
    private fun slots(date: String, from: String, to: String): List<Long> =
        generateSequence(ny(date, from)) { it + 300 }.takeWhile { it <= ny(date, to) }.toList()

    private fun spotBars(date: String, from: String, to: String, value: (Int) -> Double) =
        slots(date, from, to).mapIndexed { i, t -> SpotBar(t, value(i)) }

    private fun futuresBars(date: String, from: String, to: String, value: (Int) -> Double) =
        slots(date, from, to).mapIndexed { i, t -> FuturesBar(t, value(i), value(i)) }

    private fun valueAt(chart: CombinedChart, time: Long): Double = chart.values[chart.times.indexOf(time)]

    private fun resource(name: String) = CombinedTest::class.java.classLoader!!.getResource(name)!!.readText()

    // ---- 명세 예시 ----

    @Test fun specExample_7592times7640over7616() {
        assertEquals(7615.92, round(7592.0 * 7640 / 7616 * 100) / 100, 0.0)
        val spot = SpotSeries(
            listOf(SpotBar(ny("2026-11-12", "15:50"), 7590.0), SpotBar(ny("2026-11-12", "15:55"), 7591.0), SpotBar(ny("2026-11-12", "16:00"), 7592.0)),
            listOf(session("2026-11-12")),
        )
        // 봉 종가는 봉 끝 가격이다. 선물 15:55 봉 종가 7,616이 16:00 가격이고, 현물 16:00 봉(공식 종가 7,592)과 짝이다.
        // 선물 16:00 봉 종가(7,630)는 16:05 가격이라 Fc가 아니다.
        val futures = FuturesSeries(
            listOf(
                FuturesBar(ny("2026-11-12", "15:55"), 7615.0, 7616.0),
                FuturesBar(ny("2026-11-12", "16:00"), 7616.0, 7630.0),
                FuturesBar(ny("2026-11-12", "16:05"), 7630.0, 7640.0),
            ),
        )
        val chart = combine24h(spot, futures, now = ny("2026-11-12", "16:10"))!!
        // 정규장 안 선물 봉(15:55, 16:00)은 버리고 현물을 쓴다
        assertEquals(listOf(7590.0, 7591.0, 7592.0, 7615.92), chart.values)
        assertEquals(listOf(false, false, false, true), chart.converted)
        assertEquals(7592.0, chart.baseline, 0.0)
        assertEquals(7615.92, chart.price, 0.0)
        assertEquals(23.92, chart.change, 0.0)
        assertEquals(0.32, chart.change_pct, 0.0)
        assertEquals(7590.0, chart.low, 0.0)
        assertEquals(7615.92, chart.high, 0.0)
        assertEquals(emptyList<Int>(), chart.breaks)
        assertEquals(listOf(session("2026-11-12")), chart.sessions)
        assertTrue(chart.lastConverted)
    }

    // ---- 장 마감 짝: 같은 가격 시각 ----

    private val closingPrint = SpotSeries(
        listOf(SpotBar(ny("2026-11-12", "15:50"), 99.0), SpotBar(ny("2026-11-12", "15:55"), 99.5), SpotBar(ny("2026-11-12", "16:00"), 100.0)),
        listOf(session("2026-11-12")),
    )

    @Test fun closingPrintBar_pairsWithFuturesBarBeforeIt() {
        val futures = FuturesSeries(
            listOf(
                FuturesBar(ny("2026-11-12", "15:55"), 199.0, 200.0),
                FuturesBar(ny("2026-11-12", "16:00"), 200.0, 210.0),
                FuturesBar(ny("2026-11-12", "16:05"), 210.0, 210.0),
            ),
        )
        // Fc = 15:55 봉 종가 200(16:00 가격). 16:00 봉 종가 210(16:05 가격)을 쓰면 100.0이 되어 16:00~16:05 움직임이 사라진다.
        assertEquals(105.0, combine24h(closingPrint, futures, ny("2026-11-12", "16:10"))!!.price, 0.0)
    }

    @Test fun closingPrintBar_withoutBarBeforeUsesOpenOfSameBar() {
        val futures = FuturesSeries(
            listOf(FuturesBar(ny("2026-11-12", "16:00"), 200.0, 210.0), FuturesBar(ny("2026-11-12", "16:05"), 210.0, 202.0)),
        )
        assertEquals(101.0, combine24h(closingPrint, futures, ny("2026-11-12", "16:10"))!!.price, 0.0)
    }

    @Test fun closingPrintBar_withoutEitherBarOffHoursNotDrawn() {
        val futures = FuturesSeries(listOf(FuturesBar(ny("2026-11-12", "16:00"), null, 210.0), FuturesBar(ny("2026-11-12", "16:05"), 210.0, 202.0)))
        val chart = combine24h(closingPrint, futures, ny("2026-11-12", "16:10"))!!
        assertTrue(chart.converted.none { it })
        assertEquals(ny("2026-11-12", "16:00"), chart.times.last())
    }

    @Test fun lastBarBeforeSessionEnd_pairsWithSameTimeFuturesBar() {
        // 15:55 현물 봉 종가와 15:55 선물 봉 종가는 둘 다 16:00 가격이다
        val spot = closingPrint.copy(bars = closingPrint.bars.dropLast(1))
        val futures = FuturesSeries(
            listOf(FuturesBar(ny("2026-11-12", "15:50"), 190.0, 190.0), FuturesBar(ny("2026-11-12", "15:55"), 199.0, 199.0), FuturesBar(ny("2026-11-12", "16:05"), 201.0, 201.0)),
        )
        assertEquals(100.5, combine24h(spot, futures, ny("2026-11-12", "16:10"))!!.price, 0.0)
    }

    // ---- 실제 자료 ----

    private fun realChart(spotName: String, futuresName: String, now: Long = REAL_NOW): CombinedChart? =
        combine24h(parseSpotSeries(resource(spotName))!!, parseFuturesSeries(resource(futuresName))!!, now)

    @Test fun real_sp500_preOpenFriday() {
        val chart = realChart("yahoo_5m_5d_gspc_20261113.json", "yahoo_5m_5d_es_20261113.json")!!
        // v1.3 창: 금 09:05 뉴욕 이하 가장 최근 09:30 = 목 09:30(서울 23:30)부터 고정 24시간
        assertEquals(270, chart.values.size)
        assertEquals(ny("2026-11-12", "09:30"), chart.start)
        assertEquals(1794493800L, chart.start)
        assertEquals(1794580200L, chart.end)
        assertEquals(1794493800L, chart.times.first())
        assertEquals(7576.3, chart.values.first(), 0.0)
        assertEquals(1794578107L, chart.times.last())
        // 기준 짝: 현물 16:00 공식 종가 7,592.40 ↔ 선물 15:55 봉 종가 7,616.25(16:00 가격)
        assertEquals(7624.8, chart.price, 0.0)
        assertEquals(7592.4, chart.baseline, 0.0)
        assertEquals(32.4, chart.change, 0.0)
        assertEquals(0.43, chart.change_pct, 0.0)
        assertEquals(7576.3, chart.low, 0.0)
        assertEquals(7649.21, chart.high, 0.0)
        assertEquals(191, chart.converted.count { it })
        assertEquals(listOf(90), chart.breaks)
        assertEquals(listOf(TradingSpan(1794493800L, 1794517200L)), chart.sessions)
        // 목 09:25 환산 점은 창 밖이다. 이음매: 16:00 현물 → 16:05 환산
        assertFalse(ny("2026-11-12", "09:25") in chart.times)
        assertEquals(7576.3, valueAt(chart, ny("2026-11-12", "09:30")), 0.0)
        assertEquals(7592.4, valueAt(chart, ny("2026-11-12", "16:00")), 0.0)
        assertEquals(7596.64, valueAt(chart, ny("2026-11-12", "16:05")), 0.0)
        assertEquals(7597.88, valueAt(chart, ny("2026-11-12", "16:55")), 0.0)
        assertEquals(7603.37, valueAt(chart, ny("2026-11-12", "18:00")), 0.0)
        // 정비 공백(16:55~18:00)에서 끊는다
        assertEquals(ny("2026-11-12", "16:55"), chart.times[89])
        assertEquals(ny("2026-11-12", "18:00"), chart.times[90])
        assertEquals("선물 환산 22:55", convertedLabel(chart))
        assertEquals("23:30~", combinedStartLabel(chart))
    }

    @Test fun real_nasdaq100_preOpenFriday() {
        val chart = realChart("yahoo_5m_5d_ndx_20261113.json", "yahoo_5m_5d_nq_20261113.json")!!
        assertEquals(270, chart.values.size)
        assertEquals(1794493800L, chart.times.first())
        assertEquals(28103.21, chart.values.first(), 0.0)
        // 기준 짝: 현물 16:00 28,289.19 ↔ 선물 15:55 봉 종가 28,388.25
        assertEquals(28333.03, chart.price, 0.0)
        assertEquals(28289.19, chart.baseline, 0.0)
        assertEquals(43.85, chart.change, 0.0)
        assertEquals(0.15, chart.change_pct, 0.0)
        assertEquals(28103.21, chart.low, 0.0)
        assertEquals(28525.78, chart.high, 0.0)
        assertEquals(listOf(90), chart.breaks)
        assertFalse(ny("2026-11-12", "09:25") in chart.times)
        assertEquals(28103.21, valueAt(chart, ny("2026-11-12", "09:30")), 0.0)
        assertEquals(28325.31, valueAt(chart, ny("2026-11-12", "16:05")), 0.0)
        assertEquals(28347.48, valueAt(chart, ny("2026-11-12", "18:00")), 0.0)
    }

    @Test fun real_sameTimeOpenSeamIsSmall() {
        // 같은 시각(09:30) 봉끼리 비교한 개장 이음매 차이는 0.05% 이하다(리더 실측 0.000~0.040%)
        for ((spotName, futuresName) in listOf(
            "yahoo_5m_5d_gspc_20261113.json" to "yahoo_5m_5d_es_20261113.json",
            "yahoo_5m_5d_ndx_20261113.json" to "yahoo_5m_5d_nq_20261113.json",
        )) {
            val spot = parseSpotSeries(resource(spotName))!!.bars.associate { it.time to it.close }
            val futures = parseFuturesSeries(resource(futuresName))!!.bars.associate { it.time to it.close }
            val wedClose = ny("2026-11-11", "15:55")
            val open = ny("2026-11-12", "09:30")
            val synthetic = spot.getValue(wedClose) * futures.getValue(open) / futures.getValue(wedClose)
            assertTrue(abs(spot.getValue(open) / synthetic - 1) * 100 <= 0.05)
        }
    }

    @Test fun real_inSessionBaselineIsPreviousOfficialClose() {
        // 목 12:00(장중)으로 자르면 기준선은 수요일 공식 종가(meta.previousClose 7,619.38)다. 마지막 15:55 봉(7,617.84)이 아니다.
        val now = ny("2026-11-12", "12:00")
        val spot = parseSpotSeries(resource("yahoo_5m_5d_gspc_20261113.json"))!!
        val futures = parseFuturesSeries(resource("yahoo_5m_5d_es_20261113.json"))!!
        val chart = combine24h(
            spot.copy(bars = spot.bars.filter { it.time <= now - 300 }),
            futures.copy(bars = futures.bars.filter { it.time <= now - 300 }),
            now,
        )!!
        // v1.3: 정규장이 열린 목 09:30부터 다시 채운다(09:30~11:55 현물 30개). 오른쪽은 비어 있다.
        assertEquals(30, chart.values.size)
        assertEquals(1794493800L, chart.start)
        assertEquals(1794580200L, chart.end)
        assertEquals(7576.3, chart.values.first(), 0.0)
        assertEquals(7633.37, chart.price, 0.0)
        assertFalse(chart.lastConverted)
        assertNull(convertedLabel(chart))
        assertEquals(7619.38, chart.baseline, 0.0)
        assertEquals(13.99, chart.change, 0.0)
        assertEquals(0.18, chart.change_pct, 0.0)
        assertEquals(7576.3, chart.low, 0.0)
        assertEquals(7649.21, chart.high, 0.0)
        assertEquals(emptyList<Int>(), chart.breaks)
        // 정규장 띠는 창 안의 정해진 정규장 전체(09:30~16:00)다
        assertEquals(listOf(TradingSpan(1794493800L, 1794517200L)), chart.sessions)
        // 전일(수) 16:00 마감은 창 밖이라 장마감 표시가 없다
        assertEquals(0L, chart.close_time)
        assertNull(combinedCloseMark(chart))
    }

    @Test fun real_xAxisEveryFourHoursSeoulAndSessionBand() {
        val chart = realChart("yahoo_5m_5d_gspc_20261113.json", "yahoo_5m_5d_es_20261113.json")!!
        val axis = combinedXAxis(chart)
        // 창이 서울 23:30~다음 날 23:30이라 양 끝이 23:30, 00:00은 시작에 너무 가까워 뺀다
        assertEquals(listOf("23:30", "04:00", "08:00", "12:00", "16:00", "20:00", "23:30"), axis.ticks.map { it.label })
        assertEquals(chart.values.size, axis.positions!!.size)
        assertEquals(0f, axis.positions!!.first(), 1e-6f)
        assertEquals(0.975775f, axis.positions!!.last(), 1e-4f)
        val band = combinedBands(chart).single()
        assertEquals(0f, band.start, 1e-6f)
        assertEquals(0.270833f, band.endInclusive, 1e-4f)
        // 정규장 띠는 서울 23:30~06:00(11월, 미국 표준시)
        assertEquals("11-12 23:30", seoul(chart.sessions.single().start))
        assertEquals("11-13 06:00", seoul(chart.sessions.single().end))
    }

    @Test fun real_doesNotDependOnDeviceTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            val chart = realChart("yahoo_5m_5d_gspc_20261113.json", "yahoo_5m_5d_es_20261113.json")!!
            assertEquals(7624.8, chart.price, 1e-9)
            assertEquals("선물 환산 22:55", convertedLabel(chart))
            assertEquals("23:30", combinedXAxis(chart).ticks.first().label)
            assertEquals("23:30~", combinedStartLabel(chart))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // ---- 서머타임 ----

    @Test fun dstEnd_2026_11_01_sessionMovesFrom2230To2330Seoul() {
        val sessions = newYorkSessions(listOf(ny("2026-10-30", "10:00"), ny("2026-10-30", "15:55"), ny("2026-11-02", "09:30")))
        assertEquals(listOf("10-30 22:30" to "10-31 05:00", "11-02 23:30" to "11-03 06:00"), sessions.map { seoul(it.start) to seoul(it.end) })
    }

    @Test fun dstStart_2026_03_08_sessionMovesFrom2330To2230Seoul() {
        val sessions = newYorkSessions(listOf(ny("2026-03-06", "12:00"), ny("2026-03-09", "12:00")))
        assertEquals(listOf("03-06 23:30" to "03-07 06:00", "03-09 22:30" to "03-10 05:00"), sessions.map { seoul(it.start) to seoul(it.end) })
    }

    @Test fun dstEnd_weekendConvertsWithFridayBase_openWithOneSpotBarUsesPreviousWindow() {
        // 장 시간 목록이 없어 뉴욕 09:30~16:00으로 계산한다. 금요일은 서머타임(EDT), 월요일은 표준시(EST)다.
        val spot = SpotSeries(
            spotBars("2026-10-30", "15:50", "15:55") { 100.0 } + SpotBar(ny("2026-11-02", "09:30"), 103.5),
            previousClose = 100.2,
        )
        val futures = FuturesSeries(
            listOf(
                FuturesBar(ny("2026-10-30", "15:55"), 101.0, 101.0),
                FuturesBar(ny("2026-10-30", "16:05"), 102.0, 102.01),
                FuturesBar(ny("2026-11-01", "18:00"), 103.0, 103.02),
                FuturesBar(ny("2026-11-01", "18:05"), 103.02, 103.02),
                FuturesBar(ny("2026-11-02", "09:25"), 104.0, 104.0),
                FuturesBar(ny("2026-11-02", "09:30"), 104.5, 104.5),
            ),
        )
        val chart = combine24h(spot, futures, now = ny("2026-11-02", "09:37"))!!
        // 월 09:30 창에는 현물 09:30 봉 하나뿐이라 직전 창(일 09:30 표준시 ~ 월 09:30)을 쓴다
        assertEquals(ny("2026-11-01", "09:30"), chart.start)
        assertEquals(chart.start + 86400, chart.end)
        assertEquals(ny("2026-11-02", "09:30"), chart.end)
        assertEquals(listOf(ny("2026-11-01", "18:00"), ny("2026-11-01", "18:05"), ny("2026-11-02", "09:25"), ny("2026-11-02", "09:30")), chart.times)
        assertEquals(listOf(102.0, 102.0, 102.97, 103.5), chart.values)
        assertEquals(listOf(true, true, true, false), chart.converted)
        assertEquals(listOf(2), chart.breaks)
        // 월요일 장중이라 기준선은 금요일 공식 종가(previousClose)
        assertEquals(100.2, chart.baseline, 0.0)
        // 월요일 정규장은 창 끝(월 09:30)에서 시작해 창 안의 띠가 없다
        assertEquals(emptyList<TradingSpan>(), chart.sessions)
        // previousClose가 없으면 금요일 마지막 봉
        assertEquals(100.0, combine24h(spot.copy(previousClose = null), futures, ny("2026-11-02", "09:37"))!!.baseline, 0.0)
    }

    // ---- 정비 공백, 자정 빈 봉 ----

    private val thursdaySpot = SpotSeries(spotBars("2026-11-12", "15:30", "16:00") { 100.0 }, listOf(session("2026-11-12")))

    @Test fun maintenanceGapBreaksLine_midnightTenMinutesDoesNot() {
        val futures = FuturesSeries(
            futuresBars("2026-11-12", "15:30", "16:55") { 200.0 + it } +
                futuresBars("2026-11-12", "18:00", "23:55") { 220.0 } +
                // 실제 자료처럼 00:00 봉이 없다(23:55 → 00:05, 10분)
                futuresBars("2026-11-13", "00:05", "00:15") { 230.0 },
        )
        val chart = combine24h(thursdaySpot, futures, now = ny("2026-11-13", "00:20"))!!
        val gapAt = chart.times.indexOf(ny("2026-11-12", "18:00"))
        assertEquals(listOf(gapAt), chart.breaks)
        assertEquals(ny("2026-11-12", "16:55"), chart.times[gapAt - 1])
        // 정비 시간에는 점이 없다
        assertTrue(chart.times.none { it > ny("2026-11-12", "16:55") && it < ny("2026-11-12", "18:00") })
        // 현물 마지막 봉이 16:00 종가 봉이라 Fc는 선물 15:55 봉 종가(200 + 5)
        assertEquals(107.32, chart.values[gapAt], 0.0)
        // 자정 10분 빈 봉 뒤도 같은 기준(100 × 230 / 205)
        assertEquals(112.2, chart.values.last(), 0.0)
    }

    @Test fun lineSegments_followBreaks() {
        assertEquals(listOf(0 until 5), lineSegments(5, emptyList()))
        assertEquals(listOf(0 until 2, 2 until 5), lineSegments(5, listOf(2)))
        assertEquals(listOf(0 until 1, 1 until 3, 3 until 4), lineSegments(4, listOf(3, 1, 3, 0, 9)))
        assertEquals(emptyList<IntRange>(), lineSegments(0, listOf(1)))
    }

    // ---- 주말 ----

    @Test fun weekendWindowStartsAtFridayOpen() {
        val spot = SpotSeries(
            spotBars("2026-11-12", "15:55", "16:00") { 99.0 } + spotBars("2026-11-13", "09:30", "16:00") { 100.0 },
            listOf(session("2026-11-12"), session("2026-11-13")),
            previousClose = 99.0,
        )
        val futures = FuturesSeries(
            futuresBars("2026-11-12", "15:55", "16:55") { if (it == 0) 198.0 else 199.0 } +
                futuresBars("2026-11-12", "18:00", "23:55") { 200.0 } +
                futuresBars("2026-11-13", "00:05", "16:55") { if (it <= 197) 200.0 else 202.0 },
        )
        val chart = combine24h(spot, futures, now = ny("2026-11-14", "12:00"))!!
        // asOf = 금 17:00(마지막 점 16:55 + 5분). 그 이하 가장 최근 09:30 = 금 09:30부터 고정 24시간
        assertEquals(ny("2026-11-13", "09:30"), chart.start)
        assertEquals(ny("2026-11-14", "09:30"), chart.end)
        // 목요일 장외와 금요일 개장 전 환산은 창 밖이고 금 09:30 현물부터
        assertEquals(ny("2026-11-13", "09:30"), chart.times.first())
        assertEquals(100.0, chart.values.first(), 0.0)
        assertEquals(90, chart.values.size)
        assertEquals(ny("2026-11-13", "16:55"), chart.times.last())
        // 장 마감 뒤라 기준선은 금요일 종가, 마지막 점은 금요일 기준 환산값(100 × 202 / 금 15:55 선물 봉 종가 200)
        assertEquals(100.0, chart.baseline, 0.0)
        assertEquals(101.0, chart.price, 0.0)
        assertEquals(1.0, chart.change_pct, 0.0)
        assertTrue(chart.lastConverted)
        assertEquals(listOf(session("2026-11-13")), chart.sessions)
    }

    // ---- 추수감사절(11/26 현물 휴장, 선물 13:00 단축, 11/27 현물 13:00 마감) ----

    private val thanksgivingSpans = listOf(session("2026-11-25"), session("2026-11-27", "09:30", "13:00"))

    private val thanksgivingFutures =
        futuresBars("2026-11-25", "15:55", "16:00") { 200.0 } +
            futuresBars("2026-11-26", "09:30", "13:00") { if (it == 42) 204.0 else 202.0 } +
            futuresBars("2026-11-26", "18:00", "19:55") { 206.0 }

    @Test fun thanksgiving_holidayUsesWednesdayBaseAndBreaksAtHalt() {
        val spot = SpotSeries(spotBars("2026-11-25", "15:30", "16:00") { 100.0 }, thanksgivingSpans, previousClose = 99.0)
        val chart = combine24h(spot, FuturesSeries(thanksgivingFutures), now = ny("2026-11-26", "20:00"))!!
        assertTrue(chart.converted.all { it })
        assertEquals(102.0, valueAt(chart, ny("2026-11-26", "13:00")), 0.0)
        assertEquals(103.0, chart.price, 0.0)
        assertEquals(listOf(chart.times.indexOf(ny("2026-11-26", "18:00"))), chart.breaks)
        // 휴장일이라 기준선은 수요일 종가
        assertEquals(100.0, chart.baseline, 0.0)
        assertEquals(emptyList<TradingSpan>(), chart.sessions)
    }

    @Test fun thanksgiving_shortFridaySessionEndsAt1300() {
        val spot = SpotSeries(
            spotBars("2026-11-25", "15:30", "16:00") { 100.0 } + spotBars("2026-11-27", "09:30", "13:00") { 104.1 },
            thanksgivingSpans,
            previousClose = 100.0,
        )
        val futures = FuturesSeries(
            thanksgivingFutures +
                futuresBars("2026-11-26", "20:00", "23:55") { 206.0 } +
                futuresBars("2026-11-27", "00:05", "09:25") { 206.4 } +
                futuresBars("2026-11-27", "09:30", "13:00") { 208.2 } +
                futuresBars("2026-11-27", "13:05", "13:10") { 210.0 },
        )
        val chart = combine24h(spot, futures, now = ny("2026-11-27", "13:20"))!!
        assertEquals(ny("2026-11-27", "09:30"), chart.start)
        assertEquals(ny("2026-11-28", "09:30"), chart.end)
        // 개장 전 09:25 환산 점은 창 밖이다
        assertEquals(ny("2026-11-27", "09:30"), chart.times.first())
        assertEquals(104.1, valueAt(chart, ny("2026-11-27", "09:30")), 0.0)
        // 13:00 마감 뒤 13:05는 금요일 기준(104.1 × 210 / 208.2)
        assertEquals(105.0, valueAt(chart, ny("2026-11-27", "13:05")), 0.0)
        assertTrue(chart.converted[chart.times.indexOf(ny("2026-11-27", "13:05"))])
        assertEquals(104.1, chart.baseline, 0.0)
        assertEquals(listOf(TradingSpan(ny("2026-11-27", "09:30"), ny("2026-11-27", "13:00"))), chart.sessions)
    }

    // ---- 같은 시각 선물 봉이 없을 때 ----

    private val closeAt1555 = SpotSeries(spotBars("2026-11-12", "15:45", "15:55") { 100.0 }, listOf(session("2026-11-12")))

    @Test fun missingSameTimeFutures_usesNearestWithinFiveMinutes() {
        val futures = FuturesSeries(listOf(FuturesBar(ny("2026-11-12", "16:00"), 200.0, 200.0), FuturesBar(ny("2026-11-12", "16:05"), 202.0, 202.0)))
        val chart = combine24h(closeAt1555, futures, ny("2026-11-12", "16:10"))!!
        assertEquals(101.0, chart.price, 0.0)
    }

    @Test fun missingSameTimeFutures_tieTakesEarlierBar() {
        val futures = FuturesSeries(
            listOf(
                FuturesBar(ny("2026-11-12", "15:50"), 190.0, 190.0),
                FuturesBar(ny("2026-11-12", "16:00"), 200.0, 200.0),
                FuturesBar(ny("2026-11-12", "16:05"), 209.0, 209.0),
            ),
        )
        assertEquals(110.0, combine24h(closeAt1555, futures, ny("2026-11-12", "16:10"))!!.price, 0.0)
    }

    @Test fun missingFuturesWithinFiveMinutes_offHoursNotDrawn() {
        val futures = FuturesSeries(futuresBars("2026-11-12", "16:05", "16:30") { 202.0 })
        val chart = combine24h(closeAt1555, futures, ny("2026-11-12", "16:40"))!!
        assertTrue(chart.converted.none { it })
        assertEquals(ny("2026-11-12", "15:55"), chart.times.last())
    }

    // ---- 기준선: 장중, 마감 직후 ----

    private val wedThuSpot = SpotSeries(
        spotBars("2026-11-11", "15:45", "15:55") { 7617.84 } + spotBars("2026-11-12", "09:30", "16:00") { if (it == 78) 7592.4 else 7590.0 },
        listOf(session("2026-11-11"), session("2026-11-12")),
        previousClose = 7619.38,
    )
    private val wedThuFutures = FuturesSeries(futuresBars("2026-11-11", "15:45", "23:55") { 7640.0 } + futuresBars("2026-11-12", "00:05", "16:30") { 7616.0 })

    @Test fun baseline_duringSessionIsPreviousClose() {
        val now = ny("2026-11-12", "15:58")
        val spot = wedThuSpot.copy(bars = wedThuSpot.bars.filter { it.time <= ny("2026-11-12", "15:55") })
        val chart = combine24h(spot, wedThuFutures.copy(bars = wedThuFutures.bars.filter { it.time <= now }), now)!!
        assertEquals(7619.38, chart.baseline, 0.0)
        assertEquals(7590.0, chart.price, 0.0)
        assertEquals(-29.38, chart.change, 0.0)
        assertEquals(-0.39, chart.change_pct, 0.0)
    }

    @Test fun baseline_justAfterCloseIsTodaysClose() {
        val chart = combine24h(wedThuSpot, wedThuFutures.copy(bars = wedThuFutures.bars.filter { it.time <= ny("2026-11-12", "16:00") }), ny("2026-11-12", "16:02"))!!
        // 창은 목 09:30부터 고정 24시간. 기준선은 asOf(16:02)로 정해 방금 끝난 목요일 종가다.
        assertEquals(ny("2026-11-12", "09:30"), chart.start)
        assertEquals(ny("2026-11-13", "09:30"), chart.end)
        assertEquals(7592.4, chart.baseline, 0.0)
        assertEquals(0.0, chart.change, 0.0)
        assertFalse(chart.lastConverted)
    }

    @Test fun baseline_noFinishedSessionUsesPreviousClose_andNullWithoutIt() {
        val spot = SpotSeries(spotBars("2026-11-12", "09:30", "10:00") { 101.0 }, listOf(session("2026-11-12")), previousClose = 100.0)
        val futures = FuturesSeries(futuresBars("2026-11-12", "09:00", "10:00") { 200.0 })
        assertEquals(100.0, combine24h(spot, futures, ny("2026-11-12", "10:05"))!!.baseline, 0.0)
        assertNull(combine24h(spot.copy(previousClose = null), futures, ny("2026-11-12", "10:05")))
    }

    @Test fun lessThanTwoPointsIsNull() {
        val spot = SpotSeries(listOf(SpotBar(ny("2026-11-12", "09:30"), 101.0)), listOf(session("2026-11-12")), previousClose = 100.0)
        assertNull(combine24h(spot, FuturesSeries(emptyList()), ny("2026-11-12", "09:40")))
    }

    // ---- 롤오버: 따로 보정하지 않고 정규장 종가 기준으로 흡수 ----

    @Test fun rollover_inSessionSwitchIsAbsorbedByCloseBase() {
        // 실제 9월 교체처럼 정규장 중(11:30)에 선물이 새 계약(+1%)으로 바뀌어도, 기준이 장 마감 짝이라 장외 값에 계단이 없다
        val spot = SpotSeries(spotBars("2026-11-12", "09:30", "16:00") { 100.0 }, listOf(session("2026-11-12")))
        val futures = FuturesSeries(
            futuresBars("2026-11-12", "09:00", "11:25") { 200.0 } + futuresBars("2026-11-12", "11:30", "16:55") { 202.0 },
        )
        val chart = combine24h(spot, futures, ny("2026-11-12", "17:00"))!!
        val offHours = chart.values.filterIndexed { i, _ -> chart.converted[i] && chart.times[i] > ny("2026-11-12", "16:00") }
        assertEquals(List(11) { 100.0 }, offHours)
    }

    @Test fun rollover_maintenanceGapMoveIsKept() {
        // 공백 앞뒤의 실제 변동은 지우지 않는다(보정 없음)
        val spot = SpotSeries(spotBars("2026-11-12", "15:50", "16:00") { 100.0 }, listOf(session("2026-11-12")))
        val futures = FuturesSeries(
            listOf(
                FuturesBar(ny("2026-11-12", "15:55"), 200.0, 200.0),
                FuturesBar(ny("2026-11-12", "16:05"), 200.0, 202.0),
                FuturesBar(ny("2026-11-12", "18:00"), 204.0, 205.0),
            ),
        )
        assertEquals(listOf(101.0, 102.5), combine24h(spot, futures, ny("2026-11-12", "18:10"))!!.values.takeLast(2))
    }

    // ---- v1.2: 전일종가 선, 장마감 선, 마감 후 등락 ----

    /** 수요일 종가 7,617.84, 목요일 종가 7,592.4(전일종가 7,619.38). 목요일 16:05부터 선물이 기준(7,616)보다 0.5% 높다. */
    private val afterCloseFutures = FuturesSeries(
        wedThuFutures.bars.filter { it.time <= ny("2026-11-12", "16:00") } + futuresBars("2026-11-12", "16:05", "18:00") { 7654.08 },
    )

    @Test fun afterClose_convertedPriceAndPctFromCloseAnchor() {
        val chart = combine24h(wedThuSpot, afterCloseFutures, ny("2026-11-12", "18:02"))!!
        assertTrue(chart.afterClose)
        // 7,592.4 × 7,654.08 / 7,616 = 7,630.36
        assertEquals(7630.36, chart.price, 1e-9)
        assertEquals(0.5, chart.change_pct, 1e-9)
        assertEquals(7619.38, chart.prev_close, 0.0)
        assertEquals(7592.4, chart.close_price, 0.0)
        assertEquals(ny("2026-11-12", "16:00"), chart.close_time)
        assertEquals(-0.35, chart.closeChangePct!!, 1e-9)
        assertEquals("마감 후 ▲0.50%", formatAfterCloseBadge(chart))
        assertTrue(formatCloseSummary(chart), formatCloseSummary(chart).startsWith("종가 7,592"))
        assertTrue(formatCloseSummary(chart).endsWith(" ▼0.35%"))
        // 장마감 표시: 16:00 종가 봉 위 점과 "장마감 7,592.4"(formatPrice)
        assertEquals(chart.times.indexOf(ny("2026-11-12", "16:00")) to "장마감 ${formatPrice(7592.4)}", combinedCloseMark(chart))
        // 마감 지점에서 끊기지 않는다(16:00 종가 봉과 16:05 환산 봉은 5분 간격)
        assertTrue(chart.breaks.none { chart.times[it] == ny("2026-11-12", "16:05") })
    }

    @Test fun inSession_prevCloseLineAndYesterdayCloseLine() {
        val now = ny("2026-11-12", "15:58")
        val spot = wedThuSpot.copy(bars = wedThuSpot.bars.filter { it.time <= ny("2026-11-12", "15:55") })
        val chart = combine24h(spot, wedThuFutures.copy(bars = wedThuFutures.bars.filter { it.time <= now }), now)!!
        assertFalse(chart.afterClose)
        assertEquals(7619.38, chart.prev_close, 0.0)
        // v1.3: 어제(수요일) 16:00 마감은 창(목 09:30~) 밖이라 장마감 표시가 없다. 종가 값은 기준선으로 남는다.
        assertEquals(0L, chart.close_time)
        assertEquals(7619.38, chart.close_price, 0.0)
        assertNull(combinedCloseMark(chart))
        val gspc = Quote("GSPC", "S&P500", 7590.0, 0.0, 0.0, combined = chart)
        assertNull(afterCloseChartFor(gspc))
    }

    @Test fun afterCloseChartFor_onlySpAndNasdaq100AfterClose() {
        val chart = combine24h(wedThuSpot, afterCloseFutures, ny("2026-11-12", "18:02"))!!
        val gspc = Quote("GSPC", "S&P500", 7592.4, 0.0, 0.0, combined = chart)
        assertEquals(chart, afterCloseChartFor(gspc))
        assertNotNull(afterCloseChartFor(gspc.copy(id = "NDX")))
        assertNull(afterCloseChartFor(gspc.copy(id = "CL")))
        assertNull(afterCloseChartFor(gspc.copy(combined = null)))
        // 옛 캐시(종가 없음)는 마감 후 표시를 하지 않고, 장마감 선도 그리지 않는다
        val old = chart.copy(prev_close = 0.0, close_time = 0, close_price = 0.0)
        assertNull(afterCloseChartFor(gspc.copy(combined = old)))
        assertNull(combinedCloseMark(old))
        assertNull(old.closeChangePct)
    }

    // ---- v1.3: 뉴욕 09:30부터 고정 24시간 창 ----

    @Test fun latestNewYorkOpen_anyWeekdayAndDst() {
        assertEquals(ny("2026-11-12", "09:30"), latestNewYorkOpen(ny("2026-11-13", "09:05")))
        assertEquals(ny("2026-11-13", "09:30"), latestNewYorkOpen(ny("2026-11-13", "09:30")))
        // 일요일도 09:30이다(요일 무관)
        assertEquals(ny("2026-11-15", "09:30"), latestNewYorkOpen(ny("2026-11-15", "20:00")))
        assertEquals("11-15 23:30", seoul(latestNewYorkOpen(ny("2026-11-15", "20:00"))))
        // 서머타임(EDT)에는 서울 22:30
        assertEquals("10-29 22:30", seoul(latestNewYorkOpen(ny("2026-10-30", "05:00"))))
    }

    /** 목 정규장(100), 목 장외 선물(기준 200 → 202), 금 개장. 기대값은 파이썬 계산(scratch combined.py)과 같다. */
    private val resetSpot = spotBars("2026-11-12", "09:30", "16:00") { 100.0 }
    private val resetSpans = listOf(session("2026-11-12"), session("2026-11-13"))
    private val resetFutures =
        futuresBars("2026-11-12", "15:55", "16:55") { 200.0 } +
            futuresBars("2026-11-12", "18:00", "23:55") { 202.0 } +
            futuresBars("2026-11-13", "00:05", "09:40") { 202.0 }

    private fun resetChart(now: String, friday: List<SpotBar>): CombinedChart? = combine24h(
        SpotSeries(resetSpot + friday, resetSpans, previousClose = 99.0),
        FuturesSeries(resetFutures.filter { it.time <= ny("2026-11-13", now) - 60 }),
        ny("2026-11-13", now),
    )

    @Test fun openReset_beforeTwoSpotBarsKeepsPreviousWindow() {
        // 금 09:31: 금요일 현물 봉이 아직 없어 새 창(금 09:30~)에 점이 1개 → 직전 창(목 09:30~금 09:30)
        val before = resetChart("09:31", emptyList())!!
        assertEquals(ny("2026-11-12", "09:30"), before.start)
        assertEquals(ny("2026-11-13", "09:30"), before.end)
        assertEquals(276, before.values.size)
        assertEquals(ny("2026-11-13", "09:30"), before.times.last())
        assertEquals(1.0f, combinedXAxis(before).positions!!.last(), 1e-6f)
        // 금 09:36: 현물 09:30 봉 하나 → 아직 직전 창, 마지막 점은 그 현물 봉
        val one = resetChart("09:36", listOf(SpotBar(ny("2026-11-13", "09:30"), 101.0)))!!
        assertEquals(ny("2026-11-12", "09:30"), one.start)
        assertEquals(101.0, one.price, 0.0)
        assertFalse(one.lastConverted)
        assertEquals(99.0, one.baseline, 0.0)
    }

    @Test fun openReset_twoSpotBarsRestartFromLeftEdge() {
        val chart = resetChart("09:41", listOf(SpotBar(ny("2026-11-13", "09:30"), 101.0), SpotBar(ny("2026-11-13", "09:35"), 101.5)))!!
        assertEquals(ny("2026-11-13", "09:30"), chart.start)
        assertEquals(ny("2026-11-14", "09:30"), chart.end)
        assertEquals(listOf(101.0, 101.5), chart.values)
        val positions = combinedXAxis(chart).positions!!
        assertEquals(2, positions.size)
        assertEquals(0f, positions[0], 1e-6f)
        assertEquals(300f / 86400f, positions[1], 1e-6f)
        // 기준선·등락은 그대로 asOf 기준(장중이라 전일 공식 종가 99)
        assertEquals(99.0, chart.baseline, 0.0)
        assertEquals(2.53, chart.change_pct, 0.0)
        // 정규장 띠는 정해진 금 09:30~16:00 전체(오른쪽 빈 곳까지)
        val band = combinedBands(chart).single()
        assertEquals(0f, band.start, 1e-6f)
        assertEquals(6.5f / 24f, band.endInclusive, 1e-6f)
        assertEquals("23:30~", combinedStartLabel(chart))
    }

    private val fridaySpot = SpotSeries(spotBars("2026-11-13", "09:30", "16:00") { 100.0 }, listOf(session("2026-11-13")), previousClose = 99.0)
    private val weekendFutures = futuresBars("2026-11-13", "15:55", "16:55") { 200.0 } + futuresBars("2026-11-15", "18:00", "19:55") { 204.0 }

    @Test fun weekend_saturdaySeoulMorningShowsFridayFromOpen() {
        // 토 10:00 서울 = 금 20:00 뉴욕. asOf = 금 17:00, 창 = 금 09:30~
        val chart = combine24h(fridaySpot, FuturesSeries(weekendFutures.filter { it.time <= ny("2026-11-13", "16:55") }), ny("2026-11-13", "20:00"))!!
        assertEquals(ny("2026-11-13", "09:30"), chart.start)
        assertEquals(90, chart.values.size)
        assertEquals(ny("2026-11-13", "16:55"), chart.times.last())
        assertEquals("11-14 06:00", seoul(chart.close_time))
    }

    @Test fun weekend_mondaySeoulMorningShowsSundayFuturesOnly() {
        // 월 10:00 서울 = 일 20:00 뉴욕. 창 = 일 09:30~, 일 18:00 이후 선물 환산만 보인다
        val chart = combine24h(fridaySpot, FuturesSeries(weekendFutures), ny("2026-11-15", "20:00"))!!
        assertEquals(ny("2026-11-15", "09:30"), chart.start)
        assertEquals(ny("2026-11-16", "09:30"), chart.end)
        assertEquals(24, chart.values.size)
        assertEquals(ny("2026-11-15", "18:00"), chart.times.first())
        assertTrue(chart.converted.all { it })
        // 100 × 204 / 금 15:55 선물 봉 종가 200
        assertEquals(102.0, chart.price, 0.0)
        assertEquals(100.0, chart.baseline, 0.0)
        assertEquals(emptyList<TradingSpan>(), chart.sessions)
        // 금요일 마감은 창 밖
        assertEquals(0L, chart.close_time)
    }

    @Test fun weekend_sundayFuturesFirstBarAloneFallsBackToFridayWindow() {
        // QA D2. 일 18:02: 새 창(일 09:30~)에 점 1개, 토 09:30 창은 0개 → 하루 더 물러나 금 09:30 창
        val futures = FuturesSeries(weekendFutures.filter { it.time <= ny("2026-11-15", "18:00") })
        val chart = combine24h(fridaySpot, futures, ny("2026-11-15", "18:02"))!!
        assertEquals(ny("2026-11-13", "09:30"), chart.start)
        assertEquals(ny("2026-11-14", "09:30"), chart.end)
        assertEquals(90, chart.values.size)
        assertEquals(ny("2026-11-13", "16:55"), chart.times.last())
        assertEquals(100.0, chart.price, 0.0)
        assertTrue(chart.afterClose)
        assertEquals(ny("2026-11-13", "16:00"), chart.close_time)
    }

    @Test fun fallback_goesBackAtMostSevenDays() {
        // 점이 2개 이상인 창이 7일 전(금 11/6)이면 찾고, 8일 전(목 11/5)이면 null
        val latest = SpotBar(ny("2026-11-13", "10:00"), 101.0)
        val sevenDaysAgo = SpotSeries(spotBars("2026-11-06", "09:30", "16:00") { 100.0 } + latest, previousClose = 99.0)
        val chart = combine24h(sevenDaysAgo, FuturesSeries(emptyList()), ny("2026-11-13", "10:30"))!!
        assertEquals(ny("2026-11-06", "09:30"), chart.start)
        assertEquals(79, chart.values.size)
        val eightDaysAgo = SpotSeries(spotBars("2026-11-05", "09:30", "16:00") { 100.0 } + latest, previousClose = 99.0)
        assertNull(combine24h(eightDaysAgo, FuturesSeries(emptyList()), ny("2026-11-13", "10:30")))
    }

    @Test fun dst_windowStartsAt2230Seoul() {
        val spot = SpotSeries(spotBars("2026-10-29", "09:30", "16:00") { 100.0 }, previousClose = 99.0)
        val chart = combine24h(spot, FuturesSeries(futuresBars("2026-10-29", "15:55", "16:55") { 200.0 }), ny("2026-10-29", "17:30"))!!
        assertEquals("10-29 22:30", seoul(chart.start))
        assertEquals(chart.start + 86400, chart.end)
        assertEquals("22:30~", combinedStartLabel(chart))
        assertEquals("22:30", combinedXAxis(chart).ticks.first().label)
        assertEquals(listOf("10-29 22:30" to "10-30 05:00"), chart.sessions.map { seoul(it.start) to seoul(it.end) })
    }

    @Test fun closeMark_nearestPointAtOrBeforeCloseTime() {
        // 마지막 현물 봉이 15:50이라 16:00 점이 없다 → 16:00 이하 가장 가까운 15:50 점
        val spot = SpotSeries(spotBars("2026-11-12", "15:30", "15:50") { 100.0 }, listOf(session("2026-11-12")), previousClose = 99.0)
        val chart = combine24h(spot, FuturesSeries(futuresBars("2026-11-12", "15:50", "16:30") { 200.0 }), ny("2026-11-12", "16:40"))!!
        assertEquals(ny("2026-11-12", "16:00"), chart.close_time)
        val mark = combinedCloseMark(chart)!!
        assertEquals(ny("2026-11-12", "15:50"), chart.times[mark.first])
        assertEquals("장마감 ${formatPrice(100.0)}", mark.second)
        // close_time이 창 밖이면 없다
        assertNull(combinedCloseMark(chart.copy(close_time = chart.end + 1)))
    }

    // ---- 화면용 ----

    private val drawable = CombinedChart(
        times = listOf(100L, 400L), values = listOf(1.0, 2.0), converted = listOf(false, true),
        start = 0L, end = 1000L, sessions = listOf(TradingSpan(0L, 250L)), baseline = 1.0,
    )

    @Test fun combinedChartFor_onlySpAndNasdaq100TodayWhenConsistent() {
        val gspc = Quote("GSPC", "S&P500", 1.0, 0.0, 0.0, combined = drawable)
        assertEquals(drawable, combinedChartFor(gspc, Period.TODAY))
        assertNotNull(combinedChartFor(gspc.copy(id = "NDX"), Period.TODAY))
        assertNull(combinedChartFor(gspc, Period.ONE_MONTH))
        assertNull(combinedChartFor(gspc.copy(id = "KS11"), Period.TODAY))
        assertNull(combinedChartFor(gspc.copy(combined = null), Period.TODAY))
        assertNull(combinedChartFor(gspc.copy(combined = drawable.copy(converted = listOf(true))), Period.TODAY))
        assertNull(combinedChartFor(gspc.copy(combined = drawable.copy(times = listOf(1L))), Period.TODAY))
    }

    @Test fun bandsAndLabelFromChart() {
        assertEquals(listOf(0f..0.25f), combinedBands(drawable))
        assertEquals(listOf(0.1f, 0.4f), combinedXAxis(drawable).positions)
        // 1970-01-01 00:06:40 UTC = 서울 09:06
        assertEquals("선물 환산 09:06", convertedLabel(drawable))
        assertNull(convertedLabel(drawable.copy(converted = listOf(true, false))))
    }

    @Test fun cacheRoundTripKeepsCombined() {
        val data = MarketData("2026-11-13T23:05:04+09:00", listOf(Quote("GSPC", "S&P500", 1.0, 0.0, 0.0, combined = drawable)), emptyList())
        assertEquals(data, parseMarketData(encodeMarketData(data)))
        // 옛 캐시(combined 없음)도 읽는다
        assertNull(parseMarketData("""{"updated_at": "x", "quotes": [{"id": "GSPC", "name": "S&P500", "price": 1.0, "change": 0.0, "change_pct": 0.0}], "events": []}""").quotes[0].combined)
    }

    companion object {
        /** 2026-11-13 09:05:04 뉴욕(23:05:04 서울). 실제 자료를 받은 시각. */
        const val REAL_NOW = 1794578704L
    }
}
