package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class FormatTest {
    @Test fun formatPrice_bigNumbersHaveCommasAndNoDecimals() {
        assertEquals("68,123", formatPrice(68123.4))
        assertEquals("1,380", formatPrice(1380.4))
    }

    @Test fun formatPrice_smallNumbersHaveTwoDecimals() {
        assertEquals("4.25", formatPrice(4.25))
        assertEquals("18.90", formatPrice(18.9))
    }

    @Test fun formatPrice_nullIsDash() {
        assertEquals("-", formatPrice(null))
    }

    @Test fun formatChangePct_signAndTwoDecimals() {
        assertEquals("+0.47%", formatChangePct(0.47))
        assertEquals("-1.20%", formatChangePct(-1.2))
        assertEquals("0.00%", formatChangePct(0.0))
        assertEquals("", formatChangePct(null))
    }

    @Test fun formatEventDate_koreanWeekday() {
        assertEquals("11/13(금)", formatEventDate(LocalDate.of(2026, 11, 13)))
    }

    @Test fun formatUpdatedAt_sameDayShowsTimeOnly() {
        val updated = OffsetDateTime.parse("2026-10-30T10:30:00+09:00")
        val now = ZonedDateTime.of(2026, 10, 30, 11, 0, 0, 0, SEOUL)
        assertEquals("10:30 기준", formatUpdatedAt(updated, now))
    }

    @Test fun formatUpdatedAt_otherDayShowsDate() {
        val updated = OffsetDateTime.parse("2026-10-28T10:30:00+09:00")
        val now = ZonedDateTime.of(2026, 10, 30, 11, 0, 0, 0, SEOUL)
        assertEquals("10/28 10:30 기준", formatUpdatedAt(updated, now))
    }

    @Test fun formatUpdatedAt_comparesInSeoulEvenIfNowIsUtc() {
        // UTC 10/30 16:00 = 서울 10/31 01:00
        val updated = OffsetDateTime.parse("2026-10-30T23:30:00+09:00")
        val now = ZonedDateTime.of(2026, 10, 30, 16, 0, 0, 0, ZoneOffset.UTC)
        assertEquals("10/30 23:30 기준", formatUpdatedAt(updated, now))
    }

    @Test fun seoulToday_usesSeoulDate() {
        val now = ZonedDateTime.of(2026, 10, 30, 16, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 10, 31), seoulToday(now))
    }

    @Test fun upcomingEvents_dropsPastAndLimits() {
        val events = listOf(
            Event("2026-10-29", "지난 일정", "US"),
            Event("2026-11-13", "미국 CPI", "US"),
            Event("2026-10-30", "오늘 일정", "KR"),
            Event("2026-11-01", "a", "US"),
            Event("2026-11-02", "b", "US"),
            Event("2026-11-03", "c", "US"),
        )
        val result = upcomingEvents(events, LocalDate.of(2026, 10, 30))
        assertEquals(listOf("오늘 일정", "a", "b", "c"), result.map { it.title })
    }

    @Test fun trendOf_signs() {
        assertEquals(Trend.UP, trendOf(0.47))
        assertEquals(Trend.DOWN, trendOf(-0.1))
        assertEquals(Trend.FLAT, trendOf(0.0))
        assertEquals(Trend.FLAT, trendOf(null))
    }
}
