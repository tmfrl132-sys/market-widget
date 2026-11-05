package com.tmfrl.marketwidget.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** 차트 위 글자 칸. 왼쪽, 위, 너비. */
data class LabelBox(val left: Float, val top: Float, val width: Float)

/** 글자 칸이 겹치면 앞 칸들을 피해 위로 올린다. 돌려주는 값은 칸별 위쪽 좌표이고 0 위로는 올리지 않는다. */
fun stackLabels(boxes: List<LabelBox>, height: Float): List<Float> {
    val placed = mutableListOf<LabelBox>()
    return boxes.map { box ->
        var top = box.top
        while (top > 0 && placed.any { box.left < it.left + it.width && it.left < box.left + box.width && abs(it.top - top) < height }) {
            top -= height
        }
        val settled = top.coerceAtLeast(0f)
        placed += box.copy(top = settled)
        settled
    }
}

/** 세로축 범위와 눈금. 눈금이 범위 양 끝에 놓인다. */
data class YAxis(val min: Double, val max: Double, val ticks: List<Double>)

/** 값 범위를 덮는 딱 떨어지는 눈금 3~5개를 고른다. 간격은 1, 2, 2.5, 5, 10의 10배수다. */
fun yAxisFor(values: List<Double>): YAxis? {
    if (values.isEmpty()) return null
    var low = values.min()
    var high = values.max()
    if (low == high) {
        val margin = if (low == 0.0) 1.0 else abs(low) * 0.01
        low -= margin
        high += margin
    }
    val rough = (high - low) / 3
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = magnitude * when {
        rough / magnitude <= 1 -> 1.0
        rough / magnitude <= 2 -> 2.0
        rough / magnitude <= 2.5 -> 2.5
        rough / magnitude <= 5 -> 5.0
        else -> 10.0
    }
    val first = floor(low / step).toInt()
    val last = ceil(high / step).toInt()
    // 곱셈 오차로 4.250000001 같은 값이 나오지 않게 다듬는다
    val ticks = (first..last).map { round(it * step * 1e6) / 1e6 }
    return YAxis(ticks.first(), ticks.last(), ticks)
}

/** 값의 세로 좌표. 위가 큰 값이다. */
fun yPosition(value: Double, min: Double, max: Double, height: Float, pad: Float): Float {
    val span = max - min
    return if (span == 0.0) height / 2 else (pad + (max - value) / span * (height - 2 * pad)).toFloat()
}

/**
 * 값 목록을 width×height 영역의 좌표로 바꾼다. 위가 큰 값이다. 값이 2개 미만이면 빈 목록.
 * axis가 있으면 그 범위로, 없으면 값의 최저~최고로 맞춘다.
 * xPositions(0~1)의 개수가 값과 같으면 그 위치에 놓고, 아니면 가로 전체에 고르게 놓는다.
 */
fun chartPoints(
    values: List<Double>, width: Float, height: Float, pad: Float, axis: YAxis? = null,
    xPositions: List<Float>? = null,
): List<Pair<Float, Float>> {
    if (values.size < 2) return emptyList()
    val min = axis?.min ?: values.min()
    val max = axis?.max ?: values.max()
    val stepX = (width - 2 * pad) / (values.size - 1)
    val placed = xPositions?.takeIf { it.size == values.size }
    return values.mapIndexed { i, v ->
        val x = if (placed != null) pad + placed[i] * (width - 2 * pad) else pad + i * stepX
        x to yPosition(v, min, max, height, pad)
    }
}

data class ChartSummary(val min: Double, val max: Double, val change: Double, val changePct: Double)

/** 차트 기간의 최저·최고와 처음 대비 마지막 차이·등락률. */
fun chartSummary(values: List<Double>): ChartSummary? {
    if (values.size < 2) return null
    val change = values.last() - values.first()
    return ChartSummary(values.min(), values.max(), change, change / values.first() * 100)
}

/** 같은 종목을 다시 누르면 선택을 푼다. */
fun toggleSelection(current: String?, tapped: String): String? = if (current == tapped) null else tapped

/** 등락 배지 문구. 부호 대신 ▲▼를 쓴다. */
fun formatChangeBadge(pct: Double?): String = when (trendOf(pct)) {
    Trend.UP -> "▲" + formatChangePct(pct).removePrefix("+")
    Trend.DOWN -> "▼" + formatChangePct(abs(pct!!)).removePrefix("+")
    Trend.FLAT -> if (pct == null) "-" else formatChangePct(0.0)
}

fun formatDday(date: LocalDate, today: LocalDate): String =
    when (val days = ChronoUnit.DAYS.between(today, date)) {
        0L -> "오늘"
        1L -> "내일"
        else -> "D-$days"
    }

fun flagOf(country: String): String = when (country) {
    "US" -> "🇺🇸"
    "KR" -> "🇰🇷"
    else -> ""
}
