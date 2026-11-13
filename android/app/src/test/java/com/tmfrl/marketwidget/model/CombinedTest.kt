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
        val futures = FuturesSeries(
            listOf(
                FuturesBar(ny("2026-11-12", "15:55"), 7615.0, 7615.0),
                FuturesBar(ny("2026-11-12", "16:00"), 7616.0, 7616.0),
                FuturesBar(ny("2026-11-12", "16:05"), 7630.0, 7640.0),
            ),
            "E-Mini S&P 500 Dec 26",
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
        assertEquals("E-Mini S&P 500 Dec 26", chart.contract)
        assertTrue(chart.lastConverted)
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
        assertEquals(7626.05, chart.price, 0.0)
        assertEquals(7592.4, chart.baseline, 0.0)
        assertEquals(33.65, chart.change, 0.0)
        assertEquals(0.44, chart.change_pct, 0.0)
        assertEquals(7558.28, chart.low, 0.0)
        assertEquals(7649.21, chart.high, 0.0)
        assertEquals(196, chart.converted.count { it })
        assertEquals(listOf(95), chart.breaks)
        assertEquals("E-Mini S&P 500 Dec 26", chart.contract)
        assertEquals(listOf(TradingSpan(1794493800L, 1794517200L)), chart.sessions)
        // 이음매: 09:25 환산 → 09:30 현물, 16:00 현물 → 16:05 환산
        assertEquals(7560.03, valueAt(chart, ny("2026-11-12", "09:25")), 0.0)
        assertEquals(7576.3, valueAt(chart, ny("2026-11-12", "09:30")), 0.0)
        assertEquals(7592.4, valueAt(chart, ny("2026-11-12", "16:00")), 0.0)
        assertEquals(7597.88, valueAt(chart, ny("2026-11-12", "16:05")), 0.0)
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
        assertEquals(28332.78, chart.price, 0.0)
        assertEquals(28289.19, chart.baseline, 0.0)
        assertEquals(43.6, chart.change, 0.0)
        assertEquals(0.15, chart.change_pct, 0.0)
        assertEquals(27953.39, chart.low, 0.0)
        assertEquals(28525.78, chart.high, 0.0)
        assertEquals(listOf(95), chart.breaks)
        assertEquals("Nasdaq 100 Dec 26", chart.contract)
        assertEquals(27957.37, valueAt(chart, ny("2026-11-12", "09:25")), 0.0)
        assertEquals(28103.21, valueAt(chart, ny("2026-11-12", "09:30")), 0.0)
        assertEquals(28325.06, valueAt(chart, ny("2026-11-12", "16:05")), 0.0)
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
            assertEquals(7626.05, chart.price, 0.0)
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
        // Fc는 16:00 선물 봉(200 + 6)
        assertEquals(106.8, chart.values[gapAt], 0.0)
        // 자정 10분 빈 봉 뒤도 같은 기준(100 × 230 / 206)
        assertEquals(111.65, chart.values.last(), 0.0)
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
            futuresBars("2026-11-12", "15:55", "16:55") { if (it == 1) 198.0 else 199.0 } +
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
        // 장 마감 뒤라 기준선은 금요일 종가, 마지막 점은 금요일 기준 환산값(100 × 202 / 금 16:00 선물 200)
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

    // ---- 롤오버 ----

    private val rollFutures = FuturesSeries(
        listOf(
            FuturesBar(ny("2026-11-12", "16:00"), 200.0, 200.0),
            FuturesBar(ny("2026-11-12", "16:05"), 200.0, 201.0),
            FuturesBar(ny("2026-11-12", "16:10"), 201.0, 202.0),
            // 정비 공백 뒤 새 계약: 시가 204로 계단
            FuturesBar(ny("2026-11-12", "18:00"), 204.0, 205.0),
            FuturesBar(ny("2026-11-12", "18:05"), 205.0, 206.0),
        ),
        "E-Mini S&P 500 Mar 27",
    )
    private val rollSpot = SpotSeries(spotBars("2026-11-12", "15:50", "16:00") { 100.0 }, listOf(session("2026-11-12")))

    @Test fun rollover_withoutRollKeepsStep() {
        val chart = combine24h(rollSpot, rollFutures, ny("2026-11-12", "18:10"))!!
        assertEquals(listOf(100.5, 101.0, 102.5, 103.0), chart.values.takeLast(4))
    }

    @Test fun rollover_rescalesFcAtGapSoSeamIsContinuous() {
        val rollAt = ny("2026-11-12", "18:08")
        val chart = combine24h(rollSpot, rollFutures, ny("2026-11-12", "18:10"), rollAt)!!
        // Fc = 200 × 204 / 202. 18:00 시가 기준 값은 공백 앞 마지막 값(101.0)과 같다
        assertEquals(listOf(100.5, 101.0, 101.5, 101.99), chart.values.takeLast(4))
        assertEquals(100.0 * 204 / (200.0 * 204 / 202), 101.0, 1e-9)
        assertEquals(rollAt, chart.roll_at)
    }

    @Test fun rollover_gapAfterDetectionIsNotRescaled() {
        val chart = combine24h(rollSpot, rollFutures, ny("2026-11-12", "18:10"), rollAt = ny("2026-11-12", "17:30"))!!
        assertEquals(102.5, chart.values[chart.values.size - 2], 0.0)
    }

    @Test fun rollAtFor_detectsContractChangeAndKeepsForAWeek() {
        val now = ny("2026-12-14", "10:00")
        val before = CombinedChart(contract = "E-Mini S&P 500 Dec 26")
        assertNull(rollAtFor(null, "E-Mini S&P 500 Mar 27", now))
        assertNull(rollAtFor(before, null, now))
        assertNull(rollAtFor(before, "E-Mini S&P 500 Dec 26", now))
        assertEquals(now, rollAtFor(before, "E-Mini S&P 500 Mar 27", now))
        val rolled = CombinedChart(contract = "E-Mini S&P 500 Mar 27", roll_at = now)
        assertEquals(now, rollAtFor(rolled, "E-Mini S&P 500 Mar 27", now + 3 * 86400))
        assertNull(rollAtFor(rolled, "E-Mini S&P 500 Mar 27", now + 7 * 86400))
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
