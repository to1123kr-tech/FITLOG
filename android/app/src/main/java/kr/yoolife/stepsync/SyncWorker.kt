package kr.yoolife.stepsync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** 앱을 열지 않아도 주기적으로 걸음수를 올린다. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = try {
        Log.i(TAG, "백그라운드 동기화 시작")
        val msg = StepsSync.run(applicationContext)
        Log.i(TAG, "백그라운드 동기화 성공: ${msg.replace('\n', ' ')}")
        Result.success()
    } catch (t: Throwable) {
        // 네트워크 문제 등은 잠시 후 재시도.
        // Throwable 로 잡는 이유는 §5-14 (NoClassDefFoundError 를 Exception 으로 못 잡는다)
        Log.w(TAG, "백그라운드 동기화 실패: ${t.javaClass.simpleName}: ${t.message}")
        Result.retry()
    }

    companion object {
        private const val TAG = "StepSync"
        private const val NAME = "steps-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
