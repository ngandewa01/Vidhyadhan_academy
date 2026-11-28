# Vidyadhan Academy app — handover (20 Nov 2026)

Android app (Kotlin, Jetpack Compose, Room) for Vidyadhan Academy and its Abhyasika (study room).
Code: `android/`. Work branch: **`app-redesign`** (not yet merged into `main`).

## How it is built
- GitHub Actions (`.github/workflows/android.yml`) runs the unit tests (Robolectric, including screenshot tests
  that write PNGs to the `screenshots` artifact), builds the signed release APK and uploads it to the release
  `app-v0.6.0` (file `Vidhyadhan-0.6.0.apk`) and `app-latest` (file `Vidhyadhan.apk`).
- Bump `versionCode` in `android/app/build.gradle.kts` for every build and set `versionName` to `0.7.<versionCode>` (e.g. code 71 → "0.7.71"); the release tag and APK name follow it.
- Put `[skip ci]` in a commit message to push without building.
- Secrets in the repo: `SIGNING_KEYSTORE_B64`, `SIGNING_PASSWORD` (release key). `FIREBASE_SERVICE_ACCOUNT` is no
  longer used and should be deleted (also delete its key in Firebase → Service accounts).
- The repo is **private**, so phones cannot download updates from it without a GitHub login.

## Firebase (project `vidhyadhan-academy`, Spark/free plan)
- `android/app/google-services.json` is committed (public config). The Gradle build reads it into resValues and
  `BuildConfig.WEB_CLIENT_ID` (no Google Services plugin; AGP 9). `res/raw/keep.xml` keeps those resources.
- Auth: Google sign-in through Credential Manager (`cloud/Cloud.kt`). Release SHA-1 `01:F5:C9:…:E8:E3` is registered.
- Firestore: every table mirrored to `academies/{uid}/{Table}/{id}` (`cloud/Sync.kt`); rules in
  `firebase/firestore.rules`. Per-phone record-number blocks (`data/Ids.kt`) avoid clashes.
- Remote Config parameter `gemini_key` holds the Gemini API key (`ai/RemoteKey.kt`).
- Hosting cannot serve APKs on Spark, so it is not used.

## Features in place
Dashboard, Students & fee ledger, Enquiries & combo admission, Abhyasika room plan (seats on any wall + middle
rows), Telegram bot (receipts, reminders, birthday wishes, join link + share-phone-number linking, broadcasts,
students'/staff groups via `/students` and `/staff`, owner via `/owner`), MPSC News & current affairs
(Gemini + Google Search grounding; only lines backed by trusted sources are kept; `ai/News.kt`), receipts
(design B, `ui/Receipt.kt`, tap menu `ui/ReceiptActions.kt`), 2-page monthly PDF report (`money/Report.kt`),
Settings tab grouped by topic, cloud sync, backup/restore, Start fresh, PIN/fingerprint, welcome screen.

## Pending
- **Automatic updates:** owner to create a public repo (e.g. `vidhyadhan-app`) in the academy's GitHub account and
  add a fine-grained token (Contents: read & write on that repo) as secret `RELEASES_TOKEN` here. Then: publish
  the APK + `update.json` there from CI, set `AppSettings.DEFAULT_UPDATE_URL` to it, and put
  `UpdatePrompt()` back in `MainActivity.AppShell`.
- Students' Telegram place: group works; a channel needs `channel_post` handling in `TelegramBot.refresh`.
- Search icon placement (owner said it is in the wrong place; currently only on the Dashboard header).
- Optional: automatic bot receipt for Abhyasika payments; UPI reference field; rename admission step
  "Student & Telegram details"; show students'/staff/owner link status on the Telegram page.
- Known limits: photos are not synced; the bot runs on one phone and replies every 15 minutes; cloud is owner-only.

## Working style the owner asked for
Plan → clickable HTML mock (several options when there is a choice) → owner approves → build. Batch fixes into
one build, check the screenshots (including the large-text pass, the owner's phone uses large font) before
reporting, and check for duplicate buttons/rows before building.
