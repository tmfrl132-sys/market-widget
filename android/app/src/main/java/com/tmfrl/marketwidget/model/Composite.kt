package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.round

/**
 * 합성 지표(v1.10). 두 지표 a, b(기본 지표나 추가 종목의 id)를 사칙연산 op("/", "*", "+", "-")로 합친 타일이다.
 * TradingView의 스프레드 차트처럼 값과 모든 기간 차트를 기기가 계산한다. 예: 원/달러 ÷ 달러인덱스.
 */
@Serializable
data class Composite(val id: String = "", val a: String = "", val op: String = "/", val b: String = "", val name: String = "")

const val COMPOSITE_PREFIX = "X:"

/** 합성 지표 최대 개수. */
const val MAX_COMPOSITES = 6

val COMPOSITE_OPS: List<String> = listOf("/", "*", "+", "-")

/** 화면에 쓰는 연산 기호. */
fun opSymbol(op: String): String = when (op) {
    "/" -> "÷"
    "*" -> "×"
    "+" -> "+"
    "-" -> "−"
    else -> op
}

fun compositeId(a: String, op: String, b: String): String = COMPOSITE_PREFIX + a + op + b

fun isCompositeId(id: String): Boolean = id.startsWith(COMPOSITE_PREFIX)

fun makeComposite(a: String, op: String, b: String, nameA: String, nameB: String): Composite =
    Composite(compositeId(a, op, b), a, op, b, "$nameA${opSymbol(op)}$nameB")

/** 기본 합성 지표. 원화 약세가 달러 전반의 강세보다 큰지 본다(값이 오르면 원화가 달러인덱스보다 더 약해진 것). */
val DEFAULT_COMPOSITES: List<Composite> = listOf(makeComposite("KRW", "/", "DXY", "원/달러", "달러인덱스"))

/** 위젯에 있는 합성 지표. 지우지 않은 기본 합성 지표 다음에 사용자가 만든 것(만든 순서)이다. */
fun compositesOf(settings: WidgetSettings): List<Composite> {
    val removed = settings.composites_removed.toSet()
    val defaults = DEFAULT_COMPOSITES.filter { it.id !in removed }
    val ids = defaults.map { it.id }.toSet()
    return defaults + settings.composites.filter { it.id !in ids }.distinctBy { it.id }
}

/** 합성 지표를 만든다. 같은 두 지표, 이미 있는 것, 가득 찬 경우는 그대로다. 지운 기본 합성 지표면 다시 넣는다. */
fun addComposite(settings: WidgetSettings, composite: Composite): WidgetSettings = when {
    composite.a == composite.b || composite.op !in COMPOSITE_OPS -> settings
    compositesOf(settings).any { it.id == composite.id } -> settings
    compositesOf(settings).size >= MAX_COMPOSITES -> settings
    DEFAULT_COMPOSITES.any { it.id == composite.id } -> settings.copy(composites_removed = settings.composites_removed - composite.id)
    else -> settings.copy(composites = settings.composites + composite)
}

/** 합성 지표를 지운다. 기본 합성 지표는 지운 목록에 적는다. 순서와 꺼짐 목록에서도 지운다. */
fun removeComposite(settings: WidgetSettings, id: String): WidgetSettings = settings.copy(
    order = settings.order.filterNot { it == id },
    hidden = settings.hidden.filterNot { it == id },
    composites = settings.composites.filterNot { it.id == id },
    composites_removed = if (DEFAULT_COMPOSITES.any { it.id == id }) (settings.composites_removed + id).distinct() else settings.composites_removed,
)

private fun round4(value: Double) = round(value * 10000) / 10000

/** a op b. 0으로 나누거나 결과가 수가 아니면 null이다. */
fun applyOp(op: String, a: Double, b: Double): Double? {
    val v = when (op) {
        "/" -> if (b == 0.0) return null else a / b
        "*" -> a * b
        "+" -> a + b
        "-" -> a - b
        else -> return null
    }
    return if (v.isNaN() || v.isInfinite()) null else round4(v)
}

/**
 * a 날짜마다 b의 그 날짜 이하 마지막 값으로 계산한다(휴장일이 달라도 이어진다). b 값이 아직 없는 앞쪽 날짜는 뺀다.
 * 날짜 수가 값 수와 다르거나 읽을 수 없는 날짜가 있으면 빈 값이다. 날짜는 정렬되어 있다고 본다.
 */
