package com.tmfrl.marketwidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.tmfrl.marketwidget.model.chartPoints

/**
 * 선 그래프를 비트맵으로 그린다. 위젯(RemoteViews)은 직접 그리기를 못 해서 그림으로 붙인다.
 * 위젯 전송 크기 제한(약 1MB)이 있어 배경을 칠하고 RGB_565(픽셀당 2바이트)로 만든다.
 * detailed는 큰 차트용: 가로 눈금선, 영역 채우기, 마지막 값 점.
 */
fun renderLine(
    values: List<Double>,
    lineColor: Int,
    backgroundColor: Int,
    widthPx: Int,
    heightPx: Int,
    strokePx: Float,
    detailed: Boolean,
): Bitmap {
    val w = widthPx.coerceAtLeast(2)
    val h = heightPx.coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)

    // 점이 잘리지 않게 여백을 둔다
    val pad = if (detailed) strokePx * 3 else strokePx
    val points = chartPoints(values, w.toFloat(), h.toFloat(), pad)
    if (points.isEmpty()) return bitmap

    if (detailed) {
        val grid = Paint().apply {
            color = 0x1FFFFFFF
            strokeWidth = (strokePx / 2).coerceAtLeast(1f)
        }
        for (i in 0..3) {
            val y = pad + (h - 2 * pad) * i / 3f
            canvas.drawLine(0f, y, w.toFloat(), y, grid)
        }
    }

    val line = Path().apply {
        moveTo(points[0].first, points[0].second)
        points.drop(1).forEach { (x, y) -> lineTo(x, y) }
    }
    if (detailed) {
        val area = Path(line).apply {
            lineTo(points.last().first, h.toFloat())
            lineTo(points.first().first, h.toFloat())
            close()
        }
        val top = (0x66 shl 24) or (lineColor and 0x00FFFFFF)
        val bottom = lineColor and 0x00FFFFFF
        canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        })
    }
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
    }
    return bitmap
}
