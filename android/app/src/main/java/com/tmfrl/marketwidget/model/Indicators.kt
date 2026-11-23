package com.tmfrl.marketwidget.model

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * 보조 지표(v1.7). 모두 차트 점과 1:1인 목록이고, 계산할 수 없는 앞쪽 점은 null이다.
 * 1·3개월은 1년 일봉 전체로 계산한 뒤 끝을 잘라 써서 첫 점부터 값이 있다(예열).
 */
const val RSI_PERIOD = 14
const val BOLLINGER_PERIOD = 20
const val BOLLINGER_WIDTH = 2.0
const val HV_PERIOD = 20

private fun round2(value: Double) = round(value * 100) / 100

/** RSI(Wilder). 첫 값은 period번째 점(앞 period개 변화의 평균)에 놓인다. 오르내림이 모두 0이면 50이다. */
fun rsi(values: List<Double>, period: Int = RSI_PERIOD): List<Double?> {
    val out = MutableList<Double?>(values.size) { null }
    if (period < 1 || values.size <= period) return out
    var gain = 0.0
    var loss = 0.0
    for (i in 1..period) {
        val d = values[i] - values[i - 1]
        if (d > 0) gain += d else loss -= d
    }
    var avgGain = gain / period
    var avgLoss = loss / period
    out[period] = rsiOf(avgGain, avgLoss)
    for (i in period + 1 until values.size) {
        val d = values[i] - values[i - 1]
        avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
        avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
        out[i] = rsiOf(avgGain, avgLoss)
    }
    return out
}

private fun rsiOf(avgGain: Double, avgLoss: Double): Double = round2(
    when {
        avgLoss == 0.0 && avgGain == 0.0 -> 50.0
        avgLoss == 0.0 -> 100.0
        else -> 100 - 100 / (1 + avgGain / avgLoss)
    },
)

/** 볼린저 밴드. 가운데는 period 이동평균, 위·아래는 ± width × 모표준편차다. */
data class Bands(val middle: List<Double?>, val upper: List<Double?>, val lower: List<Double?>)

fun bollinger(values: List<Double>, period: Int = BOLLINGER_PERIOD, width: Double = BOLLINGER_WIDTH): Bands {
    val middle = MutableList<Double?>(values.size) { null }
    val upper = MutableList<Double?>(values.size) { null }
    val lower = MutableList<Double?>(values.size) { null }
    if (period >= 1) {
        for (i in period - 1 until values.size) {
            val window = values.subList(i - period + 1, i + 1)
            val mean = window.average()
            val sd = sqrt(window.sumOf { (it - mean) * (it - mean) } / period)
            middle[i] = round2(mean)
            upper[i] = round2(mean + width * sd)
            lower[i] = round2(mean - width * sd)
        }
    }
    return Bands(middle, upper, lower)
}

/**
 * 역사적 변동성(%, 연율). 최근 period개 로그 수익률의 표본표준편차 × √(1년 봉 수) × 100.
 * 0 이하 값이 섞인 구간(마이너스 유가)은 null이다.
 */
fun historicalVolatility(values: List<Double>, periodsPerYear: Int, period: Int = HV_PERIOD): List<Double?> {
    val out = MutableList<Double?>(values.size) { null }
    if (period < 2 || values.size <= period) return out
    val returns = (1 until values.size).map { i ->
        if (values[i] > 0 && values[i - 1] > 0) ln(values[i] / values[i - 1]) else null
    }
    for (i in period until values.size) {
        val window = returns.subList(i - period, i)
        if (window.any { it == null }) continue
        val r = window.filterNotNull()
        val mean = r.average()
        val variance = r.sumOf { (it - mean) * (it - mean) } / (r.size - 1)
        out[i] = round2(sqrt(variance) * sqrt(periodsPerYear.toDouble()) * 100)
    }
    return out
}

