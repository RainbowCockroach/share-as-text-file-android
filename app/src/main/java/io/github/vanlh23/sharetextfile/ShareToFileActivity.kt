package io.github.vanlh23.sharetextfile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import io.github.vanlh23.sharetextfile.core.FileNamer
import io.github.vanlh23.sharetextfile.core.ShareStore
import io.github.vanlh23.sharetextfile.core.SharedTextExtractor
import io.github.vanlh23.sharetextfile.core.TextEncoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

/**
 * Turns shared or selected text into a temporary .txt file and opens the share sheet for it.
 * Never calls setResult(RESULT_OK): for PROCESS_TEXT that would replace the user's selected text.
 */
class ShareToFileActivity : ComponentActivity() {
    private val store by lazy { ShareStore(File(cacheDir, ShareStore.DIR_NAME)) }
    private val scheduler by lazy { DeletionScheduler(this) }
    private var shareId: String? = null

    // The share sheet doesn't forward the target's result, so this only means "the share sheet closed"
    private val chooserLauncher = registerForActivityResult(StartActivityForResult()) {
        shareId?.let { id ->
            scheduler.schedule(id, if (store.isChosen(id)) Timings.GRACE_AFTER_CHOICE else Timings.CANCEL_DELAY)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shareId = savedInstanceState?.getString(KEY_SHARE_ID)
        // Recreated while the share sheet is open; the result callback will still arrive
        if (shareId != null) return

        lifecycleScope.launch { shareText() }
    }

    private suspend fun shareText() {
        withContext(Dispatchers.IO) {
            store.sweep(System.currentTimeMillis(), Timings.SAFETY_TTL.toMillis())
        }
        val shared = SharedTextExtractor.extract(
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT),
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT),
        ) ?: return toastAndFinish(R.string.nothing_to_save)

        val name = FileNamer.fileName(shared, LocalDateTime.now())
        val entry = try {
            withContext(Dispatchers.IO) { store.create(name, TextEncoding.encode(shared.text)) }
        } catch (_: IOException) {
            return toastAndFinish(R.string.could_not_create_file)
        }
        scheduler.schedule(entry.id, Timings.SAFETY_TTL)
        shareId = entry.id

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", entry.file)
        chooserLauncher.launch(ShareIntents.buildChooser(this, uri, name, entry.id))
    }

    private fun toastAndFinish(@StringRes message: Int) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SHARE_ID, shareId)
    }

    private companion object {
        const val KEY_SHARE_ID = "share_id"
    }
}
