package com.tmfrl.marketwidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.tmfrl.marketwidget.model.LabelBox
import com.tmfrl.marketwidget.model.XTick
import com.tmfrl.marketwidget.model.YAxis
import com.tmfrl.marketwidget.model.chartPoints
import com.tmfrl.marketwidget.model.formatPrice
import com.tmfrl.marketwidget.model.lineSegments
import com.tmfrl.marketwidget.model.placeXLabels
import com.tmfrl.marketwidget.model.stackLabels
import com.tmfrl.marketwidget.model.yAxisFor
import com.tmfrl.marketwidget.model.yPosition

/**
 * 선 그래프를 비트맵으로 그린다. 위젯(RemoteViews)은 직접 그리기를 못 해서 그림으로 붙인다.
 * 위젯 전송 크기 제한(약 1MB)이 있어 배경을 칠하고 RGB_565(픽셀당 2바이트)로 만든다.
 * detailed는 큰 차트용: 오른쪽 세로축 숫자와 눈금선, 기준값 점선, 영역 채우기, 마지막 값 점.
 * 아래 인자는 xPositions만 빼고 detailed일 때만 쓴다. xPositions는 작은 차트에서도 점의 가로 위치로 쓴다.
 * baseline은 점선 값(없으면 점선 없음), baselineLabel은 점선 문구, axisLabel은 세로축 숫자 형식,
 * areaBase는 영역을 채울 기준값(없으면 차트 바닥까지), fixedAxis는 정해 둔 세로축(없으면 값으로 계산),
 * marks는 점에 붙일 문구(값 목록 위치와 문구),
 * xTicks는 가로축 눈금(세로 보조선을 그리고 아래쪽에 글자 띠를 잡는다), xPositions는 점마다의 가로 위치 0~1(없으면 고르게 놓는다),
 * showXLabels가 false면 세로 보조선만 그리고 글자 띠를 잡지 않는다.
 * secondValues는 겹쳐 그릴 두 번째 선(detailed일 때만, 두 지표 비교)이고 secondColor는 그 색이다.
 * 두 번째 선이 있으면 영역 채우기를 끄고, 세로축은 두 선과 기준값을 함께 넣어 정한다. 두 번째 선을 먼저 그려 주 선이 위에 온다.
 * gutterPx는 오른쪽 숫자 칸 폭(없으면 숫자 길이로 정한다). 위아래 두 차트가 같은 폭을 쓰면 같은 인덱스가 같은 가로 위치에 놓인다.
 * areaGradient가 false면 영역을 위아래 그라데이션 대신 알파 0x55로 고르게 칠한다(잠수 차트).
 * shade는 그림 영역 전체 높이로 옅게 칠할 점 범위(최대 낙폭 구간)이고 shadeColor로 칠한다. 영역 채우기보다 먼저 그린다.
 * dots는 따로 찍을 점(위치와 색). 반지름은 선 굵기의 2배이고 배경색 테두리를 두른다.
 * baselineLabel이 빈 문자열이면 점선 문구와 그 배경 칸을 그리지 않는다.
 * breaks는 선과 영역을 끊을 점 위치(그 점 앞에서 끊는다, 합친 24시간 차트의 정비 시간·주말)다. 비면 지금처럼 한 선이다.
 * bands는 그림 영역 높이로 옅게 칠할 가로 구간(0~1, 정규장)이고 bandColor로 칠한다. detailed일 때만 쓰고 가장 먼저 그린다.
 */
