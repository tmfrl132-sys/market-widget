package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeTest {
    private val kd = DEFAULT_COMPOSITES.single()

    @Test fun defaultIsKrwOverDxy() {
        assertEquals(Composite("X:KRW/DXY", "KRW", "/", "DXY", "원/달러÷달러인덱스"), kd)
        assertTrue(isCompositeId(kd.id))
    }

    @Test fun applyOp_fourOperationsAndDivideByZero() {
        assertEquals(14.0, applyOp("/", 1400.0, 100.0)!!, 0.0)
        assertEquals(6.0, applyOp("*", 2.0, 3.0)!!, 0.0)
        assertEquals(5.0, applyOp("+", 2.0, 3.0)!!, 0.0)
        assertEquals(-1.0, applyOp("-", 2.0, 3.0)!!, 0.0)
        assertNull(applyOp("/", 1.0, 0.0))
        assertNull(applyOp("%", 1.0, 1.0))
    }

    @Test fun combineByDate_carriesLastValueOfB() {
        val (values, dates) = combineByDate(
            "/",
            listOf(1400.0, 1410.0, 1420.0), listOf("2026-11-02", "2026-11-03", "2026-11-04"),
            listOf(100.0, 101.0), listOf("2026-11-03", "2026-11-04"),
        )
        // 11/2는 B 값이 아직 없어 빠진다
        assertEquals(listOf("2026-11-03", "2026-11-04"), dates)
        assertEquals(listOf(14.1, 14.0594), values)
        assertEquals(emptyList<Double>() to emptyList<String>(), combineByDate("/", listOf(1.0), emptyList(), listOf(1.0), listOf("2026-11-02")))
    }

    @Test fun combineByTime_usesLatestBAtOrBefore() {
        val (values, times) = combineByTime("-", listOf(10.0, 11.0, 12.0), listOf(100L, 200L, 300L), listOf(1.0, 2.0), listOf(150L, 300L))
        assertEquals(listOf(200L, 300L), times)
        assertEquals(listOf(10.0, 10.0), values)
    }

    @Test fun compositeQuote_priceChangeAndSeries() {
        val krw = Quote("KRW", "원/달러", 1420.0, 10.0, 0.71, daily = listOf(1410.0, 1420.0),
            daily_dates = listOf("2026-11-03", "2026-11-04"), market_tz = "Europe/London")
        val dxy = Quote("DXY", "달러인덱스", 100.0, -1.0, -0.99, daily = listOf(101.0, 100.0),
            daily_dates = listOf("2026-11-03", "2026-11-04"))
        val q = compositeQuote(kd, krw, dxy)
        assertEquals(14.2, q.price!!, 0.0)
        // 전일: 1410 / 101 = 13.9604
        assertEquals(13.9604, q.prev_close!!, 0.0)
        assertEquals(0.2396, q.change!!, 0.0)
        assertEquals(1.72, q.change_pct!!, 0.0)
        assertEquals(listOf(13.9604, 14.2), q.daily)
        assertEquals("Europe/London", q.market_tz)
        assertNull(q.mdd)
        assertEquals(Quote(kd.id, kd.name, null, null, null), compositeQuote(kd, krw, null))
    }

    @Test fun visibleQuotes_buildsCompositeEvenIfSourcesHidden() {
        val builtIn = BUILT_IN_NAMES.map { (id, name) -> Quote(id, name, if (id == "KRW") 1400.0 else 100.0, 0.0, 0.0) }
        val quotes = visibleQuotes(builtIn, emptyList(), WidgetSettings(hidden = listOf("KRW", "DXY")))
        assertEquals(14.0, quotes.single { it.id == kd.id }.price!!, 0.0)
    }

    @Test fun addRemoveRestore() {
        val s = WidgetSettings()
        assertEquals(listOf(kd), compositesOf(s))
        val removed = removeComposite(s.copy(order = listOf(kd.id), hidden = listOf(kd.id)), kd.id)
        assertEquals(emptyList<Composite>(), compositesOf(removed))
        assertEquals(listOf(kd.id), removed.composites_removed)
        assertTrue(removed.order.isEmpty() && removed.hidden.isEmpty())
        assertEquals(listOf(kd), compositesOf(addComposite(removed, kd)))
        val gold = makeComposite("GC", "/", "CL", "금", "WTI")
        assertEquals("금÷WTI", gold.name)
        val added = addComposite(s, gold)
        assertEquals(listOf(kd, gold), compositesOf(added))
        assertEquals(added, addComposite(added, gold))
        assertEquals(s, addComposite(s, makeComposite("GC", "/", "GC", "금", "금")))
        assertEquals(listOf(kd), compositesOf(removeComposite(added, gold.id)))
    }
}
