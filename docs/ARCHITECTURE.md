# ARCHITECTURE

## 1. Overview

```
 Source app (Chrome…)
   │  ACTION_SEND text/plain  ─or─  ACTION_PROCESS_TEXT
   ▼
 ShareToFileActivity  (translucent, no layout)
   │ 1. sweep leftovers older than SAFETY_TTL           ── ShareStore
   │ 2. extract text                                      ── SharedTextExtractor
   │ 3. build file name                                   ── FileNamer
   │ 4. write cache/share/<uuid>/<name>.txt (BOM+UTF-8)   ── ShareStore + TextEncoding   (Dispatchers.IO)
   │ 5. schedule deletion in SAFETY_TTL                   ── DeletionScheduler (WorkManager)
   │ 6. launch share sheet with content:// URI            ── FileProvider + ShareIntents
   ▼
 Android share sheet ──(user picks app)──► ChooserCallbackReceiver
   │                                          markChosen(uuid); reschedule deletion in GRACE_AFTER_CHOICE
   │ (share sheet closes)
   ▼
 ShareToFileActivity result callback
   │  chosen?  → reschedule deletion in GRACE_AFTER_CHOICE
   │  not yet? → reschedule deletion in CANCEL_DELAY
   ▼
 finish()                                  … later …  DeleteShareWorker deletes cache/share/<uuid>/
```

## 2. Project layout

```
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
app/
  build.gradle.kts
  proguard-rules.pro
  src/main/
    AndroidManifest.xml
    res/values/strings.xml
    res/values/themes.xml               (translucent theme only)
    res/xml/file_paths.xml
    res/mipmap-*/ic_launcher*           (adaptive icon; shown on App info)
    java/io/github/vanlh23/sharetextfile/
      ShareToFileActivity.kt            Android: entry point, orchestration
      ChooserCallbackReceiver.kt        Android: "user picked an app" signal
      DeleteShareWorker.kt              Android: WorkManager job
      DeletionScheduler.kt              Android: WorkManager wrapper
      ShareIntents.kt                   Android: builds outgoing SEND + chooser intents
      Timings.kt                        constants (read from BuildConfig)
      core/SharedTextExtractor.kt       pure JVM
      core/FileNamer.kt                 pure JVM
      core/TextEncoding.kt              pure JVM
      core/ShareStore.kt                pure JVM (java.io.File)
  src/test/java/io/github/vanlh23/sharetextfile/core/
      SharedTextExtractorTest.kt
      FileNamerTest.kt
      TextEncodingTest.kt
      ShareStoreTest.kt
```

## 3. Components

### 3.1 `core/SharedTextExtractor` (pure)
```kotlin
data class SharedText(val text: String, val subject: String?)

object SharedTextExtractor {
    /** Returns null when there is nothing to save. */
    fun extract(processText: CharSequence?, sendText: CharSequence?, subject: CharSequence?): SharedText?
}
```
- `text = (processText ?: sendText)?.toString()`. Return `null` if it is null or `isBlank()`.
- **Do not trim or otherwise change** `text`.
- `subject` = `subject?.toString()?.takeIf { it.isNotBlank() }`.
- The activity reads the extras with `getCharSequenceExtra(...)`, because some apps send `Spanned`.

### 3.2 `core/FileNamer` (pure)
`fun fileName(shared: SharedText, now: LocalDateTime): String`. The rules are in §5.

### 3.3 `core/TextEncoding` (pure)
```kotlin
object TextEncoding {
    val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    fun encode(text: String): ByteArray   // BOM + text.toByteArray(Charsets.UTF_8)
}
```