fun renderLine(
    values: List<Double>,
    lineColor: Int,
    backgroundColor: Int,
    widthPx: Int,
    heightPx: Int,
    strokePx: Float,
    detailed: Boolean,
    textPx: Float = 0f,
    textColor: Int = 0,
    baseline: Double? = null,
    baselineLabel: String = "",
    axisLabel: (Double) -> String = ::formatPrice,
    areaBase: Double? = null,
    fixedAxis: YAxis? = null,
    marks: List<Pair<Int, String>> = emptyList(),
    xTicks: List<XTick> = emptyList(),
    xPositions: List<Float>? = null,
    showXLabels: Boolean = true,
    secondValues: List<Double>? = null,
    secondColor: Int = 0,
    gutterPx: Float? = null,
    areaGradient: Boolean = true,
    shade: IntRange? = null,
    shadeColor: Int = 0,
    dots: List<Pair<Int, Int>> = emptyList(),
    breaks: List<Int> = emptyList(),
    bands: List<ClosedFloatingPointRange<Float>> = emptyList(),
    bandColor: Int = 0,
): Bitmap {
    val w = widthPx.coerceAtLeast(2)
    val h = heightPx.coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)

    // 점선과 영역 기준값도 눈금 범위 안에 들어와야 한다
    val second = if (detailed) secondValues else null
    val axis = if (detailed) fixedAxis ?: yAxisFor(values + second.orEmpty() + listOfNotNull(baseline, areaBase)) else null
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = textPx
    }
    // 세로축 숫자가 들어갈 오른쪽 칸. 선이 숫자와 겹치지 않게 그림 영역과 나눈다.
    val gutter = if (axis == null) 0f else gutterPx ?: axisLabelWidth(axis, axisLabel, textPx).plus(textPx * 0.6f)
    val plotW = (w - gutter).coerceAtLeast(2f)
    // 가로축 글자가 들어갈 아래쪽 띠. 세로 계산은 띠를 뺀 높이 안에서 한다. 작은 차트는 눈금을 무시해 띠와 세로선이 없다.
    val ticks = if (detailed) xTicks else emptyList()
    val band = if (ticks.isEmpty() || !showXLabels) 0f else textPx * X_BAND_LINES
    val plotH = (h - band).coerceAtLeast(2f)

    // 점과 위아래 끝 눈금 숫자가 잘리지 않게 여백을 둔다
    val pad = if (detailed) maxOf(strokePx * 3, textPx * 0.6f) else strokePx
    // 가로 위치는 작은 차트도 따른다. 타일도 장 시작~끝 중 지금까지만 그린다.
    val points = chartPoints(values, plotW, plotH, pad, axis, xPositions)
    if (points.isEmpty()) return bitmap

    // 정규장 띠도 구간 칠처럼 가장 아래에 깐다. 가로 위치는 chartPoints와 같은 식이다.
    if (detailed && bands.isNotEmpty()) {
        val bandPaint = Paint().apply { color = bandColor }
        bands.forEach { band ->
            val left = pad + band.start * (plotW - 2 * pad)
            val right = pad + band.endInclusive * (plotW - 2 * pad)
            if (right > left) canvas.drawRect(left, 0f, right, plotH, bandPaint)
        }
    }
    // 구간 칠은 눈금선·영역·선 아래에 깔리게 가장 먼저 그린다
    if (detailed && shade != null && shade.first in points.indices && shade.last in points.indices) {
        canvas.drawRect(points[shade.first].first, 0f, points[shade.last].first, plotH, Paint().apply { color = shadeColor })
    }
    if (axis != null) drawAxis(canvas, axis, axisLabel, text, plotW, w.toFloat(), plotH, pad, strokePx)

    // 세로 보조선은 차트 아래에 깔리게 먼저 그리고, 글자 띠로 내려가지 않게 plotH까지만 그린다
    val xLabels = placeXLabels(ticks, plotW, pad, textPx * X_LABEL_GAP_LINES) { text.measureText(it) }
    if (xLabels.isNotEmpty()) {
        val grid = gridPaint(strokePx)
        xLabels.forEach { canvas.drawLine(it.x, 0f, it.x, plotH, grid) }
    }

    val segments = lineSegments(points.size, breaks)
    val line = linePath(points, segments)
    val secondPoints = second?.let { chartPoints(it, plotW, plotH, pad, axis, xPositions) }.orEmpty()
    if (detailed && second == null) {
        val floor = if (axis != null && areaBase != null) {
            yPosition(areaBase, axis.min, axis.max, plotH, pad)
        } else {
            plotH - pad
        }
        // 끊긴 구간마다 따로 닫는다. 구간이 하나면 지금까지와 같은 영역이다.
        val area = Path().apply {
            segments.filterNot { it.isEmpty() }.forEach { range ->
                moveTo(points[range.first].first, points[range.first].second)
                for (i in range.first + 1..range.last) lineTo(points[i].first, points[i].second)
                lineTo(points[range.last].first, floor)
                lineTo(points[range.first].first, floor)
                close()
            }
        }
        val top = (0x66 shl 24) or (lineColor and 0x00FFFFFF)
        val bottom = lineColor and 0x00FFFFFF
        canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            if (areaGradient) {
                shader = LinearGradient(0f, 0f, 0f, plotH, top, bottom, Shader.TileMode.CLAMP)
            } else {
                color = (0x55 shl 24) or (lineColor and 0x00FFFFFF)
            }
        })
    }
    if (axis != null && baseline != null) drawBaseline(canvas, baseline, axis, text, plotW, plotH, pad, strokePx)
    if (secondPoints.isNotEmpty()) canvas.drawPath(linePath(secondPoints), strokePaint(secondColor, strokePx))
    canvas.drawPath(line, strokePaint(lineColor, strokePx))
    if (detailed) {
        dots.filter { (i, _) -> i in points.indices }.forEach { (i, color) -> drawDot(canvas, points[i], color, backgroundColor, strokePx) }
        if (secondPoints.isNotEmpty()) drawEndDot(canvas, secondPoints.last(), secondColor, backgroundColor, strokePx)
        drawEndDot(canvas, points.last(), lineColor, backgroundColor, strokePx)
        if (axis != null && baseline != null && baselineLabel.isNotEmpty()) {
            drawBaselineLabel(canvas, baselineLabel, baseline, values.last(), axis, text, backgroundColor, plotW, plotH, pad)
        }
        drawMarks(canvas, marks.filter { (i, _) -> i in points.indices }, points, text, backgroundColor, plotW, strokePx)
        if (showXLabels) {
            val baselineY = h - textPx * X_BAND_BOTTOM_LINES
            xLabels.forEach { label -> canvas.drawText(label.text, label.left, baselineY, text) }
        }
    }
    return bitmap
}

