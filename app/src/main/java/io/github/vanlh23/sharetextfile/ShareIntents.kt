package io.github.vanlh23.sharetextfile

import android.app.PendingIntent
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object ShareIntents {
    fun buildChooser(context: Context, uri: Uri, fileName: String, shareId: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData is what makes the URI grant reach the chosen app
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Never add EXTRA_TEXT: targets would use the raw text instead of the file
        }
        // Mutable so the system can fill in EXTRA_CHOSEN_COMPONENT; explicit, as Android 14+ requires
        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(
            context,
            shareId.hashCode(),
            Intent(context, ChooserCallbackReceiver::class.java)
                .putExtra(ChooserCallbackReceiver.EXTRA_SHARE_ID, shareId),
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
        )
        return Intent.createChooser(send, null, callback.intentSender).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(context, ShareToFileActivity::class.java)),
            )
        }
    }
}