### 3.4 `core/ShareStore` (pure; root dir is injected)
```kotlin
class ShareStore(private val root: File) {          // root = File(context.cacheDir, "share")
    data class Entry(val id: String, val file: File)

    fun create(fileName: String, bytes: ByteArray): Entry  // mkdirs root/<uuid>/, write file; on IOException delete the dir and rethrow
    fun markChosen(id: String)                             // creates root/<id>/.chosen (no-op if dir is gone)
    fun isChosen(id: String): Boolean
    fun delete(id: String)                                 // deleteRecursively root/<id>; idempotent
    fun sweep(nowMillis: Long, maxAgeMillis: Long)         // delete every child dir whose lastModified < now - maxAge
}
```
- `id` is a random UUID string. Every public method taking an `id` **must validate it against a UUID regex** and ignore invalid ids, so an id can never cause path traversal.
- Write with `FileOutputStream(file).use { it.write(bytes) }`. No fsync needed.
- The `.chosen` marker sits next to the shared file but is never exposed: the FileProvider URI points only at the `.txt` file.

### 3.5 `Timings`
```kotlin
object Timings {
    val SAFETY_TTL: Duration          // release: 60 min   | fastTimers: 3 min
    val GRACE_AFTER_CHOICE: Duration  // release: 15 min   | fastTimers: 1 min
    val CANCEL_DELAY: Duration        // release: 1 min    | fastTimers: 20 s
}
```
Values come from `BuildConfig` fields set in `app/build.gradle.kts`. When the Gradle property `fastTimers=true` is present, all build types use the short values (for manual testing only).

### 3.6 `DeletionScheduler`
```kotlin
class DeletionScheduler(context: Context) {
    fun schedule(id: String, delay: Duration)   // replaces any earlier schedule for this id
}
```
- `WorkManager.enqueueUniqueWork("delete-share-$id", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<DeleteShareWorker>().setInitialDelay(delay).setInputData(workDataOf("id" to id)).build())`
- No constraints. The work must run even without network or charging.

### 3.7 `DeleteShareWorker`
A `CoroutineWorker` or `Worker`: `ShareStore(File(applicationContext.cacheDir, "share")).delete(inputData.getString("id"))`, then `Result.success()`. It never retries or fails. A missing directory counts as success.

### 3.8 `ChooserCallbackReceiver` (manifest-declared, `exported="false"`)
- Receives the broadcast the share sheet sends through the `IntentSender` when the user **picks a target** (`EXTRA_CHOSEN_COMPONENT`).
- Reads our extra `EXTRA_SHARE_ID` → `store.markChosen(id)` → `scheduler.schedule(id, GRACE_AFTER_CHOICE)`.
- The work is tiny, so no `goAsync()` is needed.

### 3.9 `ShareIntents`
```kotlin
fun buildChooser(context: Context, uri: Uri, fileName: String, shareId: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(fileName, uri)   // makes the URI grant reach the chosen app
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // NEVER putExtra(EXTRA_TEXT) — targets would use the raw text instead of the file
    }
    val callback = PendingIntent.getBroadcast(
        context,
        shareId.hashCode(),
        Intent(context, ChooserCallbackReceiver::class.java).putExtra(EXTRA_SHARE_ID, shareId),
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0) // system must fill in EXTRA_CHOSEN_COMPONENT
    )
    return Intent.createChooser(send, null, callback.intentSender).apply {
        putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(context, ShareToFileActivity::class.java)))
    }
}
```
The mutable `PendingIntent` is **explicit** (component set), which Android 14+ requires for mutable PendingIntents.

