@file:Suppress("PropertyName")

package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 사용자가 추가한 종목. id는 "T:" + Yahoo 심볼, kind는 EQUITY·ETF·INDEX·CRYPTOCURRENCY 중 하나,
 * naver_code는 한국 주식·ETF(.KS, .KQ)의 6자리 코드이고 나머지는 null이다.
 */
@Serializable
data class CustomTicker(
    val id: String = "",
    val symbol: String = "",
    val name: String = "",
    val kind: String = KIND_EQUITY,
    val naver_code: String? = null,
)

/**
 * 위젯에 보일 지표 설정(settings.json). 모든 위젯이 같이 쓴다.
 * order는 보일 순서(없는 id는 뒤에 붙는다), hidden은 끈 지표, custom은 추가 종목(추가한 순서)이다.
 * v1.3: show_drawdown은 큰 차트의 고점 대비 낙폭(MDD) 표시(기본 켜짐), nxt_hours는 추가한 한국 개별 종목에
 * 넥스트레이드(NXT) 08:00~20:00 시세를 넣는지(기본 꺼짐)다. 옛 settings.json에는 없어 기본값으로 읽힌다.
 */
@Serializable
data class WidgetSettings(
    val version: Int = 1,
    val order: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    val custom: List<CustomTicker> = emptyList(),
    val show_drawdown: Boolean = true,
    val nxt_hours: Boolean = false,
    // v1.7 차트 보조 표시. 위젯 차트 아래 체크박스로 켜고 끈다. 모든 위젯에 같이 쓰인다.
    val show_candles: Boolean = false,
    val show_rsi: Boolean = false,
    val show_bollinger: Boolean = false,
    val show_hv: Boolean = false,
    val show_ma: Boolean = false,
    val show_macd: Boolean = false,
    val show_volume: Boolean = false,
    // v1.9 실적 일정(차트 종목과 별개). earnings는 사용자가 검색해 더한 종목, earnings_removed는 뺀 기본 종목의 심볼이다.
    val earnings: List<CustomTicker> = emptyList(),
    val earnings_removed: List<String> = emptyList(),
    // v1.10 합성 지표(두 지표의 사칙연산 타일). composites는 사용자가 만든 것, composites_removed는 지운 기본 합성 지표 id다.
    val composites: List<Composite> = emptyList(),
    val composites_removed: List<String> = emptyList(),
)

/** 차트 보조 표시 체크박스 키(v1.7). 위젯 액션 인자로 쓴다. */
const val OPTION_CANDLES = "candles"
const val OPTION_DRAWDOWN = "drawdown"
const val OPTION_BOLLINGER = "bollinger"
const val OPTION_RSI = "rsi"
const val OPTION_HV = "hv"
const val OPTION_MA = "ma"
const val OPTION_MACD = "macd"
const val OPTION_VOLUME = "volume"

/** 키에 맞는 보조 표시를 뒤집는다. 모르는 키는 그대로 둔다. */
fun toggleChartOption(settings: WidgetSettings, key: String): WidgetSettings = when (key) {
    OPTION_CANDLES -> settings.copy(show_candles = !settings.show_candles)
    OPTION_DRAWDOWN -> settings.copy(show_drawdown = !settings.show_drawdown)
    OPTION_BOLLINGER -> settings.copy(show_bollinger = !settings.show_bollinger)
    OPTION_RSI -> settings.copy(show_rsi = !settings.show_rsi)
    OPTION_HV -> settings.copy(show_hv = !settings.show_hv)
    OPTION_MA -> settings.copy(show_ma = !settings.show_ma)
    OPTION_MACD -> settings.copy(show_macd = !settings.show_macd)
    OPTION_VOLUME -> settings.copy(show_volume = !settings.show_volume)
    else -> settings
}

const val KIND_EQUITY = "EQUITY"
const val KIND_ETF = "ETF"
const val KIND_INDEX = "INDEX"
const val KIND_CRYPTO = "CRYPTOCURRENCY"
val SUPPORTED_KINDS: Set<String> = setOf(KIND_EQUITY, KIND_ETF, KIND_INDEX, KIND_CRYPTO)

/** 추가 종목 최대 개수. 기본 11개 + 8 = 19칸(7칸이면 3줄). */
const val MAX_CUSTOM = 8

