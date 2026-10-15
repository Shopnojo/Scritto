# Scritto Backend

Node.js + TypeScript REST API for Scritto. The API is versioned under `/api/v1`.

## Requirements

- Node.js 22 or newer
- npm

## Local development

1. Copy `.env.example` to `.env`.
2. Set `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` in `.env`. Never commit this file.
3. Install dependencies with `npm install`.
4. Start the development server with `npm run dev`.
5. Open `http://localhost:3000/api/v1/health`.

## Supabase database health check

The `/api/v1/health/database` endpoint calls a minimal Postgres RPC function. Before using it, open the Supabase Dashboard's SQL Editor for the Scritto project and run the migration in:

`supabase/migrations/202610150001_scritto_db_health.sql`

Then open `http://localhost:3000/api/v1/health/database`. A successful response confirms the backend can reach the Supabase Data API and execute the database function. It does not test application tables.

The migration function returns only a fixed status and exposes no application data. Do not use this pattern to expose application queries; application tables must have deliberate grants and Row Level Security policies.

## Scripts

- `npm run dev` — run the API with watch mode
- `npm run build` — compile TypeScript into `dist/`
- `npm test` — run automated tests
- `npm start` — run the compiled server

## Security

- Never commit `.env` or real credentials.
- Never expose Supabase secret/service-role keys to clients.
- Protected routes must enforce authentication and authorization in the API.
- Application tables must use explicit access grants and appropriate Row Level Security (RLS) policies.