### 3.10 `ShareToFileActivity` (`ComponentActivity`)
```
onCreate(savedInstanceState):
    shareId = savedInstanceState?.getString(KEY_SHARE_ID)
    if shareId != null: return            // recreated while waiting for the share sheet; the result callback will still arrive

    lifecycleScope.launch {
        store.sweep(now, SAFETY_TTL)                                      // on IO
        val shared = SharedTextExtractor.extract(
            intent.getCharSequenceExtra(EXTRA_PROCESS_TEXT),
            intent.getCharSequenceExtra(EXTRA_TEXT),
            intent.getCharSequenceExtra(EXTRA_SUBJECT)) ?: return toastAndFinish(R.string.nothing_to_save)
        val name = FileNamer.fileName(shared, LocalDateTime.now())
        val entry = try { withContext(IO) { store.create(name, TextEncoding.encode(shared.text)) } }
                    catch (e: IOException) { return toastAndFinish(R.string.could_not_create_file) }
        scheduler.schedule(entry.id, SAFETY_TTL)
        shareId = entry.id
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", entry.file)
        chooserLauncher.launch(ShareIntents.buildChooser(this, uri, name, entry.id))
    }

chooserLauncher = registerForActivityResult(StartActivityForResult()) {
    val id = shareId ?: return finish()
    scheduler.schedule(id, if (store.isChosen(id)) GRACE_AFTER_CHOICE else CANCEL_DELAY)
    finish()
}

onSaveInstanceState: put KEY_SHARE_ID
```
- Never call `setResult(RESULT_OK, …)` (rule for `PROCESS_TEXT`).
- `store` and `scheduler` are created directly in the activity. No DI.
- The `IllegalArgumentException` from `getUriForFile` means the paths are misconfigured, which is a programming error. Let it crash in debug builds.

## 4. Manifest

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:allowBackup="false"
        android:supportsRtl="true">

        <activity
            android:name=".ShareToFileActivity"
            android:label="@string/action_label"
            android:theme="@style/Theme.ShareTextFile.Translucent"
            android:exported="true"
            android:excludeFromRecents="true"
            android:taskAffinity=""
            android:launchMode="standard">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.PROCESS_TEXT" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>

        <receiver android:name=".ChooserCallbackReceiver" android:exported="false" />

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```
- **No `MAIN`/`LAUNCHER` activity. No `noHistory`**: it would destroy the activity before the share-sheet result arrives.
- `res/xml/file_paths.xml`: `<paths><cache-path name="share" path="share/" /></paths>`
- Theme: parent `@android:style/Theme.Translucent.NoTitleBar`, plus `android:windowAnimationStyle` = `@null` to minimise flicker.
- Strings: `app_name` = "Share as Text File", `action_label` = "Save as .txt", `nothing_to_save` = "Nothing to save", `could_not_create_file` = "Couldn't create the file".
- WorkManager's library manifest merges in normal install-time permissions (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`). **Leave them.** `RECEIVE_BOOT_COMPLETED` is what lets pending deletions survive a reboot.

## 5. File naming rules (`FileNamer`)

Output: `<stem>_<yyyy-MM-dd_HH-mm-ss>.txt` (local time), e.g. `Tiếng Việt là ngôn ngữ_2026-09-14_15-30-12.txt`.

**Choosing the source for the stem:**
1. If the text, trimmed, is a single URL (no whitespace inside, starts with `http://` or `https://`) **and** `subject` is present → use `subject` (usually the page title).
2. Otherwise use the **first line** of the text that is not blank after sanitizing. Lines are split on `\n`, `\r`, U+2028 and U+2029.
3. If that sanitizes to empty and `subject` is present → use `subject`.
4. If still empty → `note`.

**Sanitizing** (in this order):
1. Normalize to **NFC** (`java.text.Normalizer`). This avoids decomposed Vietnamese or accented names looking odd on other systems.
2. Remove U+FEFF and bidi control characters U+200E, U+200F, U+202A–U+202E, U+2066–U+2069. They could be used to disguise the extension.
3. Replace with a space: ISO control characters (`Character.isISOControl`) and ``\ / : * ? " < > |``.
4. Collapse every run of whitespace (`Character.isWhitespace` or `Character.isSpaceChar`) into one ASCII space.
5. Trim leading and trailing spaces **and dots**. Windows rejects trailing dots, and a leading dot makes a hidden file.

