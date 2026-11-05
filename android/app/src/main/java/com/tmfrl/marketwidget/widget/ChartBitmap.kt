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
import com.tmfrl.marketwidget.model.placeXLabels
import com.tmfrl.marketwidget.model.stackLabels
import com.tmfrl.marketwidget.model.yAxisFor
import com.tmfrl.marketwidget.model.yPosition

/**
 * 선 그래프를 비트맵으로 그린다. 위젯(RemoteViews)은 직접 그리기를 못 해서 그림으로 붙인다.
 * 위젯 전송 크기 제한(약 1MB)이 있어 배경을 칠하고 RGB_565(픽셀당 2바이트)로 만든다.
 * detailed는 큰 차트용: 오른쪽 세로축 숫자와 눈금선, 기준값 점선, 영역 채우기, 마지막 값 점.
 * 아래 인자는 detailed일 때만 쓴다.
 * baseline은 점선 값(없으면 점선 없음), baselineLabel은 점선 문구, axisLabel은 세로축 숫자 형식,
 * areaBase는 영역을 채울 기준값(없으면 차트 바닥까지), fixedAxis는 정해 둔 세로축(없으면 값으로 계산),
 * marks는 점에 붙일 문구(값 목록 위치와 문구),
 * xTicks는 가로축 눈금(있으면 아래쪽에 글자 띠를 잡는다), xPositions는 점마다의 가로 위치 0~1(없으면 고르게 놓는다).
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
): Bitmap {
    val w = widthPx.coerceAtLeast(2)
    val h = heightPx.coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)

    // 점선과 영역 기준값도 눈금 범위 안에 들어와야 한다
    val axis = if (detailed) fixedAxis ?: yAxisFor(values + listOfNotNull(baseline, areaBase)) else null
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = textPx
    }
    // 세로축 숫자가 들어갈 오른쪽 칸. 선이 숫자와 겹치지 않게 그림 영역과 나눈다.
    val gutter = axis?.ticks?.maxOf { text.measureText(axisLabel(it)) }?.plus(textPx * 0.6f) ?: 0f
    val plotW = (w - gutter).coerceAtLeast(2f)
    // 가로축 글자가 들어갈 아래쪽 띠. 세로 계산은 띠를 뺀 높이 안에서 한다. 작은 차트는 눈금과 가로 위치를 무시한다.
    val ticks = if (detailed) xTicks else emptyList()
    val band = if (ticks.isEmpty()) 0f else textPx * X_BAND_LINES
    val plotH = (h - band).coerceAtLeast(2f)

    // 점과 위아래 끝 눈금 숫자가 잘리지 않게 여백을 둔다
    val pad = if (detailed) maxOf(strokePx * 3, textPx * 0.6f) else strokePx
    val points = chartPoints(values, plotW, plotH, pad, axis, if (detailed) xPositions else null)
    if (points.isEmpty()) return bitmap

    if (axis != null) drawAxis(canvas, axis, axisLabel, text, plotW, w.toFloat(), plotH, pad, strokePx)

    val line = Path().apply {
        moveTo(points[0].first, points[0].second)
        points.drop(1).forEach { (x, y) -> lineTo(x, y) }
    }
    if (detailed) {
        val floor = if (axis != null && areaBase != null) {
            yPosition(areaBase, axis.min, axis.max, plotH, pad)
        } else {
            plotH - pad
        }
        val area = Path(line).apply {
            lineTo(points.last().first, floor)
            lineTo(points.first().first, floor)
            close()
        }
        val top = (0x66 shl 24) or (lineColor and 0x00FFFFFF)
        val bottom = lineColor and 0x00FFFFFF
        canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, 0f, 0f, plotH, top, bottom, Shader.TileMode.CLAMP)
        })
    }
    if (axis != null && baseline != null) drawBaseline(canvas, baseline, axis, text, plotW, plotH, pad, strokePx)
    canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = lineColor
        strokeWidth = strokePx
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    })
    if (detailed) {
        val (x, y) = points.last()
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
        canvas.drawCircle(x, y, strokePx * 2.2f, dot)
        canvas.drawCircle(x, y, strokePx * 1.1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor })
        if (axis != null && baseline != null) {
            drawBaselineLabel(canvas, baselineLabel, baseline, values.last(), axis, text, backgroundColor, plotW, plotH, pad)
        }
        drawMarks(canvas, marks.filter { (i, _) -> i in points.indices }, points, text, backgroundColor, plotW, strokePx)
        val baselineY = h - textPx * X_BAND_BOTTOM_LINES
        placeXLabels(ticks, plotW, pad, textPx * X_LABEL_GAP_LINES) { text.measureText(it) }.forEach { label ->
            canvas.drawText(label.text, label.left, baselineY, text)
        }
    }
    return bitmap
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

/** 눈금선과 오른쪽 숫자. */
private fun drawAxis(
    canvas: Canvas, axis: YAxis, axisLabel: (Double) -> String, text: Paint,
    plotW: Float, w: Float, h: Float, pad: Float, strokePx: Float,
) {
    val grid = Paint().apply {
        color = 0x1FFFFFFF
        strokeWidth = (strokePx / 2).coerceAtLeast(1f)
    }
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
