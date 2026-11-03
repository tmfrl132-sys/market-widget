package com.tmfrl.marketwidget.model

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 모든 날짜·시간은 기기 시간대가 아니라 서울 기준으로 계산한다. */
val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

private val symbols = DecimalFormatSymbols(Locale.US)
private val bigFormat = DecimalFormat("#,##0", symbols).apply { roundingMode = RoundingMode.HALF_UP }
private val smallFormat = DecimalFormat("0.00", symbols).apply { roundingMode = RoundingMode.HALF_UP }
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("M/d")

/** 1,000 이상은 천 단위 쉼표와 정수, 그 아래는 소수 둘째 자리. */
fun formatPrice(price: Double?): String = when {
    price == null -> "-"
    kotlin.math.abs(price) >= 1000 -> bigFormat.format(price)
    else -> smallFormat.format(price)
}

/** 등락 차이 문구. 100 이상은 정수, 그 아래는 소수 둘째 자리. */
fun formatSignedAmount(change: Double): String {
    val size = kotlin.math.abs(change)
    val digits = if (size >= 100) bigFormat.format(size) else smallFormat.format(size)
    return when {
        change > 0 -> "+$digits"
        change < 0 -> "-$digits"
        else -> digits
    }
}

fun formatChangePct(pct: Double?): String = when {
    pct == null -> ""
    pct > 0 -> "+${smallFormat.format(pct)}%"
    else -> "${smallFormat.format(pct)}%"
}

fun formatEventDate(date: LocalDate): String =
    "${date.format(dayFormat)}(${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

fun seoulToday(now: ZonedDateTime): LocalDate = now.withZoneSameInstant(SEOUL).toLocalDate()

/** 오늘 갱신이면 시각만, 아니면 날짜까지 붙여 오래된 값임을 드러낸다. */
fun formatUpdatedAt(updatedAt: OffsetDateTime, now: ZonedDateTime): String {
    val updated = updatedAt.atZoneSameInstant(SEOUL)
    val time = updated.format(timeFormat)
    return if (updated.toLocalDate() == seoulToday(now)) "$time 기준"
    else "${updated.format(dayFormat)} $time 기준"
}

fun upcomingEvents(events: List<Event>, today: LocalDate, limit: Int = 4): List<Event> =
    events.filter { LocalDate.parse(it.date) >= today }
        .sortedBy { it.date }
        .take(limit)

enum class Trend { UP, DOWN, FLAT }

fun trendOf(pct: Double?): Trend = when {
    pct == null || pct == 0.0 -> Trend.FLAT
    pct > 0 -> Trend.UP
    else -> Trend.DOWN
}
