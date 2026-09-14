# PLAN

Milestones in order. Each task has an ID. Track status in [`PROGRESS.md`](../PROGRESS.md), not here. "Arch" references point to [`ARCHITECTURE.md`](ARCHITECTURE.md).

---

## M0 — Project skeleton

### T-0.1 Gradle project
- Create the single-module Android project (`:app`) with Kotlin DSL and `gradle/libs.versions.toml`. Stack per AGENTS.md.
- `applicationId`/`namespace` = `io.github.vanlh23.sharetextfile`, `minSdk 26`, latest stable `compileSdk`/`targetSdk`, JDK 17 toolchain.
- Add Gradle wrapper and a `.gitignore` (standard Android: `.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `.DS_Store`).
- **Accept:** `./gradlew assembleDebug test lint` succeeds on a clean clone.

### T-0.2 Manifest, resources, empty activity
- Manifest exactly as in Arch §4 (the receiver and provider can reference classes created as stubs).
- Translucent theme, strings, `file_paths.xml`, simple adaptive launcher icon (a plain "TXT" glyph is fine).
- `ShareToFileActivity` stub that just calls `finish()`.
- `BuildConfig` timing fields plus `fastTimers` property, and `Timings.kt` (Arch §3.5, §7).
- **Accept:** installs. "Save as .txt" appears in the share sheet for text and in Chrome's text-selection menu. Tapping it closes instantly with nothing visible. No launcher activity is declared.

## M1 — Pure logic with unit tests (no `android.*` imports)

### T-1.1 `SharedTextExtractor` — Arch §3.1
- **Accept:** all cases in TESTING §2.1 pass.

### T-1.2 `TextEncoding` — Arch §3.3
- **Accept:** TESTING §2.2 passes.

### T-1.3 `FileNamer` — Arch §5
- **Accept:** TESTING §2.3 passes, including Vietnamese, CJK, Arabic, emoji ZWJ and flag truncation.

### T-1.4 `ShareStore` — Arch §3.4
- **Accept:** TESTING §2.4 passes (uses `TemporaryFolder`).

## M2 — Share pipeline (file is created and shared; deletion is only the safety job)

### T-2.1 FileProvider + `ShareIntents` — Arch §3.9
- **Accept:** code compiles; review against hard rules 5 and 7 in AGENTS.md.

### T-2.2 `ShareToFileActivity` happy path — Arch §3.10
- Sweep → extract → name → write on IO → launch chooser → `finish()` in the result callback. Failure toasts.
- For now, schedule only `SAFETY_TTL` (needs `DeletionScheduler` + `DeleteShareWorker`, Arch §3.6–3.7; build those here).
- **Accept:** TESTING `MT-1`…`MT-6` pass.

## M3 — Temporary file lifecycle

### T-3.1 `ChooserCallbackReceiver` + mutable explicit PendingIntent — Arch §3.8–3.9
- **Accept:** after picking a target, `.chosen` exists and the job for that id is rescheduled (check with `dumpsys jobscheduler`).

### T-3.2 Result handling: grace vs cancel delay — Arch §3.10, §6.2
- **Accept:** TESTING `MT-7`…`MT-9` pass with `-PfastTimers=true`.

### T-3.3 Robustness: rotation, process death, reboot, force-stop — Arch §6.2
- Save and restore `shareId`, and don't redo the pipeline on recreate.
- **Accept:** TESTING `MT-10`…`MT-13` pass.

## M4 — Real-device verification

### T-4.1 Run the verification items
- Execute `V-1`…`V-5` in TESTING §4 on at least one physical phone (ideally Android 14+ and one older, e.g. 10–12).
- Record the results in PROGRESS.md.
- If `V-3` shows a target reading later than `GRACE_AFTER_CHOICE`, raise the constant and note it in DECISIONS (ADR-012 follow-up).
- **Accept:** all V-items recorded; any failures either fixed or written up as open questions for the user.

### T-4.2 Cross-OS file check
- Share files made from the TESTING §5 samples to Drive, then open them on Windows, macOS, iOS and Android.
- **Accept:** characters display correctly everywhere; file names are intact.

## M5 — Release readiness

### T-5.1 Lint and cleanup
- Zero lint errors. Warnings either fixed or suppressed with a one-line reason.

### T-5.2 Release build
- `isMinifyEnabled`/`isShrinkResources` on; build a release APK signed with a local keystore that is **not committed** (read from `keystore.properties`, gitignored).
- Re-run `MT-1`, `MT-7` and `MT-8` on the release build.
- **Accept:** release build behaves identically; APK size noted in PROGRESS.md.

### T-5.3 README usage section
- How to install (`adb install`), how to use, what the app icon does (opens App info).
