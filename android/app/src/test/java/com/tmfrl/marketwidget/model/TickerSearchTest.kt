package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 설계서 3.2절 규칙표. 자료는 2026-11-12 실측 응답을 줄인 것이다. */
class TickerSearchTest {
    private fun resource(name: String) = TickerSearchTest::class.java.classLoader!!.getResource(name)!!.readText()

    private fun naverItem(code: String, type: String, category: String, nation: String? = null, name: String = code): String {
        val nationField = if (nation == null) "null" else "\"$nation\""
        return """{"query": "x", "items": [{"code": "$code", "name": "$name", "typeCode": "$type",
            "nationCode": $nationField, "category": "$category"}]}"""
    }

    @Test fun hangul_detection() {
        assertTrue(hasHangul("삼성전자"))
        assertTrue(hasHangul("ㅅ"))
        assertFalse(hasHangul("AAPL"))
        assertFalse(hasHangul("005930"))
    }

    @Test fun searchReady_lengthRules() {
        assertFalse(searchReady("삼"))
        assertTrue(searchReady("삼성"))
        assertTrue(searchReady("A"))
        assertTrue(searchReady("5"))
        assertFalse(searchReady("  "))
    }

    @Test fun naver_samsungFirstIsKospi() {
        val first = parseNaverSearch(resource("naver_ac_samsung_kr.json")).first()
        assertEquals(TickerCandidate("005930.KS", "삼성전자", "코스피", "005930"), first)
        assertEquals("005930", first.displayCode)
    }

    @Test fun naver_alphanumericKospiCode() {
        assertEquals("0162Z0.KS", parseNaverSearch(resource("naver_ac_samsung_kr.json"))[1].symbol)
    }

    @Test fun naver_kosdaq() {
        assertEquals(TickerCandidate("293490.KQ", "카카오게임즈", "코스닥", "293490"), parseNaverSearch(resource("naver_ac_293490.json")).single())
    }

    @Test fun naver_usStocks() {
        assertEquals(listOf("BRK-B", "BRK-A"), parseNaverSearch(resource("naver_ac_BRK.json")).map { it.symbol })
        assertEquals(TickerCandidate("AAPL", "애플", "나스닥", null), parseNaverSearch(resource("naver_ac_AAPL.json")).single())
        // 일본(TOKYO) 종목은 버린다
        assertEquals(listOf("AAPL", "AFL", "APLE"), parseNaverSearch(resource("naver_ac_apple_kr.json")).map { it.symbol })
        assertEquals("뉴욕", parseNaverSearch(resource("naver_ac_apple_kr.json"))[1].market)
    }

    @Test fun naver_indexesAndDroppedFutures() {
        assertEquals(listOf(TickerCandidate("^N225", "니케이 225", "지수", null)), parseNaverSearch(resource("naver_ac_nikkei_kr.json")))
        assertEquals(listOf("^KS200"), parseNaverSearch(resource("naver_ac_kospi200_kr.json")).map { it.symbol })
        assertEquals("^KS11", parseNaverSearch(naverItem("KOSPI", "INDEX", "index")).single().symbol)
        assertEquals(emptyList<TickerCandidate>(), parseNaverSearch(naverItem("FUT", "FUTURES", "index")))
        assertEquals(emptyList<TickerCandidate>(), parseNaverSearch(naverItem("KRX100", "INDEX", "index")))
        assertEquals(emptyList<TickerCandidate>(), parseNaverSearch(naverItem("6273", "TOKYO", "stock", "JPN")))
        assertEquals(emptyList<TickerCandidate>(), parseNaverSearch(naverItem("123456", "KONEX", "stock", "KOR")))
    }

    @Test fun naver_emptyAndUnknownFields() {
        assertEquals(emptyList<TickerCandidate>(), parseNaverSearch("""{"query": "x", "items": []}"""))
        assertEquals(emptyList<TickerCandidate>(), parseNaverSearch("""{"query": "x"}"""))
    }

    @Test fun yahoo_aaplKeepsUsListings() {
        val symbols = parseYahooSearch(resource("yahoo_search_AAPL.json")).map { it.symbol }
        assertEquals(listOf("AAPL", "AAPU", "AAPD", "APLY", "AAPW", "AAPE"), symbols)
        val aapl = parseYahooSearch(resource("yahoo_search_AAPL.json")).first()
        assertEquals(TickerCandidate("AAPL", "Apple Inc.", "NASDAQ", null), aapl)
    }

