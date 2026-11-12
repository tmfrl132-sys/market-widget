package com.tmfrl.marketwidget.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 검색 후보. symbol은 Yahoo 심볼, market은 화면에 쓰는 시장 이름(코스피, 나스닥, 지수 등),
 * naverCode는 한국 주식·ETF의 6자리 코드(나머지는 null)다.
 */
data class TickerCandidate(val symbol: String, val name: String, val market: String, val naverCode: String?) {
    /** 화면에 쓰는 코드. 한국 종목은 6자리 코드, 나머지는 Yahoo 심볼. */
    val displayCode: String get() = koreanCode(symbol) ?: symbol
}

/** 검색 결과 최대 개수. */
const val MAX_CANDIDATES = 15

private val json = Json { ignoreUnknownKeys = true }

/** 한글(음절·자모)이 있는지. 있으면 네이버만 검색한다(Yahoo는 한글 검색어에 400을 낸다). */
fun hasHangul(query: String): Boolean = query.any { it in '가'..'힣' || it in 'ㄱ'..'ㆎ' }

/** 자동 검색을 할 만한 검색어인지. 한글은 2글자 이상, 숫자·영문은 1글자 이상. */
fun searchReady(query: String): Boolean {
    val text = query.trim()
    return if (hasHangul(text)) text.length >= 2 else text.isNotEmpty()
}

private val KOREAN_SUFFIX = Regex("^([0-9A-Z]{6})\\.(KS|KQ)$")

/** 한국 종목 심볼(005930.KS)의 6자리 코드. 아니면 null. */
fun koreanCode(symbol: String): String? = KOREAN_SUFFIX.find(symbol)?.groupValues?.get(1)

/** 추가할 종목의 네이버 코드. 한국 주식·ETF만 6자리 코드이고 나머지는 null. */
fun naverCodeFor(symbol: String, kind: String): String? =
    if (kind == KIND_EQUITY || kind == KIND_ETF) koreanCode(symbol) else null

@Serializable
private data class NaverSearch(val items: List<NaverItem> = emptyList())

@Serializable
private data class NaverItem(
    val code: String? = null,
    val name: String? = null,
    val typeCode: String? = null,
    val nationCode: String? = null,
    val category: String? = null,
)

/** 네이버 국내 지수 코드 → Yahoo 심볼. 표에 없는 국내 지수는 버린다. */
private val NAVER_INDEXES = mapOf("KOSPI" to "^KS11", "KOSDAQ" to "^KQ11", "KPI200" to "^KS200")

private val US_MARKETS = mapOf("NASDAQ" to "나스닥", "NYSE" to "뉴욕", "AMEX" to "아멕스")

private const val MARKET_INDEX = "지수"

private fun naverCandidate(item: NaverItem): TickerCandidate? {
    val code = item.code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val name = item.name?.takeIf { it.isNotBlank() } ?: code
    return when {
        item.category == "stock" && item.nationCode == "KOR" && item.typeCode == "KOSPI" ->
            TickerCandidate("$code.KS", name, "코스피", code)
        item.category == "stock" && item.nationCode == "KOR" && item.typeCode == "KOSDAQ" ->
            TickerCandidate("$code.KQ", name, "코스닥", code)
        item.category == "stock" && item.nationCode == "USA" && item.typeCode in US_MARKETS ->
            TickerCandidate(code.replace(' ', '-').replace('.', '-'), name, US_MARKETS.getValue(item.typeCode!!), null)
        item.category == "index" && item.typeCode == "WORLD_INDEX" -> TickerCandidate("^$code", name, MARKET_INDEX, null)
        item.category == "index" && item.typeCode == "INDEX" ->
            NAVER_INDEXES[code]?.let { TickerCandidate(it, name, MARKET_INDEX, null) }
        else -> null
    }
}

/** 네이버 자동완성 응답(ac.stock.naver.com/ac)을 Yahoo 심볼 후보로 바꾼다. 지원하지 않는 종류는 버린다. */
fun parseNaverSearch(text: String): List<TickerCandidate> =
    json.decodeFromString<NaverSearch>(text).items.mapNotNull(::naverCandidate)

@Serializable
private data class YahooSearch(val quotes: List<YahooSearchQuote> = emptyList())

