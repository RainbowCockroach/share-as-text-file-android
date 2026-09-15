# PROGRESS

> **Agents:** update this file at the end of every session. Tick tasks, add a session-log line, and record findings and questions. Task details are in [`docs/PLAN.md`](docs/PLAN.md).

## Status

- **Current milestone:** M4 — real-device verification
- **Next task:** T-4.1 (needs a physical phone and real target apps)
- **Blocked:** partly. Waiting on a user decision about the "stale task" finding, and on CI signing secrets (see "Open questions")

## Tasks

### M0 — Project skeleton
- [x] T-0.1 Gradle project
- [x] T-0.2 Manifest, resources, empty activity

### M1 — Pure logic with unit tests
- [x] T-1.1 `SharedTextExtractor`
- [x] T-1.2 `TextEncoding`
- [x] T-1.3 `FileNamer`
- [x] T-1.4 `ShareStore`

### M2 — Share pipeline
- [x] T-2.1 FileProvider + `ShareIntents`
- [x] T-2.2 `ShareToFileActivity` happy path (+ `DeletionScheduler`, `DeleteShareWorker`) — MT-3, MT-6 checked on emulator; MT-1, MT-2, MT-4, MT-5 need real apps

### M3 — Temporary file lifecycle
- [x] T-3.1 `ChooserCallbackReceiver`
- [x] T-3.2 Result handling: grace vs cancel delay — MT-7, MT-8 checked on emulator
- [x] T-3.3 Robustness: rotation, process death, reboot, force-stop — MT-10, MT-11, MT-13 pass; MT-12 passes with a caveat (see findings)

### M4 — Real-device verification
- [ ] T-4.1 Verification items V-1…V-5
- [ ] T-4.2 Cross-OS file check

### M5 — Release readiness
- [x] T-5.1 Lint and cleanup — 0 errors, 0 warnings
- [x] T-5.2 Release build — minified, signed, 302 KB (295 KiB). Cancel and pick flows re-run on the release build (Messages as target; Drive still to try on a phone)
- [x] T-5.3 README usage section

### Extra (user request) — CI release
- [x] `.github/workflows/release.yml`: every push to `main` runs tests + lint, builds a signed release APK, publishes it as a GitHub release `v1.0.<run number>` and deletes all older releases and their tags

## Build environment

| Item | Version |
|---|---|
| Android Gradle Plugin | 9.4.0 |
| Gradle | 9.7.1 |
| Kotlin | 2.4.20 (AGP built-in Kotlin) |
| compileSdk / targetSdk | 37 / 37 |
| WorkManager | 2.11.2 |
| core-ktx / activity-ktx / coroutines | 1.19.0 / 1.13.0 / 1.11.0 |
| JDK toolchain | 17 (auto-provisioned by the foojay resolver when missing) |

## Unit tests

46 tests, all passing: `FileNamerTest` 26, `ShareStoreTest` 10, `SharedTextExtractorTest` 6, `TextEncodingTest` 4.

## Manual test results

All on the `Medium_Phone_API_36.0` emulator (Android 16), debug build with `-PfastTimers=true` unless noted. Messages was the only target app used.

| ID | Device / Android | Result | Notes |
|---|---|---|---|
| MT-3 | Emulator, Android 16 | Pass | Share sheet lists Chrome, Drive, Messages, Quick Share…, not "Save as .txt". Same on the release build |
| MT-4 | Emulator, Android 16 | Partial | Blank `EXTRA_TEXT` → nothing written. Stream-only share from a file manager not tried |
| MT-6 | Emulator, Android 16 | Pass | One `cache/share/<uuid>/Hello from adb_2026-09-14_17-25-30.txt`, starts with `EF BB BF` |
| MT-7 | Emulator, Android 16 | Pass | `PROCESS_TEXT` → picked Messages → `.chosen` created, dir gone after ~80 s. Release build: Messages read all 15 bytes, delete job moved to ~60 s |
| MT-8 | Emulator, Android 16 | Pass | Back on the share sheet → dir gone within 35 s. Release build: ~20 s job queued, gone 30 s later |
| MT-10 | Emulator, Android 16 | Pass | Rotated to landscape and back with the share sheet open → still one file, no crash, `.chosen` after pick, deleted after grace |
| MT-11 | Emulator, Android 16 | Pass | `kill -9` of our process with the share sheet open → picked Messages → receiver started a new process, `.chosen` written, Messages read the full file, dir deleted after grace. (`am kill` does nothing here: our process counts as foreground while its task is visible) |
| MT-12 | Emulator, Android 16 | Pass with caveat | Cancel, wait 5 s, reboot → WorkManager's `RescheduleReceiver` got `BOOT_COMPLETED` ~26 s after boot, file deleted 31 s after boot. **But** a reboot ~1 s after cancelling left the file in place for 3+ min after boot (see findings) |
| MT-13 | Emulator, Android 16 | Pass | Force-stop removed all our jobs; the old file was still there after 200 s; the next share's sweep removed it and only the new dir remained |

