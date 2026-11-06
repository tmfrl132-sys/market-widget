package com.tmfrl.marketwidget.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoModeTest {
    private val ids = listOf("KS11", "KQ11", "BTC")

    private fun quote(id: String, daily: List<Double>) =
        Quote(id, id, 1.0, 0.0, 0.0, daily = daily)

    @Test fun next_advancesAndWraps() {
        assertEquals("KQ11", nextAutoQuote("KS11", ids))
        assertEquals("KS11", nextAutoQuote("BTC", ids))
    }

    @Test fun next_startsAtFirstWithoutCurrent() {
        assertEquals("KS11", nextAutoQuote(null, ids))
        assertEquals("KS11", nextAutoQuote("GONE", ids))
    }

    @Test fun next_nullWhenNoQuotes() {
        assertNull(nextAutoQuote("KS11", emptyList()))
    }

    @Test fun next_visitsAllThenRepeats() {
        val all = LIVE_SYMBOLS.keys.toList()
        var current: String? = null
        val visited = (0..all.size).map { nextAutoQuote(current, all).also { current = it } }
        assertEquals(all + all.first(), visited)
    }

    @Test fun ids_skipQuotesWithoutChart() {
        val quotes = listOf(quote("KS11", listOf(1.0, 2.0)), quote("BTC", emptyList()))
        assertEquals(listOf("KS11"), autoQuoteIds(quotes, Period.ONE_YEAR))
    }

    @Test fun ids_useAllWhenNoneHasChart() {
        val quotes = listOf(quote("KS11", emptyList()), quote("BTC", emptyList()))
        assertEquals(listOf("KS11", "BTC"), autoQuoteIds(quotes, Period.ONE_YEAR))
    }

    @Test fun refreshDue_everyFiveMinutesWithSomeTolerance() {
        assertEquals(false, refreshDue(nowMillis = 1_000_000, lastRefreshMillis = 1_000_000 - 60_000))
        assertEquals(false, refreshDue(nowMillis = 1_000_000, lastRefreshMillis = 1_000_000 - 240_000))
        assertEquals(true, refreshDue(nowMillis = 1_000_000, lastRefreshMillis = 1_000_000 - 295_000))
        assertEquals(true, refreshDue(nowMillis = 1_000_000, lastRefreshMillis = 0))
    }

    @Test fun keepTicking_followsResult() = runTest {
        assertEquals(true, keepTicking { true })
        assertEquals(false, keepTicking { false })
    }

    @Test fun keepTicking_continuesAfterError() = runTest {
        assertEquals(true, keepTicking { throw IllegalStateException("glance") })
    }

    @Test fun keepTicking_doesNotSwallowCancellation() = runTest {
        var cancelled = false
        try {
            keepTicking { throw CancellationException("stop") }
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertEquals(true, cancelled)
    }

    @Test fun tap_jumpsInAutoAndTogglesOtherwise() {
        assertEquals("KS11", selectionAfterTap("KS11", "KS11", auto = true))
        assertEquals("BTC", selectionAfterTap("KS11", "BTC", auto = true))
        assertNull(selectionAfterTap("KS11", "KS11", auto = false))
        assertEquals("BTC", selectionAfterTap("KS11", "BTC", auto = false))
    }
}
