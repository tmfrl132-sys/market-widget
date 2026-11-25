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

    @Test fun events_onlyShownEquitiesWithinWindow() {
        val etf = CustomTicker("T:SPY", "SPY", "SPY", KIND_ETF, null)
        val far = CustomTicker("T:AAPL", "AAPL", "애플", KIND_EQUITY, null)
        val q = Quote("x", "x", 1.0, 0.0, 0.0)
        val entries = listOf(
            CustomEntry(nvda.id, q, earnings = EarningsDate(1796158800)),
            CustomEntry(samsung.id, q, earnings = EarningsDate(1796101200, true)),
            CustomEntry(etf.id, q, earnings = EarningsDate(1796158800)),
            CustomEntry(far.id, q, earnings = EarningsDate(1796158800 + 60L * 86400)),
        )
        val settings = WidgetSettings(custom = listOf(nvda, samsung, etf, far), hidden = listOf(samsung.id))
        val events = earningsEvents(entries, settings, LocalDate.of(2026, 11, 25))
        assertEquals(listOf("엔비디아 실적"), events.map { it.title })
        assertEquals(2, eventStars("엔비디아 실적"))
    }

    @Test fun due_everyTwelveHours() {
        assertTrue(earningsDue(null, 0))
        assertFalse(earningsDue(0, 11 * 3_600_000L))
        assertTrue(earningsDue(0, 12 * 3_600_000L))
    }
}
