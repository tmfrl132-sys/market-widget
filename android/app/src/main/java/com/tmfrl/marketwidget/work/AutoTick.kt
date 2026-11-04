package com.tmfrl.marketwidget.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.tmfrl.marketwidget.data.Repository
import com.tmfrl.marketwidget.model.keepTicking
import com.tmfrl.marketwidget.model.refreshDue
import com.tmfrl.marketwidget.widget.advanceAutoWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val INTERVAL_MS = 60_000L

/** 1분 알람. 자동모드 순환과, 화면이 켜져 있을 때의 시세 갱신을 맡는다. 위젯이 없으면 다음 알람을 걸지 않는다. */
object AutoTick {
    private fun intent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, AutoTickReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * WorkManager는 최소 15분이라 1분 간격에 AlarmManager를 쓴다.
     * 기기를 깨우지 않는 알람이라 기기가 잠들면 쉬었다가, 화면이 켜져 깨어날 때 밀린 알람이 바로 울린다.
     */
    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val at = SystemClock.elapsedRealtime() + INTERVAL_MS
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canExact) {
            manager.setExact(AlarmManager.ELAPSED_REALTIME, at, intent(context))
        } else {
            manager.set(AlarmManager.ELAPSED_REALTIME, at, intent(context))
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
    }
}

/** 알람이 울리면 자동모드 종목을 넘기고, 시세가 오래됐으면 새로 받는다. 재부팅하면 알람이 사라지므로 다시 건다. */
class AutoTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 기기가 깨어 있어도 화면이 꺼져 있으면 넘기지도 받지도 않고 알람만 잇는다
        val screenOff = !context.getSystemService(PowerManager::class.java).isInteractive
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || screenOff) {
            AutoTick.schedule(context)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (keepTicking { advanceAutoWidgets(context) }) {
                    AutoTick.schedule(context)
                    val last = Repository.forContext(context).lastRefreshMillis()
                    if (refreshDue(System.currentTimeMillis(), last)) RefreshWorker.runOnce(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
