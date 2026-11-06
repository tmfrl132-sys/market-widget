package com.tmfrl.marketwidget.model

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** 가로축 눈금. position은 그림 영역 안의 가로 위치(0~1)다. */
data class XTick(val position: Float, val label: String)

/** 자리를 잡은 눈금 글자. left는 글자 왼쪽 좌표, x는 눈금(세로 보조선)의 가로 좌표다. */
data class XLabel(val text: String, val left: Float, val x: Float)

/**
 * 큰 차트의 가로축. ticks가 비면 눈금을 그리지 않는다.
 * positions는 점마다의 가로 위치(0~1)이고, null이면 점을 가로 전체에 고르게 놓는다.
 */
data class ChartXAxis(val ticks: List<XTick> = emptyList(), val positions: List<Float>? = null)

private const val DATE_TICKS = 5
private const val MIN_BOUNDARY_TICKS = 2
private const val MAX_YEAR_TICKS = 6
private val MAX_YEAR_STEPS = listOf(1, 2, 5, 10, 20)
private const val MAX_SESSION_TICKS = 8
private const val MIN_EDGE_DISTANCE = 0.08
private const val SECONDS_PER_HOUR = 3600L

/** 장 시간이 이보다 길면 잘못된 값으로 보고 중간 눈금을 넣지 않는다. */
private const val MAX_SESSION_SECONDS = 7 * 24 * SECONDS_PER_HOUR
private val HOUR_STEPS = listOf(1, 2, 3, 4, 6)

private val MONTH_DAY_FORMAT = DateTimeFormatter.ofPattern("M/d")
private val SHORT_YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yy.M")
private val YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy.M")
private val YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy")
private val SESSION_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** 균등 간격 눈금의 문구 형식. 경계 눈금도 5년만 빼고 같은 형식을 쓴다. */
private fun dateFormatOf(period: Period): DateTimeFormatter? = when (period) {
    Period.TODAY -> null
    Period.ONE_MONTH, Period.THREE_MONTHS -> MONTH_DAY_FORMAT
    Period.ONE_YEAR -> SHORT_YEAR_MONTH_FORMAT
    Period.FIVE_YEARS -> YEAR_MONTH_FORMAT
    Period.MAX -> YEAR_FORMAT
}

private fun parseDateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (e: DateTimeException) {
    null
}

/** 달력 구간 번호. 1개월은 월요일 시작 주, 3개월은 달, 1년은 분기, 5년과 MAX는 해다. */
private fun sectionOf(day: LocalDate, period: Period): Long = when (period) {
    Period.ONE_MONTH -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    Period.THREE_MONTHS -> day.year * 12L + day.monthValue
    Period.ONE_YEAR -> day.year * 4L + (day.monthValue - 1) / 3
    Period.TODAY, Period.FIVE_YEARS, Period.MAX -> day.year.toLong()
}

/**
 * 앞 점과 구간이 달라지는 첫 점들. 인덱스 0은 앞 구간을 알 수 없어 넣지 않는다.
 * MAX는 해가 간격의 배수인 점만 남긴다. 간격은 눈금이 6개 이하가 되는 가장 작은 값이다.
 */
private fun boundaryIndices(days: List<LocalDate>, period: Period): List<Int> {
    val firsts = (1 until days.size).filter { sectionOf(days[it], period) != sectionOf(days[it - 1], period) }
    if (period != Period.MAX) return firsts
    val step = MAX_YEAR_STEPS.firstOrNull { candidate ->
        firsts.count { days[it].year % candidate == 0 } <= MAX_YEAR_TICKS
    } ?: return emptyList()
    return firsts.filter { days[it].year % step == 0 }
}

/** 첫 점과 마지막 점을 포함한 고른 위치. 값이 5개 미만이면 값 수만큼이고, 같은 날짜는 눈금 하나만 남긴다. */
private fun evenIndices(days: List<LocalDate>): List<Int> {
    val count = minOf(DATE_TICKS, days.size)
    val lastIndex = days.size - 1
    return (0 until count).map { n -> (n * lastIndex.toDouble() / (count - 1)).roundToInt() }.distinctBy { days[it] }
}

/**
 * 날짜 눈금. 달력 경계(그 구간의 첫 거래일)에 놓고 문구는 실제 그 날짜로 쓴다.
 * 경계가 2개 미만이면 균등 간격으로 대체한다.
 * 날짜 수가 값 수와 다르거나, 읽을 수 없는 날짜가 있거나, 날짜가 정렬되어 있지 않으면 눈금을 만들지 않는다.
 */
fun dateTicks(dates: List<String>, valueCount: Int, period: Period): List<XTick> {
    if (valueCount < 2 || dates.size != valueCount) return emptyList()
    val evenFormat = dateFormatOf(period) ?: return emptyList()
    val days = dates.map { parseDateOrNull(it) ?: return emptyList() }
    if (days.zipWithNext().any { (before, after) -> before > after }) return emptyList()
    val boundaries = boundaryIndices(days, period)
    val useBoundaries = boundaries.size >= MIN_BOUNDARY_TICKS
    val indices = if (useBoundaries) boundaries else evenIndices(days)
    val format = if (useBoundaries && period == Period.FIVE_YEARS) YEAR_FORMAT else evenFormat
    val lastIndex = valueCount - 1
    return indices.map { XTick(it.toFloat() / lastIndex, days[it].format(format)) }
}

