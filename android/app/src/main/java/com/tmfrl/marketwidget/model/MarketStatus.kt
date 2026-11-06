package com.tmfrl.marketwidget.model

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 장 상태. label은 타일에 쓰는 짧은 글자다(장중은 글자 대신 초록 점). */
enum class MarketStatus(val label: String) {
    LIVE("장중"),
    CLOSED("마감"),
    PRE_OPEN("장 전"),
    HOLIDAY("휴장"),
}

/**
 * closeTime은 마감 상태의 마감 시각(초 단위 epoch), nextOpen은 장 전 상태의 다음 시작,
 * sessionDate는 마지막 장의 시장 현지 날짜(휴장 문구의 "M/d 값")다. 해당 없으면 null.
 */
data class MarketState(
    val status: MarketStatus,
    val closeTime: Long? = null,
    val nextOpen: Long? = null,
    val sessionDate: LocalDate? = null,
)

/** 마지막 봉이 이 시간 안이면 장중으로 본다. 코스피 Yahoo 대체 경로는 20~25분, 미 10년물은 약 15분 늦다. */
private const val FRESH_SECONDS = 30 * 60L

/** 다음 장 시작 뒤 이 시간까지는 데이터가 늦게 들어오는 것으로 보고 휴장으로 바꾸지 않는다. */
private const val OPEN_GRACE_SECONDS = 30 * 60L

/** 마지막 봉이 장 끝 이 시간 안이면 마감 시각을 장 끝으로 쓴다. */
private const val CLOSE_SNAP_SECONDS = 10 * 60L

private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private fun zoneOrNull(name: String?): ZoneId? = try {
    name?.let { ZoneId.of(it) }
} catch (e: DateTimeException) {
    null
}

private fun nextWeekday(day: LocalDate): LocalDate {
    var next = day.plusDays(1)
    while (next.dayOfWeek in WEEKEND) next = next.plusDays(1)
    return next
}

/**
 * 화면에 보이는 숫자가 장중 값인지 판단한다. asOf는 데이터를 받은 시각(updated_at)이다.
 * 장 시작·끝(session_start/end), 마지막 봉 시각(intraday_times 끝), 시장 시간대(market_tz)로 계산하고,
 * 휴장일 달력은 없어서 평일 휴장일은 평소 개장 30분 뒤부터 휴장이 된다. 판단할 수 없으면 null.
 */
fun marketState(quote: Quote, asOf: Instant): MarketState? {
    val t = asOf.epochSecond
    val last = quote.intraday_times.lastOrNull()
    if (quote.always_open) {
        return if (last != null && t - last <= FRESH_SECONDS) MarketState(MarketStatus.LIVE) else null
    }
    val zone = zoneOrNull(quote.market_tz) ?: return null
    val start = quote.session_start ?: return null
    val end = quote.session_end ?: return null
    if (last == null || end <= start) return null

    if (t < start) return MarketState(MarketStatus.PRE_OPEN, nextOpen = start)
    val closeTime = if (last >= end - CLOSE_SNAP_SECONDS) end else last
    if (t <= end) {
        return if (t - last <= FRESH_SECONDS) MarketState(MarketStatus.LIVE) else MarketState(MarketStatus.CLOSED, closeTime = closeTime)
    }
    val startLocal = Instant.ofEpochSecond(start).atZone(zone)
    val sessionDate = startLocal.toLocalDate()
    val today = Instant.ofEpochSecond(t).atZone(zone).toLocalDate()
    // 다음 시작은 시장 시간대로 계산해 서머타임이 바뀌어도 현지 시각이 같다
    val nextOpen = nextWeekday(sessionDate).atTime(startLocal.toLocalTime()).atZone(zone).toEpochSecond()
    return when {
        today.dayOfWeek in WEEKEND -> MarketState(MarketStatus.HOLIDAY, sessionDate = sessionDate)
        today == sessionDate -> MarketState(MarketStatus.CLOSED, closeTime = closeTime)
        t < nextOpen + OPEN_GRACE_SECONDS -> MarketState(MarketStatus.PRE_OPEN, nextOpen = nextOpen)
        else -> MarketState(MarketStatus.HOLIDAY, sessionDate = sessionDate)
    }
}

/** 장 상태의 기준 시각. data.json의 updated_at을 읽고, 읽을 수 없으면 null(장 상태를 보이지 않는다). */
fun parseUpdatedAt(text: String): Instant? = try {
    OffsetDateTime.parse(text).toInstant()
} catch (e: DateTimeException) {
    null
}

private val STATE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val STATE_DAY_FORMAT = DateTimeFormatter.ofPattern("M/d")

private fun seoulTime(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(SEOUL).format(STATE_TIME_FORMAT)

/** 차트 머리줄 문구. 시각은 서울 시간이다. 예: 장중, 마감 15:30, 장 전 · 23:30 시작, 휴장 · 11/6 값 */
fun formatMarketState(state: MarketState): String = when (state.status) {
    MarketStatus.LIVE -> "장중"
    MarketStatus.CLOSED -> state.closeTime?.let { "마감 ${seoulTime(it)}" } ?: "마감"
    MarketStatus.PRE_OPEN -> state.nextOpen?.let { "장 전 · ${seoulTime(it)} 시작" } ?: "장 전"
    MarketStatus.HOLIDAY -> state.sessionDate?.let { "휴장 · ${it.format(STATE_DAY_FORMAT)} 값" } ?: "휴장"
}
