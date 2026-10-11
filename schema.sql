-- Optional. lib/db.js creates all of this automatically on the first request; run this in the Neon / Vercel
-- Postgres SQL editor only if you prefer to set the tables up yourself. Safe to run repeatedly.

CREATE TABLE IF NOT EXISTS devices (
  installation_id uuid PRIMARY KEY,
  platform        text NOT NULL DEFAULT 'android',
  manufacturer    text,
  model           text,
  os_version      text,
  sdk_int         integer,
  app_version     text,
  app_build       integer,
  locale          text,
  push_token      text,
  created_at      timestamptz NOT NULL DEFAULT now(),
  last_seen_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS events (
  id              bigserial PRIMARY KEY,
  installation_id uuid        NOT NULL,
  name            text        NOT NULL,
  occurred_at     timestamptz NOT NULL,
  properties      jsonb,
  received_at     timestamptz NOT NULL DEFAULT now()
);

-- A retried upload of the same event is ignored instead of counted twice.
CREATE UNIQUE INDEX IF NOT EXISTS events_dedupe ON events (installation_id, name, occurred_at);
CREATE INDEX IF NOT EXISTS events_time_idx     ON events (occurred_at);
CREATE INDEX IF NOT EXISTS events_name_idx     ON events (name, occurred_at);
CREATE INDEX IF NOT EXISTS events_received_idx ON events (installation_id, received_at);

CREATE TABLE IF NOT EXISTS notifications (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  title           text NOT NULL,
  body            text NOT NULL,
  audience        text NOT NULL,
  device_ids      jsonb NOT NULL DEFAULT '[]',
  status          text NOT NULL DEFAULT 'queued',
  recipient_count integer,
  success_count   integer,
  failure_count   integer,
  created_by      text,
  created_at      timestamptz NOT NULL DEFAULT now(),
  sent_at         timestamptz
);

CREATE TABLE IF NOT EXISTS config (
  key         text PRIMARY KEY,
  value       jsonb NOT NULL,
  description text,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS login_attempts (
  id  bigserial PRIMARY KEY,
  ip  text NOT NULL,
  at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS login_attempts_idx ON login_attempts (ip, at);

-- The remote-config keys are seeded by lib/db.js (maintenance_mode, min_supported_version, latest_version,
-- feature_ai_assistant, telemetry_enabled, announcement).
