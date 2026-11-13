package com.tmfrl.marketwidget.data

import com.tmfrl.marketwidget.model.WidgetSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val oldSettings = """{"version": 1, "order": ["GSPC", "ES", "IXIC", "NQ", "KS11"], "hidden": ["IXIC", "NQ"], "custom": []}"""

    @Test fun readMigratesOldIdsWithoutWriting() {
        val file = tmp.newFile("settings.json").apply { writeText(oldSettings) }
        val read = SettingsStore(file).read()
        assertEquals(WidgetSettings(order = listOf("GSPC", "NDX", "KS11"), hidden = listOf("NDX")), read)
        // 위젯이 그릴 때 읽는 것만으로는 파일을 바꾸지 않는다
        assertEquals(oldSettings, file.readText())
    }

    @Test fun updateIdentitySavesMigratedSettingsOnce() = runTest {
        val file = tmp.newFile("settings.json").apply { writeText(oldSettings) }
        val store = SettingsStore(file)
        val saved = store.update { it }
        assertEquals(WidgetSettings(order = listOf("GSPC", "NDX", "KS11"), hidden = listOf("NDX")), saved)
        val written = file.readText()
        assertFalse(written.contains("IXIC"))
        assertFalse(written.contains("\"ES\""))
        assertEquals(saved, SettingsStore(file).read())
        // 이미 옮긴 설정은 다시 쓰지 않는다
        file.setLastModified(1_000_000L)
        store.update { it }
        assertEquals(1_000_000L, file.lastModified())
        assertEquals(listOf("settings.json"), tmp.root.list()!!.toList())
    }

    @Test fun updateOnOldSettingsAppliesChangeAfterMigration() = runTest {
        val file = tmp.newFile("settings.json").apply { writeText(oldSettings) }
        val saved = SettingsStore(file).update { it.copy(hidden = it.hidden - "NDX") }
        assertEquals(WidgetSettings(order = listOf("GSPC", "NDX", "KS11")), saved)
    }
}