/** 이 기간에 RSI·볼린저 밴드를 계산할 수 있는지. MAX는 점을 줄여 간격이 고르지 않아 뺀다. */
fun supportsMomentum(period: Period): Boolean = period != Period.MAX

/** 이 기간에 HV를 보이는지. 연율로 바꿀 봉 간격이 일정한 일봉·주봉 기간만이다. */
fun supportsVolatility(period: Period): Boolean =
    period == Period.ONE_MONTH || period == Period.THREE_MONTHS || period == Period.ONE_YEAR || period == Period.FIVE_YEARS

/** HV 연율화의 1년 봉 수. 5년은 주봉 52, 일봉은 252(늘 거래하는 비트코인은 365)다. */
fun periodsPerYear(quote: Quote, period: Period): Int = when {
    period == Period.FIVE_YEARS -> 52
    quote.always_open -> 365
    else -> 252
}

/**
 * 지표를 계산할 긴 계열. 끝 shown개가 차트 점과 같다. 1·3개월은 1년 일봉 전체라 앞쪽이 예열에 쓰인다.
 * shown이 계열보다 길거나 MAX면 null이다.
 */
fun indicatorSeries(quote: Quote, period: Period, shown: List<Double>): List<Double>? {
    if (!supportsMomentum(period)) return null
    val base = when (period) {
        Period.ONE_MONTH, Period.THREE_MONTHS -> quote.daily.ifEmpty { shown }
        else -> shown
    }
    if (base.size < shown.size || base.takeLast(shown.size) != shown) return shown
    return base
}

/** 긴 계열로 계산한 지표의 끝을 차트 점 수에 맞춘다. */
fun <T> tailOf(values: List<T>, shown: Int): List<T> = values.takeLast(shown)

/** 일봉 캔들 재료. 종가는 차트 값(chartValues)이고 시가·고가·저가는 그와 1:1이다. */
data class Candles(val open: List<Double>, val high: List<Double>, val low: List<Double>)

/** 캔들을 그릴 수 있는 기간(일봉을 쓰는 1개월·3개월·1년). */
fun supportsCandles(period: Period): Boolean =
    period == Period.ONE_MONTH || period == Period.THREE_MONTHS || period == Period.ONE_YEAR

/** 이 기간의 캔들 재료. 일봉 시가·고가·저가가 일봉 종가와 길이가 같을 때만이고, 아니면(옛 data.json) null이다. */
fun candlesFor(quote: Quote, period: Period): Candles? {
    if (!supportsCandles(period) || quote.daily.isEmpty()) return null
    val n = quote.daily.size
    if (quote.daily_open.size != n || quote.daily_high.size != n || quote.daily_low.size != n) return null
    val open = periodValues(quote.daily_open, emptyList(), period)
    if (open.size < 2) return null
    return Candles(open, periodValues(quote.daily_high, emptyList(), period), periodValues(quote.daily_low, emptyList(), period))
}

/**
 * 일봉 끝이 바뀐 만큼 시가·고가·저가도 맞춘다. 새 봉이 붙었으면 시가·고가·저가를 그 가격으로 시작하고,
 * 끝 봉을 바꿨으면 고가·저가만 넓힌다. 길이가 어긋난 목록은 건드리지 않는다.
 */
fun latestOhlc(quote: Quote, newDaily: List<Double>, price: Double): Triple<List<Double>, List<Double>, List<Double>> {
    val old = quote.daily.size
    val (o, h, l) = Triple(quote.daily_open, quote.daily_high, quote.daily_low)
    if (o.size != old || h.size != old || l.size != old || old == 0) return Triple(o, h, l)
    val p = round2(price)
    return when (newDaily.size) {
        old + 1 -> Triple(o + p, h + p, l + p)
        old -> Triple(o, h.dropLast(1) + maxOf(h.last(), p), l.dropLast(1) + minOf(l.last(), p))
        else -> Triple(o, h, l)
    }
}
