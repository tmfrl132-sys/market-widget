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
        assertEquals(275, chart.values.size)
        assertEquals(1794492007L, chart.start)
        assertEquals(1794578407L, chart.end)
        assertEquals(1794492300L, chart.times.first())
        assertEquals(7567.5, chart.values.first(), 0.0)
        assertEquals(1794578107L, chart.times.last())
        // 기준 짝: 현물 16:00 공식 종가 7,592.40 ↔ 선물 15:55 봉 종가 7,616.25(16:00 가격)
        assertEquals(7624.8, chart.price, 0.0)
        assertEquals(7592.4, chart.baseline, 0.0)
        assertEquals(32.4, chart.change, 0.0)
        assertEquals(0.43, chart.change_pct, 0.0)
        assertEquals(7558.28, chart.low, 0.0)
        assertEquals(7649.21, chart.high, 0.0)
        assertEquals(196, chart.converted.count { it })
        assertEquals(listOf(95), chart.breaks)
        assertEquals(listOf(TradingSpan(1794493800L, 1794517200L)), chart.sessions)
        // 이음매: 09:25 환산 → 09:30 현물, 16:00 현물 → 16:05 환산
        assertEquals(7560.03, valueAt(chart, ny("2026-11-12", "09:25")), 0.0)
        assertEquals(7576.3, valueAt(chart, ny("2026-11-12", "09:30")), 0.0)
        assertEquals(7592.4, valueAt(chart, ny("2026-11-12", "16:00")), 0.0)
        assertEquals(7596.64, valueAt(chart, ny("2026-11-12", "16:05")), 0.0)
        assertEquals(7597.88, valueAt(chart, ny("2026-11-12", "16:55")), 0.0)
        assertEquals(7603.37, valueAt(chart, ny("2026-11-12", "18:00")), 0.0)
        // 정비 공백(16:55~18:00)에서 끊는다
        assertEquals(ny("2026-11-12", "16:55"), chart.times[94])
        assertEquals(ny("2026-11-12", "18:00"), chart.times[95])
        assertEquals("선물 환산 22:55", convertedLabel(chart))
    }

    @Test fun real_nasdaq100_preOpenFriday() {
        val chart = realChart("yahoo_5m_5d_ndx_20261113.json", "yahoo_5m_5d_nq_20261113.json")!!
        assertEquals(275, chart.values.size)
        assertEquals(1794492300L, chart.times.first())
        assertEquals(27994.73, chart.values.first(), 0.0)
        // 기준 짝: 현물 16:00 28,289.19 ↔ 선물 15:55 봉 종가 28,388.25
        assertEquals(28333.03, chart.price, 0.0)
        assertEquals(28289.19, chart.baseline, 0.0)
        assertEquals(43.85, chart.change, 0.0)
        assertEquals(0.15, chart.change_pct, 0.0)
        assertEquals(27953.39, chart.low, 0.0)
        assertEquals(28525.78, chart.high, 0.0)
        assertEquals(listOf(95), chart.breaks)
        assertEquals(27957.37, valueAt(chart, ny("2026-11-12", "09:25")), 0.0)
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
        assertEquals(274, chart.values.size)
        assertEquals(1794416400L, chart.start)
        assertEquals(1794502800L, chart.end)
        assertEquals(7627.26, chart.values.first(), 0.0)
        assertEquals(7633.37, chart.price, 0.0)
        assertFalse(chart.lastConverted)
        assertNull(convertedLabel(chart))
        assertEquals(7619.38, chart.baseline, 0.0)
        assertEquals(13.99, chart.change, 0.0)
        assertEquals(0.18, chart.change_pct, 0.0)
        assertEquals(7555.04, chart.low, 0.0)
        assertEquals(7649.21, chart.high, 0.0)
        assertEquals(listOf(59), chart.breaks)
        assertEquals(listOf(TradingSpan(1794416400L, 1794430800L), TradingSpan(1794493800L, 1794502800L)), chart.sessions)
    }

    @Test fun real_xAxisEveryFourHoursSeoulAndSessionBand() {
        val chart = realChart("yahoo_5m_5d_gspc_20261113.json", "yahoo_5m_5d_es_20261113.json")!!
        val axis = combinedXAxis(chart)
        assertEquals(listOf("23:00", "04:00", "08:00", "12:00", "16:00", "20:00", "23:00"), axis.ticks.map { it.label })
        assertEquals(chart.values.size, axis.positions!!.size)
        assertEquals(0.00340f, axis.positions!!.first(), 1e-4f)
        assertEquals(0.99653f, axis.positions!!.last(), 1e-4f)
        val band = combinedBands(chart).single()
        assertEquals(0.020752f, band.start, 1e-4f)
        assertEquals(0.291586f, band.endInclusive, 1e-4f)
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
            assertEquals("23:00", combinedXAxis(chart).ticks.first().label)
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

    @Test fun dstEnd_weekendConvertsWithFridayBaseAndWindowIs24Hours() {
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
        assertEquals(ny("2026-11-02", "09:35"), chart.end)
        assertEquals(chart.end - 86400, chart.start)
        assertEquals(listOf(ny("2026-11-01", "18:00"), ny("2026-11-01", "18:05"), ny("2026-11-02", "09:25"), ny("2026-11-02", "09:30")), chart.times)
        assertEquals(listOf(102.0, 102.0, 102.97, 103.5), chart.values)
        assertEquals(listOf(true, true, true, false), chart.converted)
        assertEquals(listOf(2), chart.breaks)
        // 월요일 장중이라 기준선은 금요일 공식 종가(previousClose)
        assertEquals(100.2, chart.baseline, 0.0)
        assertEquals(listOf("11-02 23:30" to "11-02 23:35"), chart.sessions.map { seoul(it.start) to seoul(it.end) })
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

    @Test fun weekendWindowEndsAtFridayLastBar() {
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
        assertEquals(ny("2026-11-13", "17:00"), chart.end)
        assertEquals(ny("2026-11-12", "17:00"), chart.start)
        // 목 16:55(창 밖)는 빠지고 목 18:00부터
        assertEquals(ny("2026-11-12", "18:00"), chart.times.first())
        assertEquals(100.0, chart.values.first(), 0.0)
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
        assertEquals(ny("2026-11-27", "13:15"), chart.end)
        assertEquals(103.2, valueAt(chart, ny("2026-11-27", "09:25")), 0.0)
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
        assertEquals(ny("2026-11-12", "16:02"), chart.end)
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
