package com.tmfrl.marketwidget.model

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.log10
import kotlin.math.round

/** 차트 칸 상태. selected는 차트를 연 지표, compare는 겹쳐 볼 지표, picking은 비교할 타일을 고르는 중인지다. */
data class ChartSelection(val selected: String? = null, val compare: String? = null, val picking: Boolean = false)

/**
 * 타일(또는 차트)을 눌렀을 때의 다음 상태. 차트를 누르면 그 차트 지표의 타일을 누른 것과 같다.
 * 자동모드에서는 그 지표로 건너뛰고 비교는 없다.
 */
fun afterTileTap(s: ChartSelection, tapped: String, auto: Boolean): ChartSelection = when {
    auto -> ChartSelection(selected = tapped)
    s.picking && s.selected != null ->
        // 지금 차트의 타일을 누르면 고르기만 취소한다
        if (tapped == s.selected) s.copy(picking = false) else ChartSelection(s.selected, tapped)
    s.compare != null -> when (tapped) {
        s.selected -> ChartSelection()
        // 같은 지표끼리는 비교하지 않는다
        s.compare -> ChartSelection(selected = tapped)
        // 비교 대상을 유지해 "코스피 대비 여러 지표"를 차례로 보기 쉽게 한다
        else -> ChartSelection(tapped, s.compare)
    }
    else -> ChartSelection(selected = toggleSelection(s.selected, tapped))
}

/** 기간 줄의 비교 버튼. 고르는 중이거나 비교 중이면 끄고, 차트가 열려 있으면 고르기를 시작한다. */
fun afterCompareButton(s: ChartSelection): ChartSelection = when {
    s.picking || s.compare != null -> s.copy(compare = null, picking = false)
    s.selected != null -> s.copy(picking = true)
    else -> s
}

/** 자동모드를 켜면 비교와 고르는 중을 지운다. 1분마다 주 지표가 바뀌면 비교의 뜻이 계속 바뀐다. */
fun afterAutoOn(s: ChartSelection): ChartSelection = s.copy(compare = null, picking = false)

/** 겹쳐 그릴 두 지표. dates는 주 지표 날짜이고, 값은 공통 시작일을 100으로 맞춘 지수다. */
data class CompareSeries(val dates: List<String>, val primary: List<Double>, val other: List<Double>)

private fun parseDays(dates: List<String>): List<LocalDate>? = try {
    dates.map { LocalDate.parse(it) }
} catch (e: DateTimeException) {
    null
}

private fun isSorted(days: List<LocalDate>) = days.zipWithNext().none { (before, after) -> before > after }

/** 비교 지표는 기간으로 자르지 않은 같은 종류의 전체 목록을 쓴다. 휴장일 차이로 첫 날이 어긋나지 않게 하기 위해서다. */
private fun fullSeries(quote: Quote, period: Period): Pair<List<Double>, List<String>>? = when (period) {
    Period.TODAY -> null
    Period.ONE_MONTH, Period.THREE_MONTHS, Period.ONE_YEAR -> quote.daily to quote.daily_dates
    Period.FIVE_YEARS -> quote.weekly to quote.weekly_dates
    Period.MAX -> quote.max_closes to quote.max_dates
}

/**
 * 두 지표를 주 지표 날짜에 맞춘다. 공통 시작일(두 첫 날짜 중 늦은 날) 이후의 주 지표 점만 남기고,
 * 비교 값은 그 날짜 이하에서 가장 늦은 값을 이어 쓴다. 두 값 모두 공통 시작일 값을 100으로 둔다.
 * 오늘 기간, 날짜와 값 길이가 다름, 날짜를 읽을 수 없음, 역순, 남은 점 2개 미만, 기준값 0이면 null.
 */
