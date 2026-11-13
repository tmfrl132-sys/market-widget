package com.tmfrl.marketwidget.data

import android.content.Context
import com.tmfrl.marketwidget.model.WidgetSettings
import com.tmfrl.marketwidget.model.encodeSettings
import com.tmfrl.marketwidget.model.migrateSettings
import com.tmfrl.marketwidget.model.parseSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 위젯 지표 설정 파일(settings.json). 위젯은 그릴 때마다 동기로 읽고, 설정 화면은 update로 고친다.
 * 파일이 없거나 깨졌으면 기본값(기본 지표 모두 켜짐)이고, 다음 저장 때 덮어쓴다.
 * 읽을 때 옛 id(IXIC, ES, NQ)를 옮긴다(migrateSettings). 읽기에서는 쓰지 않고, update가 옮긴 설정을 저장한다.
 */
class SettingsStore(private val file: File) {

    fun read(): WidgetSettings = migrateSettings(readRaw())

    private fun readRaw(): WidgetSettings = try {
        if (file.exists()) parseSettings(file.readText()) else WidgetSettings()
    } catch (e: IOException) {
        WidgetSettings()
    }

    /**
     * 읽고 고쳐서 원자적으로 쓴다. 두 곳에서 동시에 고쳐도 한쪽 변경이 사라지지 않게 하나씩 한다. 바뀐 설정을 돌려준다.
     * 파일에 있던 그대로와 비교하므로 옛 id를 옮겼으면 transform이 아무것도 바꾸지 않아도 저장한다. update { it }이 이전 저장이다.
     */
    suspend fun update(transform: (WidgetSettings) -> WidgetSettings): WidgetSettings = withContext(Dispatchers.IO) {
        lock.withLock {
            val before = readRaw()
            val after = transform(migrateSettings(before))
            if (after != before) writeAtomic(file, encodeSettings(after))
            after
        }
    }

    companion object {
        private val lock = Mutex()

        fun forContext(context: Context) = SettingsStore(File(context.filesDir, "settings.json"))
    }
}
