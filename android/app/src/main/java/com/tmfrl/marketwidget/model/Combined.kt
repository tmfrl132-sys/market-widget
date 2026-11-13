@file:Suppress("PropertyName")

package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.round

/**
 * 현물·선물 24시간 합친 차트(v1.1). 정규장에는 현물 값을, 장외에는 선물을 직전 정규장 종가 기준으로 환산한 값을 쓴다.
 * 환산값 = 현물 종가 Sc × 선물 F(t) / 그 종가와 같은 가격 시각의 선물 Fc. 예: 7,592 × 7,640 / 7,616 = 7,615.92.
 * Yahoo 5분봉의 시각은 봉 시작이고 종가는 봉 끝(시각 + 5분) 가격이다. 정규장 끝 시각의 현물 봉(16:00)만 예외로 공식 종가 한 건이다.
 * 모든 시각은 초 단위 epoch이고 장 시간은 America/New_York으로 계산한다. 기기 시간대에 기대지 않는다.
 * 선물 계약 교체는 따로 보정하지 않는다. 기준(Sc, Fc)을 매 갱신 같은 응답 안의 정규장 종가로 다시 잡아 흡수한다.
 */

/** 합친 차트를 그리는 지표 id와 그 재료로 받는 선물 Yahoo 심볼. 선물은 카드가 아니다. */
val COMBINED_FUTURES: Map<String, String> = mapOf("GSPC" to "ES=F", "NDX" to "NQ=F")

val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

private val REGULAR_OPEN = LocalTime.of(9, 30)
private val REGULAR_CLOSE = LocalTime.of(16, 0)

/** 이웃한 점의 시각 차이가 이보다 크면 선을 끊는다(정비 시간, 주말, 휴장, 빈 봉). 자정 앞뒤 10분 빈 봉은 잇는다. */
const val COMBINED_GAP_SECONDS = 600L

/** 장 마감 종가와 짝지을 선물 봉을 찾는 범위. 같은 시각 봉이 없을 때 앞뒤 5분 안에서 가장 가까운 봉을 쓴다. */
private const val SAME_TIME_TOLERANCE = 300L

/** 5분봉 길이. 마지막 점 뒤로 창 끝을 늘리는 길이이기도 하다. */
private const val BAR_SECONDS = 300L

private const val WINDOW_SECONDS = 24 * 3600L

/** 시작·끝 시각(초 단위 epoch). */
@Serializable
data class TradingSpan(val start: Long = 0, val end: Long = 0)

/** 현물 5분봉. time은 봉 시작 시각. */
data class SpotBar(val time: Long, val close: Double)

/** 선물 5분봉. 시가가 비어 있으면 open은 null이다. */
data class FuturesBar(val time: Long, val open: Double?, val close: Double)

/** 현물 5일 5분봉. sessions는 Yahoo tradingPeriods에서 읽은 날짜별 정규장(없으면 빈 목록), previousClose는 meta.previousClose. */
data class SpotSeries(val bars: List<SpotBar>, val sessions: List<TradingSpan> = emptyList(), val previousClose: Double? = null)

/** 선물 5일 5분봉. */
data class FuturesSeries(val bars: List<FuturesBar>)

/** 기기가 한 번 갱신에서 받은 합친 차트 재료. 캐시에는 넣지 않는다. */
data class CombinedSource(val spot: SpotSeries, val futures: FuturesSeries)

/**
 * 합친 24시간 차트. 기기가 갱신 때 계산해 Quote.combined에 저장한다. 수집기는 보내지 않는다.
 * times·values·converted는 점마다 1:1이다. converted가 true면 선물 환산값, false면 현물 값이다.
 * breaks는 선을 끊는 점 위치(그 점 앞에서 끊는다). start·end는 창, sessions는 창 안의 정규장 구간이다.
 * baseline은 기준선(직전에 끝난 정규장 종가), price는 마지막 점 값, change·change_pct는 기준선 대비, low·high는 창 안 최저·최고다.
 */
