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
import com.tmfrl.marketwidget.data.CustomRepository
import com.tmfrl.marketwidget.data.Repository
import com.tmfrl.marketwidget.data.SettingsStore
import com.tmfrl.marketwidget.widget.redrawAllWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** 30분마다 data.json과 현재 시세, 추가 종목을 받아 위젯을 다시 그린다. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        // 기본 지표와 추가 종목을 함께 받는다. 한쪽이 실패해도 다른 쪽은 반영한다.
        coroutineScope {
            launch { quietly { Repository.forContext(context).refresh() } }
            launch { quietly { CustomRepository.forContext(context).refresh(SettingsStore.forContext(context).read()) } }
        }
        redrawAllWidgets(context)
        return Result.success()
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 받지 못한 쪽은 캐시가 그대로 남는다
        }
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