@Serializable
private data class YahooSearchQuote(
    val symbol: String? = null,
    val quoteType: String? = null,
    val exchange: String? = null,
    val exchDisp: String? = null,
    val shortname: String? = null,
    val longname: String? = null,
)

private fun yahooCandidate(quote: YahooSearchQuote): TickerCandidate? {
    val symbol = quote.symbol?.takeIf { it.isNotEmpty() } ?: return null
    val name = quote.shortname?.takeIf { it.isNotBlank() } ?: quote.longname?.takeIf { it.isNotBlank() } ?: symbol
    val stockOrEtf = quote.quoteType == KIND_EQUITY || quote.quoteType == KIND_ETF
    return when {
        stockOrEtf && symbol.endsWith(".KS") -> TickerCandidate(symbol, name, "코스피", koreanCode(symbol))
        stockOrEtf && symbol.endsWith(".KQ") -> TickerCandidate(symbol, name, "코스닥", koreanCode(symbol))
        stockOrEtf && '.' !in symbol && quote.exchange != "PNK" ->
            TickerCandidate(symbol, name, quote.exchDisp ?: quote.exchange.orEmpty(), null)
        quote.quoteType == KIND_INDEX && symbol.startsWith("^") -> TickerCandidate(symbol, name, MARKET_INDEX, null)
        quote.quoteType == KIND_CRYPTO && symbol.endsWith("-USD") -> TickerCandidate(symbol, name, "암호화폐", null)
        else -> null
    }
}

/** Yahoo 검색 응답(v1/finance/search)을 후보로 바꾼다. 선물·환율·펀드·해외 중복 상장·장외는 버린다. */
fun parseYahooSearch(text: String): List<TickerCandidate> =
    json.decodeFromString<YahooSearch>(text).quotes.mapNotNull(::yahooCandidate)

/** 네이버 결과를 먼저 두고 같은 심볼은 앞의 것만 남긴다. 최대 15개. */
fun mergeCandidates(naver: List<TickerCandidate>, yahoo: List<TickerCandidate>): List<TickerCandidate> =
    (naver + yahoo).distinctBy { it.symbol }.take(MAX_CANDIDATES)

/** 검색 결과 위의 안내 문구. 없으면 null. naver·yahoo는 그 경로의 결과이고 실패했으면 null이다(부르지 않은 경로도 null). */
fun searchMessage(query: String, naver: List<TickerCandidate>?, yahoo: List<TickerCandidate>?): String? {
    val hangul = hasHangul(query)
    return when {
        hangul && naver == null -> "한글 검색을 할 수 없습니다. 영문 이름이나 종목코드(예: 005930, AAPL)로 검색해 보세요"
        !hangul && naver == null && yahoo == null -> "검색할 수 없습니다. 인터넷 연결을 확인하고 잠시 뒤 다시 시도하세요"
        mergeCandidates(naver.orEmpty(), yahoo.orEmpty()).isNotEmpty() -> null
        hangul -> "찾지 못했습니다. 암호화폐는 영문(예: ETH)으로 검색하세요"
        else -> "찾지 못했습니다"
    }
}

/** 추가 확인 결과. kind는 Yahoo instrumentType, marketTz는 exchangeTimezoneName(없으면 null). */
data class TickerCheck(val kind: String, val marketTz: String?)

/**
 * 추가 버튼을 누를 때 range=5d 응답으로 시세를 받을 수 있는지 확인한다.
 * 결과가 있고, 현재값이 있고, 비어 있지 않은 종가가 2개 이상이고, 종류가 주식·ETF·지수·암호화폐면 성공.
 */
fun verifyTicker(chartText: String): TickerCheck? {
    val result = parseYahooChart(chartText) ?: return null
    val kind = result.meta.instrumentType?.takeIf { it in SUPPORTED_KINDS } ?: return null
    if (result.meta.regularMarketPrice == null) return null
    if (chartCloses(result).size < 2) return null
    return TickerCheck(kind, result.meta.exchangeTimezoneName)
}

/** 이름 뒤 목적격 조사(을/를)를 붙인다. 받침 있는 한글로 끝나면 "을", 그 밖(받침 없음, 영문, 숫자)은 "를". 예: 삼성전자를, 애플을 */
fun withObjectParticle(name: String): String {
    val last = name.trimEnd().lastOrNull() ?: return name
    val hasFinal = last in '가'..'힣' && (last - '가') % 28 != 0
    return name + if (hasFinal) "을" else "를"
}
