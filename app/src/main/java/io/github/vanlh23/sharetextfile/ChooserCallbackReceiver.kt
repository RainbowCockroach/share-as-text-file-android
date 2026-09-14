package io.github.vanlh23.sharetextfile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.vanlh23.sharetextfile.core.ShareStore
import java.io.File

/** The share sheet sends this broadcast when the user picks a target. */
class ChooserCallbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_SHARE_ID) ?: return
        ShareStore(File(context.cacheDir, ShareStore.DIR_NAME)).markChosen(id)
        DeletionScheduler(context).schedule(id, Timings.GRACE_AFTER_CHOICE)
    }

    companion object {
        const val EXTRA_SHARE_ID = "io.github.vanlh23.sharetextfile.extra.SHARE_ID"
    }
}
