package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.round
import kotlin.math.sin

/** 설계서 6.1~6.3절 고정 기대값. 수집기 tests/test_quotes.py와 같은 입력, 같은 결과다. */
class HistoryTest {
    private fun r1(values: List<Double>) = values.map { round(it * 10) / 10 }

    private fun chart(stamps: List<Long>, closes: String, tz: String? = null, offset: Long = -18000, type: String? = null): YahooChartResult {
        val zone = if (tz == null) "" else ""","exchangeTimezoneName": "$tz""""
        val kind = if (type == null) "" else ""","instrumentType": "$type""""
        return parseYahooChart(
            """{"chart": {"result": [{"meta": {"regularMarketPrice": 1.0, "gmtoffset": $offset$zone$kind},
               "timestamp": $stamps, "indicators": {"quote": [{"close": $closes}]}}], "error": null}}""",
        )!!
    }

    // ---- 6.1 ----

    @Test fun chartDates_usesTimeZoneThenGmtOffset() {
        // 1782878400 = 2026-07-01 00:00 뉴욕(서머타임 -4시간), gmtoffset은 겨울 값 -18000
        assertEquals(listOf("2026-07-01"), chartDates(chart(listOf(1782878400L), "[1.0]", "America/New_York")))
        assertEquals(listOf("2026-06-30"), chartDates(chart(listOf(1782878400L), "[1.0]", null)))
        assertEquals(listOf("2026-06-30"), chartDates(chart(listOf(1782878400L), "[1.0]", "Bad/Zone")))
    }

    @Test fun chartCloses_dropsNullAndRounds() {
        val result = chart(listOf(1L, 2L, 3L), "[1.005, null, 2.126]", "America/New_York")
        assertEquals(listOf(1.0, 2.13), chartCloses(result).map { round(it * 100) / 100 })
        assertEquals(2, chartDates(result).size)
    }

    @Test fun maxStartYear_usesGmtOffsetOfFirstBar() {
        assertEquals(2026, maxStartYear(chart(listOf(1782878400L), "[1.0]", "America/New_York")))
        assertNull(maxStartYear(chart(emptyList(), "[]")))
    }

    @Test fun parseYahooChart_readsTypeAndZone() {
        val result = chart(listOf(1L), "[1.0]", "Asia/Seoul", 32400, "EQUITY")
        assertEquals("EQUITY", result.meta.instrumentType)
        assertEquals("Asia/Seoul", result.meta.exchangeTimezoneName)
        assertNull(parseYahooChart("""{"chart": {"result": null, "error": {"code": "Not Found"}}}"""))
    }

    // ---- 6.2 ----

    private val sample = listOf(100.0, 110.0, 105.0, 120.0, 90.0, 95.0, 121.0, 80.0, 100.0)

    @Test fun drawdownSeries_matchesCollector() {
        assertEquals(listOf(0.0, 0.0, -4.5, 0.0, -25.0, -20.8, 0.0, -33.9, -17.4), r1(drawdownSeries(sample)))
    }

    @Test fun episode_fixedExpectations() {
        assertEquals(DrawdownEpisode(6, 7, null, -33.9), drawdownEpisode(sample))
        assertNull(drawdownEpisode(listOf(100.0, 101.0, 102.0, 103.0)))
        assertEquals(DrawdownEpisode(1, 2, null, -50.0), drawdownEpisode(listOf(100.0, 120.0, 60.0, 80.0, 110.0)))
        assertEquals(DrawdownEpisode(1, 2, 4, -50.0), drawdownEpisode(listOf(100.0, 120.0, 60.0, 80.0, 125.0)))
        assertEquals(DrawdownEpisode(1, 2, 3, -25.0), drawdownEpisode(listOf(100.0, 120.0, 90.0, 120.0, 100.0, 130.0)))
        assertEquals(DrawdownEpisode(0, 1, null, -10.0), drawdownEpisode(listOf(100.0, 90.0)))
        assertNull(drawdownEpisode(listOf(50.0)))
        assertNull(drawdownEpisode(emptyList()))
    }

