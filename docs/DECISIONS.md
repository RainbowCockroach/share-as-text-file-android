# DECISIONS

Decision records. Status is **Accepted** unless stated otherwise. To change one, ask the user, then add a new record that supersedes it.

---

## ADR-001 — Output format: plain text `.txt`

**Decision:** `.txt`, MIME `text/plain`.

**Why:** It is the most universally supported document type. Windows (Notepad), macOS (TextEdit, Quick Look), iOS/iPadOS (Files), Android, Linux, Google Drive and Dropbox previews all open it with built-in apps.

**Rejected:**
- `.md`: unknown extension on iOS and Windows ("open with…?").
- `.rtf`: poor mobile previews, adds formatting.
- `.docx`/`.odt`/`.pdf`: need generator libraries, heavy, PDF isn't editable.
- `.html`: opens in a browser rather than an editor and needs escaping.

## ADR-002 — Encoding: UTF-8 **with BOM**

**Decision:** Prefix the content with `EF BB BF`.

**Why:** UTF-8 covers every script. Without a BOM, some readers guess a legacy code page and show garbled text (`Tiáº¿ng Viá»‡t`): older Windows Notepad and WordPad, some Windows tools, and some macOS TextEdit or Quick Look cases. With the BOM, all modern readers detect UTF-8 reliably.

**Cost:** A few Unix command-line tools show a stray character at the start. That's irrelevant for notes.

## ADR-003 — Content is written exactly as received

**Decision:** No trimming, no line-ending conversion (`\n` stays `\n`).

**Why:** It's faithful to the selection. Notepad has handled LF since Windows 10 1809, and every other target handles LF.

## ADR-004 — No UI, no settings

**Decision:** No launcher activity, screens, settings or notifications. Failure toasts only.

**Why:** User requirement: "technically just a share-through pipeline". Settings from earlier drafts (BOM toggle, keep-a-copy, ASCII names) were dropped. Keep-a-copy also conflicts with ADR-007.

**Consequence:** On Android 10+ the launcher shows a synthesized icon that opens App info. That's accepted.

## ADR-005 — Two entry points to one activity

**Decision:** `ACTION_SEND` (`text/plain`) **and** `ACTION_PROCESS_TEXT`.

**Why:** Share works in every app. `PROCESS_TEXT` saves a tap where supported (Chrome and most standard text fields). Both are cheap.

**Constraint:** Never return `RESULT_OK` for `PROCESS_TEXT`, or the source text gets replaced.

## ADR-006 — Storage: app-private cache + FileProvider

**Decision:** `cacheDir/share/<uuid>/<name>.txt`, exposed only through `androidx.core.content.FileProvider` with a one-off read grant.

**Why:** No permissions needed. Invisible to the user and other apps. Cleared by "Clear cache" and uninstall, and Android may clear it under storage pressure. FileProvider reports the real display name, so Drive keeps the file name. A UUID directory per share means the exact file name can be kept with no collisions.

**Rejected:** MediaStore/Downloads (persistent and visible, against the requirement); `filesDir` (never cleared by the system).

## ADR-007 — Deletion strategy: scheduled deletion with three layers

**Decision:** A safety deletion scheduled at creation (60 min); rescheduled to 15 min after the user picks a target, or 1 min after cancelling; plus a sweep of old leftovers on every run. Implemented with WorkManager unique work (`REPLACE`). Details in ARCHITECTURE §6.

**Why:** Android gives no "receiver finished reading" signal, and targets read at different times (immediately, on tapping Save, or during a background upload). Deleting too early breaks the share. WorkManager survives process death and reboot and never runs early.

**Rejected alternatives:**
- `File.createTempFile` + `deleteOnExit`: Android kills processes without warning, so `deleteOnExit` never runs.
- Delete right after launching the share sheet: breaks targets that read later.
- Streaming the text from memory through a pipe (`openPipeHelper`), with no file on disk: the size is unknown, the stream can't be read twice or seeked, and the data is lost if our process dies while the target's dialog is open.
- Opening the file and then deleting its path (Unix unlink-while-open): breaks targets that open the URI more than once (preview, then upload).
- A sweep on next launch only: files could remain for days if the app isn't used again.

## ADR-008 — What the share sheet tells us (research findings)

**Findings (2026-09):**
- `Intent.createChooser(target, title, IntentSender)` (API 22+): the sender is "called when a choice is made". AOSP `ChooserActivity` sends `EXTRA_CHOSEN_COMPONENT` through it when the target activity starts.
- AOSP `ChooserActivity` launches the target with `startActivityAsCaller` **without** `FLAG_ACTIVITY_FORWARD_RESULT`. So the result we get back only means "the share sheet closed".
- API 35+ adds `EXTRA_CHOOSER_RESULT` (`ChooserResult`), delivered to the same sender "when the session completes". It describes *how* the session ended, but it still isn't a "target finished reading" signal.
- Mutable PendingIntents must be explicit on Android 14+. Ours is (`ChooserCallbackReceiver`).

**Decision:** Anchor the grace period on pick and share-sheet close. Detect cancel as "share sheet closed without a pick". Don't use `EXTRA_CHOOSER_RESULT` in v1. It could later tell copy/edit actions apart on API 35+ if `V-2` shows a need.

## ADR-009 — minSdk 26

**Why:** `java.time` and `Normalizer` are available with no desugaring. Covers the vast majority of active devices. No feature needs older APIs.

## ADR-010 — File names: Unicode, sanitized, byte-limited, timestamped

**Decision:** Rules in ARCHITECTURE §5.

**Why:** Unicode names work on Drive, iOS, macOS, Windows and Android. Only characters that are illegal or risky are removed (Windows-reserved, control, bidi-spoofing). The limit is in **bytes**, because filesystems allow 255 bytes and a CJK character is 3 bytes. Cuts never split a character sequence. The first line of the text is preferred over `EXTRA_SUBJECT` because it describes the selection. The subject is used when the text is just a URL. Windows reserved device names (`CON`, `NUL`…) can't occur, because the stem is always followed by `_timestamp`.

## ADR-011 — Minimal stack

**Decision:** Kotlin, `ComponentActivity` (activity-ktx), core-ktx (FileProvider), WorkManager, coroutines. No AppCompat, Material, Compose or DI.

**Why:** No UI means no UI libraries. The fewer the dependencies, the smaller the APK and the easier for an agent to keep correct.

## ADR-012 — Timings are build-configurable

**Decision:** `SAFETY_TTL` 60 min, `GRACE_AFTER_CHOICE` 15 min, `CANCEL_DELAY` 1 min. Setting `-PfastTimers=true` gives 3 min / 1 min / 20 s for manual testing.

**Why:** Deletion behavior can be tested on a device without waiting an hour. The release values stay fixed and reviewable.
