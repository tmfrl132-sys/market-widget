package com.tmfrl.marketwidget.model

import kotlin.math.round

/**
 * 오늘 차트의 발표 시점 표시(v1.8). 서울 발표 시각이 있는 일정이 차트 창(start~end) 안에 있으면 세로 점선을 긋고,
 * 이미 발표된 일정은 "발표 직전 가격 대비 지금 등락과 발표 뒤 최저"를 보인다. S&P500 선물 급락을 지표 발표와 함께 보려고 만들었다.
 */

/** 세로 점선. position은 창 안 가로 위치(0~1), label은 짧은 이름("CPI"). */
data class EventMark(val position: Float, val label: String, val time: Long)

/** 발표 뒤 움직임. change는 발표 직전 가격 대비 지금 %, low는 발표 뒤 최저의 %, time은 발표 시각(초). */
data class EventImpact(val label: String, val time: Long, val change: Double, val low: Double)

/** 차트에 쓸 짧은 일정 이름. */
fun eventShortLabel(title: String): String = when {
    "FOMC" in title -> "FOMC"
    "CPI" in title -> "CPI"
    "고용" in title -> "고용"
    "PCE" in title -> "PCE"
    "GDP" in title -> "GDP"
    "한국은행" in title || "금통위" in title -> "금통위"
    else -> title
}

/** 창 [start, end] 안에 서울 발표 시각이 있는 일정(시각순). 같은 시각의 일정은 이름을 "CPI·고용"처럼 합친다. */
fun eventMarks(events: List<Event>, start: Long, end: Long): List<EventMark> {
    if (end <= start) return emptyList()
    return events.mapNotNull { e -> eventInstant(e)?.epochSecond?.takeIf { it in start..end }?.let { it to eventShortLabel(e.title) } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (time, labels) -> EventMark(sessionPosition(time, start, end), labels.distinct().joinToString("·"), time) }
}

private fun round2(value: Double) = round(value * 100) / 100

/**
 * 발표 시각 time 뒤의 움직임. 기준은 time 전 마지막 봉의 값이다(Yahoo 5분봉의 시각은 봉 시작이라 그 봉의 종가가 발표 시각 가격이다).
 * 발표 전 봉이나 발표 뒤 봉이 없으면(아직 발표 전, 창 시작 전 발표) null이다.
 */
fun eventImpact(times: List<Long>, values: List<Double>, time: Long, label: String): EventImpact? {
    if (times.size != values.size) return null
    val before = times.indexOfLast { it < time }.takeIf { it >= 0 } ?: return null
    val after = values.indices.filter { times[it] >= time }.takeIf { it.isNotEmpty() } ?: return null
    val base = values[before].takeIf { it > 0 } ?: return null
    val low = after.minOf { values[it] }
    return EventImpact(label, time, round2((values.last() / base - 1) * 100), round2((low / base - 1) * 100))
}

/** 창 안에서 가장 최근에 발표된 일정의 움직임. 없으면 null. */
fun latestEventImpact(marks: List<EventMark>, times: List<Long>, values: List<Double>): EventImpact? =
    marks.asReversed().firstNotNullOfOrNull { eventImpact(times, values, it.time, it.label) }
