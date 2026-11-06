package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BigMoveTest {
    private fun quote(pct: Double?, limit: Double?, stale: Boolean = false) =
        Quote("KS11", "코스피", 100.0, 0.0, pct, big_move_pct = limit, stale = stale)

    @Test fun downAtLimitIsEmphasized() {
        assertEquals(Trend.DOWN, bigMoveTrend(quote(-2.0, 2.0)))
    }

    @Test fun justBelowLimitIsNot() {
        assertNull(bigMoveTrend(quote(-1.99, 2.0)))
    }

    @Test fun staleIsNotEmphasized() {
        assertNull(bigMoveTrend(quote(2.0, 2.0, stale = true)))
    }

    @Test fun missingChangeOrLimitIsNot() {
        assertNull(bigMoveTrend(quote(null, 2.0)))
        assertNull(bigMoveTrend(quote(5.0, null)))
    }

    @Test fun upAtLimitIsEmphasized() {
        assertEquals(Trend.UP, bigMoveTrend(quote(10.0, 10.0)))
    }
}
