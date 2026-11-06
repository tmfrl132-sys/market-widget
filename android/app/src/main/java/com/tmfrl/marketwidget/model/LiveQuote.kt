package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.round

/** 위젯 id와 Yahoo 심볼. 수집기 collector/config.py의 SYMBOLS와 같다. */
val LIVE_SYMBOLS: Map<String, String> = mapOf(
    "KS11" to "^KS11",
    "KQ11" to "^KQ11",
    "GSPC" to "^GSPC",
    "ES" to "ES=F",
    "IXIC" to "^IXIC",
    "NQ" to "NQ=F",
    "VIX" to "^VIX",
    "KRW" to "KRW=X",
    "DXY" to "DX-Y.NYB",
    "TNX" to "^TNX",
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
    val intradayTimes: List<Long> = emptyList(),
    val sessionStart: Long? = null,
    val sessionEnd: Long? = null,
)

/** 오늘 봉. times는 values와 1:1인 초 단위 epoch이고, 믿을 수 없으면 비어 있다. 장 시간을 모르면 시작·끝은 null이다. */
data class IntradaySeries(
    val values: List<Double> = emptyList(),
    val times: List<Long> = emptyList(),
    val sessionStart: Long? = null,
    val sessionEnd: Long? = null,
)

fun LiveQuote.withIntraday(series: IntradaySeries): LiveQuote = copy(
    intraday = series.values,
    intradayTimes = series.times,
    sessionStart = series.sessionStart,
    sessionEnd = series.sessionEnd,
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
    // 응답마다 형식이 다를 수 있어 시세 해석이 실패하지 않게 직접 읽는다
    val tradingPeriods: JsonElement? = null,
)

@Serializable
private data class ChartIndicators(val quote: List<ChartBars>)

@Serializable
private data class ChartBars(val close: List<Double?> = emptyList())

private val json = Json { ignoreUnknownKeys = true }

private fun localDate(epochSeconds: Long, offset: Long) =
    Instant.ofEpochSecond(epochSeconds + offset).atOffset(ZoneOffset.UTC).toLocalDate()

private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private fun round2(value: Double) = round(value * 100) / 100

private fun round1(value: Double) = round(value * 10) / 10

private fun replaceLast(values: List<Double>, latest: Double) =
    if (values.isEmpty()) values else values.dropLast(1) + latest

/** tradingPeriods의 `[[{start, end}]]`에서 첫 start와 마지막 end를 읽는다. 다른 형태이거나 없으면 null. */
private fun tradingSession(periods: JsonElement?): Pair<Long, Long>? {
    val outer = periods as? JsonArray ?: return null
    val spans = outer.flatMap { inner ->
        val list = inner as? JsonArray ?: return null
        list.map { span ->
            val fields = span as? JsonObject ?: return null
            val start = (fields["start"] as? JsonPrimitive)?.longOrNull ?: return null
            val end = (fields["end"] as? JsonPrimitive)?.longOrNull ?: return null
            start to end
        }
    }
    if (spans.isEmpty()) return null
    return spans.first().first to spans.last().second
}

/** Yahoo 5분봉 응답에서 비어 있지 않은 종가와 그 봉 시각, 장 시간을 뽑는다. */
fun parseIntraday(text: String): IntradaySeries {
    val result = json.decodeFromString<ChartResponse>(text).chart.result?.firstOrNull() ?: return IntradaySeries()
    val closes = result.indicators.quote.firstOrNull()?.close.orEmpty()
    // 시각과 종가 수가 다르면 어느 봉의 시각인지 알 수 없다. 값은 살리고 시각만 비운다.
    val times = if (result.timestamp.size == closes.size) {
        result.timestamp.filterIndexed { i, _ -> closes[i] != null }
    } else {
        emptyList()
    }
    val session = tradingSession(result.meta.tradingPeriods)
    return IntradaySeries(closes.filterNotNull().map(::round2), times, session?.first, session?.second)
}

