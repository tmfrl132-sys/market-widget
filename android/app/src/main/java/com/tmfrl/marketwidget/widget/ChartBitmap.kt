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
 */
fun renderLine(
    values: List<Double>,
    lineColor: Int,
    backgroundColor: Int,
    widthPx: Int,
    heightPx: Int,
    strokePx: Float,
    fill: Boolean,
): Bitmap {
    val w = widthPx.coerceAtLeast(2)
    val h = heightPx.coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)

    val points = chartPoints(values, w.toFloat(), h.toFloat(), strokePx)
    if (points.isEmpty()) return bitmap

    val line = Path().apply {
        moveTo(points[0].first, points[0].second)
        points.drop(1).forEach { (x, y) -> lineTo(x, y) }
    }
    if (fill) {
        val area = Path(line).apply {
            lineTo(points.last().first, h.toFloat())
            lineTo(points.first().first, h.toFloat())
            close()
        }
        val top = (0x55 shl 24) or (lineColor and 0x00FFFFFF)
        val bottom = lineColor and 0x00FFFFFF
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(area, paint)
    }
    canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = lineColor
        strokeWidth = strokePx
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    })
    return bitmap
}