/** 기본 지표 이름. 수집기 collector/config.py SYMBOLS와 같은 순서·이름이다. 기기에 data.json이 아직 없을 때 설정 화면 이름으로 쓴다. */
val BUILT_IN_NAMES: Map<String, String> = linkedMapOf(
    "KS11" to "코스피",
    "KQ11" to "코스닥",
    "GSPC" to "S&P500",
    "NDX" to "나스닥100",
    "VIX" to "VIX",
    "KRW" to "원/달러",
    "DXY" to "달러인덱스",
    "TNX" to "미 10년물",
    "CL" to "WTI",
    "GC" to "금",
    "BTC" to "비트코인",
)

private const val CUSTOM_PREFIX = "T:"

fun customId(symbol: String): String = CUSTOM_PREFIX + symbol

fun isCustomId(id: String): Boolean = id.startsWith(CUSTOM_PREFIX)

private val settingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** settings.json 읽기. 깨졌거나 비었으면 기본값(기본 지표 모두 켜짐, 수집기 순서)이다. */
fun parseSettings(text: String): WidgetSettings = try {
    settingsJson.decodeFromString<WidgetSettings>(text)
} catch (e: IllegalArgumentException) { // SerializationException 포함
    WidgetSettings()
}

fun encodeSettings(settings: WidgetSettings): String = settingsJson.encodeToString(settings)

/** v1.1에서 바뀐 기본 지표 id. 값이 null이면 없어진 지표다. 나스닥 종합 → 나스닥100, S&P·나스닥 선물 카드는 합친 차트로 들어갔다. */
val RETIRED_IDS: Map<String, String?> = mapOf("IXIC" to "NDX", "ES" to null, "NQ" to null)

/**
 * 옛 설정의 id를 옮긴다. IXIC는 NDX가 되어 순서 자리와 켜기·끄기를 이어받고, ES·NQ는 지운다.
 * 이미 옮긴 설정은 그대로다. 추가 종목(T:로 시작)은 건드리지 않는다.
 * 옮긴 결과 켜진 기본 지표와 추가 종목이 모두 0개면 S&P500과 나스닥100을 켠다("최소 1개 켜기" 규칙 유지).
 */
fun migrateSettings(settings: WidgetSettings): WidgetSettings {
    fun migrate(ids: List<String>) = ids.mapNotNull { id -> if (id in RETIRED_IDS) RETIRED_IDS[id] else id }.distinct()
    val order = migrate(settings.order)
    val migratedHidden = migrate(settings.hidden)
    if (order == settings.order && migratedHidden == settings.hidden) return settings
    // 옛 설정에서 선물(ES·NQ)만 켜 두었으면 옮긴 뒤 켜진 지표가 0개가 된다. 그 자리를 대신하는 S&P500·나스닥100을 켠다.
    val shown = (BUILT_IN_NAMES.keys + settings.custom.map { it.id }).filter { it !in migratedHidden }
    val hidden = if (shown.isEmpty()) migratedHidden - FUTURES_REPLACEMENTS else migratedHidden
    return settings.copy(order = order, hidden = hidden)
}

/** 옛 선물 카드를 대신하는 기본 지표. 이전 뒤 켜진 지표가 없을 때 켠다. */
private val FUTURES_REPLACEMENTS = setOf("GSPC", "NDX")

/**
 * data.json에서 위젯이 그릴 기본 지표. 기본 지표 목록(BUILT_IN_NAMES)에 있는 id만 남긴다.
 * 새 앱이 옛 data.json(IXIC, ES, NQ 포함)을 받아도 없어진 지표 타일이 생기지 않는다.
 */
fun builtInQuotes(quotes: List<Quote>): List<Quote> = quotes.filter { it.id in BUILT_IN_NAMES }

/**
 * 모든 지표 id의 보일 순서(꺼진 것 포함). order에 있는 id를 그 순서로 두고, 없는 id는
 * 기본 지표(수집기 순서) → 추가 종목(추가 순서) → 합성 지표(v1.10)로 뒤에 붙인다. order의 모르는 id는 무시한다.
 */
