@file:Suppress("PropertyName")

package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * 추가 종목 캐시(custom.json)의 한 줄. quote는 data.json과 같은 형식이다.
 * 시각은 밀리초 epoch이고, history_at은 1년·5년 기록, max_at은 전체 기간을 마지막으로 받은 시각,
 * last_ok_at은 마지막 시세 성공 시각, last_failed는 마지막 시도가 실패했는지다.
 */
@Serializable
data class CustomEntry(
    val id: String,
    val quote: Quote,
    val history_at: Long? = null,
    val max_at: Long? = null,
    val last_ok_at: Long? = null,
    val last_failed: Boolean = false,
)

@Serializable
data class CustomCache(val entries: List<CustomEntry> = emptyList())

private val cacheJson = Json { ignoreUnknownKeys = true }

/** custom.json 읽기. 깨졌으면 빈 캐시(다음 갱신에서 모두 다시 받는다). */
fun parseCustomCache(text: String): CustomCache = try {
    cacheJson.decodeFromString<CustomCache>(text)
} catch (e: IllegalArgumentException) { // SerializationException 포함
    CustomCache()
}

fun encodeCustomCache(cache: CustomCache): String = cacheJson.encodeToString(cache)

private const val HOUR_MS = 3_600_000L
private const val HISTORY_INTERVAL_MS = 6 * HOUR_MS
private const val MAX_INTERVAL_MS = 7 * 24 * HOUR_MS

/** 1년 일봉·5년 주봉을 다시 받을 때인지. 6시간이 지났거나 받은 적이 없으면 true. */
fun historyDue(historyAt: Long?, nowMillis: Long): Boolean =
    historyAt == null || nowMillis - historyAt >= HISTORY_INTERVAL_MS

/** 전체 기간 일봉을 다시 받을 때인지. 7일이 지났거나 받은 적이 없으면 true. */
fun maxDue(maxAt: Long?, nowMillis: Long): Boolean =
    maxAt == null || nowMillis - maxAt >= MAX_INTERVAL_MS

/** 큰 움직임 기준의 기본값. 개별 주식은 지수보다 하루 움직임이 커서 높게 둔다. */
fun customBigMoveBase(kind: String): Double = when (kind) {
    KIND_ETF -> 2.0
    KIND_INDEX -> 1.5
    KIND_CRYPTO -> 5.0
    else -> 3.0
}

/** 값과 날짜 목록(1년 일봉 또는 5년 주봉). */
data class Series(val values: List<Double>, val dates: List<String>)

/**
 * 추가 종목 Quote를 만든다. 받지 못한 부분(null)은 previous 값을 그대로 쓴다.
 * live가 있으면 기본 지표와 같은 applyLiveQuotes 규칙으로 끝 값·낙폭을 맞추고, 없으면 이전 값이 있을 때 흐리게(stale) 둔다.
 */
fun assembleCustomQuote(
    ticker: CustomTicker,
    previous: Quote?,
    live: LiveQuote?,
    daily: Series?,
    weekly: Series?,
    max: MaxFields?,
    marketTz: String?,
    updatedAt: String,
): Quote {
    var quote = (previous ?: Quote(ticker.id, ticker.name, null, null, null)).copy(id = ticker.id, name = ticker.name)
    if (daily != null) quote = quote.copy(daily = daily.values, daily_dates = daily.dates)
    if (weekly != null) quote = quote.copy(weekly = weekly.values, weekly_dates = weekly.dates)
    if (max != null) {
        quote = quote.copy(
            max_closes = max.closes,
            max_dates = max.dates,
            max_depths = max.depths,
            max_start = max.start,
            peak = max.peak,
            mdd = max.mdd,
            mdd_peak_date = max.peakDate,
            mdd_trough_date = max.troughDate,
            mdd_recovery_date = max.recoveryDate,
        )
    }
    quote = quote.copy(
        big_move_pct = bigMovePct(customBigMoveBase(ticker.kind), quote.daily),
        market_tz = marketTz ?: quote.market_tz,
        always_open = ticker.kind == KIND_CRYPTO,
        max_events = emptyList(),
    )
    if (live == null) return quote.copy(stale = quote.price != null)
    return applyLiveQuotes(MarketData(updatedAt, listOf(quote), emptyList()), mapOf(ticker.id to live), updatedAt).quotes[0]
}

private val STATUS_TIME = DateTimeFormatter.ofPattern("HH:mm")

/** 설정 화면의 추가 종목 상태 문구. */
fun customStatusText(entry: CustomEntry?): String = when {
    entry == null -> "받는 중"
    entry.last_failed -> "받지 못함 · 다시 시도 중"
    entry.last_ok_at == null -> "받는 중"
    else -> "${Instant.ofEpochMilli(entry.last_ok_at).atZone(SEOUL).format(STATUS_TIME)} 받음"
}
