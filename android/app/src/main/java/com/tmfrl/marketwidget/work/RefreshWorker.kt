package com.tmfrl.marketwidget.work

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tmfrl.marketwidget.data.Repository
import com.tmfrl.marketwidget.widget.redrawAllWidgets
import java.util.concurrent.TimeUnit

/** 30분마다 data.json과 현재 시세를 받아 위젯을 다시 그린다. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Repository.forContext(applicationContext).refresh()
        redrawAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "market-refresh"
        private const val NOW = "market-refresh-now"

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runOnce(context: Context) {
            val builder = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network)
            // 거의 열지 않는 앱은 안드로이드가 작업을 크게 늦춰서 바로 돌게 요청한다.
            // 안드로이드 11 이하는 알림이 필요한 방식이라 쓰지 않는다.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, builder.build())
        }
    }
}
