package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.FirebaseReportRepository
import com.example.firebase.FirebaseManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that reconciles queued offline medical report deletions once
 * network connectivity is restored and an authenticated Firebase user is active.
 */
class PendingDeletionSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

  companion object {
    private const val TAG = "NexzyDeletionWorker"
    private const val UNIQUE_WORK_NAME = "nexzy_pending_report_deletion_sync"

    fun enqueue(context: Context) {
      try {
        val constraints = Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()

        val request = OneTimeWorkRequestBuilder<PendingDeletionSyncWorker>()
          .setConstraints(constraints)
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
          .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
          UNIQUE_WORK_NAME,
          ExistingWorkPolicy.REPLACE,
          request
        )
      } catch (e: Exception) {
        Log.w(TAG, "Unable to enqueue pending deletion sync work: ${e.javaClass.simpleName}")
      }
    }
  }

  override suspend fun doWork(): Result {
    val activeUid = try {
      if (FirebaseManager.isInitialized) FirebaseManager.auth.currentUser?.uid else null
    } catch (_: Exception) {
      null
    }

    if (activeUid.isNullOrBlank()) {
      Log.d(TAG, "Skipping offline deletion sync because no authenticated session is active.")
      return Result.success()
    }

    return try {
      val repository = FirebaseReportRepository.createDefault(applicationContext)
      val remainingCount = repository.reconcilePendingDeletions(activeUid)
      if (remainingCount > 0) {
        Log.i(TAG, "Offline deletion sync has pending items remaining; scheduling retry.")
        Result.retry()
      } else {
        Log.i(TAG, "Offline deletion sync completed cleanly.")
        Result.success()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Offline deletion worker failed: ${e.javaClass.simpleName}")
      Result.retry()
    }
  }
}
