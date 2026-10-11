# Scritto backend

A Vercel project (one serverless function + Postgres) that implements the API the
[Scritto admin website](https://github.com/rajatava06/Scritto-Frontend) expects, and receives the anonymous
usage statistics sent by the Scritto Android app.

```
Android app  ──POST /devices/register, /events, GET /config──▶ ┐
Admin site   ──/auth/login, /admin/*, PUT /config (Bearer)──▶   ├─▶ this backend (/api/v1) ─▶ Postgres (Neon)
```

Everything the app sends is a random installation ID, the device model / Android version / app version / locale, and short event
names with at most a few non-personal properties (like `kind: "pdf"`). See the app repo's `PRIVACY_POLICY.md`.

## Deploy to Vercel (about 15 minutes)

1. **Put this folder on GitHub** (its own repo, or a folder in a repo).
2. **Vercel → Add New → Project →** import it. If it is a folder inside a bigger repo, set *Root Directory* to `backend`.
   Framework preset **Other**; no build command.
3. **Database:** project → *Storage* → *Create* → **Neon (Postgres)** → connect it. This adds `DATABASE_URL`. The tables
   are created on the first request (or run [`schema.sql`](schema.sql) yourself).
4. **Environment variables:** add the ones in the table below (copy names from [`.env.example`](.env.example)).
   Make the admin password hash with `npm run hash-password -- "your long password"`.
5. **Deploy**, then open `https://YOUR-BACKEND.vercel.app/api/v1/health` → `{"ok":true}`.
6. **Connect the admin website** (in its Vercel project, then redeploy it, because `NEXT_PUBLIC_*` is baked in at build time):

   ```
   NEXT_PUBLIC_API_URL=https://YOUR-BACKEND.vercel.app/api/v1
   NEXT_PUBLIC_USE_MOCKS=false
   ```

   And add the website's address to this backend's `ADMIN_ORIGIN` (for example `https://admin.scritto.app`).
7. **Connect the Android app** (app repo's `.env`, then rebuild the APK):

   ```
   SCRITTO_API_URL=https://YOUR-BACKEND.vercel.app/api/v1
   ```

With the CLI instead: `npm i -g vercel`, then `vercel` (preview) and `vercel --prod` from this folder.

## Environment variables

Copy [`.env.example`](.env.example) to `.env` for local work. On Vercel add the same names; tick **Production** (and **Preview** /
**Development** if you use them: use a separate database per environment).

| Name | Required | What it is |
| --- | --- | --- |
| `DATABASE_URL` (or `POSTGRES_URL`) | **Yes** | Postgres connection string. Added by the Neon integration. |
| `ADMIN_EMAIL` | **Yes** | The admin's sign-in email. |
| `ADMIN_PASSWORD_HASH` | **Yes** | From `npm run hash-password -- "password"`. Never store the plain password. |
| `JWT_SECRET` | **Yes** | 32+ random characters that sign login tokens. |
| `ADMIN_ORIGIN` | **Yes** for the website | Exact website origin(s), comma separated, e.g. `https://admin.scritto.app,http://localhost:3000`. No `*`: the site sends credentials. |
| `JWT_TTL_HOURS` | No | Login length, default 12. |
| `SCRITTO_APP_KEY` | No | If set, the app must send it as `X-Scritto-App-Key` (set the same `SCRITTO_APP_KEY` in the app). |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | No | Service-account key so the admin Notifications page can send push messages via FCM. |
| `CRON_SECRET` | Recommended | Protects the nightly clean-up; Vercel Cron sends it automatically. |
| `RETENTION_DAYS` | No | Events older than this are deleted nightly (default 365, minimum 7). |

Generate secrets: `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`.
The `.env` in this folder (git-ignored) has a working local setup. **Use different secrets, and your own admin password, on Vercel.**

## API (all under `/api/v1`)

Called by the **Android app** (no login):

| Request | Notes |
| --- | --- |
| `POST /devices/register` | `{ installation_id, platform, manufacturer, model, os_version, sdk_int, app_version, app_build, locale, push_token? }` → `204`. Upserts by `installation_id`. |
| `POST /events` | `{ installation_id, app_version, events: [{ name, occurred_at, properties? }] }` (max 100) → `204`. Retried batches are de-duplicated; unknown installations are registered automatically. |
| `GET /config` | `{ entries: [{ key, value, description, updated_at }], updated_at }`, public. |

Called by the **admin website** (`Authorization: Bearer <token>`; every route re-checks the admin role, `401` / `403` otherwise):

| Request | Returns |
| --- | --- |
| `POST /auth/login` | `{ access_token, token_type, expires_in }`. Failed logins from one address are throttled (10 per 15 min → `429`). |
| `GET /auth/me`, `POST /auth/logout` | `{ id, email, display_name, role: "admin", is_admin: true }` / `204` |
| `GET /admin/analytics/overview?days=7\|30\|90` | installs, active installs, new installs, events, each with `change_pct` vs the previous period |
| `GET /admin/analytics/installs?days=` | daily active + new installs (zero-filled daily series) |
| `GET /admin/analytics/events?days=` | daily totals, top events, latest events |
| `GET /admin/analytics/devices`, `…/versions` | OS version and model breakdowns; app versions + `latest_version` |
| `GET /admin/devices?page&page_size&search&app_version` | `{ items, total, page, page_size }` |
| `GET /admin/devices/{id}` | the device plus `recent_events` and `top_events` |
| `POST /admin/notifications/send`, `GET /admin/notifications[/{id}]` | send via FCM and keep a history |
| `PUT /config` | `{ values: { key: value } }`; only existing keys. Seeded keys: `maintenance_mode`, `min_supported_version`, `latest_version`, `feature_ai_assistant`, `telemetry_enabled`, `announcement`. |

`GET /health` → `{ ok: true }`. "Active" means the installation sent at least one event in the period.

## Run it locally

```
npm install
npm test            # 41 tests against a real in-process Postgres (PGlite)
npm run dev:local   # whole API on http://localhost:3001/api/v1, data in memory, no database needed
```

`dev:local` creates a demo admin (`admin@scritto.local` / `dev-password-123`). To try the real website against it, in the website
folder set `NEXT_PUBLIC_API_URL=http://localhost:3001/api/v1` and `NEXT_PUBLIC_USE_MOCKS=false`, then `npm run dev`.
To try the Android emulator: build the app with `SCRITTO_API_URL=http://10.0.2.2:3001/api/v1 ./gradlew :app:installDebug`
(debug builds may use plain HTTP; release builds need HTTPS).

## Good to know

- **One function:** all of `/api/v1/*` is a single serverless function (`api/v1/[...path].js`), plus the cron job; Hobby plans allow 12.
- **Push notifications** need Firebase on both ends: the key here, and `firebase-messaging` + `google-services.json` in the Android
  app (sending the FCM token as `push_token` on register). The app doesn't do that yet, so until then sends are recorded as `failed`.
  Not yet tested against real FCM.
- **The app endpoints are public** (an APK can't keep a secret). Input is validated and size-limited, and a device sending more than
  600 events a minute gets `429`. For more, add rate limiting with Vercel's Firewall.
- **Admin accounts:** one admin, set by environment variables. To add more people later, move accounts into a table.
- **Privacy:** if you add fields to events or registration, update the app's `PRIVACY_POLICY.md` and Play Data safety answers first.
