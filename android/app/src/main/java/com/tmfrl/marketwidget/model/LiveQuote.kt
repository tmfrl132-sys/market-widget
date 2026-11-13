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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.round

/**
 * 위젯 id와 Yahoo 심볼. 수집기 collector/config.py의 SYMBOLS와 같다.
 * v1.1부터 나스닥 종합(IXIC) 대신 나스닥100(NDX)이고, S&P·나스닥 선물(ES, NQ)은 카드가 아니라 합친 차트 재료다(COMBINED_FUTURES).
 */
val LIVE_SYMBOLS: Map<String, String> = mapOf(
    "KS11" to "^KS11",
    "KQ11" to "^KQ11",
    "GSPC" to "^GSPC",
    "NDX" to "^NDX",
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
    // 합친 24시간 차트 재료(S&P500·나스닥100만). 갱신 때 계산에만 쓰고 캐시에 넣지 않는다.
    val combinedSource: CombinedSource? = null,
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
    val previousClose: Double? = null,
    val shortName: String? = null,
)

@Serializable
private data class ChartIndicators(val quote: List<ChartBars>)

@Serializable
private data class ChartBars(val close: List<Double?> = emptyList(), val open: List<Double?> = emptyList())

private val json = Json { ignoreUnknownKeys = true }

private fun localDate(epochSeconds: Long, offset: Long) =
    Instant.ofEpochSecond(epochSeconds + offset).atOffset(ZoneOffset.UTC).toLocalDate()

private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private fun round2(value: Double) = round(value * 100) / 100

private fun round1(value: Double) = round(value * 10) / 10

private fun replaceLast(values: List<Double>, latest: Double) =
    if (values.isEmpty()) values else values.dropLast(1) + latest

/**
 * tradingPeriods의 `[[{start, end}]]`를 날짜(안쪽 목록)마다 (첫 start, 마지막 end)로 읽는다.
 * 다른 형태이거나 없으면 null. range=1d는 1개, range=5d는 날짜 수만큼이다.
 */
private fun tradingDays(periods: JsonElement?): List<TradingSpan>? {
    val outer = periods as? JsonArray ?: return null
    val days = outer.mapNotNull { inner ->
        val list = inner as? JsonArray ?: return null
        val spans = list.map { span ->
            val fields = span as? JsonObject ?: return null
            val start = (fields["start"] as? JsonPrimitive)?.longOrNull ?: return null
            val end = (fields["end"] as? JsonPrimitive)?.longOrNull ?: return null
            start to end
        }
        if (spans.isEmpty()) null else TradingSpan(spans.first().first, spans.last().second)
    }
    return days.ifEmpty { null }
}

