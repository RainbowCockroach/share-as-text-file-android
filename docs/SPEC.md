# SPEC — Share as Text File

## 1. Problem

On Android you can share selected text, but many destinations (Google Drive, file managers, some chat apps) want a **file**, not raw text. The user wants to turn selected text into a text file and send that file somewhere in one smooth flow, without keeping the file on the phone afterwards.

## 2. User flow

1. The user selects text in any app (browser, reader, chat…). The text can be in **any language or script** (Vietnamese, CJK, Arabic/RTL, emoji…).
2. The user either
   - taps **Share** and picks **Save as .txt**, or
   - taps **Save as .txt** directly in the text-selection menu (in apps that support it).
3. The Android share sheet appears right away, now sharing a `.txt` **file**.
4. The user picks a destination (e.g. Google Drive) and finishes there as usual.
5. The temporary file is deleted automatically. The user never manages it.

The app has **no screens**. The user sees at most a brief transition before the share sheet.

## 3. Functional requirements

| ID | Requirement |
|---|---|
| FR-1 | Appear as a share target for `ACTION_SEND` with MIME `text/plain`. |
| FR-2 | Appear in the text-selection menu via `ACTION_PROCESS_TEXT` (`text/plain`) with label **Save as .txt**. |
| FR-3 | Read the text from `EXTRA_PROCESS_TEXT`, otherwise `EXTRA_TEXT`, as plain text (styling dropped). |
| FR-4 | If there is no text or it is blank (including `text/plain` shares that carry only a file stream), show the toast "Nothing to save" and exit. |
| FR-5 | Write the text to a new `.txt` file: **UTF-8 with BOM**, content byte-for-byte equal to the received text (no trimming, no newline conversion). |
| FR-6 | File name is derived from the text according to ARCHITECTURE §5: human-readable, Unicode allowed, valid on Windows/macOS/iOS/Android/Linux, ends with a timestamp. |
| FR-7 | Immediately open the Android share sheet with **only the file** attached (MIME `text/plain`), readable by the chosen app. |
| FR-8 | This app does not list itself in that share sheet. |
| FR-9 | The source app's selected text is never modified (no result returned for `PROCESS_TEXT`). |
| FR-10 | The file is **temporary**. It is deleted automatically: shortly after the user cancels the share sheet, some time after the user picks an app (long enough for that app to read it), and in every case within a maximum lifetime, even if the app is killed or the phone reboots. See ARCHITECTURE §6. |
| FR-11 | Leftover files from any earlier run are removed the next time the app runs. |
| FR-12 | If writing the file fails, show the toast "Couldn't create the file" and exit without leaving partial files. |

## 4. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | **No UI**: no launcher activity, layouts, settings, notifications or success toasts. |
| NFR-2 | **Privacy**: no runtime permissions, no network, no analytics. Files are only in app-private cache and only exposed to the app the user picked, through a temporary URI grant. |
| NFR-3 | **Speed**: the share sheet appears in under ~300 ms for ordinary text (< 100 KB) on a mid-range phone. |
| NFR-4 | **Compatibility**: Android 8.0 (API 26) and newer. |
| NFR-5 | **Portability of output**: the file opens with correct characters in the default text apps of Windows 10/11 (Notepad), macOS (TextEdit/Quick Look), iOS/iPadOS (Files), Android, Google Drive preview and Linux. |
| NFR-6 | **Robustness**: survives screen rotation, app process death and reboot without leaking files or crashing. |
| NFR-7 | Only a small, fixed set of dependencies (see AGENTS.md). |

## 5. Non-goals

- Any user interface (preview, rename, settings, history)
- Keeping a copy of the file on the device (Downloads, etc.)
- Formats other than `.txt` (Markdown, RTF, DOCX, PDF, HTML)
- Sharing multiple texts at once (`ACTION_SEND_MULTIPLE`) or incoming files/images
- Rich text / HTML (`EXTRA_HTML_TEXT` is ignored)
- Text larger than Android can deliver through an intent (~1 MB binder limit; the sending app fails before we receive anything)
- Localization of the two toast strings (English only for v1)

## 6. Known, accepted platform behavior

- An app with no launcher activity still shows an icon on Android 10+. Tapping it opens the system **App info** page. This is expected and useful for uninstalling.
- Not every app shows `PROCESS_TEXT` entries in its selection menu. **Share** (FR-1) is the universal path.
- Deletion times are "not earlier than". Android may run the cleanup later (Doze, battery saver). The file is never deleted *earlier* than designed.
