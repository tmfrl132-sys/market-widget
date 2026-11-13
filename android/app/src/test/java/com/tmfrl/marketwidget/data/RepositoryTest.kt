package com.tmfrl.marketwidget.data

import com.tmfrl.marketwidget.model.CombinedChart
import com.tmfrl.marketwidget.model.CombinedSource
import com.tmfrl.marketwidget.model.LiveQuote
import com.tmfrl.marketwidget.model.encodeMarketData
import com.tmfrl.marketwidget.model.parseFuturesSeries
import com.tmfrl.marketwidget.model.parseMarketData
import com.tmfrl.marketwidget.model.parseSpotSeries
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class RepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sample(time: String) =
        """{"updated_at": "$time", "quotes": [], "events": []}"""

    @Test fun refresh_failure_returns_cached() = runTest {
        val cache = tmp.newFile("data.json").apply { writeText(sample("2026-10-30T10:00:00+09:00")) }
        val repo = Repository(cache) { throw IOException("offline") }
        assertEquals("2026-10-30T10:00:00+09:00", repo.refresh()?.updated_at)
    }

    @Test fun refresh_success_overwrites_cache() = runTest {
        val cache = tmp.newFile("data.json").apply { writeText(sample("2026-10-30T10:00:00+09:00")) }
        val repo = Repository(cache) { sample("2026-10-30T10:30:00+09:00") }
        assertEquals("2026-10-30T10:30:00+09:00", repo.refresh()?.updated_at)
        assertEquals("2026-10-30T10:30:00+09:00", repo.cached()?.updated_at)
    }

    @Test fun refresh_invalid_json_keeps_cache() = runTest {
        val cache = tmp.newFile("data.json").apply { writeText(sample("2026-10-30T10:00:00+09:00")) }
        val repo = Repository(cache) { "<html>error</html>" }
        assertEquals("2026-10-30T10:00:00+09:00", repo.refresh()?.updated_at)
        assertEquals("2026-10-30T10:00:00+09:00", repo.cached()?.updated_at)
    }

    private fun withQuote(time: String) =
        """{"updated_at": "$time", "quotes": [{"id": "KS11", "name": "코스피", "price": 100.0,
           "change": 1.0, "change_pct": 1.0, "daily": [90.0, 100.0]}], "events": []}"""

    private val live = mapOf("KS11" to LiveQuote(95.0, -5.0, -5.0, 100.0))

    @Test fun refresh_overlays_live_quotes_on_price_and_chart() = runTest {
        val cache = tmp.root.resolve("data.json")
        val repo = Repository(cache, { live }, { "2026-10-30T10:45:00+09:00" }) { withQuote("2026-10-30T10:00:00+09:00") }
        val data = repo.refresh()!!
        assertEquals("2026-10-30T10:45:00+09:00", data.updated_at)
        assertEquals(95.0, data.quotes[0].price!!, 0.0)
        assertEquals(listOf(90.0, 100.0, 95.0), data.quotes[0].daily)
        assertEquals(data, repo.cached())
    }

    @Test fun refresh_live_failure_uses_data_json() = runTest {
        val cache = tmp.root.resolve("data.json")
        val repo = Repository(cache, { emptyMap() }, { "later" }) { withQuote("2026-10-30T10:00:00+09:00") }
        val data = repo.refresh()!!
        assertEquals("2026-10-30T10:00:00+09:00", data.updated_at)
        assertEquals(100.0, data.quotes[0].price!!, 0.0)
    }

    @Test fun refresh_data_json_failure_overlays_live_on_cache() = runTest {
        val cache = tmp.newFile("data.json").apply { writeText(withQuote("2026-10-30T10:00:00+09:00")) }
        val repo = Repository(cache, { live }, { "2026-10-30T10:45:00+09:00" }) { throw IOException("offline") }
        val data = repo.refresh()!!
        assertEquals(95.0, data.quotes[0].price!!, 0.0)
        assertEquals(95.0, repo.cached()!!.quotes[0].price!!, 0.0)
    }

    @Test fun refresh_replaces_cache_without_leaving_temp_files() = runTest {
        val cache = tmp.newFile("data.json").apply { writeText(sample("2026-10-30T10:00:00+09:00")) }
        val repo = Repository(cache) { sample("2026-10-30T10:30:00+09:00") }
        repo.refresh()
        repo.refresh()
        assertEquals("2026-10-30T10:30:00+09:00", repo.cached()?.updated_at)
        assertEquals(listOf("data.json"), tmp.root.list()!!.toList())
    }

    private fun resource(name: String) = RepositoryTest::class.java.classLoader!!.getResource(name)!!.readText()

    private fun gspc(time: String) =
        """{"updated_at": "$time", "quotes": [{"id": "GSPC", "name": "S&P500", "price": 7600.0,
           "change": 1.0, "change_pct": 0.01, "daily": [7600.0]}], "events": []}"""

    @Test fun refresh_combinedUsesPreviousCacheContractAndDropsWhenFuturesMissing() = runTest {
        val cache = tmp.root.resolve("data.json")
        val old = parseMarketData(gspc("2026-11-13T22:00:00+09:00"))
        cache.writeText(encodeMarketData(old.copy(quotes = old.quotes.map { it.copy(combined = CombinedChart(contract = "E-Mini S&P 500 Sep 26")) })))
        val source = CombinedSource(
            parseSpotSeries(resource("yahoo_5m_5d_gspc_20261113.json"))!!,
            parseFuturesSeries(resource("yahoo_5m_5d_es_20261113.json"))!!,
        )
        var withFutures = true
        val repo = Repository(
            cache,
            { mapOf("GSPC" to LiveQuote(7592.4, -26.98, -0.35, 7619.38, combinedSource = source.takeIf { withFutures })) },
            { "2026-11-13T23:05:04+09:00" },
        ) { gspc("2026-11-13T23:00:00+09:00") }
        // data.json에는 combined가 없지만 직전 캐시의 계약 이름(Sep 26)과 비교해 교체를 알아챈다
        val first = repo.refresh()!!.quotes.single().combined!!
        assertEquals("E-Mini S&P 500 Dec 26", first.contract)
        assertEquals(1794578704L, first.roll_at)
        assertEquals(first, repo.cached()!!.quotes.single().combined)
        // 선물을 못 받은 갱신에서는 낡은 합친 차트를 지운다
        withFutures = false
        assertNull(repo.refresh()!!.quotes.single().combined)
    }

    @Test fun cached_missing_file_is_null() {
        val repo = Repository(tmp.root.resolve("none.json")) { "" }
        assertNull(repo.cached())
    }
}
