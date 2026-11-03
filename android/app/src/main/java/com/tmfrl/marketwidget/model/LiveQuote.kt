package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.round

/** 위젯 id와 Yahoo 심볼. 수집기 collector/config.py의 SYMBOLS와 같다. */
val LIVE_SYMBOLS: Map<String, String> = mapOf(
    "KS11" to "^KS11",
    "KQ11" to "^KQ11",
    "GSPC" to "^GSPC",
    "IXIC" to "^IXIC",
    "KRW" to "KRW=X",
    "TNX" to "^TNX",
    "VIX" to "^VIX",
    "CL" to "CL=F",
    "GC" to "GC=F",
    "BTC" to "BTC-USD",
)

data class LiveQuote(val price: Double, val change: Double, val changePct: Double, val prevClose: Double)

@Serializable
private data class ChartResponse(val chart: ChartBody)

@Serializable
private data class ChartBody(val result: List<ChartResult>? = null)

@Serializable
private data class ChartResult(
    val meta: ChartMeta,
    val timestamp: List<Long> = emptyList(),
    val indicators: ChartIndicators,
)

@Serializable
private data class ChartMeta(
    val regularMarketPrice: Double? = null,
    val regularMarketTime: Long? = null,
    val gmtoffset: Long = 0,
)

@Serializable
private data class ChartIndicators(val quote: List<ChartBars>)

@Serializable
private data class ChartBars(val close: List<Double?> = emptyList())

private val json = Json { ignoreUnknownKeys = true }

private fun localDate(epochSeconds: Long, offset: Long) =
    Instant.ofEpochSecond(epochSeconds + offset).atOffset(ZoneOffset.UTC).toLocalDate()

private fun round2(value: Double) = round(value * 100) / 100

/** Yahoo 차트 응답에서 현재값과 전일 대비를 구한다. 계산은 수집기 parse_chart와 같다. 값이 모자라면 null. */
fun parseLiveQuote(text: String): LiveQuote? {
    val result = json.decodeFromString<ChartResponse>(text).chart.result?.firstOrNull() ?: return null
    val closes = result.indicators.quote.firstOrNull()?.close ?: return null
    val bars = result.timestamp.zip(closes).mapNotNull { (ts, close) -> close?.let { ts to it } }
    if (bars.size < 2) return null
    val meta = result.meta
    val price = meta.regularMarketPrice ?: bars.last().second
    // 마지막 유효 봉이 시세 시각과 같은 날이면 그 앞 봉이 전일 종가다.
    // 오늘 봉이 아직 비어 있으면 마지막 유효 봉이 곧 전일 종가다.
    val marketTime = meta.regularMarketTime
    val sameDay = marketTime == null ||
        localDate(bars.last().first, meta.gmtoffset) == localDate(marketTime, meta.gmtoffset)
    val prevClose = if (sameDay) bars[bars.size - 2].second else bars.last().second
    if (prevClose == 0.0) return null
    val change = price - prevClose
    return LiveQuote(price, round2(change), round2(change / prevClose * 100), prevClose)
}

/** 일봉 끝을 현재값으로 맞춘다. 마지막 봉이 전일 종가면 오늘 봉이 아직 없는 것이라 덧붙이고, 아니면 바꾼다. */
private fun withLatestDaily(closes: List<Double>, live: LiveQuote): List<Double> = when {
    closes.isEmpty() -> closes
    closes.last() == round2(live.prevClose) -> closes + round2(live.price)
    else -> closes.dropLast(1) + round2(live.price)
}

/** 직접 받은 시세를 덮어쓰고 차트 끝도 맞춘다. 받지 못한 항목은 그대로 두고, 하나도 못 받았으면 갱신 시각도 그대로 둔다. */
fun applyLiveQuotes(data: MarketData, live: Map<String, LiveQuote>, updatedAt: String): MarketData {
    if (live.isEmpty()) return data
    return data.copy(
        updated_at = updatedAt,
        quotes = data.quotes.map { quote ->
            live[quote.id]?.let {
                quote.copy(
                    price = it.price,
                    change = it.change,
                    change_pct = it.changePct,
                    chart = withLatestDaily(quote.chart, it),
                    daily = withLatestDaily(quote.daily, it),
                    // 주봉은 이번 주 봉이 항상 끝에 있어 바꾸기만 한다
                    weekly = if (quote.weekly.isEmpty()) quote.weekly else quote.weekly.dropLast(1) + round2(it.price),
                    stale = false,
                )
            } ?: quote
        },
    )
}
