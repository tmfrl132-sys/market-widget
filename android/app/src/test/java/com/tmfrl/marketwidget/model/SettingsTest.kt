package com.tmfrl.marketwidget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 설계서 6.4절 고정 기대값. */
class SettingsTest {
    private val builtInIds = BUILT_IN_NAMES.keys.toList()
    private val builtIn = BUILT_IN_NAMES.map { (id, name) -> Quote(id, name, 1.0, 0.0, 0.0) }
    private val aapl = CustomTicker(customId("AAPL"), "AAPL", "애플", KIND_EQUITY, null)

    private fun ids(quotes: List<Quote>) = quotes.map { it.id }

    @Test fun builtInNames_matchLiveSymbolsInCollectorOrder() {
        // 수집기 지표가 늘거나 순서가 바뀌면 두 표를 함께 고친다
        assertEquals(LIVE_SYMBOLS.keys.toList(), builtInIds)
        assertEquals("S&P500", BUILT_IN_NAMES["GSPC"])
    }

    @Test fun visible_defaultIsCollectorOrder() {
        assertEquals(builtInIds, ids(visibleQuotes(builtIn, emptyList(), WidgetSettings())))
    }

    @Test fun visible_orderFirstThenRestWithoutHidden() {
        val settings = WidgetSettings(order = listOf("GSPC", "KS11"), hidden = listOf("VIX"))
        assertEquals(
            listOf("GSPC", "KS11", "KQ11", "NDX", "KRW", "DXY", "TNX", "CL", "GC", "BTC"),
            ids(visibleQuotes(builtIn, emptyList(), settings)),
        )
    }

    @Test fun visible_customNotInOrderGoesLastAsPlaceholder() {
        val settings = WidgetSettings(order = listOf("GSPC", "KS11"), hidden = listOf("VIX"), custom = listOf(aapl))
        val quotes = visibleQuotes(builtIn, emptyList(), settings)
        // 기본 지표에서 1개(VIX)를 끄고 추가 종목 1개를 더한다
        assertEquals(builtInIds.size, quotes.size)
        assertEquals("T:AAPL", quotes.last().id)
        assertEquals(Quote("T:AAPL", "애플", null, null, null), quotes.last())
    }

    @Test fun visible_unknownOrderIdIgnored() {
        val settings = WidgetSettings(order = listOf("T:AAPL", "XYZ", "KS11"), custom = listOf(aapl))
        assertEquals(listOf("T:AAPL", "KS11") + builtInIds.drop(1), ids(visibleQuotes(builtIn, emptyList(), settings)))
    }

    @Test fun visible_usesCachedCustomQuote() {
        val cached = Quote("T:AAPL", "애플", 200.0, 1.0, 0.5)
        val quotes = visibleQuotes(builtIn, listOf(cached), WidgetSettings(custom = listOf(aapl)))
        assertEquals(cached, quotes.last())
    }

    @Test fun visible_dropsCachedCustomNotInSettings() {
        val stale = Quote("T:QQQ", "QQQ", 1.0, 0.0, 0.0)
        assertEquals(builtInIds, ids(visibleQuotes(builtIn, listOf(stale), WidgetSettings())))
    }

    @Test fun sanitize_fixedExpectations() {
        val allButVix = builtInIds - "VIX"
        assertEquals(ChartSelection(), sanitizeSelection(ChartSelection("VIX", null, false), allButVix))
        assertEquals(ChartSelection("KS11", null, false), sanitizeSelection(ChartSelection("KS11", "VIX", false), allButVix))
        assertEquals(ChartSelection("KS11", null, true), sanitizeSelection(ChartSelection("KS11", null, true), builtInIds))
        assertEquals(ChartSelection(), sanitizeSelection(ChartSelection("VIX", null, true), allButVix))
    }

    @Test fun setHidden_lastShownCannotBeHidden() {
        val one = WidgetSettings(hidden = builtInIds - "KS11")
        assertEquals(one, setHidden(builtInIds, one, "KS11", hidden = true))
        assertEquals(1, shownCount(builtInIds, one))
    }

    @Test fun setHidden_hideAndShow() {
        val hidden = setHidden(builtInIds, WidgetSettings(), "VIX", hidden = true)
        assertEquals(listOf("VIX"), hidden.hidden)
        assertEquals(WidgetSettings(), setHidden(builtInIds, hidden, "VIX", hidden = false))
        assertEquals(hidden, setHidden(builtInIds, hidden, "VIX", hidden = true))
    }

    @Test fun addCustom_ninthIsIgnored() {
        val eight = WidgetSettings(custom = (1..8).map { CustomTicker(customId("S$it"), "S$it", "S$it") })
        assertEquals(eight, addCustom(eight, aapl))
    }

