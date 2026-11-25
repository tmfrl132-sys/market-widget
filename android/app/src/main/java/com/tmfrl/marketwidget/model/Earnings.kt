package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 추가 종목의 실적발표 예정일(v1.9). Yahoo quoteSummary calendarEvents에서 읽는다.
 * time은 초 단위 epoch, estimate는 Yahoo가 회사 발표 전 추정한 날짜인지(isEarningsDateEstimate)다.
 */
@Serializable
data class EarningsDate(val time: Long, val estimate: Boolean = false)

/** 실적 일정을 다시 받는 간격 12시간. 날짜는 자주 바뀌지 않는다. */
private const val EARNINGS_INTERVAL_MS = 12 * 3_600_000L

/** 실적 일정을 보이는 기간(오늘부터 일). 경제지표 일정과 같다. */
const val EARNINGS_WINDOW_DAYS = 31L

fun earningsDue(earningsAt: Long?, nowMillis: Long): Boolean =
    earningsAt == null || nowMillis - earningsAt >= EARNINGS_INTERVAL_MS

private val json = Json { ignoreUnknownKeys = true }

private fun JsonElement?.field(name: String): JsonElement? = (this as? JsonObject)?.get(name)

/**
 * quoteSummary(modules=calendarEvents) 응답에서 다음 실적발표일을 읽는다. 날짜가 범위(두 개)면 앞 날짜다.
 * 형식이 다르거나 날짜가 없으면 null(실적 일정이 없는 종목). 잘못된 JSON이면 IllegalArgumentException이다.
 */
fun parseEarnings(text: String): EarningsDate? {
    val root = json.parseToJsonElement(text)
    val result = (root.field("quoteSummary").field("result") as? JsonArray)?.firstOrNull() ?: return null
    val earnings = result.field("calendarEvents").field("earnings") ?: return null
    val first = (earnings.field("earningsDate") as? JsonArray)?.firstOrNull() ?: return null
    val raw = (first.field("raw") as? JsonPrimitive)?.longOrNull ?: (first as? JsonPrimitive)?.longOrNull ?: return null
    if (raw <= 0) return null
    val estimate = (earnings.field("isEarningsDateEstimate") as? JsonPrimitive)?.booleanOrNull ?: false
    return EarningsDate(raw, estimate)
}

/** 한국 종목(.KS 코스피, .KQ 코스닥)인지. */
fun isKoreanSymbol(symbol: String): Boolean = symbol.endsWith(".KS") || symbol.endsWith(".KQ")

private val NEW_YORK_ZONE: ZoneId = ZoneId.of("America/New_York")

/**
 * 실적 일정을 경제지표 일정 형식으로 바꾼다. 제목은 "엔비디아 실적", 추정 날짜면 "엔비디아 실적(예상)"이다.
 * 시각은 시장 현지 시각이 00:00이 아니고 추정이 아닐 때만 서울 시각(kst)으로 붙인다. 아니면 현지 날짜만 쓴다.
 */
fun earningsEvent(ticker: CustomTicker, earnings: EarningsDate): Event {
    val korean = isKoreanSymbol(ticker.symbol)
    val local = Instant.ofEpochSecond(earnings.time).atZone(if (korean) SEOUL else NEW_YORK_ZONE)
    val timed = !earnings.estimate && local.toLocalTime() != LocalTime.MIDNIGHT
    val title = ticker.name + " 실적" + if (earnings.estimate) "(예상)" else ""
    return Event(
        date = local.toLocalDate().toString(),
        title = title,
        country = if (korean) "KR" else "US",
        kst = if (timed) local.withZoneSameInstant(SEOUL).toOffsetDateTime().toString() else null,
    )
}

/**
 * 실적 일정 캐시(earnings.json)의 한 줄. at은 마지막으로 받은 시각(밀리초), earnings는 다음 발표일(없으면 null).
 */
@Serializable
data class EarningsEntry(val symbol: String, val earnings: EarningsDate? = null, val at: Long? = null, val failed: Boolean = false)

@Serializable
data class EarningsCache(val entries: List<EarningsEntry> = emptyList())

fun parseEarningsCache(text: String): EarningsCache = try {
    json.decodeFromString(EarningsCache.serializer(), text)
} catch (e: IllegalArgumentException) {
    EarningsCache()
}

fun encodeEarningsCache(cache: EarningsCache): String = json.encodeToString(EarningsCache.serializer(), cache)

/** 위젯에 보일 실적 일정. 실적 일정 목록의 종목 중 서울 날짜가 오늘부터 31일 안인 것이다. 목록에서 빼면 사라진다. */
fun earningsEvents(entries: List<EarningsEntry>, tickers: List<CustomTicker>, today: LocalDate): List<Event> {
    val bySymbol = entries.associateBy { it.symbol }
    return tickers.mapNotNull { ticker ->
        val earnings = bySymbol[ticker.symbol]?.earnings ?: return@mapNotNull null
        earningsEvent(ticker, earnings).takeIf { event ->
            val day = eventDate(event) ?: return@takeIf false
            ChronoUnit.DAYS.between(today, day) in 0..EARNINGS_WINDOW_DAYS
        }
    }
}

/** 설정 화면 상태 문구. 예: "12/2(수) 06:00 발표", "12/1(화) 발표 예상", "31일 안 일정 없음", "받는 중". */
fun earningsStatusText(ticker: CustomTicker, entry: EarningsEntry?): String {
    val earnings = entry?.earnings
    return when {
        entry == null || entry.at == null -> if (entry?.failed == true) "받지 못함 · 다시 시도 중" else "받는 중"
        earnings == null -> "예정된 발표일 없음"
        else -> {
            val event = earningsEvent(ticker, earnings)
            val day = eventDate(event)?.let(::formatEventDate).orEmpty()
            day + (eventTime(event)?.let { " $it" }.orEmpty()) + if (earnings.estimate) " 발표 예상" else " 발표"
        }
    }
}
