# Scritto Android — Google Sign-In Setup

This configures native Google sign-in through Android Credential Manager and Supabase Auth.

## 1. Configure the local Android app

Copy the repository-root `.env.example` to `.env` and set:

```dotenv
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_YOUR_KEY
GOOGLE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Get the Supabase URL and publishable key from the Supabase project's Connect panel / API settings. The publishable key and OAuth client ID are client configuration, not server secrets.

Do not put a Supabase secret/service-role key or a Google OAuth client secret in the Android app. Keep `.env` local; it is git-ignored.

## 2. Configure Google Cloud OAuth

In the Google Cloud project used for Scritto:

1. Configure the Google Auth Platform consent screen / audience for the users who should be allowed to sign in.
2. Create a **Web application** OAuth client. Use its client ID as `GOOGLE_WEB_CLIENT_ID`.
3. Add this authorized redirect URI to the Web client, replacing the project reference:
   `https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback`
4. Create an **Android** OAuth client with package name `com.internship.scritto`.
5. Add the SHA-1 signing certificate fingerprint for the build you will test. For the local debug build, run `./gradlew signingReport` (Windows: `gradlew.bat signingReport`) and use the `debug` variant's SHA-1. Add release fingerprints separately before distributing a release.

## 3. Configure Supabase Auth

In the Supabase dashboard:

1. Open **Authentication → Sign In / Providers → Google** (the exact menu wording may vary).
2. Enable Google and enter the Web OAuth client ID and its client secret from Google Cloud. The secret stays in the dashboard.
3. Add the Android OAuth client ID to the provider's authorized client IDs if that field is shown.
4. Save the provider configuration.

Use the **Web** OAuth client ID in the Android app. Do not use the Android client ID in `GOOGLE_WEB_CLIENT_ID`.

## 4. Build and test

After saving `.env`, sync Gradle and build the debug app:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

Then launch the app on an Android device/emulator with Google Play services and test:

- No session: Scritto shows the login screen after the splash.
- Continue with Google: Credential Manager opens and a real Google account is selected.
- Confirmation: the app shows the selected account's name, email, and available profile picture.
- Continue with this account: the app opens the existing authenticated app.
- Change account: Scritto signs out and clears Credential Manager state before returning to login.
- Relaunch: a valid persisted Supabase session opens the app without requiring sign-in again.

If sign-in fails, capture the exact error message and relevant Logcat output. Never share OAuth client secrets or Supabase secret/service-role keys.

## Security notes

- Android contains only the Supabase project URL, publishable key, and Google Web client ID.
- The Supabase Auth access token is the credential for future protected backend calls. Do not send the Google ID token as the backend Bearer token.
- The current change gates the Android app behind authentication; connecting protected backend requests is a separate follow-up step on the Android `main` branch, with backend edits restricted to the `backend` branch if later required.
