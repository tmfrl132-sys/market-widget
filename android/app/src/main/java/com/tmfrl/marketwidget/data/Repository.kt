package com.tmfrl.marketwidget.data

import android.content.Context
import com.tmfrl.marketwidget.model.MarketData
import com.tmfrl.marketwidget.model.parseMarketData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

const val DATA_URL = "https://tmfrl132-sys.github.io/market-widget/data.json"
private const val TIMEOUT_MS = 10_000

/** data.json을 받아 기기에 캐시한다. 받기에 실패하면 마지막으로 받은 값을 쓴다. */
class Repository(private val cacheFile: File, private val fetch: () -> String) {

    suspend fun refresh(): MarketData? = withContext(Dispatchers.IO) {
        try {
            val text = fetch()
            val data = parseMarketData(text)
            cacheFile.writeText(text)
            data
        } catch (e: IOException) {
            cached()
        } catch (e: IllegalArgumentException) { // 잘못된 JSON (SerializationException 포함)
            cached()
        }
    }

    fun cached(): MarketData? = try {
        if (cacheFile.exists()) parseMarketData(cacheFile.readText()) else null
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        fun forContext(context: Context) =
            Repository(File(context.filesDir, "data.json"), ::httpGet)

        private fun httpGet(): String {
            val conn = URL(DATA_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.useCaches = false
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
