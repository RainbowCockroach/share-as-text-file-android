# TESTING

## 1. Layers

| Layer | What | How |
|---|---|---|
| Unit (JVM) | `core/*` pure logic | `./gradlew test`, JUnit 4, no Robolectric |
| Manual device tests `MT-*` | Android wiring: intents, FileProvider, WorkManager, lifecycle | adb commands + real share flows |
| Verification `V-*` | Platform and third-party behavior the design assumes | Real phone, real target apps |
| Cross-OS | Output file opens correctly everywhere | Drive → other devices |

Instrumented (`androidTest`) tests are **not** required for v1.

---

## 2. Unit test cases

### 2.1 `SharedTextExtractorTest`
- `processText` present → it wins over `sendText`
- only `sendText` → used
- both null → `null`; `""` → `null`; `"  \n\t "` → `null`
- text `"  hi \n"` → returned **unchanged** (not trimmed)
- a `SpannableString`-like `CharSequence` (use a custom `CharSequence` implementation in the test) → plain `String` with the same characters
- subject `"   "` → `subject == null`; subject `"Title"` → kept

### 2.2 `TextEncodingTest`
- `encode("")` → exactly `EF BB BF`
- `encode("a")` → `EF BB BF 61`
- round trip: `encode(s)` minus the first 3 bytes, decoded as UTF-8, equals `s` for: Vietnamese `"Tiếng Việt có dấu"`, CJK `"中文日本語한국어"`, Arabic `"مرحبا بالعالم"`, emoji `"👨‍👩‍👧 🇻🇳 👍🏽"`, mixed newlines `"a\r\nb\nc"`
- `\r\n` and `\n` are preserved byte-for-byte

### 2.3 `FileNamerTest`
Use a fixed `now = 2026-09-14T15:30:12`. The suffix is `_2026-09-14_15-30-12.txt`.

| Input text (subject) | Expected stem |
|---|---|
| `"Hello world"` | `Hello world` |
| `"\n\n  First line  \nsecond"` | `First line` |
| `"a/b\\c:d*e?f\"g<h>i\|j"` | `a b c d e f g h i j` |
| `"...hidden."` | `hidden` |
| `"Tiếng Việt"` given in **NFD** | `Tiếng Việt` in **NFC** (compare code points) |
| `"report\u202Etxt.exe"` (U+202E right-to-left override) | `reporttxt.exe` (bidi control removed) |
| `"tab\there"` | `tab here` |
| `"https://example.com/x"` (subject `"Example Page"`) | `Example Page` |
| `"https://example.com/x"` (no subject) | `https example.com x` |
| `"***"` (subject `"Title"`) | `Title` |
| `"***"` (no subject) | `note` |
| `"\u200E\uFEFF"` (only invisible marks) | `note` |

Truncation (`MAX_STEM_BYTES = 64`):
- 100 × `"a"` → 64 × `"a"`
- 30 × `"中"` (3 bytes each) → 21 × `"中"` (63 bytes)
- Vietnamese long sentence → UTF-8 byte length ≤ 64, and the result is a prefix of the NFC input that doesn't end in the middle of a character sequence
- a string of 21 `"中"` followed by `"é"` in NFD (`e` + U+0301) placed so the cut falls between `e` and U+0301 → the `e` is dropped too
- ZWJ family emoji `"👨‍👩‍👧‍👦"` repeated so the cut falls inside a sequence → no trailing U+200D, and no trailing lone base emoji that belonged to the cut sequence
- flags `"🇻🇳"` repeated so the cut falls between the two regional indicators → an even count of regional indicators
- `"👍🏽"` with the cut between the thumb and the skin-tone modifier → thumb dropped too
- a long stem whose cut lands right after a space or dot → trailing spaces and dots trimmed
- every generated name: no character from ``\/:*?"<>|``, no ISO control characters, doesn't start with `.`, ends with `.txt`, total UTF-8 length ≤ 88

### 2.4 `ShareStoreTest` (JUnit `TemporaryFolder`)
- `create` → file exists at `root/<uuid>/<name>`, and its bytes equal the input
- two `create` calls with the same name → different ids, both files exist
- `markChosen` then `isChosen` → true; `isChosen` on a fresh id → false
- `delete` removes the dir; calling it twice doesn't throw
- `markChosen` after `delete` → doesn't recreate the dir
- invalid ids (`"../x"`, `""`, `"abc"`) → all methods are no-ops, nothing outside root is touched
- `sweep`: dirs with `lastModified` set older than `maxAge` are deleted, newer ones kept, non-UUID entries ignored
- `create` failure (e.g. root is a regular file) → throws `IOException`, and no partial dir is left behind

---

## 3. Manual device tests

