package com.tmfrl.marketwidget.data

import com.tmfrl.marketwidget.model.CustomTicker
import com.tmfrl.marketwidget.model.KIND_EQUITY
import com.tmfrl.marketwidget.model.SEOUL
import com.tmfrl.marketwidget.model.WidgetSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.ZonedDateTime

class CustomRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    // 뉴욕 2026-11-11, 11-12 09:30 봉
    private val day1 = 1794439800L
    private val day2 = 1794526200L

    /** 2026-11-14(토) 10:00 서울. 네이버 시간이 아니라 모든 종목이 Yahoo로 받는다. */
    private val now = ZonedDateTime.of(2026, 11, 14, 10, 0, 0, 0, SEOUL)

    private fun chart(stamps: List<Long>, closes: String, price: Double = 102.0, time: Long = day2 + 3600) = """
        {"chart": {"result": [{"meta": {"regularMarketPrice": $price, "regularMarketTime": $time, "gmtoffset": -18000,
          "exchangeTimezoneName": "America/New_York", "instrumentType": "EQUITY"},
          "timestamp": $stamps, "indicators": {"quote": [{"close": $closes}]}}], "error": null}}"""

    private val responses = mapOf(
        "range=5d" to chart(listOf(day1, day2), "[100.0, 102.0]"),
        "range=1d" to chart(listOf(day2, day2 + 300), "[101.0, 102.0]"),
        "range=1y" to chart(listOf(day1 - 86400, day1, day2), "[90.0, 100.0, 101.0]"),
        "range=5y" to chart(listOf(day1 - 7 * 86400, day1), "[80.0, 101.0]"),
        "interval=30m" to chart(listOf(day1, day2), "[99.0, 101.5]"),
        "period1=0" to chart(listOf(day1 - 3 * 86400, day1 - 2 * 86400, day1 - 86400, day1), "[50.0, 120.0, 60.0, 101.0]"),
    )

    private val requested = mutableListOf<String>()

    private fun fake(url: String): String {
        requested += url
        return responses.entries.firstOrNull { url.contains(it.key) }?.value ?: throw IOException("no fake for $url")
    }

    private val aapl = CustomTicker("T:AAPL", "AAPL", "애플", KIND_EQUITY, null)

    @Test fun refresh_buildsQuoteFromYahoo() = runTest {
        val repo = CustomRepository(tmp.root.resolve("custom.json"), ::fake)
        repo.refresh(WidgetSettings(custom = listOf(aapl)), now)
        val entry = repo.entries().single()
        val quote = entry.quote
        assertEquals(102.0, quote.price!!, 0.0)
        assertEquals(2.0, quote.change_pct!!, 0.0)
        assertEquals(listOf(90.0, 100.0, 102.0), quote.daily)
        assertEquals(listOf("2026-11-10", "2026-11-11", "2026-11-12"), quote.daily_dates)
        assertEquals(listOf(80.0, 102.0), quote.weekly)
        assertEquals(listOf(50.0, 120.0, 60.0, 102.0), quote.max_closes)
        assertEquals(4, quote.max_depths.size)
        assertEquals(-50.0, quote.mdd!!, 0.0)
        assertEquals(120.0, quote.peak!!, 0.0)
        assertEquals(listOf(101.0, 102.0), quote.intraday)
        assertEquals(listOf(99.0, 102.0), quote.week)
        assertEquals(listOf(day1, day2), quote.week_times)
        assertTrue(requested.any { it.contains("period1=${now.toEpochSecond() - 7 * 86400}&period2=${now.toEpochSecond()}&interval=30m") })
        assertEquals("America/New_York", quote.market_tz)
        assertFalse(quote.stale)
        assertEquals(now.toInstant().toEpochMilli(), entry.history_at)
        assertEquals(now.toInstant().toEpochMilli(), entry.max_at)
        assertFalse(entry.last_failed)
        assertEquals(listOf("custom.json"), tmp.root.list()!!.toList())
    }

    @Test fun refresh_skipsHistoryUntilDue() = runTest {
        val repo = CustomRepository(tmp.root.resolve("custom.json"), ::fake)
        repo.refresh(WidgetSettings(custom = listOf(aapl)), now)
        requested.clear()
        repo.refresh(WidgetSettings(custom = listOf(aapl)), now.plusMinutes(5))
        assertTrue(requested.none { it.contains("range=1y") || it.contains("range=5y") || it.contains("period1=0") })
        assertEquals(3, requested.size)
    }

    @Test fun refresh_failureKeepsValuesAsStale() = runTest {
        val file = tmp.root.resolve("custom.json")
        CustomRepository(file, ::fake).refresh(WidgetSettings(custom = listOf(aapl)), now)
        CustomRepository(file) { throw IOException("offline") }.refresh(WidgetSettings(custom = listOf(aapl)), now.plusHours(7))
        val entry = CustomRepository(file).entries().single()
        assertEquals(102.0, entry.quote.price!!, 0.0)
        assertTrue(entry.quote.stale)
        assertTrue(entry.last_failed)
        assertEquals(now.toInstant().toEpochMilli(), entry.history_at)
    }

    @Test fun refresh_firstFailureIsEmptyTile() = runTest {
        val repo = CustomRepository(tmp.root.resolve("custom.json")) { throw IOException("offline") }
        repo.refresh(WidgetSettings(custom = listOf(aapl)), now)
        val entry = repo.entries().single()
        assertNull(entry.quote.price)
        assertFalse(entry.quote.stale)
        assertTrue(entry.last_failed)
    }

    @Test fun refresh_hiddenIsNotFetchedAndRemovedIsDropped() = runTest {
        val file = tmp.root.resolve("custom.json")
        CustomRepository(file, ::fake).refresh(WidgetSettings(custom = listOf(aapl)), now)
        requested.clear()
        val repo = CustomRepository(file, ::fake)
        repo.refresh(WidgetSettings(hidden = listOf("T:AAPL"), custom = listOf(aapl)), now.plusHours(1))
        assertEquals(emptyList<String>(), requested)
        assertEquals(102.0, repo.entries().single().quote.price!!, 0.0)
        repo.refresh(WidgetSettings(), now.plusHours(2))
        assertEquals(emptyList<String>(), repo.cached().map { it.id })
    }

    // ---- v1.3: 국내주식 NXT 시간 ----

    private fun resource(name: String) = CustomRepositoryTest::class.java.classLoader!!.getResource(name)!!.readText()

    private val samsung = CustomTicker("T:005930.KS", "005930.KS", "삼성전자", KIND_EQUITY, "005930")

    /** 2026-11-17(화) 23:04 서울 실제 네이버 응답. NXT 분봉 주소가 일반 분봉 주소를 포함하지 않아 먼저 비교해도 된다. */
    private val naver by lazy {
        mapOf(
            "/basic" to resource("naver_basic_005930_20261117_2304.json"),
            "domestic/NXT/item" to resource("naver_nxt_minute_005930_20261117.json"),
            "domestic/item" to resource("naver_minute_005930_20261117.json"),
        )
    }

    private fun fakeWithNaver(url: String): String = naver.entries.firstOrNull { url.contains(it.key) }?.value?.also { requested += url } ?: fake(url)

    private val tuesdayNight = ZonedDateTime.of(2026, 11, 17, 23, 4, 0, 0, SEOUL)

    @Test fun refresh_nxtOnUsesNaverNxtAfterHours() = runTest {
        val repo = CustomRepository(tmp.root.resolve("custom.json"), ::fakeWithNaver)
        repo.refresh(WidgetSettings(custom = listOf(samsung), nxt_hours = true), tuesdayNight)
        val quote = repo.entries().single().quote
        assertEquals(189800.0, quote.price!!, 0.0)
        assertEquals(-7000.0, quote.change!!, 0.0)
        assertEquals(-3.56, quote.change_pct!!, 0.0)
        assertEquals(196800.0, quote.prev_close!!, 0.0)
        assertEquals(ZonedDateTime.of(2026, 11, 17, 8, 0, 0, 0, SEOUL).toEpochSecond(), quote.session_start)
        assertEquals(ZonedDateTime.of(2026, 11, 17, 20, 0, 0, 0, SEOUL).toEpochSecond(), quote.session_end)
        assertEquals(139, quote.intraday.size)
        assertTrue(requested.any { it.contains("domestic/NXT/item/005930/minute") })
    }

    @Test fun refresh_nxtOffAtNightStaysOnYahoo() = runTest {
        val repo = CustomRepository(tmp.root.resolve("custom.json"), ::fakeWithNaver)
        repo.refresh(WidgetSettings(custom = listOf(samsung)), tuesdayNight)
        // 꺼져 있으면 지금처럼 서울 09:00~16:00 밖에는 네이버를 부르지 않는다
        assertTrue(requested.none { it.contains("naver") })
        assertEquals(102.0, repo.entries().single().quote.price!!, 0.0)
    }

    @Test fun refresh_nxtOnWithoutNxtMinutesFallsBackLikeOff() = runTest {
        // NXT 분봉을 못 받으면 빈 목록으로 보고 꺼진 것과 같다(밤이라 Yahoo)
        val get = { url: String -> if (url.contains("domestic/NXT/item")) throw IOException("HTTP 500") else fakeWithNaver(url) }
        val repo = CustomRepository(tmp.root.resolve("custom.json"), get)
        repo.refresh(WidgetSettings(custom = listOf(samsung), nxt_hours = true), tuesdayNight)
        assertEquals(102.0, repo.entries().single().quote.price!!, 0.0)
        assertFalse(repo.entries().single().last_failed)
    }

    @Test fun refresh_brokenCacheStartsOver() = runTest {
        val file = tmp.newFile("custom.json").apply { writeText("{oops") }
        val repo = CustomRepository(file, ::fake)
        assertEquals(emptyList<String>(), repo.cached().map { it.id })
        repo.refresh(WidgetSettings(custom = listOf(aapl)), now)
        assertEquals(listOf("T:AAPL"), repo.cached().map { it.id })
    }
}
