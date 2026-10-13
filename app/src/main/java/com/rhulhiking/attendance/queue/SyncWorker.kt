package com.rhulhiking.attendance.queue

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rhulhiking.attendance.settings.SettingsRepository
import com.rhulhiking.attendance.util.DebugLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val flusher: QueueFlusher,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = flusher.flush(settings.current().columns)
        DebugLog.d(TAG) { "Queue flush: $result" }
        return when (result.status) {
            FlushResult.Status.Done -> Result.success()
            // NeedsAuth also retries: it clears once the app is opened and access re-granted.
            FlushResult.Status.Retry, FlushResult.Status.NeedsAuth -> Result.retry()
        }
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}

@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    /** Flushes the queue as soon as there's a network connection. */
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE so an entry queued while a flush is running gets its own pass.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "flush-pending-check-ins"
    }
}
