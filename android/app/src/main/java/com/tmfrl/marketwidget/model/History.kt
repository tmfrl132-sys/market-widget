@file:Suppress("PropertyName")

package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.round

/*
 * 추가 티커의 기록(1년·5년·전체 기간)을 기기에서 만드는 계산. 수집기 collector/quotes.py와 같은 입력에서 같은 결과를 낸다.
 * 함수 이름 옆 괄호는 같은 일을 하는 수집기 함수다.
 */

/** Yahoo 차트 응답. 기록 계산과 추가 확인에 쓰는 필드만 읽는다. */
@Serializable
data class YahooChartResponse(val chart: YahooChartBody = YahooChartBody())

@Serializable
data class YahooChartBody(val result: List<YahooChartResult>? = null)

@Serializable
data class YahooChartResult(
    val meta: YahooChartMeta = YahooChartMeta(),
    val timestamp: List<Long> = emptyList(),
    val indicators: YahooIndicators = YahooIndicators(),
)

@Serializable
data class YahooChartMeta(
    val regularMarketPrice: Double? = null,
    val gmtoffset: Long = 0,
    val exchangeTimezoneName: String? = null,
    val instrumentType: String? = null,
)

@Serializable
data class YahooIndicators(val quote: List<YahooBars> = emptyList())

@Serializable
data class YahooBars(val close: List<Double?> = emptyList())

private val json = Json { ignoreUnknownKeys = true }

/** 차트 응답의 첫 결과. 결과가 없으면 null. 형식이 틀리면 IllegalArgumentException(SerializationException)이 난다. */
fun parseYahooChart(text: String): YahooChartResult? =
    json.decodeFromString<YahooChartResponse>(text).chart.result?.firstOrNull()

private fun round2(value: Double) = round(value * 100) / 100

private fun round1(value: Double) = round(value * 10) / 10

private fun rawCloses(result: YahooChartResult): List<Double?> = result.indicators.quote.firstOrNull()?.close.orEmpty()

/** 비어 있지 않은 봉의 (시각, 종가). */
private fun bars(result: YahooChartResult): List<Pair<Long, Double>> =
    result.timestamp.zip(rawCloses(result)).mapNotNull { (time, close) -> close?.let { time to it } }

private fun offsetDate(epochSeconds: Long, offset: Long): LocalDate =
    Instant.ofEpochSecond(epochSeconds + offset).atOffset(ZoneOffset.UTC).toLocalDate()

/** 봉 시각을 시장 현지 날짜로 바꾸는 함수(_bar_date_fn). 시간대 이름을 읽을 수 없으면 gmtoffset 하나로 계산한다. */
private fun barDateFn(meta: YahooChartMeta): (Long) -> LocalDate {
    val zone = try {
        meta.exchangeTimezoneName?.takeIf { it.isNotEmpty() }?.let { ZoneId.of(it) }
    } catch (e: DateTimeException) {
        null
    }
    return if (zone == null) {
        { time -> offsetDate(time, meta.gmtoffset) }
    } else {
        { time -> Instant.ofEpochSecond(time).atZone(zone).toLocalDate() }
    }
}

/** 비어 있지 않은 종가, 소수 둘째 자리(chart_closes). */
fun chartCloses(result: YahooChartResult): List<Double> = rawCloses(result).filterNotNull().map(::round2)

/** chartCloses와 같은 봉의 시장 현지 날짜 YYYY-MM-DD(chart_dates). */
fun chartDates(result: YahooChartResult): List<String> {
    val toDate = barDateFn(result.meta)
    return bars(result).map { (time, _) -> toDate(time).toString() }
}

/** 첫 봉의 연도. gmtoffset 기준(max_fields의 max_start). 봉이 없으면 null. */
fun maxStartYear(result: YahooChartResult): Int? =
    bars(result).firstOrNull()?.let { (time, _) -> offsetDate(time, result.meta.gmtoffset).year }

/** 날마다 그때까지의 고점 대비 낙폭 %(drawdowns). 반올림하지 않는다. */
fun drawdownSeries(values: List<Double>): List<Double> {
    if (values.isEmpty()) return emptyList()
    var peak = values.first()
    return values.map { value ->
        peak = maxOf(peak, value)
        drawdownPct(value, peak)
    }
}

/** 최대 낙폭 구간. 위치는 값 목록 안의 인덱스이고, recovery는 회복하지 못했으면 null, depth는 소수 첫째 자리 %. */
data class DrawdownEpisode(val peak: Int, val trough: Int, val recovery: Int?, val depth: Double)

/**
 * 최대 낙폭 구간(drawdown_episode). 같은 값의 고점은 나중 위치로 갱신하고, 같은 깊이의 바닥은 처음 위치를 지킨다.
 * 회복은 바닥 뒤 처음으로 그 고점 값 이상이 된 위치다. 낙폭이 없으면 null.
 */
fun drawdownEpisode(values: List<Double>): DrawdownEpisode? {
    if (values.isEmpty()) return null
    var peak = values.first()
    var peakIndex = 0
    var best = 0.0
    var trough = -1
    var bestPeak = -1
    values.forEachIndexed { i, value ->
        if (value >= peak) {
            peak = value
            peakIndex = i
        }
        val depth = drawdownPct(value, peak)
        if (depth < best) {
            best = depth
            trough = i
            bestPeak = peakIndex
        }
    }
    if (trough < 0) return null
    val recovery = (trough + 1 until values.size).firstOrNull { values[it] >= values[bestPeak] }
    return DrawdownEpisode(bestPeak, trough, recovery, round1(best))
}

