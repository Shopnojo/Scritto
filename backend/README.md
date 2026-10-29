# Scritto Backend

Node.js + TypeScript REST API for Scritto. The API is versioned under `/api/v1`.

## Requirements

- Node.js 22 or newer
- npm

## Local development

1. Copy `.env.example` to `.env`.
2. Set `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and the server-only `SUPABASE_SECRET_KEY` in `.env`. Never commit this file or expose the secret key in the Android app.
3. Install dependencies with `npm install`.
4. Start the development server with `npm run dev`.
5. Open `http://localhost:3000/api/v1/health`.

## Supabase migrations

Apply migrations from `supabase/migrations/` in filename order using the Supabase SQL Editor or Supabase CLI.

- `202610150001_scritto_db_health.sql` creates a minimal database connectivity RPC.
- `202610150002_scritto_application_schema.sql` creates the initial application tables and security policies.

After applying the health migration, `GET /api/v1/health/database` verifies the Data API can execute the database health RPC. This does not prove that the application schema has been applied.

The application schema includes admin profiles, device installations, privacy-conscious usage events, remote configuration, admin notifications, and notification delivery records. The usage event properties are restricted to the allowlisted `kind` and `voice` fields. Do not add note/document content, filenames, or AI conversation text to telemetry.

## Admin API routes

The admin API is implemented against the frontend's typed API contract. All
`/api/v1/admin/*` routes require a valid Supabase access token and an active
row in `admin_profiles`.

- `GET /api/v1/admin/devices?page=1&page_size=20&search=&app_version=` — paginated installations.
- `GET /api/v1/admin/devices/:id` — installation details and recent usage events.
- `GET /api/v1/admin/analytics/overview?days=7|30|90` — installation and event summary.
- `GET /api/v1/admin/analytics/installs?days=7|30|90` — daily active installations and new installations.
- `GET /api/v1/admin/analytics/events?days=7|30|90` — daily events, top event names, and recent activity.
- `GET /api/v1/admin/analytics/devices` — OS and device-model breakdowns.
- `GET /api/v1/admin/analytics/versions` — app-version breakdown. `LATEST_APP_VERSION` is optional.
- `GET /api/v1/admin/notifications?page=1&page_size=20` — notification history.
- `GET /api/v1/admin/notifications/:id` — notification details.
- `POST /api/v1/admin/notifications/send` — validates an audience and queues a notification.
- `GET /api/v1/config` — public remote-config read for clients.
- `PUT /api/v1/config` — admin-only remote-config update.

Notification sending is currently **queued, not delivered**: records and pending
delivery rows are persisted, but a Firebase Cloud Messaging sender/worker is
not yet configured. Do not treat a `202 queued` response as a successful push
delivery. FCM credentials must remain server-side when delivery is implemented.

Set `FRONTEND_ORIGIN=http://localhost:3000` for local Next.js development.
For multiple origins, use a comma-separated list. Production must set the exact
deployed frontend origin. The backend defaults to localhost origins only.

## Authentication routes

- `GET /api/v1/auth/me` — validates a Supabase access token and returns the current user's identity.
- `GET /api/v1/admin/me` — returns the administrator profile only if an active row exists in `admin_profiles`.
- `POST /api/v1/devices/register` — registers or updates the authenticated user's installation. The server derives `user_id` from the verified access token and rejects installation IDs owned by another account. The request may include `installation_id` (UUID), `platform`, `manufacturer`, `model`, `os_version`, `sdk_int`, `app_version`, `app_build`, `locale`, and `push_token`. If no installation ID is supplied, the server generates one; clients should persist the returned ID for later registrations.

Device registration requires `SUPABASE_SECRET_KEY` configured only on the backend. The endpoint returns `503 DEVICE_REGISTRATION_NOT_CONFIGURED` when that key is missing.

Protected routes require `Authorization: Bearer <supabase_access_token>`. The API verifies the token with Supabase Auth. Admin access is checked against the database profile and active status; the API must not trust a client-supplied role or user ID.

Google OAuth sign-in is initiated by the client through Supabase Auth. This API does not collect Google passwords. A profile row in `admin_profiles` must be provisioned deliberately; there is no public admin-registration route.

## Scripts

- `npm run dev` — run the API with watch mode
- `npm run build` — compile TypeScript into `dist/`
- `npm test` — run automated tests
- `npm start` — run the compiled server

## Security

- Never commit `.env` or real credentials.
- Never expose Supabase secret/service-role keys to clients.
- Protected routes must enforce authentication and authorization in the API.
- Application tables use explicit grants and Row Level Security policies.
- Do not store note content, document contents, filenames, or AI conversations in analytics.
