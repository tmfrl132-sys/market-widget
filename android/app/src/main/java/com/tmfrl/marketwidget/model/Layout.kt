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

/** 시세 타일 배치. perRow는 한 줄 칸 수(마지막 줄도 같은 칸 수로 맞춘다), rows는 줄 수다. */
data class TileGrid(val perRow: Int, val rows: Int)

/** 타일 한 칸의 최소 폭. 바깥 여백·테두리 자리·안쪽 여백을 빼면 글자 영역이 80dp다. */
private const val MIN_TILE_DP = 96f

/** 위젯 테두리 안쪽 여백(좌우 10dp씩). */
private const val WIDGET_PADDING_DP = 20f
private const val MIN_PER_ROW = 4
private const val MAX_PER_ROW = 7

/**
 * 위젯 폭으로 한 줄 칸 수(4~7)를 정하고, 줄마다 같은 수가 되게 나눈다.
 * 예: 13개를 폭 712dp에 놓으면 7·6(2줄), 691dp면 5·5·3(3줄).
 */
fun tileGrid(widthDp: Float, count: Int): TileGrid {
    val most = ((widthDp - WIDGET_PADDING_DP) / MIN_TILE_DP).toInt().coerceIn(MIN_PER_ROW, MAX_PER_ROW)
    if (count <= 0) return TileGrid(most, 0)
    val rows = (count + most - 1) / most
    return TileGrid((count + rows - 1) / rows, rows)
}

/** 차트·일정 칸 위쪽(테두리 여백 20, 머리줄·간격 32, 차트 위 간격 8, 타일 한 줄 85)의 높이(dp). 2줄이면 230이다. */
fun topPartHeightDp(rows: Int): Float = 60f + 85f * rows

/** 이 높이(dp) 이상이면 시세 아래에 차트·일정 칸과 작은 차트를 둔다. 2줄이면 220이다. */
fun detailMinHeightDp(rows: Int): Float = topPartHeightDp(rows) - 10f
