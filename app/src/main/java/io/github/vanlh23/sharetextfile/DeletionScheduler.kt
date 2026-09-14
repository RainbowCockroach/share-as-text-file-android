package io.github.vanlh23.sharetextfile

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration

class DeletionScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    /** Replaces any earlier schedule for this id. */
    fun schedule(id: String, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<DeleteShareWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf(DeleteShareWorker.KEY_ID to id))
            .build()
        workManager.enqueueUniqueWork("delete-share-$id", ExistingWorkPolicy.REPLACE, request)
    }
}
