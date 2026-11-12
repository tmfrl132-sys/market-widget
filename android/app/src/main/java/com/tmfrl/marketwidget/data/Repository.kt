package com.tmfrl.marketwidget.data

import android.content.Context
import com.tmfrl.marketwidget.model.IntradaySeries
import com.tmfrl.marketwidget.model.LIVE_SYMBOLS
import com.tmfrl.marketwidget.model.LiveQuote
import com.tmfrl.marketwidget.model.MarketData
import com.tmfrl.marketwidget.model.NAVER_CODES
import com.tmfrl.marketwidget.model.applyLiveQuotes
import com.tmfrl.marketwidget.model.encodeMarketData
import com.tmfrl.marketwidget.model.parseIntraday
import com.tmfrl.marketwidget.model.parseLiveQuote
import com.tmfrl.marketwidget.model.parseNaverIndex
import com.tmfrl.marketwidget.model.parseNaverMinutes
import com.tmfrl.marketwidget.model.parseMarketData
import com.tmfrl.marketwidget.model.withIntraday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.time.OffsetDateTime

const val DATA_URL = "https://tmfrl132-sys.github.io/market-widget/data.json"
internal const val YAHOO_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=5d&interval=1d"
internal const val INTRADAY_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=1d&interval=5m"
private const val NAVER_INDEX_URL = "https://m.stock.naver.com/api/index/%s/basic"
private const val NAVER_MINUTE_URL = "https://api.stock.naver.com/chart/domestic/index/%s/minute"

/**
 * data.json을 받고, 시세는 국내 지수는 네이버, 나머지는 Yahoo에서 직접 받은 값으로 덮어써 기기에 캐시한다.
 * data.json을 받지 못하면 캐시에 시세만 덮어쓰고, 둘 다 실패하면 마지막으로 받은 값을 쓴다.
 */
class Repository(
    private val cacheFile: File,
    private val fetchLive: suspend () -> Map<String, LiveQuote> = { emptyMap() },
    private val now: () -> String = { OffsetDateTime.now().toString() },
    private val fetch: () -> String,
) {

    suspend fun refresh(): MarketData? = withContext(Dispatchers.IO) {
        val base = try {
            parseMarketData(fetch())
        } catch (e: IOException) {
            cached()
        } catch (e: IllegalArgumentException) { // 잘못된 JSON (SerializationException 포함)
            cached()
        }
        val data = base?.let { applyLiveQuotes(it, fetchLive(), now()) }
        if (data != null) writeAtomic(cacheFile, encodeMarketData(data))
        data
    }

    /** 마지막으로 새로 받은 시각. 받은 적이 없으면 0. */
    fun lastRefreshMillis(): Long = cacheFile.lastModified()

    fun cached(): MarketData? = try {
        if (cacheFile.exists()) parseMarketData(cacheFile.readText()) else null
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        fun forContext(context: Context) =
            Repository(File(context.filesDir, "data.json"), ::fetchLiveQuotes) { httpGet(DATA_URL) }

        /** LIVE_SYMBOLS 13개를 한꺼번에 받는다. 실패한 항목은 결과에서 빠진다. */
        private suspend fun fetchLiveQuotes(): Map<String, LiveQuote> = coroutineScope {
            LIVE_SYMBOLS.map { (id, symbol) ->
                async(Dispatchers.IO) {
                    val live = NAVER_CODES[id]?.let(::fetchNaver) ?: fetchYahoo(symbol)
                    live?.let { id to it }
                }
            }.awaitAll().filterNotNull().toMap()
        }

        private fun fetchNaver(code: String): LiveQuote? = orElse(null) {
            parseNaverIndex(httpGet(NAVER_INDEX_URL.format(code)))?.withIntraday(
                // 분봉이 실패해도 시세는 살린다
                orElse(IntradaySeries()) { parseNaverMinutes(httpGet(NAVER_MINUTE_URL.format(code))) },
            )
        }

        private fun fetchYahoo(symbol: String): LiveQuote? = orElse(null) {
            val encoded = URLEncoder.encode(symbol, "UTF-8")
            parseLiveQuote(httpGet(YAHOO_URL.format(encoded)))?.withIntraday(
                // 5분봉이 실패해도 시세는 살린다
                orElse(IntradaySeries()) { parseIntraday(httpGet(INTRADAY_URL.format(encoded))) },
            )
        }
    }
}