**Truncating** to at most `MAX_STEM_BYTES = 64` bytes of UTF-8:
1. Take the longest prefix of whole code points that fits in 64 bytes.
2. Don't split a character sequence. While the cut position is not at the start, **step back one code point** if either
   - the code point *right after* the cut is a joiner or extender: combining mark (`Character.getType` is `NON_SPACING_MARK`, `ENCLOSING_MARK` or `COMBINING_SPACING_MARK`), ZWJ U+200D, variation selector U+FE00–U+FE0F, or emoji skin-tone modifier U+1F3FB–U+1F3FF, or
   - the code point *right before* the cut is ZWJ U+200D.
3. If the prefix ends with an **odd** number of regional-indicator code points (U+1F1E6–U+1F1FF), drop the last one so no half flag remains.
4. Trim trailing spaces and dots again. If this leaves it empty → `note`.

Resulting names are at most 64 + 20 + 4 = 88 bytes, well below the 255-byte limit of every common filesystem. No collision handling is needed because every file has its own UUID directory.

## 6. Temporary file lifecycle

### 6.1 What Android does and doesn't tell us

Verified against Android docs and AOSP source, see DECISIONS ADR-008:
- The `IntentSender` passed to `createChooser` fires **when the user picks a target**, just as it launches. Its payload includes `EXTRA_CHOSEN_COMPONENT`.
- The share sheet launches the target **without forwarding its result**. The activity-result callback therefore means "**the share sheet closed**" (picked or cancelled), **not** "the target app has finished".
- **Nothing** tells us when the target app has finished reading the file. So after a pick we wait a grace period.
- WorkManager delays are minimums. Work may run later but never earlier. Pending work survives process death and reboot.

### 6.2 State machine for one share (`id`)

| Event | Action | Deleted |
|---|---|---|
| File written | `schedule(id, SAFETY_TTL)` | ≤ 60 min (worst case) |
| User picks a target (receiver) | `markChosen(id)`; `schedule(id, GRACE_AFTER_CHOICE)` | ~15 min after picking |
| Share sheet closed and `.chosen` exists | `schedule(id, GRACE_AFTER_CHOICE)` (restarts the countdown) | ~15 min after closing |
| Share sheet closed and no `.chosen` | `schedule(id, CANCEL_DELAY)` | ~1 min after cancelling |
| Receiver fires *after* the "no .chosen" result (race) | receiver's `schedule(id, GRACE_AFTER_CHOICE)` replaces the short one | ~15 min |
| Activity destroyed without a result (killed while waiting) | nothing; the safety job is already queued | ≤ 60 min |
| App force-stopped (clears JobScheduler jobs) | WorkManager reschedules its jobs the next time the app process starts; `sweep` on the next share also removes old dirs | next app run |
| Any later share | `sweep(now, SAFETY_TTL)` first | leftovers removed |

**Why cancelling waits 1 minute instead of deleting immediately:** the receiver broadcast and the result callback can arrive in either order. On Android ≤ 14, some share-sheet actions may also not fire the pick callback. One minute is short enough to feel "gone" and avoids deleting a file a target is about to read. Both callbacks run on the main thread, so the `REPLACE` calls are applied in order.

### 6.3 Tunable constants
`GRACE_AFTER_CHOICE` should be confirmed with real target apps (TESTING `V-3`). If a target reads the file later than 15 min after picking, raise it. Never lower `SAFETY_TTL` below `GRACE_AFTER_CHOICE`.

## 7. Build configuration notes

- `app/build.gradle.kts`:
  - `buildFeatures { buildConfig = true }`
  - `val fast = providers.gradleProperty("fastTimers").orNull == "true"`, then `buildConfigField("long", "SAFETY_TTL_MS", …)` and so on
  - release: `isMinifyEnabled = true`, `isShrinkResources = true`
- R8: WorkManager and FileProvider ship their own keep rules, so no custom rules should be needed. Verify with a release build on a device (`T-5.2`).
- `java.time` and `java.text.Normalizer` are available from API 26, so no desugaring is needed.
