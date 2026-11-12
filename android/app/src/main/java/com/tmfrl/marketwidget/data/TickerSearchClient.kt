package com.tmfrl.marketwidget.data

import com.tmfrl.marketwidget.model.TickerCandidate
import com.tmfrl.marketwidget.model.TickerCheck
import com.tmfrl.marketwidget.model.hasHangul
import com.tmfrl.marketwidget.model.parseNaverSearch
import com.tmfrl.marketwidget.model.parseYahooSearch
import com.tmfrl.marketwidget.model.verifyTicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder

private const val NAVER_SEARCH_URL = "https://ac.stock.naver.com/ac?q=%s&target=stock,ipo,index,marketindicator"
private const val YAHOO_SEARCH_URL =
    "https://query2.finance.yahoo.com/v1/finance/search?q=%s&quotesCount=10&newsCount=0&listsCount=0&enableFuzzyQuery=false"

/** 검색 결과. 실패했거나 부르지 않은 경로는 null이다(searchMessage가 안내 문구를 고른다). */
data class SearchResult(val naver: List<TickerCandidate>?, val yahoo: List<TickerCandidate>?)

/** 설정 화면의 종목 검색과 추가 확인. 모두 IO 스레드에서 부른다. 인증키가 필요 없는 네이버·Yahoo만 쓴다. */
object TickerSearchClient {

    /** 한글이 있으면 네이버만, 없으면 네이버와 Yahoo를 동시에 부른다. */
    suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        val text = query.trim()
        val encoded = URLEncoder.encode(text, "UTF-8")
        coroutineScope {
            val naver = async { attempt { parseNaverSearch(httpGet(NAVER_SEARCH_URL.format(encoded))) } }
            val yahoo = if (hasHangul(text)) null else async { attempt { parseYahooSearch(httpGet(YAHOO_SEARCH_URL.format(encoded))) } }
            SearchResult(naver.await(), yahoo?.await())
        }
    }

    /** 추가 버튼: range=5d 응답으로 시세를 받을 수 있는지 확인한다. 받을 수 없으면 null. */
    suspend fun verify(symbol: String): TickerCheck? = withContext(Dispatchers.IO) {
        attempt { verifyTicker(CustomRepository.fetchCheck(symbol)) }
    }

    /** 네트워크 오류나 응답 형식이 바뀌어 해석이 실패하면 그 경로 실패(null)로 본다. */
    private inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
