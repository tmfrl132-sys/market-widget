package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDataTest {
    @Test fun parse_ignoresUnknownKeysAndAcceptsNullPrice() {
        val json = """
            {"updated_at": "2026-10-30T10:30:00+09:00", "extra": 1,
             "quotes": [{"id": "KS11", "name": "코스피", "price": null, "change": null,
                         "change_pct": null, "history": [], "stale": true, "new_field": "x"}],
             "events": [{"date": "2026-11-13", "title": "미국 CPI", "country": "US"}]}
        """.trimIndent()
        val data = parseMarketData(json)
        assertEquals("2026-10-30T10:30:00+09:00", data.updated_at)
        assertNull(data.quotes[0].price)
        assertTrue(data.quotes[0].stale)
        assertEquals("미국 CPI", data.events[0].title)
    }
}
