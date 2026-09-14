package io.github.vanlh23.sharetextfile

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.vanlh23.sharetextfile.core.ShareStore
import java.io.File

class DeleteShareWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        inputData.getString(KEY_ID)?.let { id ->
            ShareStore(File(applicationContext.cacheDir, ShareStore.DIR_NAME)).delete(id)
        }
        return Result.success()
    }

    companion object {
        const val KEY_ID = "id"
    }
}
