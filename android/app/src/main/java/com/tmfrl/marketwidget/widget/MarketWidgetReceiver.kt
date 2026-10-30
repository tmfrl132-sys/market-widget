package com.tmfrl.marketwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.tmfrl.marketwidget.data.Repository
import com.tmfrl.marketwidget.work.RefreshWorker

class MarketWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MarketWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
        RefreshWorker.runOnce(context)
    }
}

/** 위젯을 누르면 바로 새로 받는다. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repository.forContext(context).refresh()
        MarketWidget().updateAll(context)
        RefreshWorker.schedule(context)
    }
}
