# AGENTS.md — instructions for AI coding agents

This repo is an Android app with **no UI**. It is a share-through pipeline:

```
selected text ──(Share / text-selection menu)──► this app ──► temporary .txt file ──► Android share sheet ──► Drive, Gmail, …
```

The temporary file is deleted automatically after sharing.

## Read before coding

| Doc | What it holds |
|---|---|
| [`docs/SPEC.md`](docs/SPEC.md) | What the app must do. Requirements `FR-*` / `NFR-*`, non-goals |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | How it is built: components, flows, manifest, file naming, deletion lifecycle |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Why: decision records `ADR-*` with rejected alternatives. **Do not reverse one without asking the user** |
| [`docs/PLAN.md`](docs/PLAN.md) | Milestones and tasks `T-*` with acceptance criteria |
| [`docs/TESTING.md`](docs/TESTING.md) | Unit test cases, adb commands, manual device test matrix `MT-*`, verification items `V-*` |
| [`PROGRESS.md`](PROGRESS.md) | **Current state.** Task checklist, verification results, session log, open questions |

## Workflow for each session

1. Read `PROGRESS.md` and pick the **first unchecked task** whose dependencies are done.
2. Read that task in `docs/PLAN.md` and the sections of `docs/ARCHITECTURE.md` it links to.
3. Implement it. Write or extend unit tests for all pure logic.
4. Run `./gradlew test lint assembleDebug`. All must pass.
5. Update `PROGRESS.md`: tick the task, add a session-log line, and record anything surprising under "Open questions / findings".
6. If implementation shows the design is wrong or impossible, **stop and write it down** in `PROGRESS.md` → "Open questions". Don't quietly invent a different design. Small implementation details are fine to decide yourself.
7. Commit (see "Commits").

## Hard rules

These come from the spec and must not be broken:

1. **No UI.** No launcher activity, no layouts, no Compose, no settings screen, no notifications. The only allowed visible feedback is a short `Toast` on failure.
2. **No runtime permissions, no network, no storage outside `cacheDir`.** Do not add `INTERNET`, `READ/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE` or MediaStore writes. (The normal install-time permissions that WorkManager merges in automatically are expected and fine.)
3. **File format:** `.txt`, MIME `text/plain`, **UTF-8 with BOM** (`EF BB BF`). The text is written **exactly as received** after the BOM: no trimming, no line-ending conversion.
4. **Temporary files live only in `cacheDir/share/<uuid>/`** and are always scheduled for deletion (see ARCHITECTURE §6).
5. The outgoing share intent carries **only the file** (`EXTRA_STREAM` + `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION`). **Never** also put `EXTRA_TEXT` on it.
6. For `ACTION_PROCESS_TEXT`, **never call `setResult(RESULT_OK, …)`**. Returning a result would replace the user's selected text in the source app.
7. Exclude our own activity from the share sheet (`EXTRA_EXCLUDE_COMPONENTS`).
8. Pure logic (`SharedTextExtractor`, `FileNamer`, `TextEncoding`, `ShareStore`) must not import `android.*`, so it can be unit-tested on the JVM.

## Tech stack (fixed)

- Kotlin, Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`)
- JDK 17 toolchain. Use the latest **stable** Android Gradle Plugin and Kotlin. Record the versions you pick in `PROGRESS.md`
- `minSdk 26`; `compileSdk`/`targetSdk` = latest stable API level
- Dependencies (only these): `androidx.core:core-ktx`, `androidx.activity:activity-ktx`, `androidx.work:work-runtime-ktx`, `org.jetbrains.kotlinx:kotlinx-coroutines-android`; tests: `junit:junit`
- **Not allowed** without asking: AppCompat, Material, Compose, Hilt/Dagger, Room, DataStore, Robolectric
- Package / applicationId: `io.github.vanlh23.sharetextfile`

## Commands

```bash
./gradlew assembleDebug          # build
./gradlew test                   # JVM unit tests
./gradlew lint                   # Android lint
./gradlew installDebug           # install on connected device/emulator
./gradlew installDebug -PfastTimers=true   # short deletion timers for manual testing (see TESTING.md)
```

## Code style

- Idiomatic Kotlin, small files, one responsibility each (the component list is in ARCHITECTURE §3)
- Add comments only where the *why* isn't obvious (Android quirks, race conditions)
- No dead code, no speculative abstractions, no interfaces with a single implementation, except where needed for tests

## Commits

- Short, plain-English subject under ~60 characters with no `feat:` prefix, then 3–5 bullets in plain words
- English only. No `Co-Authored-By` or other attribution trailers
- One task (`T-*`) per commit where practical

## Definition of done (per task)

- Acceptance criteria in `docs/PLAN.md` met
- `./gradlew test lint assembleDebug` green
- `PROGRESS.md` updated
