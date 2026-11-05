package com.tmfrl.marketwidget.model

/** 차트 기간. 1개월·3개월은 1년 일봉을 잘라 쓰고, 5년은 주봉을 쓴다. 오늘은 5분봉, MAX는 전체 기간이다. */
enum class Period(val label: String, val agoLabel: String) {
    TODAY("오늘", "장 시작"),
    ONE_MONTH("1개월", "1개월 전"),
    THREE_MONTHS("3개월", "3개월 전"),
    ONE_YEAR("1년", "1년 전"),
    FIVE_YEARS("5년", "5년 전"),
    MAX("MAX", "처음");

    /** 값 범위가 넓어 로그 눈금이 쓸모 있는 기간. */
    val supportsLog: Boolean get() = this == FIVE_YEARS || this == MAX
}

private const val ONE_MONTH_DAYS = 22
private const val THREE_MONTH_DAYS = 63

/** 값과 날짜를 같은 범위로 자르려고 형식을 묶지 않는다. */
fun <T> periodValues(daily: List<T>, weekly: List<T>, period: Period): List<T> = when (period) {
    Period.ONE_MONTH -> daily.takeLast(ONE_MONTH_DAYS)
    Period.THREE_MONTHS -> daily.takeLast(THREE_MONTH_DAYS)
    Period.ONE_YEAR -> daily
    Period.FIVE_YEARS -> weekly
    Period.TODAY, Period.MAX -> emptyList()
}

fun parsePeriod(name: String?): Period =
    Period.entries.firstOrNull { it.name == name } ?: Period.THREE_MONTHS

/** 기기에 옛 형식(daily 없음) data.json만 있으면 3개월 chart로 대신한다. */
fun chartValues(quote: Quote, period: Period): List<Double> = when (period) {
    Period.TODAY -> quote.intraday
    Period.MAX -> quote.max_closes
    else -> if (quote.daily.isEmpty()) quote.chart else periodValues(quote.daily, quote.weekly, period)
}

/**
 * chartValues와 같은 범위의 날짜. 날짜를 믿을 수 없으면 빈 목록이다.
 * 길이는 자르기 전에 비교한다. 자른 뒤에는 길이가 같아도 날짜가 어긋나 있을 수 있다.
 */
fun chartDates(quote: Quote, period: Period): List<String> = when (period) {
    Period.TODAY -> emptyList()
    Period.MAX -> if (quote.max_dates.size == quote.max_closes.size) quote.max_dates else emptyList()
    Period.FIVE_YEARS -> when {
        quote.daily.isEmpty() || quote.weekly_dates.size != quote.weekly.size -> emptyList()
        else -> periodValues(quote.daily_dates, quote.weekly_dates, period)
    }
    Period.ONE_MONTH, Period.THREE_MONTHS, Period.ONE_YEAR -> when {
        quote.daily.isEmpty() || quote.daily_dates.size != quote.daily.size -> emptyList()
        else -> periodValues(quote.daily_dates, quote.weekly_dates, period)
    }
}
