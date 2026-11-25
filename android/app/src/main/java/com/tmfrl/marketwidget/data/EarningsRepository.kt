package com.tmfrl.marketwidget.data

import android.content.Context
import com.tmfrl.marketwidget.model.EarningsCache
import com.tmfrl.marketwidget.model.EarningsEntry
import com.tmfrl.marketwidget.model.WidgetSettings
import com.tmfrl.marketwidget.model.earningsDue
import com.tmfrl.marketwidget.model.earningsTickers
import com.tmfrl.marketwidget.model.encodeEarningsCache
import com.tmfrl.marketwidget.model.parseEarnings
import com.tmfrl.marketwidget.model.parseEarningsCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/**
 * 실적 일정(v1.9). 설정의 실적 일정 목록(기본 + 사용자가 더한 종목)의 다음 발표일을 Yahoo에서 받아 earnings.json에 캐시한다.
 * 차트 종목(custom.json)과 따로다. 종목마다 12시간마다 받고, 받지 못하면 이전 값을 두며, 목록에 없는 종목은 지운다.
 */
class EarningsRepository(
    private val cacheFile: File,
    private val get: (String) -> String = YahooSession::get,
) {
    fun entries(): List<EarningsEntry> = try {
        if (cacheFile.exists()) parseEarningsCache(cacheFile.readText()).entries else emptyList()
    } catch (e: IOException) {
        emptyList()
    }

    fun lastModified(): Long = cacheFile.lastModified()

    suspend fun refresh(settings: WidgetSettings, nowMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        lock.withLock {
            val old = entries().associateBy { it.symbol }
            // 한 종목이 실패해도 나머지는 이어서 받는다. Yahoo 세션은 한 번에 하나씩 쓴다.
            val updated = earningsTickers(settings).map { ticker ->
                val previous = old[ticker.symbol]
                if (previous != null && !earningsDue(previous.at, nowMillis)) return@map previous
                val text = orElse<String?>(null) { get(EARNINGS_URL.format(URLEncoder.encode(ticker.symbol, "UTF-8"))) }
                when (text) {
                    null -> (previous ?: EarningsEntry(ticker.symbol)).copy(failed = true)
                    else -> EarningsEntry(ticker.symbol, orElse(previous?.earnings) { parseEarnings(text) }, nowMillis, failed = false)
                }
            }
            writeAtomic(cacheFile, encodeEarningsCache(EarningsCache(updated)))
        }
    }

    companion object {
        private val lock = Mutex()

        fun forContext(context: Context) = EarningsRepository(File(context.filesDir, "earnings.json"))
    }
}
