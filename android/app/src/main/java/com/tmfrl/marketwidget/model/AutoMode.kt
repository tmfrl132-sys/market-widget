package com.tmfrl.marketwidget.model

/** 자동모드에서 돌아가며 보여줄 종목. 이 기간의 차트가 있는 종목만 고르고, 하나도 없으면 전부 쓴다. */
fun autoQuoteIds(quotes: List<Quote>, period: Period): List<String> {
    val withChart = quotes.filter { chartValues(it, period).size >= 2 }
    return withChart.ifEmpty { quotes }.map { it.id }
}

/** 다음 종목. 마지막 다음은 처음이고, 현재 종목이 목록에 없으면 처음부터 시작한다. */
fun nextAutoQuote(current: String?, ids: List<String>): String? {
    if (ids.isEmpty()) return null
    return ids[(ids.indexOf(current) + 1) % ids.size]
}

/** 타일을 눌렀을 때의 선택. 자동모드에서는 닫지 않고 그 종목으로 건너뛴다. */
fun selectionAfterTap(current: String?, tapped: String, auto: Boolean): String? =
    if (auto) tapped else toggleSelection(current, tapped)
