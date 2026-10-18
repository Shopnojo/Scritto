# Backend for usage statistics (`SCRITTO_API_URL`)

Scritto sends anonymous usage statistics to a small HTTPS API that **you** host. The app only needs a base URL.
Leave `SCRITTO_API_URL` blank and the app collects and sends nothing.

## 1. The contract

All requests are `POST` with `Content-Type: application/json`. Reply with any `2xx` (e.g. `204`) when stored.

**Register an installation** (sent once per install)

```
POST {SCRITTO_API_URL}/v1/devices
{ "installation_id": "<uuid>", "app_version": "1.5", "android_sdk": 36 }
```

**Upload events** (batches of up to 50)

```
POST {SCRITTO_API_URL}/v1/events
{ "installation_id": "<uuid>",
  "events": [ { "name": "note_created", "at": 1791655709180 }, ... ] }
```

`at` is the time on the phone in milliseconds since 1970. Event names are lowercase letters, digits and `_`.

How the app treats your answer: `2xx` = stored; `408`, `429`, `5xx` or no connection = it retries later;
any other `4xx` = the batch is dropped (so reply `4xx` only for genuinely invalid data).

## 2. A working example

[`docs/backend-example/server.js`](backend-example/server.js) implements exactly this with no dependencies
(it appends JSON lines to `data/`):

```
cd docs/backend-example
node server.js          # listens on :8080, or set PORT=18080
```

It is a starting point: for production put it behind HTTPS and store the data in a real database
(Postgres, Supabase, SQLite, ...). You can port the two endpoints to a Cloudflare Worker, an AWS Lambda,
Firebase Functions or any framework: only the contract above matters.

## 3. Make it public over HTTPS

Release builds refuse plain `http://`. Easiest ways to get an `https://` URL:

- **Render / Railway / Fly.io:** deploy `docs/backend-example` as a Node service; they give you `https://<name>.onrender.com` etc.
- **Cloudflare Worker:** port the two handlers; you get `https://<name>.<account>.workers.dev`.
- **Your own server:** run it behind Caddy or nginx with a Let's Encrypt certificate.

Then check it from your PC:

```
curl -i -X POST https://YOUR-URL/v1/devices -H "Content-Type: application/json" \
  -d '{"installation_id":"00000000-0000-4000-8000-000000000000","app_version":"1.5","android_sdk":36}'
```

## 4. Point the app at it

Put the URL in `.env` (no trailing slash needed) and rebuild:

```
SCRITTO_API_URL=https://YOUR-URL
```

For a CI or release build you can set an environment variable of the same name instead.

## 5. Test locally with the emulator

Debug builds (only) may use a plain-HTTP server on your PC; the emulator reaches it at `10.0.2.2`:

```
cd docs/backend-example && PORT=18080 node server.js
# new terminal, from the project root:
SCRITTO_API_URL=http://10.0.2.2:18080 ./gradlew :app:installDebug
```

Open the app; within ~20 seconds `docs/backend-example/data/devices.ndjson` and `events.ndjson` appear.
(Port 8080 can already be taken on your machine, so 18080 is used here.)

## 6. Keep it lawful

The app collects this automatically, so keep `PRIVACY_POLICY.md` public and accurate, and the Play Console
Data safety form in line with `docs/PLAY_DATA_SAFETY.md`. Don't add fields that could identify a person to the
events without updating both.
