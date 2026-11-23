package com.tmfrl.marketwidget.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 일정 줄 맨 앞 이름표와 글자 크기(sp). 화면과 폭 추정이 같은 값을 쓴다.
 * v1.6: 누르면 전체 목록이 열린다는 것을 ▾로 알리고, 글자를 11sp에서 12sp로 키웠다.
 */
const val EVENT_LINE_LABEL = "일정 ▾"
const val EVENT_LINE_SP = 12

/** 중요도 최고 별 수. */
const val MAX_EVENT_STARS = 3

/**
 * 일정 중요도(별 1~3, v1.6). 제목의 낱말로 정한다. 기준은 발표 당일 시장이 움직이는 정도에 대한 일반적인 인식이다.
 * ★★★ FOMC·CPI·고용보고서, ★★ 한국은행 금리 결정·PCE, ★ GDP. 모르는 일정은 0(별 없음).
 */
private val EVENT_STARS: List<Pair<String, Int>> = listOf(
    "FOMC" to 3,
    "CPI" to 3,
    "고용보고서" to 3,
    "한국은행" to 2,
    "금리 결정" to 2,
    "PCE" to 2,
    "GDP" to 1,
)

fun eventStars(title: String): Int = EVENT_STARS.filter { title.contains(it.first) }.maxOfOrNull { it.second } ?: 0

/** "★★★". 0이면 빈 문자열. */
fun formatStars(stars: Int): String = "★".repeat(stars.coerceIn(0, MAX_EVENT_STARS))

/** 이름표와 첫 일정 사이 간격(dp). */
const val EVENT_LINE_GAP_DP = 6

/** 위젯 테두리 안쪽 좌우 여백 합(dp). */
private const val LINE_PADDING_DP = 20f

/** 일정 사이 구분 문자. */
private const val DAY_SEPARATOR = " · "

/** 같은 날 제목을 잇는 문자. */
private const val TITLE_SEPARATOR = "·"

/** 제목 앞에 붙는 나라 이름. 같은 날 같은 나라에서 반복되면 한 번만 쓴다. */
private val COUNTRY_PREFIX = mapOf("US" to "미국 ", "KR" to "한국 ")

/** 한 줄에 넣으려고 줄여 쓰는 기관 이름. 전체 일정 목록은 원래 제목을 그대로 보인다. */
private const val BOK_FULL = "한국은행 "
private const val BOK_SHORT = "한은 "

/**
 * 일정 줄의 하루치. title은 같은 날 일정을 묶은 제목, dday는 오늘·내일·D-n이다.
 * soon은 오늘·내일 일정이라 색으로 강조한다는 뜻이다.
 */
data class EventDay(
    val date: LocalDate,
    val title: String,
    val dday: String,
    val soon: Boolean,
    val stars: Int = 0,
    val time: String? = null,
) {
    /** 예: "12/10(목) 22:30 미국 CPI ★★★ D-17". stars는 그날 가장 높은 중요도, time은 그날 가장 이른 서울 발표 시각(v1.8)이다. */
    val text: String get() =
        listOf(formatEventDate(date), time.orEmpty(), title, formatStars(stars), dday).filter { it.isNotEmpty() }.joinToString(" ")
}

/** 화면에 색을 나눠 그릴 두 조각. 오늘·내일은 언제나 앞쪽이라 조각 둘이면 충분하다. */
data class EventLineText(val soon: String, val later: String)

/**
 * 같은 날 일정의 제목을 하나로 묶는다. 같은 나라끼리 모으고, 나라 이름이 반복되면 처음 한 번만 쓴다.
 * 예: 미국 GDP, 미국 PCE는 "미국 GDP·PCE", 미국 CPI, 한국은행 금리 결정은 "미국 CPI·한은 금리 결정".
 */
fun mergeEventTitles(events: List<Event>): String =
    events.groupBy { it.country }
        .flatMap { (country, sameCountry) -> sameCountryTitles(country, sameCountry) }
        .joinToString(TITLE_SEPARATOR)

private fun sameCountryTitles(country: String, events: List<Event>): List<String> {
    val titles = events.map { shortEventTitle(it.title) }.filter { it.isNotEmpty() }.distinct()
    val prefix = COUNTRY_PREFIX[country] ?: return titles
    // 나라 이름이 있는 제목을 붙여 놓아야 뒤 제목의 나라를 읽는 사람이 헷갈리지 않는다
    val (named, others) = titles.partition { it.startsWith(prefix) && it.length > prefix.length }
    return others + named.mapIndexed { index, title -> if (index == 0) title else title.removePrefix(prefix) }
}

private fun shortEventTitle(title: String): String {
    val trimmed = title.trim()
    return if (trimmed.startsWith(BOK_FULL)) BOK_SHORT + trimmed.removePrefix(BOK_FULL) else trimmed
}

/** 오늘·내일 일정인지. 일정 줄과 전체 목록이 같은 기준으로 강조한다. */
fun isSoonEvent(date: LocalDate, today: LocalDate): Boolean = ChronoUnit.DAYS.between(today, date) in 0L..1L

