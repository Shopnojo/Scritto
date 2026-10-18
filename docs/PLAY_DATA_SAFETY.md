# Google Play Console: Data safety form (draft answers)

Draft for Play Console → **App content → Data safety**, matching the code as of this commit.
Re-check it against the app before every release that changes what is collected.

Usage statistics are collected **automatically** (no switch in the app), so they must be declared as
collected and **required**, and described in the privacy policy. Collecting data that the policy and the
Data safety form don't mention breaks Google Play's User Data policy and can get the app removed.

## Overview questions

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS only in release builds) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: uninstalling deletes the random ID from the device, after which uploaded data can't be linked to anyone; add a contact email in the privacy policy for deletion requests |
| Privacy policy URL | Host `PRIVACY_POLICY.md` publicly and paste the URL |

## Data types

### App activity → *App interactions*
- **Collected:** Yes. **Shared:** No. **Processed ephemerally:** No.
- **Required or optional:** **Required** (not user-configurable).
- **Purposes:** Analytics.

### App info and performance → *Diagnostics* (app version, Android version)
- **Collected:** Yes. **Shared:** No. **Required.** Purpose: Analytics.

### Device or other IDs
- **Collected:** Yes: a **random installation ID** created by the app (not the advertising ID, not a hardware ID).
- **Shared:** No. **Required.** Purpose: Analytics.

### User content sent to Google Gemini (Scritto AI)
Messages typed or spoken to Scritto AI, and files the user attaches, are sent to Google's Gemini API to produce replies. Declare:
- **Messages → Other in-app messages** (assistant prompts): Collected: **Yes**. Shared: **Yes**, with Google as a service provider
  ("transferred to a service provider that processes data on your behalf" does not count as *sharing*; confirm against Google's current
  definition when submitting). Optional: **Yes**, since the assistant is only used when the user asks. Purpose: App functionality.
- **Files and docs:** same answers, only when the user attaches or asks the assistant to read a file.
- **Audio (voice):** speech recognition is done by the device's speech service. If you decide that counts as collected, declare
  *Audio → Voice or sound recordings* as optional, App functionality.

## Not collected (answer **No**)
Location, contacts, calendar data read from other apps, photos/videos (beyond files the user explicitly picks for the assistant),
financial info, health info, web browsing, search history, emails/SMS, and the advertising ID.

## Other checks
- No ads SDK, no third-party analytics SDK. Telemetry is first-party and goes only to the URL in `SCRITTO_API_URL`.
- If `SCRITTO_API_URL` is blank at build time, the app queues and sends **nothing**; in that case you can answer
  "No" to the three usage-statistics rows for that build.
- Account creation: **none** (no sign-in), so the "account deletion" section does not apply.