/** 구간마다 moveTo로 새로 시작한다. segments를 주지 않으면 모든 점을 한 선으로 잇는다. */
private fun linePath(
    points: List<Pair<Float, Float>>,
    segments: List<IntRange> = listOf(points.indices),
) = Path().apply {
    segments.filterNot { it.isEmpty() }.forEach { range ->
        moveTo(points[range.first].first, points[range.first].second)
        for (i in range.first + 1..range.last) lineTo(points[i].first, points[i].second)
    }
}

private fun strokePaint(lineColor: Int, strokePx: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    color = lineColor
    strokeWidth = strokePx
    strokeJoin = Paint.Join.ROUND
    strokeCap = Paint.Cap.ROUND
}

/** 마지막 값 점. 선 색 원 안에 배경색 원을 겹친다. */
private fun drawEndDot(canvas: Canvas, point: Pair<Float, Float>, lineColor: Int, backgroundColor: Int, strokePx: Float) {
    val (x, y) = point
    canvas.drawCircle(x, y, strokePx * 2.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor })
    canvas.drawCircle(x, y, strokePx * 1.1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor })
}

/** 고점·바닥 점. 배경색 테두리 안에 색 원을 그린다. */
private fun drawDot(canvas: Canvas, point: Pair<Float, Float>, color: Int, backgroundColor: Int, strokePx: Float) {
    val (x, y) = point
    canvas.drawCircle(x, y, strokePx * 2.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = backgroundColor })
    canvas.drawCircle(x, y, strokePx * 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
}

/** 세로축 숫자 중 가장 넓은 글자 폭(px). renderLine과 같은 글자 크기로 잰다. 위아래 두 차트의 숫자 칸 폭을 맞출 때 쓴다. */
fun axisLabelWidth(axis: YAxis, axisLabel: (Double) -> String, textPx: Float): Float {
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = textPx }
    return axis.ticks.maxOfOrNull { text.measureText(axisLabel(it)) } ?: 0f
}

