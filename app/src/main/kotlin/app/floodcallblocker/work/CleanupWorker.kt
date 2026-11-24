package app.floodcallblocker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.floodcallblocker.FloodApp
import java.util.concurrent.TimeUnit

/** Daily cleanup: old log lines, stale call timestamps, expired blocks (SPEC F-42). */
class CleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as FloodApp).container.repository.maintenance()
        return Result.success()
    }

    companion object {
        private const val NAME = "cleanup"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
