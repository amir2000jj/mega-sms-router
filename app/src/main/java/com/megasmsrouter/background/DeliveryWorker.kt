package com.megasmsrouter.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.megasmsrouter.MsrApp

class DeliveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_PENDING_ID) ?: return Result.failure()
        val app = applicationContext as MsrApp
        val outcome = DeliveryExecutor(
            db = app.db,
            cipher = app.cipher,
            client = app.apiClient,
            isForwardingEnabled = { app.prefs.isForwardingEnabled() }
        ).run(id, runAttemptCount)
        return if (outcome == DeliveryExecutor.Outcome.RETRY) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_PENDING_ID = "pending_id"
    }
}
