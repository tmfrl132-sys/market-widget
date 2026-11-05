package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.util.TimeZone

class XAxisTest {
    private fun seoul(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(SEOUL).toEpochSecond()

    private fun labels(ticks: List<XTick>) = ticks.map { it.label }

    private fun days(count: Int) = (0 until count).map { java.time.LocalDate.of(2026, 1, 1).plusDays(it.toLong()).toString() }

    @Test fun dateTicks_fiveEvenTicksIncludingEnds() {
        val ticks = dateTicks(days(9), 9, Period.ONE_MONTH)
        assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f, 1f), ticks.map { it.position })
        assertEquals(listOf("1/1", "1/3", "1/5", "1/7", "1/9"), labels(ticks))
    }

    @Test fun dateTicks_uneven63ValuesStillHitsBothEnds() {
        val ticks = dateTicks(days(63), 63, Period.THREE_MONTHS)
        assertEquals(5, ticks.size)
        assertEquals(0f, ticks.first().position, 0f)
        assertEquals(1f, ticks.last().position, 0f)
    }

    @Test fun dateTicks_fewerThanFiveValuesUsesEachValue() {
        assertEquals(4, dateTicks(days(4), 4, Period.ONE_MONTH).size)
        assertEquals(listOf(0f, 1f), dateTicks(days(2), 2, Period.ONE_MONTH).map { it.position })
        assertEquals(emptyList<XTick>(), dateTicks(days(1), 1, Period.ONE_MONTH))
    }

    @Test fun dateTicks_labelFormatPerPeriod() {
        val dates = listOf("1996-12-11", "2009-03-02")
        assertEquals(listOf("12/11", "3/2"), labels(dateTicks(dates, 2, Period.ONE_MONTH)))
        assertEquals(listOf("12/11", "3/2"), labels(dateTicks(dates, 2, Period.THREE_MONTHS)))
        assertEquals(listOf("96.12", "09.3"), labels(dateTicks(dates, 2, Period.ONE_YEAR)))
        assertEquals(listOf("1996.12", "2009.3"), labels(dateTicks(dates, 2, Period.FIVE_YEARS)))
        assertEquals(listOf("1996", "2009"), labels(dateTicks(dates, 2, Period.MAX)))
    }

    @Test fun dateTicks_noTicksWhenLengthsDifferOrDateUnreadable() {
        assertEquals(emptyList<XTick>(), dateTicks(days(4), 5, Period.ONE_YEAR))
        assertEquals(emptyList<XTick>(), dateTicks(emptyList(), 5, Period.ONE_YEAR))
        assertEquals(emptyList<XTick>(), dateTicks(listOf("2026-11-04", "new"), 2, Period.ONE_YEAR))
        assertEquals(emptyList<XTick>(), dateTicks(days(5), 5, Period.TODAY))
    }

    @Test fun dateTicks_keepsWithLogChartOfSameLength() {
        val values = listOf(100.0, 1000.0, 10000.0, 5000.0, 20000.0)
        val log = logChartFor(values)!!
        assertEquals(dateTicks(days(5), values.size, Period.MAX), dateTicks(days(5), log.values.size, Period.MAX))
        assertEquals(5, dateTicks(days(5), log.values.size, Period.MAX).size)
    }

    private fun quote(
        daily: List<Double> = emptyList(), dailyDates: List<String> = emptyList(),
        weekly: List<Double> = emptyList(), weeklyDates: List<String> = emptyList(),
        maxCloses: List<Double> = emptyList(), maxDates: List<String> = emptyList(),
        drawdowns: List<Double> = emptyList(), chart: List<Double> = emptyList(),
    ) = Quote(
        "KS11", "코스피", 1.0, 0.0, 0.0, chart = chart,
        daily = daily, daily_dates = dailyDates, weekly = weekly, weekly_dates = weeklyDates,
        max_closes = maxCloses, max_dates = maxDates, max_drawdowns = drawdowns,
    )

    private val longDaily = (1..250).map { it.toDouble() }

    @Test fun chartDates_slicesSameRangeAsValues() {
        val dates = days(250)
        val q = quote(daily = longDaily, dailyDates = dates)
        assertEquals(dates.takeLast(22), chartDates(q, Period.ONE_MONTH))
        assertEquals(dates.takeLast(63), chartDates(q, Period.THREE_MONTHS))
        assertEquals(dates, chartDates(q, Period.ONE_YEAR))
        listOf(Period.ONE_MONTH, Period.THREE_MONTHS, Period.ONE_YEAR).forEach {
            assertEquals(chartValues(q, it).size, chartDates(q, it).size)
        }
    }

    @Test fun chartDates_misalignedFullListGivesNoDatesEvenIfSliceLengthMatches() {
        // 자른 뒤 길이만 보면 22개로 같아 하루 어긋난 날짜를 그리게 된다
        val q = quote(daily = longDaily, dailyDates = days(249))
        assertEquals(emptyList<String>(), chartDates(q, Period.ONE_MONTH))
        assertEquals(emptyList<XTick>(), chartXAxis(q, Period.ONE_MONTH).ticks)
    }

    @Test fun chartDates_oldCacheUsingChartFallbackHasNoDates() {
        val q = quote(chart = listOf(1.0, 2.0), dailyDates = listOf("2026-11-04", "2026-11-05"))
        assertEquals(emptyList<String>(), chartDates(q, Period.THREE_MONTHS))
        assertEquals(emptyList<XTick>(), chartXAxis(q, Period.THREE_MONTHS).ticks)
    }

    @Test fun chartDates_keptValuesWithEmptyDatesHaveNoTicks() {
        val q = quote(
            daily = listOf(1.0, 2.0), weekly = listOf(1.0, 2.0), weeklyDates = emptyList(),
            maxCloses = listOf(1.0, 2.0), maxDates = emptyList(),
        )
        assertEquals(emptyList<XTick>(), chartXAxis(q, Period.FIVE_YEARS).ticks)
        assertEquals(emptyList<XTick>(), chartXAxis(q, Period.MAX).ticks)
        assertEquals(emptyList<XTick>(), chartXAxis(q, Period.ONE_YEAR).ticks)
    }

    @Test fun chartXAxis_fiveYearsUsesWeeklyDates() {
        val q = quote(
            daily = listOf(1.0, 2.0), weekly = listOf(1.0, 2.0),
            weeklyDates = listOf("2021-11-08", "2026-11-02"),
        )
        assertEquals(listOf("2021.11", "2026.11"), labels(chartXAxis(q, Period.FIVE_YEARS).ticks))
        assertNull(chartXAxis(q, Period.FIVE_YEARS).positions)
    }

    @Test fun chartXAxis_maxTicksDoNotDependOnDrawdownLength() {
        // 실제 data.json에서 코스피 max_closes는 494개, max_drawdowns는 246개다
        val closes = (1..494).map { it.toDouble() }
        val q = quote(maxCloses = closes, maxDates = days(494), drawdowns = (1..246).map { -it.toDouble() })
        assertEquals(5, chartXAxis(q, Period.MAX).ticks.size)
    }

    @Test fun sessionPosition_clampsToZeroAndOne() {
        assertEquals(0f, sessionPosition(50, 100, 200), 0f)
        assertEquals(0.5f, sessionPosition(150, 100, 200), 0f)
        assertEquals(1f, sessionPosition(250, 100, 200), 0f)
    }

    @Test fun todayXAxis_kospiAtTenIsAboutOneSixthAcross() {
        val axis = todayXAxis(2, listOf(seoul(5, 9), seoul(5, 10)), seoul(5, 9), seoul(5, 15, 30))
        assertEquals(0f, axis.positions!![0], 0f)
        assertEquals(0.1538f, axis.positions!![1], 0.001f)
    }

    @Test fun todayXAxis_fallsBackWithoutTicks() {
        val times = listOf(seoul(5, 9), seoul(5, 10))
        val start = seoul(5, 9)
        val end = seoul(5, 15)
        val none = ChartXAxis()
        assertEquals(none, todayXAxis(3, times, start, end))
        assertEquals(none, todayXAxis(2, emptyList(), start, end))
        assertEquals(none, todayXAxis(2, times, null, end))
        assertEquals(none, todayXAxis(2, times, start, null))
        assertEquals(none, todayXAxis(2, times, end, end))
        assertEquals(none, todayXAxis(2, times, end, start))
    }

    @Test fun chartXAxis_oldCacheTodayHasNoAxis() {
        val q = Quote("KS11", "코스피", 1.0, 0.0, 0.0, intraday = listOf(1.0, 2.0))
        assertEquals(ChartXAxis(), chartXAxis(q, Period.TODAY))
    }

    @Test fun chartXAxis_todayUsesTimesAndSession() {
        val q = Quote(
            "KS11", "코스피", 1.0, 0.0, 0.0, intraday = listOf(1.0, 2.0),
            intraday_times = listOf(seoul(5, 9), seoul(5, 12)), session_start = seoul(5, 9), session_end = seoul(5, 15),
        )
        val axis = chartXAxis(q, Period.TODAY)
        assertEquals(listOf(0f, 0.5f), axis.positions)
        assertEquals("09:00", axis.ticks.first().label)
        assertEquals("15:00", axis.ticks.last().label)
    }

    // 2026-11-05 실측 장 시간

    @Test fun sessionTicks_kospiDropsHourNearEnd() {
        assertEquals(
            listOf("09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:30"),
            labels(sessionTicks(seoul(5, 9), seoul(5, 15, 30))),
        )
    }

    @Test fun sessionTicks_usIndexDropsHourNearStart() {
        assertEquals(
            listOf("23:30", "01:00", "02:00", "03:00", "04:00", "05:00", "06:00"),
            labels(sessionTicks(seoul(4, 23, 30), seoul(5, 6))),
        )
    }

    @Test fun sessionTicks_treasuryKeepsEveryHour() {
        assertEquals(
            listOf("22:20", "23:00", "00:00", "01:00", "02:00", "03:00", "04:00", "05:00"),
            labels(sessionTicks(seoul(5, 22, 20), seoul(6, 5))),
        )
    }

    @Test fun sessionTicks_dayLongFxUsesFourHours() {
        assertEquals(
            listOf("09:00", "12:00", "16:00", "20:00", "00:00", "04:00", "08:59"),
            labels(sessionTicks(seoul(5, 9), seoul(6, 8, 59))),
        )
    }

    @Test fun sessionTicks_dayLongFuturesUsesFourHours() {
        assertEquals(
            listOf("14:00", "16:00", "20:00", "00:00", "04:00", "08:00", "12:00", "13:59"),
            labels(sessionTicks(seoul(5, 14), seoul(6, 13, 59))),
        )
    }

    @Test fun sessionTicks_endsAtZeroAndOneAndNeverMoreThanEight() {
        val spans = listOf(
            seoul(5, 9) to seoul(5, 15, 30), seoul(4, 23, 30) to seoul(5, 6),
            seoul(5, 9) to seoul(6, 8, 59), seoul(5, 14) to seoul(6, 13, 59),
            seoul(5, 0) to seoul(6, 0), seoul(5, 9) to seoul(5, 9, 30),
        )
        spans.forEach { (start, end) ->
            val ticks = sessionTicks(start, end)
            assertEquals(0f, ticks.first().position, 0f)
            assertEquals(1f, ticks.last().position, 0f)
            assertEquals(true, ticks.size <= 8)
        }
    }

    @Test fun sessionTicks_tooLongSessionOnlyHasEnds() {
        assertEquals(2, sessionTicks(0L, 4_000_000_000L).size)
    }

    @Test fun sessionTicks_labelsDoNotDependOnDeviceTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals("09:00", sessionTicks(seoul(5, 9), seoul(5, 15, 30)).first().label)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private val tenWide: (String) -> Float = { 10f }

    @Test fun placeXLabels_alignsFirstLeftLastRightOthersCenter() {
        val ticks = listOf(XTick(0f, "a"), XTick(0.5f, "b"), XTick(1f, "c"))
        val placed = placeXLabels(ticks, plotW = 120f, pad = 10f, gap = 0f, measure = tenWide)
        assertEquals(listOf(XLabel("a", 10f), XLabel("b", 55f), XLabel("c", 100f)), placed)
    }

    @Test fun placeXLabels_skipsLabelOverlappingPrevious() {
        val ticks = listOf(XTick(0f, "a"), XTick(0.04f, "b"), XTick(0.5f, "c"), XTick(1f, "d"))
        val placed = placeXLabels(ticks, plotW = 100f, pad = 0f, gap = 0f, measure = tenWide)
        assertEquals(listOf("a", "c", "d"), placed.map { it.text })
    }

    @Test fun placeXLabels_neverSkipsLastAndDropsOthersOverlappingIt() {
        val ticks = listOf(XTick(0f, "a"), XTick(0.5f, "b"), XTick(0.92f, "c"), XTick(1f, "d"))
        val placed = placeXLabels(ticks, plotW = 100f, pad = 0f, gap = 0f, measure = tenWide)
        assertEquals(listOf("a", "b", "d"), placed.map { it.text })
    }

    @Test fun placeXLabels_narrowChartKeepsOnlyLast() {
        val ticks = listOf(XTick(0f, "a"), XTick(1f, "b"))
        assertEquals(listOf("b"), placeXLabels(ticks, plotW = 15f, pad = 0f, gap = 0f, measure = tenWide).map { it.text })
    }

    @Test fun placeXLabels_gapCountsAsOverlap() {
        val ticks = listOf(XTick(0f, "a"), XTick(0.15f, "b"), XTick(1f, "c"))
        assertEquals(listOf("a", "b", "c"), placeXLabels(ticks, 100f, 0f, 0f, tenWide).map { it.text })
        assertEquals(listOf("a", "c"), placeXLabels(ticks, 100f, 0f, 1f, tenWide).map { it.text })
    }

    @Test fun placeXLabels_emptyTicksGiveNothing() {
        assertEquals(emptyList<XLabel>(), placeXLabels(emptyList(), 100f, 0f, 0f, tenWide))
    }

    @Test fun placeXLabels_matchesChartPointsPosition() {
        val xs = chartPoints(listOf(1.0, 2.0, 3.0), 120f, 50f, 10f, xPositions = listOf(0f, 0.5f, 1f)).map { it.first }
        val placed = placeXLabels(
            listOf(XTick(0f, "a"), XTick(0.5f, "b"), XTick(1f, "c")), 120f, 10f, 0f, { 0f },
        )
        assertEquals(xs, placed.map { it.left })
    }
}