    @Test fun addCustom_sameOrBuiltInSymbolIsIgnored() {
        val one = addCustom(WidgetSettings(), aapl)
        assertEquals(listOf(aapl), one.custom)
        assertEquals(one, addCustom(one, aapl.copy(name = "Apple")))
        assertEquals(one, addCustom(one, CustomTicker(customId("^GSPC"), "^GSPC", "S&P 500", KIND_INDEX)))
        assertTrue(isAdded(one, "BTC-USD"))
        assertFalse(isAdded(one, "QQQ"))
    }

    @Test fun removeCustom_clearsOrderHiddenAndList() {
        val settings = WidgetSettings(order = listOf("T:AAPL", "KS11"), hidden = listOf("T:AAPL"), custom = listOf(aapl))
        assertEquals(WidgetSettings(order = listOf("KS11")), removeCustom(settings, "T:AAPL"))
    }

    @Test fun moveUp_topStaysAndOthersSwap() {
        assertEquals(WidgetSettings(), moveUp(builtInIds, WidgetSettings(), "KS11"))
        val moved = moveUp(builtInIds, WidgetSettings(), "KQ11")
        assertEquals(listOf("KQ11", "KS11") + builtInIds.drop(2), moved.order)
    }

    @Test fun moveDown_bottomStays() {
        val settings = WidgetSettings(custom = listOf(aapl))
        assertEquals(settings, moveDown(builtInIds, settings, "T:AAPL"))
        val moved = moveDown(builtInIds, settings, "BTC")
        assertEquals(builtInIds.dropLast(1) + listOf("T:AAPL", "BTC"), moved.order)
    }

    @Test fun moveUp_toTopMakesFirstTile() {
        var settings = WidgetSettings()
        repeat(2) { settings = moveUp(builtInIds, settings, "GSPC") }
        assertEquals("GSPC", visibleQuotes(builtIn, emptyList(), settings).first().id)
    }

    @Test fun resetOrder_keepsCustom() {
        val settings = WidgetSettings(order = listOf("GSPC"), hidden = listOf("VIX"), custom = listOf(aapl))
        assertEquals(WidgetSettings(custom = listOf(aapl)), resetOrder(settings))
    }

    @Test fun json_roundTripAndBrokenIsDefault() {
        val settings = WidgetSettings(order = listOf("GSPC", "KS11", "T:005930.KS"), hidden = listOf("VIX"),
            custom = listOf(CustomTicker("T:005930.KS", "005930.KS", "삼성전자", KIND_EQUITY, "005930")))
        assertEquals(settings, parseSettings(encodeSettings(settings)))
        assertEquals(WidgetSettings(), parseSettings("{broken"))
        assertEquals(WidgetSettings(), parseSettings(""))
        assertEquals(WidgetSettings(), parseSettings("{}"))
    }

    // ---- v1.1: 나스닥 종합 → 나스닥100, 선물 카드 삭제 ----

    @Test fun builtInNames_v11ElevenWithNasdaq100() {
        assertEquals(
            listOf("KS11", "KQ11", "GSPC", "NDX", "VIX", "KRW", "DXY", "TNX", "CL", "GC", "BTC"),
            builtInIds,
        )
        assertEquals("나스닥100", BUILT_IN_NAMES["NDX"])
        assertFalse(listOf("IXIC", "ES", "NQ").any { it in BUILT_IN_NAMES })
    }

    @Test fun migrate_ixicTakesOverNdxSlotAndToggle_futuresRemoved() {
        val old = WidgetSettings(
            order = listOf("GSPC", "ES", "IXIC", "NQ", "KS11", "T:AAPL"),
            hidden = listOf("IXIC", "NQ", "VIX"),
            custom = listOf(aapl),
        )
        val migrated = migrateSettings(old)
        assertEquals(listOf("GSPC", "NDX", "KS11", "T:AAPL"), migrated.order)
        assertEquals(listOf("NDX", "VIX"), migrated.hidden)
        assertEquals(listOf(aapl), migrated.custom)
        // NDX가 옛 IXIC 자리(GSPC 다음)에 꺼진 채로 보이지 않는다
        val visible = ids(visibleQuotes(builtIn, emptyList(), migrated))
        assertEquals(listOf("GSPC", "KS11", "T:AAPL", "KQ11", "KRW", "DXY", "TNX", "CL", "GC", "BTC"), visible)
    }

    @Test fun migrate_shownIxicKeepsPlaceAsNdx() {
        val migrated = migrateSettings(WidgetSettings(order = listOf("IXIC", "KS11")))
        assertEquals(listOf("NDX", "KS11"), migrated.order)
        assertEquals("NDX", visibleQuotes(builtIn, emptyList(), migrated).first().id)
    }