fun orderedIds(builtInIds: List<String>, settings: WidgetSettings): List<String> {
    val all = (builtInIds + settings.custom.map { it.id } + compositesOf(settings).map { it.id }).distinct()
    val known = all.toSet()
    val first = settings.order.filter { it in known }.distinct()
    val placed = first.toSet()
    return first + all.filter { it !in placed }
}

/**
 * 위젯에 실제로 보일 지표. builtIn은 data.json의 기본 지표, custom은 추가 종목 캐시다.
 * 추가 종목이 캐시에 없으면 값이 빈 타일(Quote(id, name, null...))로 채운다.
 */
fun visibleQuotes(allBuiltIn: List<Quote>, custom: List<Quote>, settings: WidgetSettings): List<Quote> {
    // 기본 지표 목록에 없는 id(옛 data.json의 IXIC, ES, NQ)는 그리지 않는다
    val builtIn = builtInQuotes(allBuiltIn)
    val cached = custom.associateBy { it.id }
    val sources = builtIn.associateBy { it.id } + settings.custom.associate { ticker ->
        ticker.id to (cached[ticker.id] ?: Quote(ticker.id, ticker.name, price = null, change = null, change_pct = null))
    }
    // 합성 지표는 재료 지표가 꺼져 있어도 계산한다
    val byId = sources + compositesOf(settings).associate { c -> c.id to compositeQuote(c, sources[c.a], sources[c.b]) }
    val hidden = settings.hidden.toSet()
    return orderedIds(builtIn.map { it.id }, settings).filter { it !in hidden }.mapNotNull { byId[it] }
}

/**
 * 보이는 지표 목록으로 차트 상태를 정리한다. 차트를 연 지표가 안 보이면 모두 지우고(일정 화면),
 * 비교 지표만 안 보이면 비교만 지운다.
 */
fun sanitizeSelection(selection: ChartSelection, visibleIds: Collection<String>): ChartSelection = when {
    selection.selected != null && selection.selected !in visibleIds -> ChartSelection()
    selection.compare != null && selection.compare !in visibleIds -> selection.copy(compare = null)
    else -> selection
}

private fun move(builtInIds: List<String>, settings: WidgetSettings, id: String, step: Int): WidgetSettings {
    val ids = orderedIds(builtInIds, settings).toMutableList()
    val from = ids.indexOf(id)
    val to = from + step
    if (from < 0 || to !in ids.indices) return settings
    ids[from] = ids[to]
    ids[to] = id
    return settings.copy(order = ids)
}

/** 한 칸 위로. 맨 위면 그대로. */
fun moveUp(builtInIds: List<String>, settings: WidgetSettings, id: String): WidgetSettings = move(builtInIds, settings, id, -1)

/** 한 칸 아래로. 맨 아래면 그대로. */
fun moveDown(builtInIds: List<String>, settings: WidgetSettings, id: String): WidgetSettings = move(builtInIds, settings, id, 1)

/** 켜진 지표 수. */
fun shownCount(builtInIds: List<String>, settings: WidgetSettings): Int =
    orderedIds(builtInIds, settings).count { it !in settings.hidden }

/** 켜고 끄기. 켜진 것이 1개뿐이면 그것을 끄지 않는다(위젯이 빈 칸이 된다). */
fun setHidden(builtInIds: List<String>, settings: WidgetSettings, id: String, hidden: Boolean): WidgetSettings = when {
    hidden && id in settings.hidden -> settings
    hidden && shownCount(builtInIds, settings) <= 1 -> settings
    hidden -> settings.copy(hidden = settings.hidden + id)
    else -> settings.copy(hidden = settings.hidden.filterNot { it == id })
}

/** 기본 지표가 쓰는 Yahoo 심볼. 검색 결과에서 "추가됨"으로 보인다. */
val BUILT_IN_SYMBOLS: Set<String> get() = LIVE_SYMBOLS.values.toSet()

/** 이미 위젯에 있는 심볼인지(기본 지표 포함). */
fun isAdded(settings: WidgetSettings, symbol: String): Boolean =
    symbol in BUILT_IN_SYMBOLS || settings.custom.any { it.symbol == symbol }

