package com.tmfrl.marketwidget.data

import com.tmfrl.marketwidget.model.LiveQuote
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

    @Test fun cached_missing_file_is_null() {
        val repo = Repository(tmp.root.resolve("none.json")) { "" }
        assertNull(repo.cached())
    }
}
