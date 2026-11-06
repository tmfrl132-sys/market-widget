package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

class MarketStatusTest {
    private fun at(text: String): Long = OffsetDateTime.parse(text).toEpochSecond()

    private fun instant(text: String): Instant = OffsetDateTime.parse(text).toInstant()

    private fun quote(tz: String?, start: String?, end: String?, last: Long?, alwaysOpen: Boolean = false) = Quote(
        "X", "X", 1.0, 0.0, 0.0,
        intraday = listOf(1.0),
        intraday_times = listOfNotNull(last),
        session_start = start?.let { at(it) },
        session_end = end?.let { at(it) },
        market_tz = tz,
        always_open = alwaysOpen,
    )

    // 코스피 11-06(금) 장
    private fun kospi(last: String) = quote("Asia/Seoul", "2026-11-06T09:00+09:00", "2026-11-06T15:30+09:00", at(last))

    private fun stateOf(q: Quote, asOf: String) = marketState(q, instant(asOf))

    @Test fun kospi_freshBarDuringSessionIsLive() {
        assertEquals(MarketStatus.LIVE, stateOf(kospi("2026-11-06T09:55+09:00"), "2026-11-06T10:00+09:00")?.status)
    }

    @Test fun kospi_barExactlyThirtyMinutesOldIsStillLive() {
        assertEquals(MarketStatus.LIVE, stateOf(kospi("2026-11-06T09:30+09:00"), "2026-11-06T10:00+09:00")?.status)
    }

    @Test fun kospi_staleBarDuringSessionIsClosed() {
        assertEquals(MarketStatus.CLOSED, stateOf(kospi("2026-11-06T09:20+09:00"), "2026-11-06T10:00+09:00")?.status)
    }

    @Test fun kospi_afterCloseSameDayIsClosedAtSessionEnd() {
        val state = stateOf(kospi("2026-11-06T15:30+09:00"), "2026-11-06T16:00+09:00")!!
        assertEquals(MarketStatus.CLOSED, state.status)
        assertEquals("마감 15:30", formatMarketState(state))
    }

    @Test fun kospi_saturdayIsHoliday() {
        val state = stateOf(kospi("2026-11-06T15:30+09:00"), "2026-11-07T10:00+09:00")!!
        assertEquals(MarketStatus.HOLIDAY, state.status)
        assertEquals(LocalDate.of(2026, 11, 6), state.sessionDate)
        assertEquals("휴장 · 11/6 값", formatMarketState(state))
    }

    @Test fun kospi_mondayBeforeOpenIsPreOpen() {
        val state = stateOf(kospi("2026-11-06T15:30+09:00"), "2026-11-09T08:30+09:00")!!
        assertEquals(MarketStatus.PRE_OPEN, state.status)
        assertEquals(at("2026-11-09T09:00+09:00"), state.nextOpen)
        assertEquals("장 전 · 09:00 시작", formatMarketState(state))
    }

    @Test fun kospi_openGraceEndsAtThirtyMinutes() {
        assertEquals(MarketStatus.PRE_OPEN, stateOf(kospi("2026-11-06T15:30+09:00"), "2026-11-09T09:29+09:00")?.status)
        assertEquals(MarketStatus.HOLIDAY, stateOf(kospi("2026-11-06T15:30+09:00"), "2026-11-09T09:30+09:00")?.status)
    }

    @Test fun kospi_beforeSessionStartIsPreOpen() {
        val state = stateOf(kospi("2026-11-06T09:00+09:00"), "2026-11-06T08:50+09:00")!!
        assertEquals(MarketStatus.PRE_OPEN, state.status)
        assertEquals(at("2026-11-06T09:00+09:00"), state.nextOpen)
    }

    // S&P500 뉴욕 11-05(목) 정규장
    private fun sp500() = quote(
        "America/New_York", "2026-11-05T09:30-05:00", "2026-11-05T16:00-05:00", at("2026-11-05T15:55-05:00"),
    )

    @Test fun sp500_seoulMorningIsClosedAtSix() {
        val state = stateOf(sp500(), "2026-11-06T10:00+09:00")!!
        assertEquals(MarketStatus.CLOSED, state.status)
        assertEquals("마감 06:00", formatMarketState(state))
    }

