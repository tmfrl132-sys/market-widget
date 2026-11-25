package com.tmfrl.marketwidget.data

import android.content.Context
import com.tmfrl.marketwidget.model.CustomCache
import com.tmfrl.marketwidget.model.CustomEntry
import com.tmfrl.marketwidget.model.CustomTicker
import com.tmfrl.marketwidget.model.IntradaySeries
import com.tmfrl.marketwidget.model.KIND_EQUITY
import com.tmfrl.marketwidget.model.earningsDue
import com.tmfrl.marketwidget.model.parseEarnings
import com.tmfrl.marketwidget.model.LiveQuote
import com.tmfrl.marketwidget.model.MaxFields
import com.tmfrl.marketwidget.model.Quote
import com.tmfrl.marketwidget.model.Series
import com.tmfrl.marketwidget.model.WidgetSettings
import com.tmfrl.marketwidget.model.assembleCustomQuote
import com.tmfrl.marketwidget.model.chartCloses
import com.tmfrl.marketwidget.model.chartDates
import com.tmfrl.marketwidget.model.chartOhlc
import com.tmfrl.marketwidget.model.chartVolumes
import com.tmfrl.marketwidget.model.encodeCustomCache
import com.tmfrl.marketwidget.model.hasDrawdown
import com.tmfrl.marketwidget.model.historyDue
import com.tmfrl.marketwidget.model.maxDue
import com.tmfrl.marketwidget.model.maxFields
import com.tmfrl.marketwidget.model.naverStockQuote
import com.tmfrl.marketwidget.model.naverStockWindow
import com.tmfrl.marketwidget.model.nxtStockQuote
import com.tmfrl.marketwidget.model.parseCustomCache
import com.tmfrl.marketwidget.model.parseIntraday
import com.tmfrl.marketwidget.model.parseLiveQuote
import com.tmfrl.marketwidget.model.parseYahooChart
import com.tmfrl.marketwidget.model.withIntraday
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.time.ZonedDateTime

private const val YAHOO_1Y_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=1y&interval=1d"
private const val YAHOO_5Y_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=5y&interval=1wk"

/** 수집기 MAX_URL과 같다. range=max는 간격을 멋대로 늘려서 날짜를 지정해 일봉을 받는다. */
private const val YAHOO_MAX_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s?period1=0&period2=%d&interval=1d"
private const val NAVER_STOCK_URL = "https://m.stock.naver.com/api/stock/%s/basic"
private const val NAVER_STOCK_MINUTE_URL = "https://api.stock.naver.com/chart/domestic/item/%s/minute"

/** 넥스트레이드(NXT) 1분봉 08:00~20:00. 네이버 차트가 NXT 종목에 쓰는 주소다. NXT 비거래 종목은 빈 목록이다. */
private const val NAVER_NXT_MINUTE_URL = "https://api.stock.naver.com/chart/domestic/NXT/item/%s/minute"

/** 기본 받기. 테스트가 바꾼 get과 구별하려고 이름 있는 객체로 둔다. */
private object DefaultGet : (String) -> String {
    override fun invoke(url: String): String = httpGet(url)
}

/** 한 번에 받는 추가 종목 수. 한꺼번에 수십 요청을 보내 Yahoo가 막는 일을 줄인다. */
private const val PARALLEL_TICKERS = 4

/**
 * 추가 종목 시세와 기록을 기기에서 직접 받아 custom.json에 캐시한다. 수집기는 사용자 목록을 모른다.
 * 시세는 갱신마다, 1년·5년은 6시간마다, 전체 기간은 7일마다 받고, 받지 못한 부분은 직전 값을 쓴다.
 */
