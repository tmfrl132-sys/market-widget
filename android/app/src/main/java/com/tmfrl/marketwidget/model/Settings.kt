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
 */
@Serializable
data class WidgetSettings(
    val version: Int = 1,
    val order: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    val custom: List<CustomTicker> = emptyList(),
)

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
 */
fun migrateSettings(settings: WidgetSettings): WidgetSettings {
    fun migrate(ids: List<String>) = ids.mapNotNull { id -> if (id in RETIRED_IDS) RETIRED_IDS[id] else id }.distinct()
    val order = migrate(settings.order)
    val hidden = migrate(settings.hidden)
    return if (order == settings.order && hidden == settings.hidden) settings else settings.copy(order = order, hidden = hidden)
}

/**
 * data.json에서 위젯이 그릴 기본 지표. 기본 지표 목록(BUILT_IN_NAMES)에 있는 id만 남긴다.
 * 새 앱이 옛 data.json(IXIC, ES, NQ 포함)을 받아도 없어진 지표 타일이 생기지 않는다.
 */
fun builtInQuotes(quotes: List<Quote>): List<Quote> = quotes.filter { it.id in BUILT_IN_NAMES }

/**
 * 모든 지표 id의 보일 순서(꺼진 것 포함). order에 있는 id를 그 순서로 두고, 없는 id는
 * 기본 지표(수집기 순서) → 추가 종목(추가 순서)으로 뒤에 붙인다. order의 모르는 id는 무시한다.
 */
fun orderedIds(builtInIds: List<String>, settings: WidgetSettings): List<String> {
    val all = (builtInIds + settings.custom.map { it.id }).distinct()
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
    val byId = builtIn.associateBy { it.id } + settings.custom.associate { ticker ->
        ticker.id to (cached[ticker.id] ?: Quote(ticker.id, ticker.name, price = null, change = null, change_pct = null))
    }
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