/** 오늘(서울) 이후 일정을 날짜별로 묶어 가까운 순으로 돌려준다. 날짜를 읽을 수 없거나 제목이 빈 일정은 뺀다. */
fun eventDays(events: List<Event>, today: LocalDate): List<EventDay> =
    events.mapNotNull { event -> eventDate(event)?.takeIf { it >= today }?.let { it to event } }
        .groupBy({ it.first }, { it.second })
        .entries
        // LocalDate는 Comparable<LocalDate>가 아니라 sortedBy에 바로 넘길 수 없다
        .sortedBy { it.key.toEpochDay() }
        .mapNotNull { (date, sameDay) ->
            val title = mergeEventTitles(sameDay)
            if (title.isEmpty()) return@mapNotNull null
            EventDay(
                date, title, formatDday(date, today), isSoonEvent(date, today), sameDay.maxOf { eventStars(it.title) },
                sameDay.mapNotNull { eventTime(it) }.minOrNull(),
            )
        }

/**
 * 글자 폭 추정(dp). 실제 글꼴을 잴 수 없어 조금 넉넉하게 잡는다.
 * 모자라게 추정하면 마지막 일정의 글자가 잘리고, 넉넉하면 일정이 하나 덜 보일 뿐이다.
 */
fun estimateTextWidthDp(text: String, fontSp: Float): Float =
    text.sumOf { charWidthEm(it).toDouble() }.toFloat() * fontSp

private fun charWidthEm(c: Char): Float = when (c) {
    '▾' -> 0.7f
    ' ' -> 0.3f
    in '0'..'9' -> 0.6f
    in 'A'..'Z' -> 0.72f
    in 'a'..'z' -> 0.6f
    '(', ')', '/', '-', '.', ',' -> 0.45f
    '·' -> 0.5f
    else -> 1f
}

/** 일정 글자가 쓸 수 있는 폭(dp). 위젯 폭에서 테두리 여백, 이름표, 간격을 뺀다. */
fun eventLineWidthDp(widgetWidthDp: Float, fontScale: Float): Float =
    widgetWidthDp - LINE_PADDING_DP - EVENT_LINE_GAP_DP -
        estimateTextWidthDp(EVENT_LINE_LABEL, EVENT_LINE_SP * fontScale)

/** 폭 안에 온전히 들어가는 일정 수. 가까운 일정부터 세고, 일정이 있으면 최소 1건이다. */
fun eventDaysFitting(days: List<EventDay>, availableDp: Float, fontSp: Float): Int {
    if (days.isEmpty()) return 0
    val separator = estimateTextWidthDp(DAY_SEPARATOR, fontSp)
    var used = estimateTextWidthDp(days.first().text, fontSp)
    var count = 1
    for (day in days.drop(1)) {
        used += separator + estimateTextWidthDp(day.text, fontSp)
        if (used > availableDp) break
        count++
    }
    return count
}

/** 일정 줄 글자를 강조할 앞 조각과 나머지 조각으로 나눈다. */
fun eventLineText(days: List<EventDay>): EventLineText {
    val (soon, later) = days.partition { it.soon }
    val soonText = soon.joinToString(DAY_SEPARATOR) { it.text }
    val laterText = later.joinToString(DAY_SEPARATOR) { it.text }
    return EventLineText(soonText, if (soonText.isNotEmpty() && laterText.isNotEmpty()) DAY_SEPARATOR + laterText else laterText)
}

/** 일정 줄을 누른 뒤 목록이 열려 있는지. 고른 지표가 없으면 차트 자리가 이미 일정 목록이라 열고 닫을 것이 없다. */
fun afterEventLineTap(open: Boolean, selected: String?): Boolean = !open && selected != null

/**
 * 일정 목록이 지금 화면에 보이는지. 저장된 열림 상태가 있어도 줄이나 차트 칸이 없는 크기에서는 목록을 그릴 수 없어
 * 열리지 않은 것으로 본다. 일정이 없으면 줄이 없으므로 이 경우도 포함된다.
 */
fun eventListVisible(stored: Boolean, layout: WidgetLayout): Boolean =
    stored && layout.showEventLine && layout.showDetail

/**
 * 타일을 눌렀을 때의 선택. 일정 목록이 보이는 중이면 목록을 닫고 그 지표 차트를 연다.
 * 이때 afterTileTap을 쓰면 지금 지표의 타일을 눌렀을 때 차트가 닫혀 버린다.
 * 지금 지표를 누르면 차트 상태를 그대로 두고, 다른 지표를 누르면 비교 대상을 유지한다.
 */
fun tileTapSelection(s: ChartSelection, tapped: String, auto: Boolean, listOpen: Boolean): ChartSelection = when {
    !listOpen -> afterTileTap(s, tapped, auto)
    tapped == s.selected -> s.copy(picking = false)
    // 같은 지표끼리는 비교하지 않고, 자동모드에는 비교가 없다
    auto || tapped == s.compare -> ChartSelection(selected = tapped)
    else -> ChartSelection(tapped, s.compare)
}

/** 차트 자리에 보일 일정 목록. more는 칸이 모자라 못 보인 일정을 알리는 마지막 줄이고, 다 보이면 null이다. */
data class EventListPage(val events: List<Event>, val more: String?)

/** 앞으로의 일정을 rows줄에 맞춘다. 다 들어가지 않으면 마지막 줄을 "외 N건"으로 쓴다. */
fun eventListPage(events: List<Event>, today: LocalDate, rows: Int): EventListPage {
    val upcoming = upcomingEvents(events, today, limit = Int.MAX_VALUE)
    if (upcoming.size <= rows) return EventListPage(upcoming, null)
    val shown = upcoming.take(maxOf(rows - 1, 0))
    return EventListPage(shown, "외 ${upcoming.size - shown.size}건")
}
