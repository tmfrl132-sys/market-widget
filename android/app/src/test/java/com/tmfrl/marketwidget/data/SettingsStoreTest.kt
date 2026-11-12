package com.tmfrl.marketwidget.data

import com.tmfrl.marketwidget.model.WidgetSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun missingFileIsDefault() {
        assertEquals(WidgetSettings(), SettingsStore(tmp.root.resolve("settings.json")).read())
    }

    @Test fun brokenFileIsDefault() {
        val file = tmp.newFile("settings.json").apply { writeText("{\"order\": [1, ") }
        assertEquals(WidgetSettings(), SettingsStore(file).read())
    }

    @Test fun updateWritesAndLeavesNoTempFiles() = runTest {
        val store = SettingsStore(tmp.root.resolve("settings.json"))
        val saved = store.update { it.copy(hidden = listOf("VIX")) }
        assertEquals(listOf("VIX"), saved.hidden)
        assertEquals(saved, SettingsStore(tmp.root.resolve("settings.json")).read())
        store.update { it.copy(order = listOf("GSPC")) }
        assertEquals(WidgetSettings(order = listOf("GSPC"), hidden = listOf("VIX")), store.read())
        assertEquals(listOf("settings.json"), tmp.root.list()!!.toList())
    }
}
