package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EventLineTest {
    private val today = LocalDate.of(2026, 11, 18)

    private fun us(date: String, title: String) = Event(date, title, "US")
    private fun kr(date: String, title: String) = Event(date, title, "KR")

    @Test fun mergeTitles_repeatedCountryPrefixWrittenOnce() {
        assertEquals("미국 GDP·PCE", mergeEventTitles(listOf(us("2026-11-25", "미국 GDP"), us("2026-11-25", "미국 PCE"))))
    }

    @Test fun mergeTitles_differentCountriesKeepTitles() {
        assertEquals(
            "미국 CPI·한국 CPI",
            mergeEventTitles(listOf(us("2026-11-25", "미국 CPI"), kr("2026-11-25", "한국 CPI"))),
        )
    }

    @Test fun mergeTitles_groupsSameCountryTogether() {
        val events = listOf(us("2026-11-25", "미국 GDP"), kr("2026-11-25", "한국 CPI"), us("2026-11-25", "미국 PCE"))
        assertEquals("미국 GDP·PCE·한국 CPI", mergeEventTitles(events))
    }

    @Test fun mergeTitles_titleWithoutPrefixStaysWhole() {
        val events = listOf(us("2026-12-09", "FOMC 금리 결정"), us("2026-12-09", "미국 CPI"), us("2026-12-09", "미국 PCE"))
        assertEquals("FOMC 금리 결정·미국 CPI·PCE", mergeEventTitles(events))
    }

    @Test fun mergeTitles_shortensBankOfKorea() {
        assertEquals("한은 금리 결정", mergeEventTitles(listOf(kr("2026-11-26", "한국은행 금리 결정"))))
    }

    @Test fun mergeTitles_dropsDuplicatesAndBlanks() {
        val events = listOf(us("2026-11-25", "미국 GDP"), us("2026-11-25", "미국 GDP"), us("2026-11-25", "  "))
        assertEquals("미국 GDP", mergeEventTitles(events))
    }

    @Test fun eventDays_matchesApprovedLayout() {
        val events = listOf(
            us("2026-11-25", "미국 GDP"),
            us("2026-11-25", "미국 PCE"),
            kr("2026-11-26", "한국은행 금리 결정"),
            us("2026-12-04", "미국 고용보고서"),
        )
        assertEquals(
            listOf("11/25(수) 미국 GDP·PCE D-7", "11/26(목) 한은 금리 결정 D-8", "12/4(금) 미국 고용보고서 D-16"),
            eventDays(events, today).map { it.text },
        )
    }

    @Test fun eventDays_todayTomorrowAndCountWithHighlight() {
        val days = eventDays(
            listOf(us("2026-11-18", "미국 CPI"), us("2026-11-19", "미국 PCE"), us("2026-11-20", "미국 GDP")),
            today,
        )
        assertEquals(listOf("오늘", "내일", "D-2"), days.map { it.dday })
        assertEquals(listOf(true, true, false), days.map { it.soon })
    }

    @Test fun eventDays_usesGivenSeoulDateAcrossMonthEnd() {
        val days = eventDays(listOf(us("2026-12-01", "미국 CPI")), LocalDate.of(2026, 11, 30))
        assertEquals("내일", days.single().dday)
    }

    @Test fun eventDays_dropsPastEvents() {
        val days = eventDays(listOf(us("2026-11-17", "미국 CPI"), us("2026-11-25", "미국 GDP")), today)
        assertEquals(listOf(LocalDate.of(2026, 11, 25)), days.map { it.date })
    }

    @Test fun eventDays_sortsByDate() {
        val days = eventDays(listOf(us("2026-12-04", "미국 CPI"), us("2026-11-25", "미국 GDP")), today)
        assertEquals(listOf(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 4)), days.map { it.date })
    }

    @Test fun eventDays_emptyWhenNothingAhead() {
        assertTrue(eventDays(emptyList(), today).isEmpty())
        assertTrue(eventDays(listOf(us("2026-11-01", "미국 CPI")), today).isEmpty())
    }

    @Test fun eventDays_skipsUnreadableDateAndBlankTitle() {
        val days = eventDays(listOf(us("미정", "미국 CPI"), us("2026-11-20", " "), us("2026-11-25", "미국 GDP")), today)
        assertEquals(listOf("미국 GDP"), days.map { it.title })
    }

    @Test fun upcomingEvents_skipsUnreadableDate() {
        val result = upcomingEvents(listOf(us("미정", "미국 CPI"), us("2026-11-25", "미국 GDP")), today)
        assertEquals(listOf("미국 GDP"), result.map { it.title })
    }

    @Test fun estimateWidth_scalesWithFontSize() {
        assertEquals(20f, estimateTextWidthDp("가나", 10f), 0.001f)
        assertEquals(16.2f, estimateTextWidthDp("A1 ", 10f), 0.001f)
        assertEquals(40f, estimateTextWidthDp("가나", 20f), 0.001f)
    }

    @Test fun lineWidth_subtractsPaddingLabelAndGap() {
        assertEquals(452f, eventLineWidthDp(500f, fontScale = 1f), 0.001f)
        assertEquals(447.6f, eventLineWidthDp(500f, fontScale = 1.2f), 0.001f)
    }

    private val threeDays = eventDays(
        listOf(us("2026-11-25", "미국 GDP"), kr("2026-11-26", "한국은행 금리 결정"), us("2026-12-04", "미국 고용보고서")),
        today,
    )

    private fun widthOf(count: Int): Float =
        estimateTextWidthDp(threeDays.take(count).joinToString(" · ") { it.text }, 11f)

    @Test fun fitting_countsWhatFitsExactly() {
        assertEquals(3, eventDaysFitting(threeDays, widthOf(3) + 0.01f, 11f))
        assertEquals(2, eventDaysFitting(threeDays, widthOf(3) - 0.01f, 11f))
        assertEquals(2, eventDaysFitting(threeDays, widthOf(2) + 0.01f, 11f))
        assertEquals(1, eventDaysFitting(threeDays, widthOf(2) - 0.01f, 11f))
    }

    @Test fun fitting_atLeastOneEvenWhenTooNarrow() {
        assertEquals(1, eventDaysFitting(threeDays, 10f, 11f))
    }

    @Test fun fitting_zeroWithoutEvents() {
        assertEquals(0, eventDaysFitting(emptyList(), 500f, 11f))
    }

    @Test fun fitting_largerFontShowsFewer() {
        val available = widthOf(3) + 0.01f
        assertTrue(eventDaysFitting(threeDays, available, 11f * 1.3f) < 3)
    }

    @Test fun lineText_splitsHighlightedPart() {
        val days = eventDays(listOf(us("2026-11-18", "미국 CPI"), us("2026-11-25", "미국 GDP")), today)
        assertEquals(
            EventLineText("11/18(수) 미국 CPI 오늘", " · 11/25(수) 미국 GDP D-7"),
            eventLineText(days),
        )
    }

    @Test fun lineText_noLeadingSeparatorWithoutHighlight() {
        val days = eventDays(listOf(us("2026-11-25", "미국 GDP")), today)
        assertEquals(EventLineText("", "11/25(수) 미국 GDP D-7"), eventLineText(days))
    }

    @Test fun lineText_onlyHighlightedPart() {
        val days = eventDays(listOf(us("2026-11-19", "미국 GDP")), today)
        assertEquals(EventLineText("11/19(목) 미국 GDP 내일", ""), eventLineText(days))
    }

    @Test fun lineTap_opensAndCloses() {
        assertTrue(afterEventLineTap(open = false, selected = "KS11"))
        assertFalse(afterEventLineTap(open = true, selected = "KS11"))
    }

    @Test fun lineTap_staysClosedWithoutChartBehind() {
        assertFalse(afterEventLineTap(open = false, selected = null))
    }

    @Test fun listOpen_closedWhenNoUpcomingEvents() {
        assertTrue(eventListOpen(stored = true, hasUpcoming = true))
        assertFalse(eventListOpen(stored = true, hasUpcoming = false))
        assertFalse(eventListOpen(stored = false, hasUpcoming = true))
    }

    @Test fun tileTap_withListOpenOpensTappedChart() {
        assertEquals(ChartSelection("BTC"), tileTapSelection(ChartSelection("KS11"), "BTC", auto = false, listOpen = true))
        assertEquals(ChartSelection("BTC"), tileTapSelection(ChartSelection("KS11"), "BTC", auto = true, listOpen = true))
    }

    @Test fun tileTap_withListOpenSameTileReturnsToItsChart() {
        assertEquals(ChartSelection("KS11"), tileTapSelection(ChartSelection("KS11"), "KS11", auto = false, listOpen = true))
        assertEquals(
            ChartSelection("KS11", "NDX"),
            tileTapSelection(ChartSelection("KS11", "NDX"), "KS11", auto = false, listOpen = true),
        )
    }

    @Test fun tileTap_withListOpenIgnoresComparePicking() {
        assertEquals(
            ChartSelection("BTC"),
            tileTapSelection(ChartSelection("KS11", picking = true), "BTC", auto = false, listOpen = true),
        )
    }

    @Test fun tileTap_withListClosedKeepsExistingBehavior() {
        assertEquals(ChartSelection(), tileTapSelection(ChartSelection("KS11"), "KS11", auto = false, listOpen = false))
        assertEquals(
            ChartSelection("KS11", "NDX"),
            tileTapSelection(ChartSelection("KS11", picking = true), "NDX", auto = false, listOpen = false),
        )
    }
}