@Serializable
data class CombinedChart(
    val times: List<Long> = emptyList(),
    val values: List<Double> = emptyList(),
    val converted: List<Boolean> = emptyList(),
    val breaks: List<Int> = emptyList(),
    val start: Long = 0,
    val end: Long = 0,
    val sessions: List<TradingSpan> = emptyList(),
    val baseline: Double = 0.0,
    val price: Double = 0.0,
    val change: Double = 0.0,
    val change_pct: Double = 0.0,
    val low: Double = 0.0,
    val high: Double = 0.0,
) {
    /** 마지막 점이 선물 환산값인지. */
    val lastConverted: Boolean get() = converted.lastOrNull() == true
}

private fun round2(value: Double) = round(value * 100) / 100

/** tradingPeriods가 없을 때 쓰는 정규장. 현물 봉이 있는 뉴욕 날짜마다 09:30~16:00(서머타임은 시간대가 알아서 맞춘다). */
fun newYorkSessions(times: List<Long>): List<TradingSpan> =
    times.map { Instant.ofEpochSecond(it).atZone(NEW_YORK).toLocalDate() }.distinct().sorted().map { day ->
        TradingSpan(
            day.atTime(REGULAR_OPEN).atZone(NEW_YORK).toEpochSecond(),
            day.atTime(REGULAR_CLOSE).atZone(NEW_YORK).toEpochSecond(),
        )
    }

/** 현물 봉이 있는 정규장. end는 정규장 끝과 마지막 봉 중 늦은 쪽이고, futuresClose는 마지막 봉과 짝지은 선물 가격(없으면 null)이다. */
private class CombinedSession(val open: Long, val end: Long, val bars: List<SpotBar>, val futuresClose: Double?) {
    val last: SpotBar get() = bars.last()
}

/** 시각 t 봉의 선물 종가(= t + 5분 가격). 같은 시각 봉이 없으면 앞뒤 5분 안에서 가장 가까운 봉(같은 거리면 앞 봉)이고, 그것도 없으면 null. */
private fun futuresCloseAt(bars: List<FuturesBar>, time: Long): Double? =
    bars.filter { abs(it.time - time) <= SAME_TIME_TOLERANCE }
        .minWithOrNull(compareBy<FuturesBar>({ abs(it.time - time) }, { it.time }))
        ?.close?.takeIf { it > 0 }

/**
 * 현물 마지막 봉과 같은 가격 시각의 선물 가격(Fc).
 * - 마지막 봉이 정규장 끝 시각 이후(16:00 공식 종가 한 건, 가격 시각 16:00)면 선물 Tc − 5분 봉의 종가(16:00 가격)다.
 *   그 봉이 없으면 선물 Tc 봉의 시가(역시 16:00 가격), 그것도 없으면 null(그 장 뒤 장외는 그리지 않는다).
 * - 마지막 봉이 그보다 이르면(15:55 봉, 종가 = 16:00 가격) 선물 같은 시각 봉의 종가이고, 없으면 앞뒤 5분 안의 가장 가까운 봉이다.
 */
private fun futuresPriceAtClose(bars: List<FuturesBar>, last: SpotBar, sessionEnd: Long): Double? {
    if (last.time < sessionEnd) return futuresCloseAt(bars, last.time)
    val before = bars.firstOrNull { it.time == last.time - BAR_SECONDS }?.close
    val price = before ?: bars.firstOrNull { it.time == last.time }?.open
    return price?.takeIf { it > 0 }
}

private class CombinedPoint(val time: Long, val value: Double, val converted: Boolean)

/**
 * 합친 24시간 차트를 계산한다. 점이 2개 미만이거나 기준선을 정할 수 없으면 null.
 *
 * - 정규장 안([개장, 끝]): 현물 종가를 쓰고 그 시간의 선물 봉은 버린다.
 * - 장외 선물 봉 t: t보다 먼저 끝난 가장 최근 정규장의 (Sc, Fc)로 Sc × F(t) / Fc. Fc를 못 찾은 정규장 뒤 장외는 그리지 않는다.
 *   F(t)는 봉 종가(t + 5분 가격)이지만 점은 현물 봉처럼 봉 시각 t에 놓는다(현물 봉 종가도 t + 5분 가격이다).
 * - 창: 끝 = min(now, 마지막 점 + 5분), 시작 = 끝 - 24시간.
 * - 기준선: 끝 이전에 끝난 가장 최근 정규장의 종가. 그 정규장이 자료에서 마지막 바로 앞 정규장이면(지금 장중)
 *   meta.previousClose가 그 장의 공식 종가라 그것을 쓴다. 끝난 정규장이 없으면 previousClose.
 */