/** 장 시작과 끝 사이의 가로 위치. 0~1로 제한한다. */
fun sessionPosition(time: Long, start: Long, end: Long): Float =
    ((time - start).toDouble() / (end - start)).coerceIn(0.0, 1.0).toFloat()

private fun seoulHour(epochSeconds: Long): Int = Instant.ofEpochSecond(epochSeconds).atZone(SEOUL).hour

/** 시작과 끝 사이의 서울 시간 정시 눈금. 시작과 끝까지 합쳐 8개 이하가 되는 가장 작은 간격을 쓴다. */
private fun hourMarks(start: Long, end: Long): List<Long> {
    if (end - start > MAX_SESSION_SECONDS) return emptyList()
    val firstHour = Instant.ofEpochSecond(start).atZone(SEOUL).truncatedTo(ChronoUnit.HOURS).toEpochSecond() + SECONDS_PER_HOUR
    val hours = generateSequence(firstHour) { it + SECONDS_PER_HOUR }.takeWhile { it < end }.toList()
    val step = HOUR_STEPS.firstOrNull { candidate ->
        hours.count { seoulHour(it) % candidate == 0 } + 2 <= MAX_SESSION_TICKS
    } ?: return emptyList()
    return hours.filter { seoulHour(it) % step == 0 }
}

private fun sessionLabel(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(SEOUL).format(SESSION_TIME_FORMAT)

private fun isAwayFromEdges(position: Double) =
    position >= MIN_EDGE_DISTANCE && 1 - position >= MIN_EDGE_DISTANCE

/** 오늘 차트 눈금. 시작과 끝 사이에 정시를 넣고, 시작이나 끝에 너무 가까운 정시는 뺀다. */
fun sessionTicks(start: Long, end: Long): List<XTick> {
    val middle = hourMarks(start, end).filter { isAwayFromEdges((it - start).toDouble() / (end - start)) }
    return (listOf(start) + middle + end).map { XTick(sessionPosition(it, start, end), sessionLabel(it)) }
}

/**
 * 오늘 차트의 가로축. 시각이나 장 시간을 믿을 수 없으면 지금처럼 가로 전체에 늘려 그리고 눈금도 없앤다.
 * 봉의 절반을 넘게 장 시간 밖이면 다른 거래일의 장 시간으로 보고 믿지 않는다. 소수만 밖이면 끝에 붙인다.
 */
fun todayXAxis(valueCount: Int, times: List<Long>, start: Long?, end: Long?): ChartXAxis {
    if (valueCount < 2 || times.size != valueCount) return ChartXAxis()
    if (start == null || end == null || end <= start) return ChartXAxis()
    if (times.count { it < start || it > end } * 2 > times.size) return ChartXAxis()
    return ChartXAxis(sessionTicks(start, end), times.map { sessionPosition(it, start, end) })
}

/**
 * 시세 타일 작은 차트의 점 위치(0~1). 큰 오늘 차트와 같은 todayXAxis 위치를 쓴다.
 * 장 시간을 믿을 수 없거나 시각이 없으면 null이고, 그러면 지금처럼 가로 전체에 고르게 놓는다.
 */
fun tileXPositions(quote: Quote): List<Float>? =
    todayXAxis(quote.intraday.size, quote.intraday_times, quote.session_start, quote.session_end).positions

/**
 * 고른 기간의 가로축. MAX의 낙폭 차트도 이 눈금을 쓴다.
 * max_drawdowns는 묶음마다 1점이라 max_dates와 개수가 다르지만 같은 기간을 같은 비율로 덮는다.
 */
fun chartXAxis(quote: Quote, period: Period): ChartXAxis =
    if (period == Period.TODAY) {
        todayXAxis(quote.intraday.size, quote.intraday_times, quote.session_start, quote.session_end)
    } else {
        ChartXAxis(dateTicks(chartDates(quote, period), chartValues(quote, period).size, period))
    }

/**
 * 눈금 글자의 자리를 잡는다. 글자는 눈금 가운데에 놓되 [0, plotW] 안으로 밀어 넣는다.
 * 앞 글자나 마지막 글자와 gap보다 가까우면 건너뛴다. 마지막 글자는 건너뛰지 않는다.
 * 건너뛴 눈금은 결과에서 빠지므로 세로 보조선도 그리지 않는다. 가로 위치는 chartPoints와 같은 식이다.
 */
fun placeXLabels(ticks: List<XTick>, plotW: Float, pad: Float, gap: Float, measure: (String) -> Float): List<XLabel> {
    if (ticks.isEmpty()) return emptyList()
    val boxes = ticks.map { tick ->
        val x = pad + tick.position * (plotW - 2 * pad)
        val width = measure(tick.label)
        // 글자가 그림보다 넓어도 범위가 뒤집히지 않게 위쪽 한계를 0 아래로 내리지 않는다
        val left = (x - width / 2).coerceIn(0f, (plotW - width).coerceAtLeast(0f))
        XLabel(tick.label, left, x) to width
    }
    val last = boxes.last().first
    val placed = mutableListOf<XLabel>()
    var right = Float.NEGATIVE_INFINITY
    boxes.dropLast(1).forEach { (label, width) ->
        if (label.left >= right + gap && label.left + width + gap <= last.left) {
            placed += label
            right = label.left + width
        }
    }
    return placed + last
}
