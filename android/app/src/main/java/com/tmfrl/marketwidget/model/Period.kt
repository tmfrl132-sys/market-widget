package com.tmfrl.marketwidget.model

/** 차트 기간. 1개월·3개월은 1년 일봉을 잘라 쓰고, 5년은 주봉을 쓴다. */
enum class Period(val label: String, val agoLabel: String) {
    ONE_MONTH("1개월", "1개월 전"),
    THREE_MONTHS("3개월", "3개월 전"),
    ONE_YEAR("1년", "1년 전"),
    FIVE_YEARS("5년", "5년 전"),
}

private const val ONE_MONTH_DAYS = 22
private const val THREE_MONTH_DAYS = 63

fun periodValues(daily: List<Double>, weekly: List<Double>, period: Period): List<Double> = when (period) {
    Period.ONE_MONTH -> daily.takeLast(ONE_MONTH_DAYS)
    Period.THREE_MONTHS -> daily.takeLast(THREE_MONTH_DAYS)
    Period.ONE_YEAR -> daily
    Period.FIVE_YEARS -> weekly
}

fun parsePeriod(name: String?): Period =
    Period.entries.firstOrNull { it.name == name } ?: Period.THREE_MONTHS

/** 기기에 옛 형식(daily 없음) data.json만 있으면 3개월 chart로 대신한다. */
fun chartValues(quote: Quote, period: Period): List<Double> =
    if (quote.daily.isEmpty()) quote.chart else periodValues(quote.daily, quote.weekly, period)