    @Test fun yahoo_samsungDropsOtcAndForeign() {
        val symbols = parseYahooSearch(resource("yahoo_search_samsung.json")).map { it.symbol }
        assertEquals(listOf("005930.KS", "005935.KS", "SMNG", "009150.KS"), symbols)
        assertEquals("코스피", parseYahooSearch(resource("yahoo_search_samsung.json")).first().market)
        assertEquals("005930", parseYahooSearch(resource("yahoo_search_samsung.json")).first().naverCode)
    }

    @Test fun yahoo_nikkeiKeepsIndexOnly() {
        assertEquals(listOf("^N225"), parseYahooSearch(resource("yahoo_search_nikkei.json")).map { it.symbol })
    }

    @Test fun yahoo_ethKeepsCryptoOnly() {
        val candidates = parseYahooSearch(resource("yahoo_search_ETH.json"))
        assertEquals("ETH-USD", candidates.first().symbol)
        assertEquals("암호화폐", candidates.first().market)
        assertTrue(candidates.none { it.symbol.endsWith("=F") })
    }

    @Test fun merge_naverFirstAndUnique() {
        val merged = mergeCandidates(parseNaverSearch(resource("naver_ac_AAPL.json")), parseYahooSearch(resource("yahoo_search_AAPL.json")))
        assertEquals("AAPL", merged.first().symbol)
        assertEquals("애플", merged.first().name)
        assertEquals(1, merged.count { it.symbol == "AAPL" })
        assertEquals(6, merged.size)
    }

    @Test fun merge_limitFifteen() {
        val many = (1..20).map { TickerCandidate("S$it", "S$it", "NASDAQ", null) }
        assertEquals(15, mergeCandidates(many, emptyList()).size)
    }

    @Test fun messages_followFailureTable() {
        val one = listOf(TickerCandidate("AAPL", "애플", "나스닥", null))
        assertEquals("한글 검색을 할 수 없습니다. 영문 이름이나 종목코드(예: 005930, AAPL)로 검색해 보세요", searchMessage("삼성", null, null))
        assertNull(searchMessage("AAPL", null, one))
        assertNull(searchMessage("AAPL", one, null))
        assertEquals("검색할 수 없습니다. 인터넷 연결을 확인하고 잠시 뒤 다시 시도하세요", searchMessage("AAPL", null, null))
        assertEquals("찾지 못했습니다. 암호화폐는 영문(예: ETH)으로 검색하세요", searchMessage("이더리움", emptyList(), null))
        assertEquals("찾지 못했습니다", searchMessage("ZZZZ", emptyList(), emptyList()))
        assertNull(searchMessage("삼성", one, null))
    }

    @Test fun naverCode_onlyKoreanStocksAndEtfs() {
        assertEquals("005930", naverCodeFor("005930.KS", KIND_EQUITY))
        assertEquals("0162Z0", naverCodeFor("0162Z0.KS", KIND_ETF))
        assertEquals("293490", naverCodeFor("293490.KQ", KIND_EQUITY))
        assertNull(naverCodeFor("^KS200", KIND_INDEX))
        assertNull(naverCodeFor("AAPL", KIND_EQUITY))
    }

    @Test fun objectParticle() {
        assertEquals("삼성전자를", withObjectParticle("삼성전자"))
        assertEquals("애플을", withObjectParticle("애플"))
        assertEquals("Apple Inc.를", withObjectParticle("Apple Inc."))
        assertEquals("", withObjectParticle(""))
    }

    private fun check(type: String?, price: String = "100.0", closes: String = "[99.0, 100.0]"): String {
        val kind = if (type == null) "" else ""","instrumentType": "$type""""
        return """{"chart": {"result": [{"meta": {"regularMarketPrice": $price, "gmtoffset": 32400,
            "exchangeTimezoneName": "Asia/Seoul"$kind}, "timestamp": [1, 2], "indicators": {"quote": [{"close": $closes}]}}]}}"""
    }

    @Test fun verify_acceptsSupportedKinds() {
        assertEquals(TickerCheck("EQUITY", "Asia/Seoul"), verifyTicker(check("EQUITY")))
        assertEquals("ETF", verifyTicker(check("ETF"))!!.kind)
        assertEquals("INDEX", verifyTicker(check("INDEX"))!!.kind)
        assertEquals("CRYPTOCURRENCY", verifyTicker(check("CRYPTOCURRENCY"))!!.kind)
    }

    @Test fun verify_rejectsOtherOrIncomplete() {
        assertNull(verifyTicker(check("FUTURE")))
        assertNull(verifyTicker(check("CURRENCY")))
        assertNull(verifyTicker(check(null)))
        assertNull(verifyTicker(check("EQUITY", price = "null")))
        assertNull(verifyTicker(check("EQUITY", closes = "[null, 100.0]")))
        assertNull(verifyTicker("""{"chart": {"result": null, "error": {"code": "Not Found"}}}"""))
    }
}
