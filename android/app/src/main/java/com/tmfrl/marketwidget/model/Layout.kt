package com.tmfrl.marketwidget.model

import kotlin.math.sqrt

/** 남은 높이에 들어가는 일정 줄 수. 아무리 작아도 min줄은 보여준다. */
fun eventRows(availableDp: Float, rowDp: Float, min: Int): Int =
    maxOf(min, (availableDp / rowDp).toInt())

/**
 * 위젯 전송 크기 제한 때문에 비트맵 픽셀 수를 maxPixels 이하로 맞춘다.
 * 가로세로를 같은 비율로 줄여서 위젯이 늘려 그려도 선 굵기가 고르다.
 */
fun fitBitmap(widthPx: Int, heightPx: Int, maxPixels: Int): Pair<Int, Int> {
    val pixels = widthPx.toLong() * heightPx
    if (pixels <= maxPixels) return widthPx to heightPx
    val scale = sqrt(maxPixels.toDouble() / pixels)
    return (widthPx * scale).toInt() to (heightPx * scale).toInt()
}
