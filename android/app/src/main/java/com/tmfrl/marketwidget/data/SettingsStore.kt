package com.tmfrl.marketwidget.data

import android.content.Context
import com.tmfrl.marketwidget.model.WidgetSettings
import com.tmfrl.marketwidget.model.encodeSettings
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
 */
class SettingsStore(private val file: File) {

    fun read(): WidgetSettings = try {
        if (file.exists()) parseSettings(file.readText()) else WidgetSettings()
    } catch (e: IOException) {
        WidgetSettings()
    }

    /** 읽고 고쳐서 원자적으로 쓴다. 두 곳에서 동시에 고쳐도 한쪽 변경이 사라지지 않게 하나씩 한다. 바뀐 설정을 돌려준다. */
    suspend fun update(transform: (WidgetSettings) -> WidgetSettings): WidgetSettings = withContext(Dispatchers.IO) {
        lock.withLock {
            val before = read()
            val after = transform(before)
            if (after != before) writeAtomic(file, encodeSettings(after))
            after
        }
    }

    companion object {
        private val lock = Mutex()

        fun forContext(context: Context) = SettingsStore(File(context.filesDir, "settings.json"))
    }
}