/** 전체 기간 차트에 보내는 최대 점 수(수집기 MAX_POINTS). */
private const val MAX_POINTS = 500

/** 묶음 크기(_bucket_size). 묶음마다 2점에 처음·마지막 값까지 더해도 MAX_POINTS를 넘지 않게 한다. */
fun bucketSize(count: Int): Int {
    if (count <= MAX_POINTS) return 1
    val perBucket = (MAX_POINTS - 2) / 2
    return (count + perBucket - 1) / perBucket
}

/** 남길 점의 위치(thin_indices). 묶음의 최저·최고를 시간 순서로 남기고 처음·마지막 점은 지킨다. 겹치면 같은 위치가 두 번 들어간다. */
fun thinIndices(values: List<Double>): List<Int> {
    val size = bucketSize(values.size)
    if (size == 1) return values.indices.toList()
    val picks = mutableListOf(0)
    for (start in values.indices step size) {
        val chunk = values.subList(start, minOf(start + size, values.size))
        // 같은 값이 여럿이면 처음 위치(파이썬 list.index와 같다)
        val low = chunk.indexOf(chunk.min())
        val high = chunk.indexOf(chunk.max())
        picks += sortedSetOf(low, high).map { start + it }
    }
    picks += values.size - 1
    return picks
}

/** thinIndices에 extras 중 빠진 위치를 정렬 위치에 더한다(max_picks). thinIndices가 만든 중복은 그대로 둔다. */
fun maxPicks(values: List<Double>, extras: List<Int>): List<Int> {
    val picks = thinIndices(values)
    val present = picks.toSet()
    val added = extras.filter { it in values.indices && it !in present }.distinct()
    return (picks + added).sorted()
}

/** 낙폭을 계산하지 않는 지수 심볼. 변동성·금리는 고점 대비 하락이 손실이 아니다(수집기 NO_DRAWDOWN과 같은 뜻). */
val NO_DRAWDOWN_SYMBOLS: Set<String> = setOf("^VIX", "^VXN", "^VVIX", "^TNX", "^TYX", "^FVX", "^IRX")

/** 추가 티커가 낙폭을 계산하는지. */
fun hasDrawdown(symbol: String): Boolean = symbol !in NO_DRAWDOWN_SYMBOLS

/** 전체 기간 필드(max_fields에서 사건을 뺀 것). 낙폭 없는 지표는 depths가 비고 peak·mdd·날짜가 null이다. */
data class MaxFields(
    val closes: List<Double>,
    val dates: List<String>,
    val depths: List<Double>,
    val start: Int?,
    val peak: Double?,
    val mdd: Double?,
    val peakDate: String?,
    val troughDate: String?,
    val recoveryDate: String?,
)

/** period1=0 일봉 응답으로 전체 기간 필드를 만든다. 낙폭은 전체 일봉으로 계산하고 점만 줄인다. 봉이 없으면 null. */
fun maxFields(result: YahooChartResult, withDrawdown: Boolean): MaxFields? {
    val bars = bars(result)
    if (bars.isEmpty()) return null
    val closes = bars.map { it.second }
    val toDate = barDateFn(result.meta)
    val dateAt = { i: Int -> toDate(bars[i].first).toString() }
    val depths = if (withDrawdown) drawdownSeries(closes) else emptyList()
    val episode = if (withDrawdown) drawdownEpisode(closes) else null
    val extras = listOfNotNull(episode?.peak, episode?.trough, episode?.recovery)
    val picks = maxPicks(closes, extras)
    return MaxFields(
        closes = picks.map { round2(closes[it]) },
        dates = picks.map(dateAt),
        depths = if (withDrawdown) picks.map { round1(depths[it]) } else emptyList(),
        start = maxStartYear(result),
        peak = if (withDrawdown) round2(closes.max()) else null,
        mdd = if (withDrawdown) round1(depths.min()) else null,
        peakDate = episode?.let { dateAt(it.peak) },
        troughDate = episode?.let { dateAt(it.trough) },
        recoveryDate = episode?.recovery?.let(dateAt),
    )
}

private const val BIG_MOVE_DAYS = 60
private const val BIG_MOVE_FACTOR = 2.5

/** 큰 움직임 기준 %(big_move_pct). max(base, 2.5 × 최근 60거래일 하루 등락폭 절댓값의 중앙값), 소수 첫째 자리. 종가 61개 미만이면 base. */
fun bigMovePct(base: Double, closes: List<Double>): Double {
    if (closes.size < BIG_MOVE_DAYS + 1) return base
    val tail = closes.takeLast(BIG_MOVE_DAYS + 1)
    val moves = tail.zipWithNext { a, b -> abs(b / a - 1) * 100 }.sorted()
    val mid = moves.size / 2
    val median = if (moves.size % 2 == 0) (moves[mid - 1] + moves[mid]) / 2 else moves[mid]
    return round1(maxOf(base, BIG_MOVE_FACTOR * median))
}
