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
 * 지표가 4개 이하여도 한 줄은 4칸이다. 1개만 켰을 때 타일 하나가 위젯 폭 전체로 늘어나지 않게 남는 칸은 빈 칸으로 둔다.
 */
fun tileGrid(widthDp: Float, count: Int): TileGrid {
    val most = ((widthDp - WIDGET_PADDING_DP) / MIN_TILE_DP).toInt().coerceIn(MIN_PER_ROW, MAX_PER_ROW)
    if (count <= 0) return TileGrid(most, 0)
    val rows = (count + most - 1) / most
    return TileGrid(maxOf((count + rows - 1) / rows, MIN_PER_ROW), rows)
}

/** 작은 차트 비트맵 전체 픽셀 상한(RGB_565 약 320KB). 타일이 21개여도 큰 차트와 합쳐 전송 크기 제한을 넘지 않게 한다. */
private const val SPARK_TOTAL_PIXELS = 160_000

/** 작은 차트 한 개의 픽셀 상한. 13개면 지금 크기(한 개 약 11,400픽셀) 그대로다. */
fun sparkMaxPixels(count: Int): Int = SPARK_TOTAL_PIXELS / maxOf(count, 1)

/** 위아래 두 차트가 함께 쓸 오른쪽 숫자 칸 폭. 두 축 숫자 중 가장 넓은 글자에 여백(글자 크기 × 0.6)을 더한다. */
fun commonGutter(widths: List<Float>, textPx: Float): Float = (widths.maxOrNull() ?: 0f) + textPx * 0.6f

/** 머리줄 아래 일정 한 줄의 높이(dp). */
const val EVENT_LINE_HEIGHT_DP = 18f

/**
 * 차트·일정 칸 위쪽(테두리 여백 20, 머리줄·간격 32, 차트 위 간격 8, 타일 한 줄 85)의 높이(dp). 2줄이면 230이다.
 * 일정 줄을 보이면 그 높이만큼 더한다.
 */
fun topPartHeightDp(rows: Int, eventLine: Boolean = false): Float =
    60f + 85f * rows + if (eventLine) EVENT_LINE_HEIGHT_DP else 0f

/** 이 높이(dp) 이상이면 시세 아래에 차트·일정 칸과 작은 차트를 둔다. 2줄이면 220이다. */
fun detailMinHeightDp(rows: Int, eventLine: Boolean = false): Float = topPartHeightDp(rows, eventLine) - 10f

/** 테두리 여백 20과 머리줄·간격 32를 합한 높이(dp). */
private const val HEADER_PART_DP = 52f

/** 작은 차트 없는 타일 한 줄의 높이(dp). 마감 후 종가 줄이 들어간 타일 기준이다. */
private const val COMPACT_TILE_ROW_DP = 75f

/** 차트·일정 칸의 최소 높이(dp). */
private const val MIN_DETAIL_DP = 60f

/** 위젯 높이로 정한 배치. detailHeightDp는 showDetail일 때만 쓴다. */
data class WidgetLayout(val showEventLine: Boolean, val showDetail: Boolean, val detailHeightDp: Float)

/**
 * 일정 줄과 차트 칸을 놓을지 정한다. 우선순위는 줄과 차트 모두, 줄만, 차트만, 타일만 순서다.
 * 타일이 잘릴 만큼 낮으면 줄을 숨겨 시세를 지킨다.
 */
fun widgetLayout(heightDp: Float, rows: Int, hasEvents: Boolean): WidgetLayout {
    val lineOnlyMin = HEADER_PART_DP + EVENT_LINE_HEIGHT_DP + COMPACT_TILE_ROW_DP * rows
    val showLine = hasEvents && (heightDp >= detailMinHeightDp(rows, eventLine = true) || heightDp >= lineOnlyMin)
    val showDetail = heightDp >= detailMinHeightDp(rows, eventLine = showLine)
    return WidgetLayout(showLine, showDetail, maxOf(MIN_DETAIL_DP, heightDp - topPartHeightDp(rows, showLine)))
}

/** 일정 목록의 제목·간격을 뺀 줄 수 한도. Glance는 Column 하나에 자식을 10개까지만 그린다. */
private const val MAX_EVENT_LIST_ROWS = 8

/** 차트 자리에 보일 일정 목록 줄 수. 높이에 맞추되 Glance 한도를 넘지 않는다. */
fun eventListRows(availableDp: Float, rowDp: Float): Int =
    minOf(eventRows(availableDp, rowDp, min = 3), MAX_EVENT_LIST_ROWS)