/** tradingPeriods의 첫 start와 마지막 end. 다른 형태이거나 없으면 null. */
private fun tradingSession(periods: JsonElement?): Pair<Long, Long>? {
    val days = tradingDays(periods) ?: return null
    return days.first().start to days.last().end
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

/** 비어 있지 않은 종가와 그 봉 시각. 시각과 종가 수가 다르면 어느 봉인지 알 수 없어 null. */
private fun closeRows(result: ChartResult): List<Pair<Long, Double>>? {
    val closes = result.indicators.quote.firstOrNull()?.close ?: return null
    if (result.timestamp.size != closes.size) return null
    return result.timestamp.zip(closes).mapNotNull { (time, close) -> close?.let { time to it } }
}

/**
 * 현물 range=5d 5분봉 응답에서 마지막 정규장만 뽑는다. range=1d 응답의 parseIntraday와 같은 결과다(카드 작은 차트, 기존 오늘 차트).
 * 마지막 정규장은 봉이 있는 tradingPeriods의 마지막 날이다. tradingPeriods가 없으면 마지막 봉과 같은 시장 날짜의 봉이고 장 시간은 null이다.
 * 시각과 종가 수가 다르면 빈 값(이전 오늘 차트를 유지한다).
 */
fun parseLastSession(text: String): IntradaySeries {
    val result = json.decodeFromString<ChartResponse>(text).chart.result?.firstOrNull() ?: return IntradaySeries()
    val rows = closeRows(result) ?: return IntradaySeries()
    val lastBar = rows.lastOrNull() ?: return IntradaySeries()
    val day = tradingDays(result.meta.tradingPeriods)?.lastOrNull { span -> rows.any { it.first in span.start..span.end } }
    val kept = if (day != null) {
        rows.filter { it.first in day.start..day.end }
    } else {
        val lastDate = localDate(lastBar.first, result.meta.gmtoffset)
        rows.filter { localDate(it.first, result.meta.gmtoffset) == lastDate }
    }
    return IntradaySeries(kept.map { round2(it.second) }, kept.map { it.first }, day?.start, day?.end)
}

/** 현물 range=5d 5분봉 응답을 합친 차트 재료로 읽는다. 봉이 없거나 시각과 종가 수가 다르면 null. */
fun parseSpotSeries(text: String): SpotSeries? {
    val result = json.decodeFromString<ChartResponse>(text).chart.result?.firstOrNull() ?: return null
    val rows = closeRows(result)?.takeIf { it.isNotEmpty() } ?: return null
    return SpotSeries(
        bars = rows.map { SpotBar(it.first, it.second) },
        sessions = tradingDays(result.meta.tradingPeriods).orEmpty(),
        previousClose = result.meta.previousClose,
    )
}

/**
 * 선물 range=5d 5분봉 응답을 읽는다. 선물 tradingPeriods는 뉴욕 날짜 00:00~23:59라 실제 거래 시간이 아니어서 쓰지 않는다.
 * 시가 목록 길이가 다르면 시가는 모두 null이다. 봉이 없거나 시각과 종가 수가 다르면 null.
 */
fun parseFuturesSeries(text: String): FuturesSeries? {
    val result = json.decodeFromString<ChartResponse>(text).chart.result?.firstOrNull() ?: return null
    val bars = result.indicators.quote.firstOrNull() ?: return null
    if (result.timestamp.size != bars.close.size) return null
    val opens = bars.open.takeIf { it.size == bars.close.size }
    val rows = result.timestamp.indices.mapNotNull { i ->
        bars.close[i]?.let { FuturesBar(result.timestamp[i], opens?.get(i), it) }
    }
    if (rows.isEmpty()) return null
    return FuturesSeries(rows, result.meta.shortName?.takeIf { it.isNotBlank() })
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

/** 한국 개별 종목에 네이버를 쓰는 시간(서울 평일 09:00~16:00). 그 밖에는 Yahoo가 KRX 종가를 갖고 있다. */
private val NAVER_STOCK_FROM = LocalTime.of(9, 0)
private val NAVER_STOCK_UNTIL = LocalTime.of(16, 0)

/** KRX 정규장 마지막 봉(15:30:59까지). 그 뒤 봉은 넥스트레이드(NXT) 시간외 값이라 쓰지 않는다. */
private val KRX_LAST_BAR = LocalTime.of(15, 30, 59)

/** 지금 한국 개별 종목 시세를 네이버에서 받을 시간인지. */
fun naverStockWindow(now: ZonedDateTime): Boolean {
    val seoul = now.withZoneSameInstant(SEOUL)
    val time = seoul.toLocalTime()
    return seoul.dayOfWeek !in WEEKEND && time >= NAVER_STOCK_FROM && time < NAVER_STOCK_UNTIL
}

@Serializable
private data class NaverStock(
    val closePrice: String? = null,
    val compareToPreviousClosePrice: String? = null,
)

/**
 * 네이버 한국 개별 종목 시세. 값은 KRX 정규장(09:00~15:30) 1분봉 끝 값이고, 전일 종가는 basic의 closePrice - 전일 대비다.
 * 서울 평일 09:00~16:00이 아니거나, 오늘 정규장 봉이 없거나, 값을 읽을 수 없으면 null(Yahoo로 받는다).
 * 오늘 봉은 정규장 봉을 5개마다 1개로 줄이고(마지막 봉은 지킴) 장 시간은 그 날짜 09:00~15:30이다.
 */
fun naverStockQuote(basicText: String, minuteText: String, now: ZonedDateTime): LiveQuote? {
    if (!naverStockWindow(now)) return null
    val today = now.withZoneSameInstant(SEOUL).toLocalDate()
    val minutes = json.decodeFromString<List<NaverMinute>>(minuteText).filter { it.currentPrice != null }
    val lastDay = minutes.lastOrNull()?.localDateTime?.take(8) ?: return null
    val regular = minutes.filter { it.localDateTime.startsWith(lastDay) }.mapNotNull { bar ->
        val time = try {
            LocalDateTime.parse(bar.localDateTime, NAVER_TIME_FORMAT)
        } catch (e: DateTimeException) {
            return@mapNotNull null
        }
        if (time.toLocalTime() < NAVER_OPEN || time.toLocalTime() > KRX_LAST_BAR) null else time to bar.currentPrice!!
    }
    if (regular.isEmpty() || regular.last().first.toLocalDate() != today) return null
    val basic = json.decodeFromString<NaverStock>(basicText)
    val close = naverNumber(basic.closePrice) ?: return null
    val compare = naverNumber(basic.compareToPreviousClosePrice) ?: return null
    val prevClose = round2(close - compare)
    if (prevClose == 0.0) return null
    val price = regular.last().second
    val change = round2(price - prevClose)
    val kept = regular.filterIndexed { i, _ -> i % NAVER_MINUTE_STEP == 0 || i == regular.lastIndex }
    val epochAt = { time: LocalTime -> today.atTime(time).atZone(SEOUL).toEpochSecond() }
    return LiveQuote(
        price = price,
        change = change,
        changePct = round2(change / prevClose * 100),
        prevClose = prevClose,
        intraday = kept.map { round2(it.second) },
        intradayTimes = kept.map { it.first.atZone(SEOUL).toEpochSecond() },
        sessionStart = epochAt(NAVER_OPEN),
        sessionEnd = epochAt(NAVER_CLOSE),
    )
}

/** 일봉 끝을 현재값으로 맞춘다. 마지막 봉이 전일 종가면 오늘 봉이 아직 없는 것이라 덧붙이고, 아니면 바꾼다. */
private fun withLatestDaily(closes: List<Double>, live: LiveQuote): List<Double> = when {
    closes.isEmpty() -> closes
    closes.last() == round2(live.prevClose) -> closes + round2(live.price)
    else -> closes.dropLast(1) + round2(live.price)
}

/** 일봉이 1개 늘었으면 날짜도 갱신 날짜로 1개 늘린다. 이미 길이가 어긋난 목록은 건드리지 않는다. */
private fun withLatestDate(dates: List<String>, oldSize: Int, newSize: Int, updatedAt: String): List<String> =
    if (newSize == oldSize + 1 && dates.size == oldSize) dates + updatedAt.take(10) else dates

/**
 * 시세로 바뀐 MDD 바닥·회복 날짜(바닥, 회복). 새 MDD면 바닥이 갱신 날짜가 되고 회복은 지운다.
 * 아직 회복하지 못한 구간에서 옛 고점 이상이 되면 회복이 갱신 날짜가 된다. 고점 날짜는 바꾸지 않는다.
 */
private fun latestEpisode(quote: Quote, price: Double, depth: Double?, updatedAt: String): Pair<String?, String?> {
    val trough = quote.mdd_trough_date
    val recovery = quote.mdd_recovery_date
    val oldMdd = quote.mdd
    val oldPeak = quote.peak
    val today = updatedAt.take(10)
    return when {
        depth == null || oldMdd == null || oldPeak == null -> trough to recovery
        round1(depth) < oldMdd -> today to null
        trough != null && recovery == null && price >= oldPeak -> trough to today
        else -> trough to recovery
    }
}

/**
 * 합친 24시간 차트(S&P500·나스닥100). 이번 갱신에 재료(현물·선물 5일 5분봉)를 받았으면 새로 계산하고, 못 받았으면 null이다.
 * 낡은 합친 차트를 남기지 않기 위해서다(그러면 기존 오늘 차트를 그린다). 다른 지표는 그대로 둔다.
 * 계약 교체 보정에 쓰는 지난 결과는 직전 캐시(previous)에서, 없으면 덮어쓸 데이터에서 찾는다.
 */
private fun combinedAfter(quote: Quote, live: LiveQuote, now: Long?, previous: MarketData?): CombinedChart? {
    if (quote.id !in COMBINED_FUTURES) return quote.combined
    val source = live.combinedSource ?: return null
    if (now == null) return null
    val before = previous?.quotes?.firstOrNull { it.id == quote.id }?.combined ?: quote.combined
    return combine24h(source.spot, source.futures, now, rollAtFor(before, source.futures.contract, now))
}

/**
 * 직접 받은 시세를 덮어쓰고 차트 끝과 낙폭도 맞춘다. 받지 못한 항목은 그대로 두고, 하나도 못 받았으면 갱신 시각도 그대로 둔다.
 * previous는 덮어쓰기 전 기기 캐시다. 합친 차트의 선물 계약 이름을 기억하는 데만 쓴다.
 */
fun applyLiveQuotes(data: MarketData, live: Map<String, LiveQuote>, updatedAt: String, previous: MarketData? = null): MarketData {
    if (live.isEmpty()) return data
    val now = parseUpdatedAt(updatedAt)?.epochSecond
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
                val episode = latestEpisode(quote, it.price, depth, updatedAt)
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
                    max_depths = if (depth == null) quote.max_depths else replaceLast(quote.max_depths, round1(depth)),
                    mdd_trough_date = episode.first,
                    mdd_recovery_date = episode.second,
                    intraday = if (keepIntraday) quote.intraday else replaceLast(it.intraday, latest),
                    intraday_times = if (keepIntraday) quote.intraday_times else it.intradayTimes,
                    session_start = if (keepIntraday) quote.session_start else it.sessionStart,
                    session_end = if (keepIntraday) quote.session_end else it.sessionEnd,
                    prev_close = round2(it.prevClose),
                    combined = combinedAfter(quote, it, now, previous),
                    stale = false,
                )
            } ?: quote
        },
    )
}