/** 종목 추가. 8개가 찼거나 이미 있는 심볼(기본 지표 포함)이면 그대로. 켜진 상태로 맨 뒤에 붙는다. */
fun addCustom(settings: WidgetSettings, ticker: CustomTicker): WidgetSettings = when {
    settings.custom.size >= MAX_CUSTOM -> settings
    isAdded(settings, ticker.symbol) -> settings
    settings.custom.any { it.id == ticker.id } -> settings
    else -> settings.copy(custom = settings.custom + ticker)
}

/** 종목 삭제. 순서와 꺼짐 목록에서도 지운다. */
fun removeCustom(settings: WidgetSettings, id: String): WidgetSettings = settings.copy(
    order = settings.order.filterNot { it == id },
    hidden = settings.hidden.filterNot { it == id },
    custom = settings.custom.filterNot { it.id == id },
)

/** 기본값으로: 순서를 기본으로, 모두 켜짐. 추가 종목은 지우지 않는다. */
fun resetOrder(settings: WidgetSettings): WidgetSettings = settings.copy(order = emptyList(), hidden = emptyList())

private fun stock(symbol: String, name: String) = CustomTicker(EARNINGS_PREFIX + symbol, symbol, name, KIND_EQUITY, null)

private const val EARNINGS_PREFIX = "E:"

/** 실적 일정 최대 종목 수(기본 포함). */
const val MAX_EARNINGS = 40

/** 기본 실적 일정 종목(v1.9). 한국 시가총액 상위와 미국 빅테크·반도체. 설정에서 빼고 다시 넣을 수 있다. */
val DEFAULT_EARNINGS: List<CustomTicker> = listOf(
    stock("005930.KS", "삼성전자"),
    stock("000660.KS", "SK하이닉스"),
    stock("373220.KS", "LG에너지솔루션"),
    stock("005380.KS", "현대차"),
    stock("035420.KS", "NAVER"),
    stock("AAPL", "애플"),
    stock("MSFT", "마이크로소프트"),
    stock("NVDA", "엔비디아"),
    stock("GOOGL", "알파벳"),
    stock("AMZN", "아마존"),
    stock("META", "메타"),
    stock("TSLA", "테슬라"),
    stock("AVGO", "브로드컴"),
    stock("TSM", "TSMC"),
    stock("MU", "마이크론"),
)

/** 실적 일정을 받을 종목. 빼지 않은 기본 종목 다음에 사용자가 더한 종목(더한 순서)이다. */
fun earningsTickers(settings: WidgetSettings): List<CustomTicker> {
    val removed = settings.earnings_removed.toSet()
    val defaults = DEFAULT_EARNINGS.filter { it.symbol !in removed }
    val defaultSymbols = defaults.map { it.symbol }.toSet()
    return defaults + settings.earnings.filter { it.symbol !in defaultSymbols }.distinctBy { it.symbol }
}

fun isEarningsAdded(settings: WidgetSettings, symbol: String): Boolean = earningsTickers(settings).any { it.symbol == symbol }

/** 실적 일정에 더한다. 뺀 기본 종목이면 다시 넣고, 이미 있거나 가득 찼으면 그대로다. */
fun addEarnings(settings: WidgetSettings, symbol: String, name: String): WidgetSettings = when {
    isEarningsAdded(settings, symbol) -> settings
    earningsTickers(settings).size >= MAX_EARNINGS -> settings
    DEFAULT_EARNINGS.any { it.symbol == symbol } -> settings.copy(earnings_removed = settings.earnings_removed - symbol)
    else -> settings.copy(earnings = settings.earnings + stock(symbol, name))
}

/** 실적 일정에서 뺀다. 기본 종목은 뺀 목록에 적어 다시 생기지 않게 한다. */
fun removeEarnings(settings: WidgetSettings, symbol: String): WidgetSettings {
    val removed = if (DEFAULT_EARNINGS.any { it.symbol == symbol }) (settings.earnings_removed + symbol).distinct() else settings.earnings_removed
    return settings.copy(earnings = settings.earnings.filter { it.symbol != symbol }, earnings_removed = removed)
}

/** 뺀 기본 종목을 모두 다시 넣는다. 사용자가 더한 종목은 그대로다. */
fun restoreEarningsDefaults(settings: WidgetSettings): WidgetSettings = settings.copy(earnings_removed = emptyList())