/** Yahoo 차트 응답에서 현재값과 전일 대비를 구한다. 계산은 수집기 parse_chart와 같다. 값이 모자라면 null. */
fun parseLiveQuote(text: String): LiveQuote? {
    val result = json.decodeFromString<ChartResponse>(text).chart.result?.firstOrNull() ?: return null
    val closes = result.indicators.quote.firstOrNull()?.close ?: return null
    val meta = result.meta
    // 시장 현지 날짜가 토·일인 빈 봉은 거래가 없던 날이라 뺀다(달러인덱스 일요일 봉). 평일 빈 봉은 남겨 아래 규칙을 따른다.
    val rows = result.timestamp.zip(closes).filterNot { (time, close) ->
        close == null && localDate(time, meta.gmtoffset).dayOfWeek in WEEKEND
    }
    val filled = rows.mapNotNull { it.second }
    if (rows.size < 2 || filled.isEmpty()) return null
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

private val NAVER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
private val NAVER_OPEN = LocalTime.of(9, 0)
private val NAVER_CLOSE = LocalTime.of(15, 30)

/** 이 시각까지의 봉은 동시호가로 보고 장 끝을 늘리지 않는다. */
private val NAVER_CLOSE_AUCTION_END = LocalTime.of(15, 35)

/** 네이버 시각은 서울 시간이다. 읽을 수 없으면 null. 파싱 예외는 호출부 orElse가 잡지 못해 여기서 막는다. */
private fun naverEpoch(text: String): Long? = try {
    LocalDateTime.parse(text, NAVER_TIME_FORMAT).atZone(SEOUL).toEpochSecond()
} catch (e: DateTimeException) {
    null
}

/** 그 날짜 09:00~15:30. 마지막 봉이 15:35보다 늦으면 개장이 늦춰진 날로 보고 끝을 마지막 봉으로 늘린다. */
private fun naverSession(lastBar: Long): Pair<Long, Long> {
    val day = Instant.ofEpochSecond(lastBar).atZone(SEOUL).toLocalDate()
    val epochAt = { time: LocalTime -> day.atTime(time).atZone(SEOUL).toEpochSecond() }
    val end = if (lastBar > epochAt(NAVER_CLOSE_AUCTION_END)) lastBar else epochAt(NAVER_CLOSE)
    return epochAt(NAVER_OPEN) to end
}

/** 네이버 1분봉을 5분 간격으로 줄이고 봉 시각과 장 시간을 함께 돌려준다. 가장 최근 날짜만 쓰고 마지막 값은 지킨다. */
fun parseNaverMinutes(text: String): IntradaySeries {
    val minutes = json.decodeFromString<List<NaverMinute>>(text).filter { it.currentPrice != null }
    val day = minutes.lastOrNull()?.localDateTime?.take(8) ?: return IntradaySeries()
    val today = minutes.filter { it.localDateTime.startsWith(day) }
    val kept = today.filterIndexed { i, _ -> i % NAVER_MINUTE_STEP == 0 || i == today.lastIndex }
    val values = kept.map { round2(it.currentPrice!!) }
    val times = kept.mapNotNull { naverEpoch(it.localDateTime) }
    if (times.size != kept.size) return IntradaySeries(values)
    val session = naverSession(times.last())
    return IntradaySeries(values, times, session.first, session.second)
}

private const val NAVER_MINUTE_STEP = 5

/** 일봉 끝을 현재값으로 맞춘다. 마지막 봉이 전일 종가면 오늘 봉이 아직 없는 것이라 덧붙이고, 아니면 바꾼다. */
private fun withLatestDaily(closes: List<Double>, live: LiveQuote): List<Double> = when {
    closes.isEmpty() -> closes
    closes.last() == round2(live.prevClose) -> closes + round2(live.price)
    else -> closes.dropLast(1) + round2(live.price)
}

/** 일봉이 1개 늘었으면 날짜도 갱신 날짜로 1개 늘린다. 이미 길이가 어긋난 목록은 건드리지 않는다. */
private fun withLatestDate(dates: List<String>, oldSize: Int, newSize: Int, updatedAt: String): List<String> =
    if (newSize == oldSize + 1 && dates.size == oldSize) dates + updatedAt.take(10) else dates

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
                val daily = withLatestDaily(quote.daily, it)
                // 오늘 봉을 못 받았으면 값, 시각, 장 시간을 모두 이전 것으로 유지한다
                val keepIntraday = it.intraday.isEmpty()
                quote.copy(
                    price = it.price,
                    change = it.change,
                    change_pct = it.changePct,
                    chart = withLatestDaily(quote.chart, it),
                    daily = daily,
                    daily_dates = withLatestDate(quote.daily_dates, quote.daily.size, daily.size, updatedAt),
                    // 주봉은 이번 주 봉이 항상 끝에 있어 바꾸기만 한다
                    weekly = replaceLast(quote.weekly, latest),
                    max_closes = replaceLast(quote.max_closes, latest),
                    max_drawdowns = if (depth == null) quote.max_drawdowns else replaceLast(quote.max_drawdowns, round1(depth)),
                    peak = peak,
                    mdd = if (depth == null) quote.mdd else quote.mdd?.let { old -> minOf(old, round1(depth)) },
                    intraday = if (keepIntraday) quote.intraday else replaceLast(it.intraday, latest),
                    intraday_times = if (keepIntraday) quote.intraday_times else it.intradayTimes,
                    session_start = if (keepIntraday) quote.session_start else it.sessionStart,
                    session_end = if (keepIntraday) quote.session_end else it.sessionEnd,
                    prev_close = round2(it.prevClose),
                    stale = false,
                )
            } ?: quote
        },
    )
}
