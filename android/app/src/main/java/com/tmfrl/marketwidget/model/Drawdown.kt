package com.tmfrl.marketwidget.model

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
