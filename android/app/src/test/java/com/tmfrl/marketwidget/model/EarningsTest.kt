package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EarningsTest {
    private fun summary(dates: String, estimate: String = "false") =
        """{"quoteSummary": {"result": [{"calendarEvents": {"maxAge": 1, "earnings": {"earningsDate": $dates,
            "isEarningsDateEstimate": $estimate, "earningsAverage": {"raw": 1.2}}}}], "error": null}}"""

    private val nvda = CustomTicker("T:NVDA", "NVDA", "엔비디아", KIND_EQUITY, null)
    private val samsung = CustomTicker("T:005930.KS", "005930.KS", "삼성전자", KIND_EQUITY, "005930")

    @Test fun parse_firstDateAndEstimateFlag() {
        assertEquals(EarningsDate(1796158800, false), parseEarnings(summary("""[{"raw": 1796158800, "fmt": "2026-12-01"}]""")))
        assertEquals(
            EarningsDate(1796101200, true),
            parseEarnings(summary("""[{"raw": 1796101200}, {"raw": 1796533200}]""", estimate = "true")),
        )
        assertNull(parseEarnings(summary("[]")))
        assertNull(parseEarnings("""{"quoteSummary": {"result": [{"calendarEvents": {}}], "error": null}}"""))
        assertNull(parseEarnings("""{"quoteSummary": {"result": null, "error": {"code": "Not Found"}}}"""))
    }

    @Test fun event_usTimeInSeoulWhenKnown() {
        // 뉴욕 12/1 16:00 = 서울 12/2 06:00
        val event = earningsEvent(nvda, EarningsDate(1796158800, false))
        assertEquals("엔비디아 실적", event.title)
        assertEquals("US", event.country)
        assertEquals("06:00", eventTime(event))
        assertEquals(LocalDate.of(2026, 12, 2), eventDate(event))
    }

    @Test fun event_midnightOrEstimateHasDateOnly() {
        // 뉴욕 12/1 00:00 = 시각 미정
        val midnight = earningsEvent(nvda, EarningsDate(1796101200, false))
        assertNull(midnight.kst)
        assertEquals("2026-12-01", midnight.date)
        val estimate = earningsEvent(nvda, EarningsDate(1796158800, true))
        assertEquals("엔비디아 실적(예상)", estimate.title)
        assertNull(estimate.kst)
        assertEquals("엔비디아 실적", eventShortLabel(estimate.title))
    }

    @Test fun event_koreanUsesSeoulDate() {
        val event = earningsEvent(samsung, EarningsDate(1796101200, true))
        assertEquals("KR", event.country)
        assertEquals("2026-12-01", event.date)
        assertTrue(isKoreanSymbol("247540.KQ"))
        assertFalse(isKoreanSymbol("AAPL"))
    }

    @Test fun events_onlyListedTickersWithinWindow() {
        val far = CustomTicker("E:AAPL", "AAPL", "애플", KIND_EQUITY, null)
        val entries = listOf(
            EarningsEntry("NVDA", EarningsDate(1796158800), at = 1),
            EarningsEntry("005930.KS", EarningsDate(1796101200, true), at = 1),
            EarningsEntry("AAPL", EarningsDate(1796158800 + 60L * 86400), at = 1),
            EarningsEntry("MSFT", EarningsDate(1796158800), at = 1),
        )
        val events = earningsEvents(entries, listOf(nvda, samsung, far), LocalDate.of(2026, 11, 25))
        assertEquals(listOf("엔비디아 실적", "삼성전자 실적(예상)"), events.map { it.title })
        assertEquals(2, eventStars("엔비디아 실적"))
    }

    @Test fun statusText() {
        assertEquals("받는 중", earningsStatusText(nvda, null))
        assertEquals("받지 못함 · 다시 시도 중", earningsStatusText(nvda, EarningsEntry("NVDA", failed = true)))
        assertEquals("예정된 발표일 없음", earningsStatusText(nvda, EarningsEntry("NVDA", null, at = 1)))
        assertEquals("12/2(수) 06:00 발표", earningsStatusText(nvda, EarningsEntry("NVDA", EarningsDate(1796158800), at = 1)))
        assertEquals("12/1(화) 발표 예상", earningsStatusText(samsung, EarningsEntry("005930.KS", EarningsDate(1796101200, true), at = 1)))
    }

    @Test fun settings_defaultsRemoveRestoreAndAdd() {
        val s = WidgetSettings()
        assertEquals(DEFAULT_EARNINGS, earningsTickers(s))
        assertTrue(isEarningsAdded(s, "005930.KS"))
        val removed = removeEarnings(s, "005930.KS")
        assertFalse(isEarningsAdded(removed, "005930.KS"))
        assertEquals(listOf("005930.KS"), removed.earnings_removed)
        assertTrue(isEarningsAdded(addEarnings(removed, "005930.KS", "삼성전자"), "005930.KS"))
        assertEquals(DEFAULT_EARNINGS, earningsTickers(restoreEarningsDefaults(removed)))
        val added = addEarnings(s, "AMD", "AMD")
        assertEquals("AMD", earningsTickers(added).last().symbol)
        assertEquals(added, addEarnings(added, "AMD", "AMD"))
        assertFalse(isEarningsAdded(removeEarnings(added, "AMD"), "AMD"))
        assertTrue(removeEarnings(added, "AMD").earnings_removed.isEmpty())
    }

    @Test fun cache_roundTripsAndBrokenIsEmpty() {
        val cache = EarningsCache(listOf(EarningsEntry("NVDA", EarningsDate(1796158800, true), at = 5)))
        assertEquals(cache, parseEarningsCache(encodeEarningsCache(cache)))
        assertEquals(EarningsCache(), parseEarningsCache("not json"))
    }

    @Test fun due_everyTwelveHours() {
        assertTrue(earningsDue(null, 0))
        assertFalse(earningsDue(0, 11 * 3_600_000L))
        assertTrue(earningsDue(0, 12 * 3_600_000L))
    }
}
