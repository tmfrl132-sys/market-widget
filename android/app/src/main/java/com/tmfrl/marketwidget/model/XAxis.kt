package com.tmfrl.marketwidget.model

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** 가로축 눈금. position은 그림 영역 안의 가로 위치(0~1)다. */
data class XTick(val position: Float, val label: String)

/** 자리를 잡은 눈금 글자. left는 글자 왼쪽 좌표다. */
data class XLabel(val text: String, val left: Float)

/**
 * 큰 차트의 가로축. ticks가 비면 눈금을 그리지 않는다.
 * positions는 점마다의 가로 위치(0~1)이고, null이면 점을 가로 전체에 고르게 놓는다.
 */
data class ChartXAxis(val ticks: List<XTick> = emptyList(), val positions: List<Float>? = null)

private const val DATE_TICKS = 5
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

/**
 * 날짜 눈금. 첫 점과 마지막 점을 포함해 고르게 5개를 놓는다. 값이 5개 미만이면 값 수만큼이다.
 * 날짜 수가 값 수와 다르거나 읽을 수 없는 날짜가 있으면 눈금을 만들지 않는다.
 */
fun dateTicks(dates: List<String>, valueCount: Int, period: Period): List<XTick> {
    if (valueCount < 2 || dates.size != valueCount) return emptyList()
    val format = dateFormatOf(period) ?: return emptyList()
    val count = minOf(DATE_TICKS, valueCount)
    val lastIndex = valueCount - 1
    return (0 until count).map { n ->
        val index = (n * lastIndex.toDouble() / (count - 1)).roundToInt()
        val date = parseDateOrNull(dates[index]) ?: return emptyList()
        XTick(index.toFloat() / lastIndex, date.format(format))
    }
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

/** 오늘 차트의 가로축. 시각이나 장 시간을 믿을 수 없으면 지금처럼 가로 전체에 늘려 그리고 눈금도 없앤다. */
fun todayXAxis(valueCount: Int, times: List<Long>, start: Long?, end: Long?): ChartXAxis {
    if (valueCount < 2 || times.size != valueCount) return ChartXAxis()
    if (start == null || end == null || end <= start) return ChartXAxis()
    return ChartXAxis(sessionTicks(start, end), times.map { sessionPosition(it, start, end) })
}

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
 * 눈금 글자의 자리를 잡는다. 첫 글자는 왼쪽, 마지막 글자는 오른쪽, 나머지는 가운데 정렬이다.
 * 앞 글자나 마지막 글자와 gap보다 가까우면 건너뛴다. 마지막 글자는 건너뛰지 않는다.
 * 가로 위치는 chartPoints와 같은 식이다.
 */
fun placeXLabels(ticks: List<XTick>, plotW: Float, pad: Float, gap: Float, measure: (String) -> Float): List<XLabel> {
    if (ticks.isEmpty()) return emptyList()
    val boxes = ticks.mapIndexed { i, tick ->
        val x = pad + tick.position * (plotW - 2 * pad)
        val width = measure(tick.label)
        val left = when (i) {
            0 -> x
            ticks.lastIndex -> x - width
            else -> x - width / 2
        }
        XLabel(tick.label, left) to width
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