/** 가로축 글자 띠의 높이, 글자 아래 여백, 글자 사이 최소 간격. 글자 크기의 배수다. */
private const val X_BAND_LINES = 1.4f
private const val X_BAND_BOTTOM_LINES = 0.25f
private const val X_LABEL_GAP_LINES = 0.4f

/** 사건 표시. 점을 찍고 그 위에 문구를 둔다. 문구끼리 겹치면 위로 올린다. */
private fun drawMarks(
    canvas: Canvas, marks: List<Pair<Int, String>>, points: List<Pair<Float, Float>>, text: Paint,
    backgroundColor: Int, plotW: Float, strokePx: Float,
) {
    val size = text.textSize
    val rowHeight = size * 1.3f
    val boxes = marks.map { (i, label) ->
        val (x, y) = points[i]
        val width = text.measureText(label)
        LabelBox((x - width / 2).coerceIn(0f, (plotW - width).coerceAtLeast(0f)), (y - rowHeight - strokePx * 2).coerceAtLeast(0f), width)
    }
    val tops = stackLabels(boxes, rowHeight)
    val background = Paint().apply { color = backgroundColor }
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = text.color }
    marks.forEachIndexed { n, (i, label) ->
        val (x, y) = points[i]
        val box = boxes[n]
        canvas.drawCircle(x, y, strokePx * 1.4f, dot)
        canvas.drawRect(box.left - size * 0.2f, tops[n], box.left + box.width + size * 0.2f, tops[n] + rowHeight, background)
        canvas.drawText(label, box.left, tops[n] + size, text)
    }
}

/** 가로 눈금선과 세로 보조선의 색과 굵기. */
private fun gridPaint(strokePx: Float) = Paint().apply {
    color = 0x1FFFFFFF
    strokeWidth = (strokePx / 2).coerceAtLeast(1f)
}

/** 눈금선과 오른쪽 숫자. */
private fun drawAxis(
    canvas: Canvas, axis: YAxis, axisLabel: (Double) -> String, text: Paint,
    plotW: Float, w: Float, h: Float, pad: Float, strokePx: Float,
) {
    val grid = gridPaint(strokePx)
    val label = Paint(text).apply { textAlign = Paint.Align.RIGHT }
    axis.ticks.forEach { tick ->
        val y = yPosition(tick, axis.min, axis.max, h, pad)
        canvas.drawLine(0f, y, plotW, y, grid)
        canvas.drawText(axisLabel(tick), w, y + text.textSize * 0.35f, label)
    }
}

/** 기준값 점선. 현재 위치가 이 선에서 떨어진 만큼이 등락이다. */
private fun drawBaseline(
    canvas: Canvas, baseline: Double, axis: YAxis, text: Paint,
    plotW: Float, h: Float, pad: Float, strokePx: Float,
) {
    val y = yPosition(baseline, axis.min, axis.max, h, pad)
    val dash = strokePx * 2
    canvas.drawLine(0f, y, plotW, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = text.color
        strokeWidth = (strokePx / 2).coerceAtLeast(1f)
        pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
    })
}

/** 점선 숫자. 마지막 값 점과 반대쪽에 두고, 선이 지나가도 읽히게 배경을 깐다. */
private fun drawBaselineLabel(
    canvas: Canvas, label: String, baseline: Double, last: Double, axis: YAxis, text: Paint, backgroundColor: Int,
    plotW: Float, h: Float, pad: Float,
) {
    val y = yPosition(baseline, axis.min, axis.max, h, pad)
    val size = text.textSize
    val textY = (if (last <= baseline) y - size * 0.4f else y + size * 1.1f).coerceIn(size, h - size * 0.3f)
    val right = plotW - pad
    val left = right - text.measureText(label)
    canvas.drawRect(
        left - size * 0.2f, textY - size, right + size * 0.2f, textY + size * 0.25f,
        Paint().apply { color = backgroundColor },
    )
    canvas.drawText(label, left, textY, text)
}