    /** 설계서 S 수열: round2(100 + 40·sin(i/37) + 0.08·i − (700≤i<760 ? 30 : 0)), i=0..1199 */
    private val s: List<Double> = (0 until 1200).map { i ->
        val v = 100 + 40 * sin(i / 37.0) + 0.08 * i - (if (i in 700 until 760) 30 else 0)
        round(v * 100) / 100
    }

    @Test fun sSequence_bucketsAndEpisode() {
        assertEquals(5, bucketSize(s.size))
        assertEquals(482, thinIndices(s).size)
        assertEquals(DrawdownEpisode(61, 171, 257, -49.0), drawdownEpisode(s))
    }

    @Test fun sSequence_maxPicksAddsRecovery() {
        val picks = maxPicks(s, listOf(61, 171, 257))
        assertEquals(483, picks.size)
        assertEquals(listOf(257), (picks.toSet() - thinIndices(s).toSet()).toList())
        assertEquals(listOf(0, 0, 4, 5, 9, 10, 14, 15, 19, 20), picks.take(10))
        assertEquals(listOf(1190, 1194, 1195, 1199, 1199), picks.takeLast(5))
        val depths = drawdownSeries(s)
        assertEquals(-49.0, picks.minOf { round(depths[it] * 10) / 10 }, 0.0)
    }

    @Test fun thinIndices_shortListKeepsAll() {
        assertEquals(1, bucketSize(500))
        assertEquals(listOf(0, 1, 2), thinIndices(listOf(1.0, 2.0, 3.0)))
        assertEquals(listOf(0, 1, 2), maxPicks(listOf(1.0, 2.0, 3.0), listOf(1, 7)))
    }

    @Test fun maxFields_sSequenceIsAlignedAndHitsMdd() {
        val start = 946684800L // 2000-01-01 00:00 UTC
        val stamps = s.indices.map { start + it * 86_400L }
        val fields = maxFields(chart(stamps, s.toString(), "Asia/Seoul", 32400), withDrawdown = true)!!
        assertEquals(483, fields.closes.size)
        assertEquals(483, fields.dates.size)
        assertEquals(483, fields.depths.size)
        assertEquals(-49.0, fields.mdd!!, 0.0)
        assertEquals(-49.0, fields.depths.min(), 0.0)
        assertEquals(-49.0, fields.depths[fields.dates.indexOf(fields.troughDate)], 0.0)
        assertTrue(fields.peakDate!! in fields.dates)
        assertTrue(fields.recoveryDate!! in fields.dates)
        assertEquals(s.max(), fields.peak!!, 0.0)
        assertEquals(2000, fields.start)
    }

    @Test fun maxFields_withoutDrawdownIsThinOnly() {
        val stamps = s.indices.map { 946684800L + it * 86_400L }
        val fields = maxFields(chart(stamps, s.toString(), "Asia/Seoul", 32400), withDrawdown = false)!!
        assertEquals(482, fields.closes.size)
        assertEquals(emptyList<Double>(), fields.depths)
        assertNull(fields.peak)
        assertNull(fields.mdd)
        assertNull(fields.peakDate)
        assertNull(fields.troughDate)
        assertNull(fields.recoveryDate)
    }

    @Test fun maxFields_noBarsIsNull() {
        assertNull(maxFields(chart(emptyList(), "[]"), withDrawdown = true))
    }

    @Test fun noDrawdownSymbols() {
        listOf("^VIX", "^VXN", "^VVIX", "^TNX", "^TYX", "^FVX", "^IRX").forEach { assertEquals(it, false, hasDrawdown(it)) }
        assertEquals(true, hasDrawdown("005930.KS"))
        assertEquals(true, hasDrawdown("^N225"))
    }

    // ---- 6.3 ----

    @Test fun bigMovePct_fixedExpectations() {
        assertEquals(5.0, bigMovePct(3.0, (0..60).map { 100 * Math.pow(1.02, it.toDouble()) }), 0.0)
        assertEquals(2.5, bigMovePct(2.0, (0..60).map { 100 * Math.pow(1.01, it.toDouble()) }), 0.0)
        assertEquals(3.0, bigMovePct(3.0, (0..59).map { 100 * Math.pow(1.02, it.toDouble()) }), 0.0)
    }
}