    @Test fun migrate_alreadyMigratedOrDefaultIsSameInstance() {
        val current = WidgetSettings(order = listOf("NDX", "GSPC"), hidden = listOf("VIX"))
        assertTrue(current === migrateSettings(current))
        val default = WidgetSettings()
        assertTrue(default === migrateSettings(default))
    }

    @Test fun migrate_onlyFuturesShownTurnsOnSp500AndNasdaq100() {
        // 옛 설정에서 ES·NQ만 켜 두었으면 옮긴 뒤 켜진 지표가 0개가 된다. 그 자리를 대신하는 GSPC·NDX를 켠다.
        val oldIds = listOf("KS11", "KQ11", "GSPC", "IXIC", "VIX", "KRW", "DXY", "TNX", "CL", "GC", "BTC")
        val migrated = migrateSettings(WidgetSettings(order = listOf("ES", "NQ"), hidden = oldIds))
        assertEquals(listOf("KS11", "KQ11", "VIX", "KRW", "DXY", "TNX", "CL", "GC", "BTC"), migrated.hidden)
        assertEquals(listOf("GSPC", "NDX"), ids(visibleQuotes(builtIn, emptyList(), migrated)))
        assertEquals(2, shownCount(builtInIds, migrated))
    }

    @Test fun migrate_shownCustomTickerKeepsEverythingElseHidden() {
        val oldIds = listOf("KS11", "KQ11", "GSPC", "IXIC", "VIX", "KRW", "DXY", "TNX", "CL", "GC", "BTC")
        val migrated = migrateSettings(WidgetSettings(hidden = oldIds, custom = listOf(aapl)))
        assertEquals(listOf("T:AAPL"), ids(visibleQuotes(builtIn, emptyList(), migrated)))
    }

    @Test fun migrate_bothOldAndNewIdKeepsFirst() {
        assertEquals(listOf("NDX", "GSPC"), migrateSettings(WidgetSettings(order = listOf("NDX", "GSPC", "IXIC"))).order)
    }

    @Test fun builtInQuotes_dropsRetiredIdsFromOldDataJson() {
        // 수집기보다 앱을 먼저 배포하면 data.json에 옛 13개(IXIC, ES, NQ)가 남아 있다. NDX는 수집기 배포 뒤에 생긴다.
        val oldIds = listOf("KS11", "KQ11", "GSPC", "ES", "IXIC", "NQ", "VIX", "KRW", "DXY", "TNX", "CL", "GC", "BTC")
        val oldData = oldIds.map { Quote(it, it, 1.0, 0.0, 0.0) }
        val expected = listOf("KS11", "KQ11", "GSPC", "VIX", "KRW", "DXY", "TNX", "CL", "GC", "BTC")
        assertEquals(expected, ids(builtInQuotes(oldData)))
        assertEquals(expected, ids(visibleQuotes(oldData, emptyList(), WidgetSettings())))
        // 옛 설정의 IXIC를 옮긴 NDX는 data.json에 아직 없으면 타일이 없다
        assertEquals(expected, ids(visibleQuotes(oldData, emptyList(), migrateSettings(WidgetSettings(order = listOf("IXIC"))))))
    }

    @Test fun sanitize_retiredIdSelectionIsCleared() {
        assertEquals(ChartSelection(), sanitizeSelection(ChartSelection("IXIC", null, false), builtInIds))
        assertEquals(ChartSelection("GSPC", null, false), sanitizeSelection(ChartSelection("GSPC", "ES", false), builtInIds))
    }

    @Test fun addCustom_nasdaqCompositeIndexCanBeAdded() {
        // 기본 지표에서 빠진 나스닥 종합은 추가 종목으로 넣을 수 있다
        assertFalse(isAdded(WidgetSettings(), "^IXIC"))
        val ixic = CustomTicker(customId("^IXIC"), "^IXIC", "NASDAQ Composite", KIND_INDEX)
        assertEquals(listOf(ixic), addCustom(WidgetSettings(), ixic).custom)
        assertTrue(isAdded(WidgetSettings(), "^NDX"))
    }

    @Test fun json_readsSpecExample() {
        val settings = parseSettings(
            """{"version": 1, "order": ["GSPC", "KS11", "T:005930.KS"], "hidden": ["VIX"],
               "custom": [{"id": "T:005930.KS", "symbol": "005930.KS", "name": "삼성전자", "kind": "EQUITY", "naver_code": "005930"}]}""",
        )
        assertEquals("005930", settings.custom.single().naver_code)
        assertTrue(encodeSettings(settings).contains("\"version\":1"))
    }
}
