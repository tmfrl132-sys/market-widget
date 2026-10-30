package com.tmfrl.marketwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import com.tmfrl.marketwidget.work.RefreshWorker

class MarketWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MarketWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
        RefreshWorker.runOnce(context)
    }
}

/**
 * 위젯을 누르면 바로 새로 받는다.
 * 받는 일은 WorkManager에 맡긴다. 이 콜백은 수신기 시간 제한 안에서 돌아서 네트워크를 기다리면 끊길 수 있다.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.runOnce(context)
        RefreshWorker.schedule(context)
    }
}