    @Test fun sp500_seoulAfternoonIsPreOpen() {
        val state = stateOf(sp500(), "2026-11-06T15:00+09:00")!!
        assertEquals(MarketStatus.PRE_OPEN, state.status)
        assertEquals("장 전 · 23:30 시작", formatMarketState(state))
    }

    @Test fun sp500_halfHourAfterUsualOpenIsHoliday() {
        assertEquals(MarketStatus.HOLIDAY, stateOf(sp500(), "2026-11-07T00:10+09:00")?.status)
    }

    @Test fun sp500_nextOpenFollowsDaylightSavingEnd() {
        // 금 10-30은 서머타임(-04:00), 월 11-02는 표준시(-05:00). 다음 시작은 뉴욕 09:30 = 서울 23:30
        val q = quote("America/New_York", "2026-10-30T09:30-04:00", "2026-10-30T16:00-04:00", at("2026-10-30T15:55-04:00"))
        val state = stateOf(q, "2026-11-02T15:00+09:00")!!
        assertEquals(MarketStatus.PRE_OPEN, state.status)
        assertEquals(at("2026-11-02T09:30-05:00"), state.nextOpen)
        assertEquals("장 전 · 23:30 시작", formatMarketState(state))
    }

    @Test fun futures_freshBarIsLive() {
        val t = "2026-11-06T10:00+09:00"
        val q = quote("America/New_York", "2026-11-05T00:00-05:00", "2026-11-05T23:59-05:00", at(t) - 5 * 60)
        assertEquals(MarketStatus.LIVE, stateOf(q, t)?.status)
    }

    // S&P 선물 뉴욕 11-06(금) 달력 하루, 마지막 봉 16:55
    private fun futuresFriday() = quote(
        "America/New_York", "2026-11-06T00:00-05:00", "2026-11-06T23:59-05:00", at("2026-11-06T16:55-05:00"),
    )

    @Test fun futures_fridayEveningIsClosedAtLastBar() {
        val state = stateOf(futuresFriday(), "2026-11-07T10:00+09:00")!!
        assertEquals(MarketStatus.CLOSED, state.status)
        assertEquals("마감 06:55", formatMarketState(state))
    }

    @Test fun futures_saturdayInNewYorkIsHoliday() {
        assertEquals(MarketStatus.HOLIDAY, stateOf(futuresFriday(), "2026-11-07T15:00+09:00")?.status)
    }

    @Test fun bitcoin_isLiveOnlyWhileFresh() {
        val t = "2026-11-07T15:00+09:00"
        val fresh = quote("UTC", null, null, at(t) - 10 * 60, alwaysOpen = true)
        val old = quote("UTC", null, null, at(t) - 2 * 3600, alwaysOpen = true)
        val state = stateOf(fresh, t)!!
        assertEquals(MarketStatus.LIVE, state.status)
        assertEquals("장중", formatMarketState(state))
        assertNull(stateOf(old, t))
    }

    @Test fun unknownWhenZoneOrBarsMissing() {
        val t = "2026-11-06T10:00+09:00"
        assertNull(stateOf(quote(null, "2026-11-06T09:00+09:00", "2026-11-06T15:30+09:00", at(t)), t))
        assertNull(stateOf(quote("Asia/Seoul", "2026-11-06T09:00+09:00", "2026-11-06T15:30+09:00", null), t))
        assertNull(stateOf(quote("Mars/Base", "2026-11-06T09:00+09:00", "2026-11-06T15:30+09:00", at(t)), t))
        assertNull(stateOf(quote("Asia/Seoul", null, "2026-11-06T15:30+09:00", at(t)), t))
        // 끝이 시작보다 빠르면 믿지 않는다
        assertNull(stateOf(quote("Asia/Seoul", "2026-11-06T15:30+09:00", "2026-11-06T09:00+09:00", at(t)), t))
    }

    @Test fun parseUpdatedAt_readsOffsetOrNull() {
        assertEquals(instant("2026-11-07T00:13:53+09:00"), parseUpdatedAt("2026-11-07T00:13:53+09:00"))
        assertNull(parseUpdatedAt("t"))
    }
}
