# Scritto

A dark, focused workspace for **notes, tasks, schedule and files**, with **Scritto AI**, a Gemini-powered assistant (text and voice) that can create and edit all of it.

Native Android · Kotlin · Jetpack Compose · package `com.internship.scritto`

## Setup

1. **Requirements:** Android Studio (or the Android SDK) and JDK 17+. The Gradle wrapper does the rest.
2. **Secrets:** copy `.env.example` to `.env` and fill it in. `.env` is git-ignored and must never be committed.

   | Variable | Needed for |
   |---|---|
   | `GEMINI_API_KEYS` | Scritto AI. Comma-separated; requests rotate across the keys. Without it the app still runs and the assistant says it has no key. |
   | `SCRITTO_API_URL` | Backend for the anonymous usage statistics (`https://` only; receives `POST /v1/devices` and `POST /v1/events`). Blank means the app never sends anything. See `PRIVACY_POLICY.md` and `docs/PLAY_DATA_SAFETY.md`. |
   | `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` | Signing a release APK only. |

   Real environment variables with the same names override `.env` (useful in CI).
   Get Gemini keys at <https://aistudio.google.com/apikey>. Keys from different Google Cloud projects have separate quotas.
3. **Run:** open the project in Android Studio and press Run, or on Windows use `.\run.ps1` (starts an emulator if needed, then builds, installs and launches).

## Build

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK (signed if the RELEASE_* values are set)
./gradlew :app:testDebugUnitTest  # unit tests
./gradlew build                   # everything, including lint
```

Create a release keystore once with
`keytool -genkeypair -keystore scritto-release.jks -alias scritto -keyalg RSA -keysize 2048 -validity 10000`
and keep it **outside** the repository. Raise `versionCode` in `app/build.gradle.kts` for every release.

## Live AI tests

`AssistantLiveTest` drives the real Gemini API and is skipped unless keys are provided:

```bash
SCRITTO_TEST_KEYS=key1,key2 ./gradlew :app:testDebugUnitTest --tests "*AssistantLiveTest*"
```

## Security note

API keys are compiled into the app (`BuildConfig`), so anyone with the APK can extract them. That is fine for personal builds; for a public release, put the keys behind a small backend instead.
