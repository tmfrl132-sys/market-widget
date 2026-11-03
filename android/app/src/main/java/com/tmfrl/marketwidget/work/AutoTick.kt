package com.tmfrl.marketwidget.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.tmfrl.marketwidget.data.Repository
import com.tmfrl.marketwidget.model.refreshDue
import com.tmfrl.marketwidget.widget.advanceAutoWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val INTERVAL_MS = 60_000L

/** 자동모드 1분 알람. 자동모드인 위젯이 없으면 다음 알람을 걸지 않아 저절로 멈춘다. */
object AutoTick {
    /**
     * WorkManager는 최소 15분이라 1분 간격에 AlarmManager를 쓴다.
     * 기기를 깨우지 않는 알람이라 화면이 꺼져 있으면 멈췄다가 켜질 때 이어진다.
     */
    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, AutoTickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = SystemClock.elapsedRealtime() + INTERVAL_MS
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canExact) {
            manager.setExact(AlarmManager.ELAPSED_REALTIME, at, intent)
        } else {
            manager.set(AlarmManager.ELAPSED_REALTIME, at, intent)
        }
    }
}

/** 알람이 울리면 다음 종목으로 넘긴다. 재부팅하면 알람이 사라지므로 다시 건다. */
class AutoTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AutoTick.schedule(context)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (advanceAutoWidgets(context)) {
                    AutoTick.schedule(context)
                    // 알람이 화면이 켜져 있을 때만 울리므로 시세도 그때만 받는다
                    val last = Repository.forContext(context).lastRefreshMillis()
                    if (refreshDue(System.currentTimeMillis(), last)) RefreshWorker.runOnce(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
