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

data class LiveQuote(
    val price: Double,
    val change: Double,
    val changePct: Double,
    val prevClose: Double,
    val intraday: List<Double> = emptyList(),
)

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

private fun round1(value: Double) = round(value * 10) / 10

private fun replaceLast(values: List<Double>, latest: Double) =
    if (values.isEmpty()) values else values.dropLast(1) + latest

/** Yahoo 5분봉 응답에서 비어 있지 않은 종가만 뽑는다. */
fun parseIntraday(text: String): List<Double> {
    val result = json.decodeFromString<ChartResponse>(text).chart.result?.firstOrNull() ?: return emptyList()
    return result.indicators.quote.firstOrNull()?.close.orEmpty().filterNotNull().map(::round2)
}

/** Yahoo 차트 응답에서 현재값과 전일 대비를 구한다. 계산은 수집기 parse_chart와 같다. 값이 모자라면 null. */
fun parseLiveQuote(text: String): LiveQuote? {
    val result = json.decodeFromString<ChartResponse>(text).chart.result?.firstOrNull() ?: return null
    val closes = result.indicators.quote.firstOrNull()?.close ?: return null
    val rows = result.timestamp.zip(closes)
    val filled = rows.mapNotNull { it.second }
    if (rows.size < 2 || filled.isEmpty()) return null
    val meta = result.meta
    val price = meta.regularMarketPrice ?: filled.last()
    val marketTime = meta.regularMarketTime
    // 마지막 봉이 시세 시각과 같은 날이면 그 앞 봉이 전일 종가이고, 아니면 마지막 봉이 전일 종가다.
    // 그 봉이 비어 있으면 더 앞 종가와 비교하지 않는다. 전전일 종가와 비교하면 등락률이 틀린다.
    val prevClose = when {
        marketTime == null -> filled.getOrNull(filled.size - 2)
        localDate(rows.last().first, meta.gmtoffset) == localDate(marketTime, meta.gmtoffset) -> rows[rows.size - 2].second
        else -> rows.last().second
    } ?: return null
    if (prevClose == 0.0) return null
    val change = price - prevClose
    return LiveQuote(price, round2(change), round2(change / prevClose * 100), prevClose)
}

/** 위젯 id와 네이버 증권 지수 코드. Yahoo 코스피는 약 20~25분 늦어서 국내 지수는 네이버를 먼저 쓴다. */
val NAVER_CODES: Map<String, String> = mapOf("KS11" to "KOSPI", "KQ11" to "KOSDAQ")

@Serializable
private data class NaverIndex(
    val closePrice: String? = null,
    val compareToPreviousClosePrice: String? = null,
    val fluctuationsRatio: String? = null,
)

@Serializable
private data class NaverMinute(val localDateTime: String = "", val currentPrice: Double? = null)

private fun naverNumber(text: String?) = text?.replace(",", "")?.toDoubleOrNull()

/** 네이버 지수 응답에서 현재값과 전일 대비를 읽는다. 등락 값은 네이버가 준 그대로 쓴다. 값이 모자라면 null. */
fun parseNaverIndex(text: String): LiveQuote? {
    val index = json.decodeFromString<NaverIndex>(text)
    val price = naverNumber(index.closePrice) ?: return null
    val change = naverNumber(index.compareToPreviousClosePrice) ?: return null
    val changePct = naverNumber(index.fluctuationsRatio) ?: return null
    return LiveQuote(price, change, changePct, round2(price - change))
}

/** 네이버 1분봉을 5분 간격으로 줄인다. 가장 최근 날짜만 쓰고 마지막 값은 지킨다. */
fun parseNaverMinutes(text: String): List<Double> {
    val minutes = json.decodeFromString<List<NaverMinute>>(text).filter { it.currentPrice != null }
    val day = minutes.lastOrNull()?.localDateTime?.take(8) ?: return emptyList()
    val today = minutes.filter { it.localDateTime.startsWith(day) }.map { round2(it.currentPrice!!) }
    return today.filterIndexed { i, _ -> i % NAVER_MINUTE_STEP == 0 || i == today.lastIndex }
}

private const val NAVER_MINUTE_STEP = 5

/** 일봉 끝을 현재값으로 맞춘다. 마지막 봉이 전일 종가면 오늘 봉이 아직 없는 것이라 덧붙이고, 아니면 바꾼다. */
private fun withLatestDaily(closes: List<Double>, live: LiveQuote): List<Double> = when {
    closes.isEmpty() -> closes
    closes.last() == round2(live.prevClose) -> closes + round2(live.price)
    else -> closes.dropLast(1) + round2(live.price)
}

/** 직접 받은 시세를 덮어쓰고 차트 끝과 낙폭도 맞춘다. 받지 못한 항목은 그대로 두고, 하나도 못 받았으면 갱신 시각도 그대로 둔다. */
fun applyLiveQuotes(data: MarketData, live: Map<String, LiveQuote>, updatedAt: String): MarketData {
    if (live.isEmpty()) return data
    return data.copy(
        updated_at = updatedAt,
        quotes = data.quotes.map { quote ->
            live[quote.id]?.let {
                val latest = round2(it.price)
                val peak = quote.peak?.let { old -> maxOf(old, it.price) }
                val depth = peak?.let { high -> drawdownPct(it.price, high) }
                quote.copy(
                    price = it.price,
                    change = it.change,
                    change_pct = it.changePct,
                    chart = withLatestDaily(quote.chart, it),
                    daily = withLatestDaily(quote.daily, it),
                    // 주봉은 이번 주 봉이 항상 끝에 있어 바꾸기만 한다
                    weekly = replaceLast(quote.weekly, latest),
                    max_closes = replaceLast(quote.max_closes, latest),
                    max_drawdowns = if (depth == null) quote.max_drawdowns else replaceLast(quote.max_drawdowns, round1(depth)),
                    peak = peak,
                    mdd = if (depth == null) quote.mdd else quote.mdd?.let { old -> minOf(old, round1(depth)) },
                    intraday = if (it.intraday.isEmpty()) quote.intraday else replaceLast(it.intraday, latest),
                    prev_close = round2(it.prevClose),
                    stale = false,
                )
            } ?: quote
        },
    )
}