class CustomRepository(
    private val cacheFile: File,
    private val get: (String) -> String = DefaultGet,
) {
    /** 실적 일정(v1.9)은 쿠키·crumb가 필요해 YahooSession으로 받는다. 테스트가 get을 바꾸면 그것을 쓴다. */
    private val getEarnings: (String) -> String = if (get === DefaultGet) YahooSession::get else get


    fun entries(): List<CustomEntry> = try {
        if (cacheFile.exists()) parseCustomCache(cacheFile.readText()).entries else emptyList()
    } catch (e: IOException) {
        emptyList()
    }

    /** 캐시를 마지막으로 쓴 시각. 설정 화면이 바뀌었을 때만 다시 읽으려고 쓴다. 없으면 0. */
    fun lastModified(): Long = cacheFile.lastModified()

    /** 위젯이 그릴 추가 종목 Quote. */
    fun cached(): List<Quote> = entries().map { it.quote }

    /** 켜진 추가 종목을 받고 캐시를 고친다. 꺼진 종목은 받지 않고 직전 값을 두며, 설정에 없는 종목은 지운다. */
    suspend fun refresh(settings: WidgetSettings, now: ZonedDateTime = ZonedDateTime.now()) = withContext(Dispatchers.IO) {
        lock.withLock {
            val old = entries().associateBy { it.id }
            val hidden = settings.hidden.toSet()
            val permits = Semaphore(PARALLEL_TICKERS)
            val entries = coroutineScope {
                settings.custom.map { ticker ->
                    async {
                        if (ticker.id in hidden) {
                            old[ticker.id]
                        } else {
                            permits.withPermit { fetchTicker(ticker, old[ticker.id], now, settings.nxt_hours) }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            writeAtomic(cacheFile, encodeCustomCache(CustomCache(entries)))
        }
    }

    /** nxt가 true면 한국 개별 종목에 넥스트레이드(NXT) 프리·애프터마켓을 넣는다(설정 "국내주식 NXT 시간 포함"). */
    private fun fetchTicker(ticker: CustomTicker, previous: CustomEntry?, now: ZonedDateTime, nxt: Boolean = false): CustomEntry {
        val nowMillis = now.toInstant().toEpochMilli()
        val updatedAt = now.toOffsetDateTime().toString()
        return try {
            val encoded = URLEncoder.encode(ticker.symbol, "UTF-8")
            var marketTz: String? = null
            val naver = ticker.naver_code?.let { code -> naverQuote(code, now, nxt) }
            val live: LiveQuote? = naver ?: orElse(null) {
                val text = get(YAHOO_URL.format(encoded))
                marketTz = parseYahooChart(text)?.meta?.exchangeTimezoneName
                // 5분봉이 실패해도 시세는 살린다
                parseLiveQuote(text)?.withIntraday(orElse(IntradaySeries()) { parseIntraday(get(INTRADAY_URL.format(encoded))) })
            }
            if (live == null) return failed(ticker, previous, updatedAt)
            // 7일 차트 봉. 한국 종목도 Yahoo 심볼(005930.KS)로 받는다. 실패하면 이전 값을 둔다.
            val withWeek = live.copy(week = fetchWeek(encoded, now.toEpochSecond(), get))

            val due = historyDue(previous?.history_at, nowMillis)
            val daily = if (due) series(YAHOO_1Y_URL.format(encoded)) { marketTz = marketTz ?: it } else null
            val weekly = if (due) series(YAHOO_5Y_URL.format(encoded)) {} else null
            val max: MaxFields? = if (maxDue(previous?.max_at, nowMillis)) {
                orElse(null) {
                    parseYahooChart(get(YAHOO_MAX_URL.format(encoded, now.toEpochSecond())))
                        ?.let { maxFields(it, hasDrawdown(ticker.symbol)) }
                }
            } else {
                null
            }
            // 실적 일정(v1.9): 개별 주식만 12시간마다. 받지 못하면 이전 값을 두고, 받았는데 날짜가 없으면 비운다.
            val earningsText = if (ticker.kind == KIND_EQUITY && earningsDue(previous?.earnings_at, nowMillis)) {
                orElse<String?>(null) { getEarnings(EARNINGS_URL.format(encoded)) }
            } else {
                null
            }
            val earnings = earningsText?.let { text -> orElse(previous?.earnings) { parseEarnings(text) } }
            CustomEntry(
                id = ticker.id,
                quote = assembleCustomQuote(ticker, previous?.quote, withWeek, daily, weekly, max, marketTz, updatedAt),
                history_at = if (daily != null && weekly != null) nowMillis else previous?.history_at,
                max_at = if (max != null) nowMillis else previous?.max_at,
                last_ok_at = nowMillis,
                last_failed = false,
                earnings = if (earningsText != null) earnings else previous?.earnings,
                earnings_at = if (earningsText != null) nowMillis else previous?.earnings_at,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 응답이 뜻밖의 모양이어도 다른 종목과 기본 지표 갱신은 이어간다
            failed(ticker, previous, updatedAt)
        }
    }

    /**
     * 네이버 한국 개별 종목 시세. 실패하거나 쓸 시간이 아니면 null(Yahoo로 받는다).
     * NXT를 켜면 시간과 상관없이 NXT 분봉까지 받아 nxtStockQuote로 잇는다. NXT 분봉을 못 받으면 빈 목록으로 보고
     * 꺼진 것과 같은 결과가 된다. 끄면 지금까지처럼 서울 평일 09:00~16:00에만 받는다.
     */
    private fun naverQuote(code: String, now: ZonedDateTime, nxt: Boolean): LiveQuote? = when {
        nxt -> orElse(null) {
            val basic = get(NAVER_STOCK_URL.format(code))
            val krxMinutes = get(NAVER_STOCK_MINUTE_URL.format(code))
            val nxtMinutes = orElse("[]") { get(NAVER_NXT_MINUTE_URL.format(code)) }
            nxtStockQuote(basic, krxMinutes, nxtMinutes, now)
        }
        naverStockWindow(now) -> orElse(null) {
            naverStockQuote(get(NAVER_STOCK_URL.format(code)), get(NAVER_STOCK_MINUTE_URL.format(code)), now)
        }
        else -> null
    }

    /** 1년 일봉 또는 5년 주봉. 실패하거나 결과가 없으면 null(직전 값을 쓴다). zone은 응답의 시장 시간대를 받는다. */
    private fun series(url: String, zone: (String) -> Unit): Series? = orElse(null) {
        parseYahooChart(get(url))?.let { result ->
            result.meta.exchangeTimezoneName?.let(zone)
            val (open, high, low) = chartOhlc(result)
            Series(chartCloses(result), chartDates(result), open, high, low, chartVolumes(result))
        }
    }

    private fun failed(ticker: CustomTicker, previous: CustomEntry?, updatedAt: String) = CustomEntry(
        id = ticker.id,
        quote = assembleCustomQuote(ticker, previous?.quote, null, null, null, null, null, updatedAt),
        history_at = previous?.history_at,
        max_at = previous?.max_at,
        last_ok_at = previous?.last_ok_at,
        last_failed = true,
        earnings = previous?.earnings,
        earnings_at = previous?.earnings_at,
    )

    companion object {
        private val lock = Mutex()

        fun forContext(context: Context) = CustomRepository(File(context.filesDir, "custom.json"))

        /** 추가 버튼을 누를 때 range=5d 응답. 메인 스레드에서 부르지 않는다. */
        fun fetchCheck(symbol: String): String = httpGet(YAHOO_URL.format(URLEncoder.encode(symbol, "UTF-8")))
    }
}