## Verification results

| ID | Device / Android | Result | Notes |
|---|---|---|---|
| V-1 | Emulator, Android 16 | Pass (Messages only) | Receiver fired (`.chosen` written), also after our process was killed. Still needs a physical phone and more targets |
| V-2 | | | |
| V-3 | | | |
| V-4 | | | |
| V-5 | | | |

## Open questions / findings

- **Stale task can swallow a new share (needs a decision).** Shared "First share" to Messages, pressed Home, then launched a second share with `adb shell am start`. Android replied "Activity not started, its current task has been brought to the front": Messages came back, no share sheet appeared and "Second share" was never saved. Cause: the first `ShareToFileActivity` still sits at the root of its task under Messages, waiting for the share-sheet result, and `taskAffinity=""` + `FLAG_ACTIVITY_NEW_TASK` makes Android reuse that task. `am start` always adds `NEW_TASK`. **Not yet checked** whether real source apps (Chrome share, `PROCESS_TEXT`) launch us with `NEW_TASK`; if they don't, this only affects adb. Possible fixes, which change ARCHITECTURE §4, so not applied: `android:documentLaunchMode="always"` or `FLAG_ACTIVITY_MULTIPLE_TASK` behaviour for this activity, or dropping `taskAffinity=""`. Check with a real app on a phone first (add to T-4.1).
- **Reboot right after sharing can skip the scheduled delete.** WorkManager's database still had the job (`ENQUEUED`, 20 s delay), but after the reboot nothing woke the app and `RescheduleReceiver` was listed under `disabledComponents`. With a 5 s gap before rebooting it worked. Likely (not verified) Android hadn't yet written the "receiver enabled" state to disk. The file is still removed by the next share's sweep or when WorkManager next starts, so no leak beyond "next app run". No change made.
- **CI signing secrets needed.** The release workflow fails until the repo has the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. A local keystore (`release.keystore` + `keystore.properties`, both gitignored) was generated; back it up, because releases signed with a different key can't update an installed app.
- AGP 9 has built-in Kotlin, so `org.jetbrains.kotlin.android` is **not applied**. It is only declared `apply false` in the root build to pin Kotlin 2.4.20.
- Lint warned `DataExtractionRules` for `allowBackup="false"`. Suppressed in the manifest with a reason: the app only has cache files, which are never backed up.
- core-ktx merges in a signature permission `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. It is not a runtime permission, so hard rule 2 still holds.
- `FileNamer`: when the subject sanitizes to empty it is treated as absent. Unpaired surrogates in the text are treated like control characters (replaced with a space), since they can't be encoded in a file name.
- The release build isn't debuggable, so `run-as` can't list its cache. Release checks used `dumpsys jobscheduler` and the target app's log instead.

## Session log

| Date | Task(s) | Summary |
|---|---|---|
| 2026-09-14 | — | Design and documentation written (SPEC, ARCHITECTURE, DECISIONS, PLAN, TESTING). |
| 2026-09-14 | T-0.1…T-3.2, T-5.1, T-5.3, CI | Whole app implemented with 46 unit tests; lint clean; signed release APK builds. Smoke-tested MT-3/6/7/8 on an Android 16 emulator. Added GitHub Actions release workflow. |
| 2026-09-15 | T-3.3, T-5.2 | Pushed work to `develop`. Ran MT-10…MT-13 and release-build checks on the emulator. Found the stale-task issue and the reboot-timing caveat. Replaced raw control bytes in `FileNamerTest` that made git treat it as binary. |