fun compareSeries(primary: Quote, other: Quote, period: Period): CompareSeries? {
    if (period == Period.TODAY) return null
    val values = chartValues(primary, period)
    val dates = chartDates(primary, period)
    if (values.size < 2 || dates.size != values.size) return null
    val (otherValues, otherDates) = fullSeries(other, period) ?: return null
    if (otherValues.isEmpty() || otherDates.size != otherValues.size) return null
    val days = parseDays(dates) ?: return null
    val otherDays = parseDays(otherDates) ?: return null
    if (!isSorted(days) || !isSorted(otherDays)) return null

    val start = maxOf(days.first(), otherDays.first())
    val kept = days.indices.filter { days[it] >= start }
    if (kept.size < 2) return null
    var cursor = 0
    val matched = kept.map { i ->
        // 날짜가 정렬되어 있어 앞에서부터 한 번만 훑는다
        while (cursor + 1 < otherDays.size && otherDays[cursor + 1] <= days[i]) cursor++
        otherValues[cursor]
    }
    val primaryBase = values[kept.first()]
    val otherBase = matched.first()
    if (primaryBase == 0.0 || otherBase == 0.0) return null
    return CompareSeries(
        kept.map { dates[it] },
        kept.map { values[it] * 100 / primaryBase },
        matched.map { it * 100 / otherBase },
    )
}

/** 로그 비교 차트. 값은 시작 대비 배수의 지수(시작이 0)이고, 세로축은 두 선을 합친 범위다. */
data class CompareLog(val primary: List<Double>, val other: List<Double>, val axis: YAxis)

/** 두 선을 로그로 바꾼다. 0 이하 값이 있거나 점이 2개 미만이면 null이고, 그러면 일반 눈금으로 그린다. */
fun compareLogChart(primary: List<Double>, other: List<Double>): CompareLog? {
    if (primary.size < 2 || other.size < 2) return null
    if (primary.any { it <= 0 } || other.any { it <= 0 }) return null
    val primaryLogs = primary.map { log10(it / primary.first()) }
    val otherLogs = other.map { log10(it / other.first()) }
    val all = primaryLogs + otherLogs
    return CompareLog(primaryLogs, otherLogs, logAxis(all.min(), all.max()))
}

private val symbols = DecimalFormatSymbols(Locale.US)
private val indexFormat = DecimalFormat("#,##0.##", symbols).apply { roundingMode = RoundingMode.HALF_UP }
private val legendFormat = DecimalFormat("#,##0.0", symbols).apply { roundingMode = RoundingMode.HALF_UP }
private val COMPARE_DAY_FORMAT = DateTimeFormatter.ofPattern("M/d")
private val COMPARE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.M.d")

/** 비교 차트 세로축 숫자. 정수면 110, 아니면 102.5. */
fun formatIndex(value: Double): String = indexFormat.format(value)

/** 범례의 지수값. 소수 첫째 자리. */
fun formatLegendValue(value: Double): String = legendFormat.format(value)

/** 범례의 등락 %. 지수값에서 100을 빼고 소수 첫째 자리로 반올림한다. 글자와 색이 같은 값을 쓴다. */
fun legendChangePct(value: Double): Double = round((value - 100) * 10) / 10

/** 범례의 등락 문구. 소수 첫째 자리에서 0이 되면 부호를 붙이지 않는다. */
fun formatLegendChange(value: Double): String {
    val change = legendChangePct(value)
    return when {
        change > 0 -> "+${legendFormat.format(change)}%"
        change < 0 -> "${legendFormat.format(change)}%"
        else -> "0.0%"
    }
}

/** 비교 차트 아래 왼쪽 문구. 1개월~1년은 M/d, 5년·MAX는 yyyy.M.d. 날짜를 읽을 수 없으면 빈 문자열. */
fun formatCompareStart(date: String, period: Period): String {
    val day = parseDays(listOf(date))?.first() ?: return ""
    val format = if (period == Period.FIVE_YEARS || period == Period.MAX) COMPARE_DATE_FORMAT else COMPARE_DAY_FORMAT
    return "${day.format(format)} = 100"
}
