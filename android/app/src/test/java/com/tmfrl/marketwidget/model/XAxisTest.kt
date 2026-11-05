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

    /** 2026-11-05 실제 수집(cc9fe70)에서 뽑은 날짜. 값은 날짜 수만큼 채워 실제 기간 자르기를 거치게 한다. */
    private val real: Map<String, Quote> by lazy {
        val text = XAxisTest::class.java.classLoader!!.getResource("chart_dates_2026_11_05.json")!!.readText()
        parseMarketData(text).quotes.associate { q ->
            q.id to q.copy(
                daily = q.daily_dates.map { 1.0 },
                weekly = q.weekly_dates.map { 1.0 },
                max_closes = q.max_dates.map { 1.0 },
            )
        }
    }

    private fun assertTicks(expectedLabels: List<String>, expectedPositions: List<Float>, ticks: List<XTick>) {
        assertEquals(expectedLabels, labels(ticks))
        assertEquals(expectedPositions.size, ticks.size)
        expectedPositions.forEachIndexed { i, position ->
            assertEquals(expectedLabels[i], position, ticks[i].position, 0.0001f)
        }
    }

    @Test fun realDates_kospiOneMonthIsMondays() {
        assertTicks(
            listOf("10/12", "10/19", "10/26", "11/2"), listOf(0.1429f, 0.3810f, 0.6190f, 0.8571f),
            chartXAxis(real.getValue("KS11"), Period.ONE_MONTH).ticks,
        )
    }

    @Test fun realDates_kospiThreeMonthsUsesFirstTradingDay() {
        // 11/1은 일요일이라 11/2가 그 달의 첫 거래일이다
        assertTicks(
            listOf("9/1", "10/1", "11/2"), listOf(0.3065f, 0.6290f, 0.9516f),
            chartXAxis(real.getValue("KS11"), Period.THREE_MONTHS).ticks,
        )
    }

    @Test fun realDates_kospiOneYearIsQuarters() {
        assertTicks(
            listOf("26.1", "26.4", "26.7", "26.10"), listOf(0.1598f, 0.4016f, 0.6516f, 0.9057f),
            chartXAxis(real.getValue("KS11"), Period.ONE_YEAR).ticks,
        )
    }

    @Test fun realDates_kospiFiveYearsIsYears() {
        assertTicks(
            listOf("2022", "2023", "2024", "2025", "2026"), listOf(0.0344f, 0.2328f, 0.4313f, 0.6336f, 0.8321f),
            chartXAxis(real.getValue("KS11"), Period.FIVE_YEARS).ticks,
        )
    }

    @Test fun realDates_kospiMaxUsesFiveYearStep() {
        assertTicks(
            listOf("2000", "2005", "2010", "2015", "2020", "2025"),
            listOf(0.1034f, 0.2698f, 0.4361f, 0.6045f, 0.7708f, 0.9351f),
            chartXAxis(real.getValue("KS11"), Period.MAX).ticks,
        )
    }

    @Test fun realDates_sp500MaxUsesTenYearStep() {
        assertTicks(
            listOf("1980", "1990", "2000", "2010", "2020"), listOf(0.1771f, 0.3521f, 0.5292f, 0.7022f, 0.8773f),
            chartXAxis(real.getValue("GSPC"), Period.MAX).ticks,
        )
    }

    @Test fun realDates_bitcoinTradesOnWeekends() {
        val btc = real.getValue("BTC")
        assertTicks(
            listOf("10/19", "10/26", "11/2"), listOf(0.1905f, 0.5238f, 0.8571f),
            chartXAxis(btc, Period.ONE_MONTH).ticks,
        )
        // 코스피와 달리 일요일인 11/1에도 거래해 그날이 경계다
        assertTicks(listOf("10/1", "11/1"), listOf(0.4355f, 0.9355f), chartXAxis(btc, Period.THREE_MONTHS).ticks)
        assertTicks(
            listOf("2016", "2018", "2020", "2022", "2024", "2026"),
            listOf(0.1071f, 0.2727f, 0.4343f, 0.6000f, 0.7636f, 0.9293f),
            chartXAxis(btc, Period.MAX).ticks,
        )
    }

    @Test fun dateTicks_firstValueOnBoundaryIsNotATick() {
        // 첫 값 2026-11-02는 월요일이지만 앞 구간을 알 수 없다
        val dates = listOf("2026-11-02", "2026-11-09", "2026-11-16", "2026-11-17")
        assertTicks(listOf("11/9", "11/16"), listOf(0.3333f, 0.6667f), dateTicks(dates, 4, Period.ONE_MONTH))
    }

    @Test fun dateTicks_holidayMondayUsesActualFirstDayOfWeek() {
        val dates = listOf("2026-10-02", "2026-10-06", "2026-10-13", "2026-10-14")
        assertEquals(listOf("10/6", "10/13"), labels(dateTicks(dates, 4, Period.ONE_MONTH)))
    }

    @Test fun dateTicks_quarterSkipsOtherMonths() {
        val dates = listOf("2025-12-30", "2026-01-02", "2026-02-02", "2026-03-02", "2026-04-01", "2026-05-04")
        assertEquals(listOf("26.1", "26.4"), labels(dateTicks(dates, 6, Period.ONE_YEAR)))
    }

    @Test fun dateTicks_maxStepIsSmallestWithAtMostSixTicks() {
        val everyYear: (IntRange) -> List<String> = { years -> years.map { "$it-01-02" } }
        // 2001~2006: 첫 값을 뺀 경계 5개. 간격 1
        assertEquals(
            listOf("2002", "2003", "2004", "2005", "2006"),
            labels(dateTicks(everyYear(2001..2006), 6, Period.MAX)),
        )
        // 2001~2008: 경계 7개라 간격 2
        assertEquals(listOf("2002", "2004", "2006", "2008"), labels(dateTicks(everyYear(2001..2008), 8, Period.MAX)))
        // 1900~2026: 간격 10은 12개라 간격 20. 1900은 첫 값이라 경계가 아니다
        assertEquals(
            listOf("1920", "1940", "1960", "1980", "2000", "2020"),
            labels(dateTicks(everyYear(1900..2026), 127, Period.MAX)),
        )
    }

    @Test fun dateTicks_maxYearGapSkipsYearWithoutPoint() {
        // 2000은 첫 값이라 경계가 아니고, 2002에는 점이 없다
        val dates = listOf("2000-01-03", "2001-01-03", "2003-01-03", "2004-01-03")
        assertEquals(listOf("2001", "2003", "2004"), labels(dateTicks(dates, 4, Period.MAX)))
    }

    @Test fun dateTicks_repeatedDatesDoNotStackTicks() {
        // max_dates는 처음·마지막 봉이 두 번 들어간다
        val dates = listOf("1999-12-30", "1999-12-30", "2000-01-03", "2000-01-03", "2001-01-02", "2001-01-02")
        assertTicks(listOf("2000", "2001"), listOf(0.4f, 0.8f), dateTicks(dates, 6, Period.MAX))
    }

    @Test fun dateTicks_unsortedDatesGiveNoTicks() {
        val dates = listOf("2026-01-02", "2026-03-02", "2026-02-02", "2026-04-01")
        listOf(Period.ONE_MONTH, Period.THREE_MONTHS, Period.ONE_YEAR, Period.FIVE_YEARS, Period.MAX).forEach {
            assertEquals(emptyList<XTick>(), dateTicks(dates, 4, it))
        }
    }

    @Test fun dateTicks_unreadableDateAnywhereGivesNoTicks() {
        val dates = listOf("2026-01-02", "bad", "2026-02-02", "2026-03-02", "2026-04-01", "2026-05-04")
        assertEquals(emptyList<XTick>(), dateTicks(dates, 6, Period.THREE_MONTHS))
    }

    // 경계가 2개 미만이면 균등 간격으로 대체한다

    @Test fun dateTicks_fallbackFiveEvenTicksIncludingEnds() {
        // 2026-01-01~09에서 주 경계는 1/5 하나뿐이다
        val ticks = dateTicks(days(9), 9, Period.ONE_MONTH)
        assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f, 1f), ticks.map { it.position })
        assertEquals(listOf("1/1", "1/3", "1/5", "1/7", "1/9"), labels(ticks))
    }

    @Test fun dateTicks_noBoundaryAlsoFallsBack() {
        val dates = listOf("2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06", "2026-11-07")
        // 위치 0, 1.25, 2.5, 3.75, 5를 반올림하면 0, 1, 3, 4, 5번 점이다
        assertEquals(listOf("11/2", "11/3", "11/5", "11/6", "11/7"), labels(dateTicks(dates, 6, Period.ONE_MONTH)))
    }

    @Test fun dateTicks_twoBoundariesDoNotFallBack() {
        // 2026-01-01~03-04: 달 경계 2/1, 3/1
        assertTicks(listOf("2/1", "3/1"), listOf(0.5f, 0.9516f), dateTicks(days(63), 63, Period.THREE_MONTHS))
    }

    @Test fun dateTicks_fallbackFewerThanFiveValuesUsesEachValue() {
        assertEquals(4, dateTicks(days(4), 4, Period.ONE_MONTH).size)
        assertEquals(listOf(0f, 1f), dateTicks(days(2), 2, Period.ONE_MONTH).map { it.position })
        assertEquals(emptyList<XTick>(), dateTicks(days(1), 1, Period.ONE_MONTH))
    }

    @Test fun dateTicks_fallbackRepeatedDatesDoNotStackTicks() {
        val ticks = dateTicks(listOf("2026-11-05", "2026-11-05", "2026-11-06"), 3, Period.MAX)
        assertEquals(listOf(0f, 1f), ticks.map { it.position })
        assertEquals(emptyList<XTick>(), dateTicks(listOf("2026-11-05"), 1, Period.MAX))
        assertEquals(listOf(0f), dateTicks(listOf("2026-11-05", "2026-11-05"), 2, Period.MAX).map { it.position })
    }

    @Test fun dateTicks_boundaryFiveYearsIsYearOnly() {
        val dates = listOf("2024-12-30", "2025-01-06", "2026-01-05", "2026-11-02")
        assertEquals(listOf("2025", "2026"), labels(dateTicks(dates, 4, Period.FIVE_YEARS)))
    }

    @Test fun dateTicks_fallbackLabelFormatPerPeriod() {
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
        val kospi = real.getValue("KS11")
        val withDrawdowns = kospi.copy(max_drawdowns = (1..246).map { -it.toDouble() })
        assertEquals(494, kospi.max_dates.size)
        assertEquals(6, chartXAxis(withDrawdowns, Period.MAX).ticks.size)
        assertEquals(chartXAxis(kospi, Period.MAX), chartXAxis(withDrawdowns, Period.MAX))
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

    // 장 시간 밖 봉 방어: 봉의 절반을 넘게 밖이면 믿지 않는다

    @Test fun todayXAxis_allBarsOutsideMeansUntrusted() {
        // 주말: 봉은 금요일인데 장 시간은 다음 거래일을 가리킨다
        val friday = listOf(seoul(6, 9), seoul(6, 10), seoul(6, 11))
        assertEquals(ChartXAxis(), todayXAxis(3, friday, seoul(9, 9), seoul(9, 15, 30)))
    }

    @Test fun todayXAxis_moreThanHalfOutsideMeansUntrusted() {
        val times = listOf(seoul(5, 8), seoul(5, 8, 30), seoul(5, 8, 40), seoul(5, 10), seoul(5, 11))
        assertEquals(ChartXAxis(), todayXAxis(5, times, seoul(5, 9), seoul(5, 15)))
    }

    @Test fun todayXAxis_exactlyHalfOutsideIsTrusted() {
        val times = listOf(seoul(5, 8), seoul(5, 8, 30), seoul(5, 12), seoul(5, 15))
        val axis = todayXAxis(4, times, seoul(5, 9), seoul(5, 15))
        assertEquals(listOf(0f, 0f, 0.5f, 1f), axis.positions)
        assertEquals("09:00", axis.ticks.first().label)
    }

    @Test fun todayXAxis_oneClosingAuctionBarSticksToEnd() {
        val times = listOf(seoul(5, 9), seoul(5, 12), seoul(5, 15, 30), seoul(5, 15, 32))
        val axis = todayXAxis(4, times, seoul(5, 9), seoul(5, 15, 30))
        assertEquals(1f, axis.positions!![3], 0f)
        assertEquals("15:30", axis.ticks.last().label)
    }

    @Test fun todayXAxis_barsOnSessionEdgesCountAsInside() {
        val times = listOf(seoul(5, 9), seoul(5, 15))
        assertEquals(listOf(0f, 1f), todayXAxis(2, times, seoul(5, 9), seoul(5, 15)).positions)
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

    @Test fun placeXLabels_centersEachLabelOnItsTick() {
        val ticks = listOf(XTick(0f, "a"), XTick(0.5f, "b"), XTick(1f, "c"))
        val placed = placeXLabels(ticks, plotW = 120f, pad = 10f, gap = 0f, measure = tenWide)
        assertEquals(listOf(XLabel("a", 5f, 10f), XLabel("b", 55f, 60f), XLabel("c", 105f, 110f)), placed)
    }

    @Test fun placeXLabels_pushesEndLabelsInsidePlot() {
        // 오늘 차트의 시작·끝 글자는 이 규칙으로 양 끝에 붙는다. 세로선 자리 x는 밀리지 않는다.
        val ticks = listOf(XTick(0f, "a"), XTick(1f, "b"))
        val placed = placeXLabels(ticks, plotW = 100f, pad = 2f, gap = 0f, measure = tenWide)
        assertEquals(listOf(XLabel("a", 0f, 2f), XLabel("b", 90f, 98f)), placed)
    }

    @Test fun placeXLabels_boundaryLastTickStaysCentered() {
        // 경계 눈금은 마지막 눈금이 오른쪽 끝에 있지 않다
        val ticks = listOf(XTick(0.25f, "a"), XTick(0.75f, "b"))
        val placed = placeXLabels(ticks, plotW = 100f, pad = 0f, gap = 0f, measure = tenWide)
        assertEquals(listOf(XLabel("a", 20f, 25f), XLabel("b", 70f, 75f)), placed)
    }

    @Test fun placeXLabels_labelWiderThanPlotDoesNotThrow() {
        val placed = placeXLabels(listOf(XTick(0.5f, "a")), plotW = 20f, pad = 0f, gap = 0f, measure = { 30f })
        assertEquals(listOf(XLabel("a", 0f, 10f)), placed)
    }

    @Test fun placeXLabels_skippedTickHasNoLineEither() {
        // 세로 보조선은 결과의 x로 그리므로 건너뛴 눈금에는 선도 없다
        val ticks = listOf(XTick(0.5f, "a"), XTick(0.52f, "b"), XTick(0.9f, "c"))
        val placed = placeXLabels(ticks, plotW = 100f, pad = 0f, gap = 0f, measure = tenWide)
        assertEquals(2, placed.size)
        assertEquals(50f, placed[0].x, 0.001f)
        assertEquals(90f, placed[1].x, 0.001f)
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
        assertEquals(xs, placed.map { it.x })
    }
}
