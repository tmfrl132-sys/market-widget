package com.tmfrl.marketwidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.tmfrl.marketwidget.model.YAxis
import com.tmfrl.marketwidget.model.chartPoints
import com.tmfrl.marketwidget.model.formatPrice
import com.tmfrl.marketwidget.model.yAxisFor
import com.tmfrl.marketwidget.model.yPosition

/**
 * 선 그래프를 비트맵으로 그린다. 위젯(RemoteViews)은 직접 그리기를 못 해서 그림으로 붙인다.
 * 위젯 전송 크기 제한(약 1MB)이 있어 배경을 칠하고 RGB_565(픽셀당 2바이트)로 만든다.
 * detailed는 큰 차트용: 오른쪽 세로축 숫자와 눈금선, 기준값 점선, 영역 채우기, 마지막 값 점.
 * 아래 인자는 detailed일 때만 쓴다.
 * baseline은 점선 값(없으면 점선 없음), baselineLabel은 점선 문구, axisLabel은 세로축 숫자 형식,
 * areaBase는 영역을 채울 기준값(없으면 차트 바닥까지), fixedAxis는 정해 둔 세로축(없으면 값으로 계산).
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

    // 점과 위아래 끝 눈금 숫자가 잘리지 않게 여백을 둔다
    val pad = if (detailed) maxOf(strokePx * 3, textPx * 0.6f) else strokePx
    val points = chartPoints(values, plotW, h.toFloat(), pad, axis)
    if (points.isEmpty()) return bitmap

    if (axis != null) drawAxis(canvas, axis, axisLabel, text, plotW, w.toFloat(), h.toFloat(), pad, strokePx)

    val line = Path().apply {
        moveTo(points[0].first, points[0].second)
        points.drop(1).forEach { (x, y) -> lineTo(x, y) }
    }
    if (detailed) {
        val floor = if (axis != null && areaBase != null) {
            yPosition(areaBase, axis.min, axis.max, h.toFloat(), pad)
        } else {
            h - pad
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
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        })
    }
    if (axis != null && baseline != null) drawBaseline(canvas, baseline, axis, text, plotW, h.toFloat(), pad, strokePx)
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
            drawBaselineLabel(canvas, baselineLabel, baseline, values.last(), axis, text, backgroundColor, plotW, h.toFloat(), pad)
        }
    }
    return bitmap
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
