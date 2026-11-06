package com.tmfrl.marketwidget.model

import kotlin.math.abs

/**
 * 큰 움직임 강조. 오늘 등락률의 절댓값이 수집기가 보낸 기준(big_move_pct) 이상이면 그 방향, 아니면 null.
 * 직전 값(stale)이거나 등락률·기준이 없으면 강조하지 않는다. 장 상태와는 상관없다.
 */
fun bigMoveTrend(quote: Quote): Trend? {
    if (quote.stale) return null
    val pct = quote.change_pct ?: return null
    val limit = quote.big_move_pct ?: return null
    if (abs(pct) < limit) return null
    return when (trendOf(pct)) {
        Trend.UP -> Trend.UP
        Trend.DOWN -> Trend.DOWN
        Trend.FLAT -> null
    }
}
