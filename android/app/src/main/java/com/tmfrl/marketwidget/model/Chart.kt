package com.tmfrl.marketwidget.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** 값 목록을 width×height 영역의 좌표로 바꾼다. 위가 큰 값이다. 값이 2개 미만이면 빈 목록. */
fun chartPoints(values: List<Double>, width: Float, height: Float, pad: Float): List<Pair<Float, Float>> {
    if (values.size < 2) return emptyList()
    val min = values.min()
    val max = values.max()
    val span = max - min
    val stepX = (width - 2 * pad) / (values.size - 1)
    return values.mapIndexed { i, v ->
        val y = if (span == 0.0) height / 2 else (pad + (max - v) / span * (height - 2 * pad)).toFloat()
        (pad + i * stepX) to y
    }
}

data class ChartSummary(val min: Double, val max: Double, val changePct: Double)

/** 차트 기간의 최저·최고와 처음 대비 마지막 등락률. */
fun chartSummary(values: List<Double>): ChartSummary? {
    if (values.size < 2) return null
    return ChartSummary(values.min(), values.max(), (values.last() - values.first()) / values.first() * 100)
}

/** 같은 종목을 다시 누르면 선택을 푼다. */
fun toggleSelection(current: String?, tapped: String): String? = if (current == tapped) null else tapped

/** 등락 배지 문구. 부호 대신 ▲▼를 쓴다. */
fun formatChangeBadge(pct: Double?): String = when (trendOf(pct)) {
    Trend.UP -> "▲" + formatChangePct(pct).removePrefix("+")
    Trend.DOWN -> "▼" + formatChangePct(abs(pct!!))
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