### 3.1 Useful commands
```bash
PKG=io.github.vanlh23.sharetextfile

# install (short timers for lifecycle tests)
./gradlew installDebug -PfastTimers=true

# simulate a text share (the share sheet should appear)
adb shell am start -n $PKG/.ShareToFileActivity -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "'Hello from adb'" --es android.intent.extra.SUBJECT "'Test subject'"

# simulate the text-selection menu entry
adb shell am start -n $PKG/.ShareToFileActivity -a android.intent.action.PROCESS_TEXT -t text/plain \
  --es android.intent.extra.PROCESS_TEXT "'Selected text'"

# inspect temp files (debug builds only)
adb shell run-as $PKG ls -laR cache/share

# pull a file and check its first bytes are EF BB BF
adb exec-out run-as $PKG sh -c 'cat cache/share/*/*.txt' > /tmp/shared.txt && xxd /tmp/shared.txt | head -2

# see pending deletion jobs
adb shell dumpsys jobscheduler | grep -A3 $PKG

# logcat for the app
adb logcat --pid=$(adb shell pidof -s $PKG)
```
Non-ASCII text through `am start` depends on the shell. For Unicode checks, select real text on the phone instead.

### 3.2 Test matrix

| ID | Scenario | Expected |
|---|---|---|
| MT-1 | Chrome: select text → Share → Save as .txt → pick Google Drive → Save | Share sheet appears with no other visible screen. Drive saves `<first words>_<timestamp>.txt` with the correct content |
| MT-2 | Chrome: select text → Save as .txt in the selection menu | Same as MT-1. The selected text in Chrome is **not** replaced |
| MT-3 | Share sheet shown by our app | "Save as .txt" is **not** listed in it |
| MT-4 | Share with only an image or file stream (`text/plain` with `EXTRA_STREAM`, no text), e.g. from a file manager | Toast "Nothing to save", nothing written |
| MT-5 | Gmail as target | Attachment is the `.txt` file. The email body does **not** contain the raw text |
| MT-6 | Temp dir while the share sheet is open | Exactly one `cache/share/<uuid>/<name>.txt`, starting with `EF BB BF` |
| MT-7 | (fastTimers) Pick a target, finish | `.chosen` created; dir deleted ~1 min after picking or closing, not before the target finished |
| MT-8 | (fastTimers) Open the share sheet, press Back | Dir deleted ~20 s later |
| MT-9 | (fastTimers) Open the share sheet, press Home, leave it | Dir deleted ≤ ~3 min later |
| MT-10 | Open the share sheet, rotate the phone, then pick a target | No crash, no second file, correct lifecycle as MT-7 |
| MT-11 | Open the share sheet, `adb shell am kill $PKG` (background kill), then pick a target | The target still gets the file. Dir deleted later |
| MT-12 | (fastTimers) Share, cancel, reboot within 20 s | After reboot, dir deleted shortly after WorkManager runs |
| MT-13 | Share, `adb shell am force-stop $PKG`, wait past SAFETY_TTL, share again | The old dir is removed by the sweep. Only the new dir remains |

---

## 4. Verification items (record results in PROGRESS.md)

| ID | Question | How to check |
|---|---|---|
| V-1 | Does `EXTRA_CHOSEN_COMPONENT` arrive through our receiver on each tested Android version? | Log in the receiver; pick Drive, Gmail, Telegram, Nearby Share |
| V-2 | What happens with share-sheet built-in actions (Copy, Nearby, Edit) on Android 14+? Does the callback fire, and does the target still get the file within `CANCEL_DELAY`? | Try each action, check logs and the result |
| V-3 | When does each target read the file? Does the share still work if the file is deleted `GRACE_AFTER_CHOICE` after picking? | With release timers: pick the target, wait on its dialog 2–3 min, then save/send; confirm upload. Also queue a Drive upload in airplane mode, wait 16 min, go online: does the upload succeed? (Drive may copy on save; record the result) |
| V-4 | Which common apps show "Save as .txt" in the selection menu? | Chrome, Firefox, Gmail, Google Docs, Messages, Telegram, Zalo, Facebook, a PDF viewer |
| V-5 | Does the transition flicker noticeably? | Visual check on each device; if bad, try `windowDisablePreview`/`windowNoDisplay` variants and record |

Targets to cover at minimum: **Google Drive, Gmail, Telegram, Zalo, Nearby Share / Quick Share, Files by Google**.

---

## 5. Cross-OS sample texts

Share each sample as its own file, upload it to Drive, then open it on every OS (Windows Notepad, macOS TextEdit + Quick Look, iOS Files, Android Drive preview):

1. `Tiếng Việt có dấu: Đường phố Hà Nội — “ngoặc kép”`
2. `中文 / 日本語 / 한국어 混合テキスト`
3. `مرحبا بالعالم — English mixed עברית`
4. `Emoji: 👨‍👩‍👧 🇻🇳 👍🏽 ✅`
5. A multi-paragraph English text of ~5 KB with blank lines

Pass: characters render correctly, line breaks look right, and the file name shows correctly (including Vietnamese and CJK names).
