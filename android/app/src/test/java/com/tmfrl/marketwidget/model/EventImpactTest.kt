package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EventImpactTest {
    private val cpi = Event("2026-12-10", "미국 CPI", "US", kst = "2026-12-10T22:30:00+09:00")
    private val fomc = Event("2026-12-09", "FOMC 금리 결정", "US", kst = "2026-12-10T04:00:00+09:00")
    private val t = java.time.OffsetDateTime.parse("2026-12-10T22:30:00+09:00").toEpochSecond()

    @Test fun eventDateAndTime_useSeoulClock() {
        assertEquals(LocalDate.of(2026, 12, 10), eventDate(fomc))
        assertEquals("04:00", eventTime(fomc))
        assertEquals("22:30", eventTime(cpi))
        val old = Event("2026-12-09", "FOMC 금리 결정", "US")
        assertEquals(LocalDate.of(2026, 12, 9), eventDate(old))
        assertNull(eventTime(old))
        assertNull(eventTime(old.copy(kst = "bad")))
    }

    @Test fun eventDays_showTimeAndUseSeoulDate() {
        val days = eventDays(listOf(fomc, cpi), LocalDate.of(2026, 12, 1))
        assertEquals(listOf("12/10(목) 04:00 FOMC 금리 결정·미국 CPI ★★★ D-9"), days.map { it.text })
    }

    @Test fun upcomingEvents_sortBySeoulDateThenTime() {
        val list = upcomingEvents(listOf(cpi, fomc), LocalDate.of(2026, 12, 1))
        assertEquals(listOf("FOMC 금리 결정", "미국 CPI"), list.map { it.title })
    }

    @Test fun marks_onlyInsideWindowAndMergeSameTime() {
        val jobs = Event("2026-12-10", "미국 고용보고서", "US", kst = "2026-12-10T22:30:00+09:00")
        val marks = eventMarks(listOf(cpi, jobs, fomc), t - 3600, t + 3600)
        assertEquals(listOf("CPI·고용"), marks.map { it.label })
        assertEquals(0.5f, marks.single().position, 1e-6f)
        assertEquals(emptyList<EventMark>(), eventMarks(listOf(cpi), t + 1, t + 3600))
        assertEquals("금통위", eventShortLabel("한국은행 금리 결정"))
    }

    @Test fun impact_fromBarBeforeReleaseToNowAndLow() {
        // 22:25 봉(종가 = 22:30 가격) 100 → 이후 98, 99.5
        val times = listOf(t - 600, t - 300, t, t + 300)
        val values = listOf(101.0, 100.0, 98.0, 99.5)
        val impact = eventImpact(times, values, t, "CPI")!!
        assertEquals(-0.5, impact.change, 0.0)
        assertEquals(-2.0, impact.low, 0.0)
        assertNull(eventImpact(times, values, t + 600, "CPI"))
        assertNull(eventImpact(times, values, t - 600, "CPI"))
        assertNull(eventImpact(times.dropLast(1), values, t, "CPI"))
    }

    @Test fun latestImpact_skipsFutureEvents() {
        val times = listOf(t - 300, t, t + 300)
        val values = listOf(100.0, 99.0, 101.0)
        val marks = listOf(EventMark(0.5f, "CPI", t), EventMark(0.9f, "FOMC", t + 7200))
        assertEquals("CPI", latestEventImpact(marks, times, values)!!.label)
    }
}
