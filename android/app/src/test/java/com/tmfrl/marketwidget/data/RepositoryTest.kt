package com.tmfrl.marketwidget.data

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

    @Test fun cached_missing_file_is_null() {
        val repo = Repository(tmp.root.resolve("none.json")) { "" }
        assertNull(repo.cached())
    }
}
