package com.tmfrl.marketwidget.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.round

/** 로그 차트. values는 첫 값 대비 배수의 지수(첫 값이 0)이고, axis의 눈금도 지수다. */
data class LogChart(val values: List<Double>, val axis: YAxis)

private val FRACTION_STEPS = listOf(0.05, 0.1, 0.2, 0.5)
private const val MAX_TICKS = 5

private fun ticksIn(low: Double, high: Double, step: Double): List<Double> =
    (ceil(low / step).toInt()..floor(high / step).toInt()).map { round(it * step * 1e6) / 1e6 }

/**
 * 첫 값을 10^0으로 놓은 로그 차트. 0 이하 값이 있으면 로그를 그릴 수 없어 null.
 * 눈금은 10의 제곱수다. 값이 10배도 차이 나지 않으면 눈금이 하나뿐이라 소수 지수로 나눈다.
 */
fun logChartFor(values: List<Double>): LogChart? {
    if (values.size < 2 || values.any { it <= 0 }) return null
    val logs = values.map { log10(it / values.first()) }
    return LogChart(logs, logAxis(logs.min(), logs.max()))
}

/** 지수 범위의 세로축. 10배 이상 차이 나면 정수 지수, 아니면 0.05/0.1/0.2/0.5 중 눈금 5개 이하인 가장 작은 간격. */
internal fun logAxis(low: Double, high: Double): YAxis {
    val step = if (high - low >= 1) 1.0 else FRACTION_STEPS.first { ticksIn(low, high, it).size <= MAX_TICKS }
    return YAxis(low, high, ticksIn(low, high, step))
}

private val SUPERSCRIPTS = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻',
)

/** 지수 눈금 문구. 정수는 10², 소수는 10^0.2로 쓴다. */
fun formatPowerOfTen(exponent: Double): String {
    val whole = round(exponent)
    if (abs(exponent - whole) > 1e-6) return "10^${formatExponent(exponent)}"
    return "10" + whole.toInt().toString().map { SUPERSCRIPTS.getValue(it) }.joinToString("")
}
