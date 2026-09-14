# PROGRESS

> **Agents:** update this file at the end of every session. Tick tasks, add a session-log line, and record findings and questions. Task details are in [`docs/PLAN.md`](docs/PLAN.md).

## Status

- **Current milestone:** M3/M4 — code complete; device lifecycle tests and real-device verification remain
- **Next task:** T-3.3 (run MT-10…MT-13 on a device), then T-4.1
- **Blocked:** no (CI release needs the signing secrets, see "Open questions")

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
- [ ] T-3.3 Robustness: rotation, process death, reboot, force-stop — code done (`shareId` saved/restored, pipeline not redone); MT-10…MT-13 not run yet

### M4 — Real-device verification
- [ ] T-4.1 Verification items V-1…V-5
- [ ] T-4.2 Cross-OS file check

### M5 — Release readiness
- [x] T-5.1 Lint and cleanup — 0 errors, 0 warnings
- [ ] T-5.2 Release build — minified, signed release APK builds (295 KB); MT-1/7/8 not yet re-run on the release build
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

| ID | Device / Android | Result | Notes |
|---|---|---|---|
| MT-3 | Emulator, Android 16 (API 36) | Pass | Share sheet lists Chrome, Drive, Messages, Quick Share…, not "Save as .txt" |
| MT-4 | Emulator, Android 16 | Partial | Blank `EXTRA_TEXT` → nothing written. Stream-only share from a file manager not tried |
| MT-6 | Emulator, Android 16 | Pass | One `cache/share/<uuid>/Hello from adb_2026-09-14_17-25-30.txt`, starts with `EF BB BF` |
| MT-7 | Emulator, Android 16 (fastTimers) | Pass | `PROCESS_TEXT` → picked Messages → `.chosen` created, dir gone after ~80 s |
| MT-8 | Emulator, Android 16 (fastTimers) | Pass | Back on the share sheet → dir gone within 35 s |

## Verification results

| ID | Device / Android | Result | Notes |
|---|---|---|---|
| V-1 | Emulator, Android 16 | Pass (Messages only) | Receiver fired (`.chosen` written). Still needs a physical phone and more targets |
| V-2 | | | |
| V-3 | | | |
| V-4 | | | |
| V-5 | | | |

## Open questions / findings

- **CI signing secrets needed.** The release workflow fails until the repo has the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. A local keystore (`release.keystore` + `keystore.properties`, both gitignored) was generated; back it up, because releases signed with a different key can't update an installed app.
- AGP 9 has built-in Kotlin, so `org.jetbrains.kotlin.android` is **not applied**. It is only declared `apply false` in the root build to pin Kotlin 2.4.20.
- Lint warned `DataExtractionRules` for `allowBackup="false"`. Suppressed in the manifest with a reason: the app only has cache files, which are never backed up.
- core-ktx merges in a signature permission `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. It is not a runtime permission, so hard rule 2 still holds.
- `FileNamer`: when the subject sanitizes to empty it is treated as absent. Unpaired surrogates in the text are treated like control characters (replaced with a space), since they can't be encoded in a file name.

## Session log

| Date | Task(s) | Summary |
|---|---|---|
| 2026-09-14 | — | Design and documentation written (SPEC, ARCHITECTURE, DECISIONS, PLAN, TESTING). |
| 2026-09-14 | T-0.1…T-3.2, T-5.1, T-5.3, CI | Whole app implemented with 46 unit tests; lint clean; signed release APK builds. Smoke-tested MT-3/6/7/8 on an Android 16 emulator. Added GitHub Actions release workflow. |
