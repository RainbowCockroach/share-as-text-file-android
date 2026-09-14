# share-as-text-file-android

An Android app with no UI. Select text in any app, tap **Share** (or **Save as .txt** in the text-selection menu), and the app turns the text into a `.txt` file and opens the share sheet again so you can send the file to Google Drive, Gmail, Telegram and so on. The file is temporary and deletes itself after sharing.

- Format: plain text `.txt`, UTF-8 with BOM, so it opens correctly on Windows, macOS, iOS, Android and Linux, in any language
- Permissions: no runtime permissions, no internet access

## Install

- **From GitHub:** open the [latest release](https://github.com/RainbowCockroach/share-as-text-file-android/releases/latest) on your phone, download the `.apk` and open it. Android asks you to allow installing apps from your browser the first time.
- **With adb:** `./gradlew installDebug`, or `adb install share-as-text-file-<version>.apk`

Every push to `main` builds a new signed APK and replaces the previous release, so there is only ever one release.

## Use

1. Select text in any app.
2. Tap **Share → Save as .txt**, or **Save as .txt** in the text-selection menu (not every app shows it there).
3. Pick where to send the file: Drive, Gmail, Telegram…

There is nothing else to do. The file deletes itself about a minute after you cancel, about 15 minutes after you pick an app, and at most an hour later in any case.

The app has no screens. Its icon in the app drawer opens the system **App info** page, which is where you uninstall it.

## Docs

- [AGENTS.md](AGENTS.md): start here if you are an AI coding agent
- [docs/SPEC.md](docs/SPEC.md): requirements
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): technical design
- [docs/DECISIONS.md](docs/DECISIONS.md): design decisions and rejected alternatives
- [docs/PLAN.md](docs/PLAN.md): implementation plan
- [docs/TESTING.md](docs/TESTING.md): tests and device verification
- [PROGRESS.md](PROGRESS.md): current progress
