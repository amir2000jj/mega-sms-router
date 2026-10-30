package com.megasmsrouter.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

interface DeliveryScheduler {
    fun enqueue(pendingId: String)
}

/**
 * WorkManager is NOT the SMS receiver; it only delivers the HTTP request later, with a network
 * constraint and exponential back-off. The work input contains just an opaque id: the (encrypted)
 * payload lives in the local database and is deleted when delivery ends.
 */
class WorkManagerScheduler(private val context: Context) : DeliveryScheduler {
    override fun enqueue(pendingId: String) {
        val request = OneTimeWorkRequestBuilder<DeliveryWorker>()
            .setInputData(workDataOf(DeliveryWorker.KEY_PENDING_ID to pendingId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("msr-delivery-$pendingId", ExistingWorkPolicy.KEEP, request)
    }
}
