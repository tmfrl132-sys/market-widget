package com.tmfrl.marketwidget.model

import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.round

/** 고점 대비 낙폭(%). 마이너스 유가 때문에 -100 아래는 -100으로 막는다. */
fun drawdownPct(value: Double, peak: Double): Double =
    if (peak <= 0) 0.0 else maxOf(-100.0, (value / peak - 1) * 100)

/** current는 현재 값의 고점 대비 낙폭, mdd는 그 기간에 있었던 가장 큰 낙폭. */
data class DrawdownSummary(val current: Double, val mdd: Double)

/**
 * 선택한 기간 기준의 낙폭. 오늘 차트와, 낙폭을 안 쓰는 지표(수집기가 mdd를 보내지 않음)는 null.
 * MAX는 일봉 전체로 계산한 수집기 값을 쓴다. 차트 점은 줄어 있어서 다시 계산하면 작게 나온다.
 */
fun drawdownSummary(quote: Quote, period: Period): DrawdownSummary? {
    val allTimeMdd = quote.mdd ?: return null
    return when (period) {
        Period.TODAY -> null
        Period.MAX -> {
            val price = quote.price ?: return null
            val peak = quote.peak ?: return null
            DrawdownSummary(drawdownPct(price, peak), allTimeMdd)
        }
        else -> {
            val values = chartValues(quote, period)
            if (values.size < 2) return null
            var peak = values.first()
            var mdd = 0.0
            values.forEach { value ->
                peak = maxOf(peak, value)
                mdd = minOf(mdd, drawdownPct(value, peak))
            }
            DrawdownSummary(drawdownPct(values.last(), peak), mdd)
        }
    }
}

/**
 * 잠수 차트. depths는 가격 차트 점과 1:1인 낙폭 %(0 이하, 소수 첫째 자리), episode는 최대 낙폭 구간(없으면 null),
 * current·mdd는 문구 줄 값, marks는 잠수 차트 표시(점 위치와 문구)다.
 */
data class DrawdownChart(
    val depths: List<Double>,
    val episode: DrawdownEpisode?,
    val current: Double,
    val mdd: Double,
    val marks: List<Pair<Int, String>>,
)

private fun round1(value: Double) = round(value * 10) / 10

/** 날짜 이하인 마지막 위치. 날짜를 읽을 수 없거나 첫 날짜보다 앞이면 null. 날짜가 정렬되어 있다고 본다. */
fun indexAtOrBefore(dates: List<String>, date: String?): Int? {
    if (date == null) return null
    val day = parseDayOrNull(date) ?: return null
    var found: Int? = null
    dates.forEachIndexed { i, text ->
        val d = parseDayOrNull(text) ?: return null
        if (d <= day) found = i
    }
    return found
}

private fun parseDayOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (e: DateTimeException) {
    null
}

/** MAX 구간. 수집기 날짜로 위치를 찾고, 날짜가 없거나 맞지 않으면 줄인 점으로 다시 계산한다. */
private fun maxEpisode(quote: Quote): DrawdownEpisode? {
    val dates = quote.max_dates.takeIf { it.size == quote.max_closes.size }
    val peak = dates?.let { indexAtOrBefore(it, quote.mdd_peak_date) }
    val trough = dates?.let { indexAtOrBefore(it, quote.mdd_trough_date) }
    if (dates == null || peak == null || trough == null || peak > trough) return drawdownEpisode(quote.max_closes)
    val recovery = quote.mdd_recovery_date?.let { indexAtOrBefore(dates, it) }?.takeIf { it > trough }
    return DrawdownEpisode(peak, trough, recovery, quote.mdd ?: 0.0)
}

/**
 * 고른 기간의 잠수 차트. 오늘 차트와 낙폭을 안 쓰는 지표(mdd가 null)는 null.
 * 1개월~5년은 그 기간 점으로 기기가 계산하고(기간 첫 값부터의 누적 고점), MAX는 수집기 max_depths(길이가 max_closes와 같을 때)를 쓴다.
 * 표시는 사건(MAX, 날짜 있는 것만) → 바닥 → 현재 순서다. 같은 점이면 문구 하나로 합친다.
 */
fun drawdownChart(quote: Quote, period: Period): DrawdownChart? {
    if (period == Period.TODAY) return null
    val summary = drawdownSummary(quote, period) ?: return null
    val values = chartValues(quote, period)
    if (values.size < 2) return null
    val isMax = period == Period.MAX
    val depths = if (isMax && quote.max_depths.size == quote.max_closes.size) {
        quote.max_depths
    } else {
        drawdownSeries(values).map(::round1)
    }
    val episode = if (isMax) maxEpisode(quote) else drawdownEpisode(values)
    val current = round1(summary.current)
    val mdd = round1(summary.mdd)
    val last = depths.lastIndex

    val events = if (!isMax || quote.max_dates.size != quote.max_closes.size) {
        emptyList()
    } else {
        quote.max_events.mapNotNull { event ->
            indexAtOrBefore(quote.max_dates, event.date)?.let { it to event }
        }
    }
    val trough = episode?.trough
    val troughEvent = events.firstOrNull { it.first == trough }?.second
    val marks = mutableListOf<Pair<Int, String>>()
    events.filter { it.first != trough }.forEach { (i, event) -> marks += i to "${event.label} ${formatDrawdown(event.depth)}" }
    if (trough != null) {
        val label = when {
            troughEvent != null -> "${troughEvent.label} ${formatDrawdown(troughEvent.depth)} · MDD"
            trough == last -> "MDD·현재 ${formatDrawdown(mdd)}"
            else -> "MDD ${formatDrawdown(mdd)}"
        }
        marks += trough to label
    }
    if (trough != last) marks += last to "현재 ${formatDrawdown(current)}"
    return DrawdownChart(depths, episode, current, mdd, marks)
}

private val EPISODE_DAY = DateTimeFormatter.ofPattern("M/d")
private val EPISODE_SHORT_DATE = DateTimeFormatter.ofPattern("yy.M.d")
private val EPISODE_MONTH = DateTimeFormatter.ofPattern("yyyy.M")

/**
 * 문구 줄의 구간 부분. 예: "10/7 고점 → 10/8 바닥 → 미회복". dates는 차트 점과 1:1인 날짜(chartDates).
 * 날짜를 믿을 수 없거나(빈 목록, 읽을 수 없음, 위치가 범위 밖) 구간이 없으면 null.
 * 날짜 형식은 1·3개월 M/d, 1년 yy.M.d, 5년·MAX yyyy.M.
 */
fun formatEpisode(dates: List<String>, episode: DrawdownEpisode?, period: Period): String? {
    if (episode == null || dates.isEmpty()) return null
    val format = when (period) {
        Period.ONE_MONTH, Period.THREE_MONTHS -> EPISODE_DAY
        Period.ONE_YEAR -> EPISODE_SHORT_DATE
        Period.FIVE_YEARS, Period.MAX -> EPISODE_MONTH
        Period.TODAY -> return null
    }
    val day = { i: Int -> dates.getOrNull(i)?.let(::parseDayOrNull)?.format(format) }
    val peak = day(episode.peak) ?: return null
    val trough = day(episode.trough) ?: return null
    val recovery = episode.recovery?.let { day(it) ?: return null } ?: "미회복"
    val end = if (episode.recovery == null) recovery else "$recovery 회복"
    return "$peak 고점 → $trough 바닥 → $end"
}