fun combine24h(spot: SpotSeries, futures: FuturesSeries, now: Long): CombinedChart? {
    val spotBars = spot.bars.sortedBy { it.time }
    val futuresBars = futures.bars.sortedBy { it.time }
    val spans = spot.sessions.ifEmpty { newYorkSessions(spotBars.map { it.time }) }.sortedBy { it.start }
    val sessions = spans.mapNotNull { span ->
        val bars = spotBars.filter { it.time in span.start..span.end }
        val last = bars.lastOrNull() ?: return@mapNotNull null
        CombinedSession(span.start, maxOf(span.end, last.time), bars, futuresPriceAtClose(futuresBars, last, span.end))
    }

    val points = sessions.flatMap { session -> session.bars.map { CombinedPoint(it.time, it.close, converted = false) } }.toMutableList()
    for (bar in futuresBars) {
        if (sessions.any { bar.time in it.open..it.end }) continue
        val owner = sessions.lastOrNull { it.end < bar.time } ?: continue
        val futuresClose = owner.futuresClose ?: continue
        points += CombinedPoint(bar.time, owner.last.close * bar.close / futuresClose, converted = true)
    }
    points.sortBy { it.time }

    val lastPoint = points.lastOrNull() ?: return null
    val end = minOf(now, lastPoint.time + BAR_SECONDS)
    val start = end - WINDOW_SECONDS
    val window = points.filter { it.time in start..end }
    if (window.size < 2) return null

    val doneIndex = sessions.indexOfLast { it.end <= end }
    val baseline = when {
        doneIndex < 0 -> spot.previousClose
        doneIndex == sessions.lastIndex - 1 && spot.previousClose != null -> spot.previousClose
        else -> sessions[doneIndex].last.close
    }?.takeIf { it > 0 } ?: return null

    val values = window.map { round2(it.value) }
    val current = window.last().value
    return CombinedChart(
        times = window.map { it.time },
        values = values,
        converted = window.map { it.converted },
        breaks = (1 until window.size).filter { window[it].time - window[it - 1].time > COMBINED_GAP_SECONDS },
        start = start,
        end = end,
        sessions = sessions.filter { it.open < end && it.end > start }.map { TradingSpan(maxOf(it.open, start), minOf(it.end, end)) },
        baseline = round2(baseline),
        price = values.last(),
        change = round2(current - baseline),
        change_pct = round2((current - baseline) / baseline * 100),
        low = values.min(),
        high = values.max(),
    )
}

/**
 * 그릴 합친 차트. S&P500·나스닥100의 오늘 기간이고 값이 온전할 때만 돌려준다. 아니면 null(기존 오늘 차트를 그린다).
 */
fun combinedChartFor(quote: Quote, period: Period): CombinedChart? {
    val chart = quote.combined ?: return null
    if (period != Period.TODAY || quote.id !in COMBINED_FUTURES) return null
    val consistent = chart.values.size >= 2 && chart.times.size == chart.values.size &&
        chart.converted.size == chart.values.size && chart.end > chart.start && chart.baseline > 0
    return if (consistent) chart else null
}

/** 합친 차트의 가로축. 창 24시간의 시각 비율로 점을 놓고, 눈금은 오늘 차트와 같은 서울 시간 정시(24시간이면 4시간 간격)다. */
fun combinedXAxis(chart: CombinedChart): ChartXAxis =
    ChartXAxis(sessionTicks(chart.start, chart.end), chart.times.map { sessionPosition(it, chart.start, chart.end) })

/** 정규장 배경 띠의 가로 위치(0~1). */
fun combinedBands(chart: CombinedChart): List<ClosedFloatingPointRange<Float>> =
    chart.sessions.filter { it.end > it.start }.map {
        sessionPosition(it.start, chart.start, chart.end)..sessionPosition(it.end, chart.start, chart.end)
    }

private val CONVERTED_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** 마지막 점이 환산값이면 "선물 환산 HH:mm"(서울 시간, 봉 시각), 현물이면 null. */
fun convertedLabel(chart: CombinedChart): String? {
    if (!chart.lastConverted) return null
    val time = chart.times.lastOrNull() ?: return null
    return "선물 환산 " + Instant.ofEpochSecond(time).atZone(SEOUL).format(CONVERTED_TIME_FORMAT)
}
