package com.tmfrl.marketwidget.data

import com.tmfrl.marketwidget.model.DEFAULT_EARNINGS
import com.tmfrl.marketwidget.model.EarningsDate
import com.tmfrl.marketwidget.model.WidgetSettings
import com.tmfrl.marketwidget.model.removeEarnings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class EarningsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val requested = mutableListOf<String>()

    private fun fake(url: String): String {
        requested += url
        if ("NVDA" in url) {
            return """{"quoteSummary": {"result": [{"calendarEvents": {"earnings": {
                "earningsDate": [{"raw": 1796158800}], "isEarningsDateEstimate": false}}}]}}"""
        }
        if ("AAPL" in url) throw IOException("offline")
        return """{"quoteSummary": {"result": [{"calendarEvents": {}}]}}"""
    }

    @Test fun refresh_fetchesListedTickersOnlyEveryTwelveHours() = runTest {
        val repo = EarningsRepository(tmp.root.resolve("earnings.json"), ::fake)
        repo.refresh(WidgetSettings(), nowMillis = 0)
        val entries = repo.entries().associateBy { it.symbol }
        assertEquals(DEFAULT_EARNINGS.map { it.symbol }, repo.entries().map { it.symbol })
        assertEquals(EarningsDate(1796158800, false), entries.getValue("NVDA").earnings)
        assertNull(entries.getValue("MSFT").earnings)
        assertTrue(entries.getValue("AAPL").failed)
        assertNull(entries.getValue("AAPL").at)
        // 1시간 뒤: 받은 종목은 건너뛰고, 실패한 종목만 다시 시도한다
        requested.clear()
        repo.refresh(WidgetSettings(), nowMillis = 3_600_000)
        assertEquals(1, requested.size)
        assertTrue("AAPL" in requested.single())
    }

    @Test fun refresh_dropsRemovedTickers() = runTest {
        val repo = EarningsRepository(tmp.root.resolve("earnings.json"), ::fake)
        repo.refresh(WidgetSettings(), nowMillis = 0)
        repo.refresh(removeEarnings(WidgetSettings(), "NVDA"), nowMillis = 1)
        assertTrue(repo.entries().none { it.symbol == "NVDA" })
    }
}
