package com.tmfrl.marketwidget.data

import android.content.Context
import com.tmfrl.marketwidget.model.LIVE_SYMBOLS
import com.tmfrl.marketwidget.model.LiveQuote
import com.tmfrl.marketwidget.model.MarketData
import com.tmfrl.marketwidget.model.applyLiveQuotes
import com.tmfrl.marketwidget.model.encodeMarketData
import com.tmfrl.marketwidget.model.parseLiveQuote
import com.tmfrl.marketwidget.model.parseMarketData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime

const val DATA_URL = "https://tmfrl132-sys.github.io/market-widget/data.json"
private const val YAHOO_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=5d&interval=1d"
private const val TIMEOUT_MS = 10_000

/**
 * data.json을 받고, 시세는 Yahoo에서 직접 받은 값으로 덮어써 기기에 캐시한다.
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
        if (data != null) cacheFile.writeText(encodeMarketData(data))
        data
    }

    fun cached(): MarketData? = try {
        if (cacheFile.exists()) parseMarketData(cacheFile.readText()) else null
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        fun forContext(context: Context) =
            Repository(File(context.filesDir, "data.json"), ::fetchLiveQuotes) { httpGet(DATA_URL) }

        /** 10개를 한꺼번에 받는다. 실패한 항목은 결과에서 빠진다. */
        private suspend fun fetchLiveQuotes(): Map<String, LiveQuote> = coroutineScope {
            LIVE_SYMBOLS.map { (id, symbol) ->
                async(Dispatchers.IO) {
                    try {
                        val url = YAHOO_URL.format(URLEncoder.encode(symbol, "UTF-8"))
                        parseLiveQuote(httpGet(url))?.let { id to it }
                    } catch (e: IOException) {
                        null
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }

        private fun httpGet(url: String): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.useCaches = false
                // Yahoo는 브라우저 이름이 없는 요청을 막는다
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP ${conn.responseCode}")
                }
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }
}