fun combineByDate(op: String, aValues: List<Double>, aDates: List<String>, bValues: List<Double>, bDates: List<String>):
    Pair<List<Double>, List<String>> {
    if (aValues.size != aDates.size || bValues.size != bDates.size || bValues.isEmpty()) return emptyList<Double>() to emptyList()
    val bDays = bDates.map { parseDayOrNull(it) ?: return emptyList<Double>() to emptyList() }
    val values = mutableListOf<Double>()
    val dates = mutableListOf<String>()
    var j = -1
    aDates.forEachIndexed { i, text ->
        val day = parseDayOrNull(text) ?: return emptyList<Double>() to emptyList()
        while (j + 1 < bDays.size && bDays[j + 1] <= day) j++
        if (j < 0) return@forEachIndexed
        applyOp(op, aValues[i], bValues[j])?.let { values += it; dates += text }
    }
    return values to dates
}

/** combineByDate의 시각(초) 판. a 시각마다 b의 그 시각 이하 마지막 값을 쓴다. */
fun combineByTime(op: String, aValues: List<Double>, aTimes: List<Long>, bValues: List<Double>, bTimes: List<Long>):
    Pair<List<Double>, List<Long>> {
    if (aValues.size != aTimes.size || bValues.size != bTimes.size || bValues.isEmpty()) return emptyList<Double>() to emptyList()
    val values = mutableListOf<Double>()
    val times = mutableListOf<Long>()
    var j = -1
    aTimes.forEachIndexed { i, t ->
        while (j + 1 < bTimes.size && bTimes[j + 1] <= t) j++
        if (j < 0) return@forEachIndexed
        applyOp(op, aValues[i], bValues[j])?.let { values += it; times += t }
    }
    return values to times
}

private fun parseDayOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (e: java.time.DateTimeException) {
    null
}

/** 전일 종가. 기기가 받은 값이 있으면 그것, 없으면 현재값 − 등락이다. */
private fun previousClose(q: Quote): Double? = q.prev_close ?: q.price?.let { p -> q.change?.let { p - it } }

/** 합성 지표 큰 움직임 기준 기본값(%). 최근 60거래일 움직임에 맞춰 올라간다. */
private const val COMPOSITE_BIG_MOVE_BASE = 1.0

/**
 * 합성 지표 Quote. a·b가 없으면(받기 전) 값이 빈 타일이다. 낙폭(MDD)은 계산하지 않는다(peak·mdd null).
 * 장 시간·시장 시간대·오늘 창은 a를 따른다. 둘 중 하나라도 흐리면(stale) 흐리게 보인다.
 */
fun compositeQuote(c: Composite, a: Quote?, b: Quote?): Quote {
    val empty = Quote(c.id, c.name, price = null, change = null, change_pct = null)
    if (a == null || b == null || a.price == null || b.price == null) return empty
    val price = applyOp(c.op, a.price, b.price) ?: return empty
    val prev = previousClose(a)?.let { pa -> previousClose(b)?.let { pb -> applyOp(c.op, pa, pb) } }
    val change = prev?.let { round4(price - it) }
    val changePct = prev?.takeIf { it != 0.0 }?.let { round((price - it) / kotlin.math.abs(it) * 10000) / 100 }
    val (daily, dailyDates) = combineByDate(c.op, a.daily, a.daily_dates, b.daily, b.daily_dates)
    val (weekly, weeklyDates) = combineByDate(c.op, a.weekly, a.weekly_dates, b.weekly, b.weekly_dates)
    val (maxCloses, maxDates) = combineByDate(c.op, a.max_closes, a.max_dates, b.max_closes, b.max_dates)
    val (intraday, intradayTimes) = combineByTime(c.op, a.intraday, a.intraday_times, b.intraday, b.intraday_times)
    val (week, weekTimes) = combineByTime(c.op, a.week, a.week_times, b.week, b.week_times)
    return empty.copy(
        price = price,
        change = change,
        change_pct = changePct,
        prev_close = prev,
        daily = daily,
        daily_dates = dailyDates,
        chart = daily.takeLast(63),
        weekly = weekly,
        weekly_dates = weeklyDates,
        max_closes = maxCloses,
        max_dates = maxDates,
        max_start = maxDates.firstOrNull()?.take(4)?.toIntOrNull(),
        intraday = intraday,
        intraday_times = intradayTimes,
        session_start = a.session_start,
        session_end = a.session_end,
        week = week,
        week_times = weekTimes,
        big_move_pct = bigMovePct(COMPOSITE_BIG_MOVE_BASE, daily),
        market_tz = a.market_tz,
        always_open = a.always_open && b.always_open,
        stale = a.stale || b.stale,
    )
}
