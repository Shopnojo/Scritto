import { neon } from "@neondatabase/serverless";

const DAY = 24 * 60 * 60 * 1000;

/** Remote config the app can read. Created once; admins change the values, never the key list. */
export const DEFAULT_CONFIG = [
  ["maintenance_mode", false, "Shows a maintenance banner in the app."],
  ["min_supported_version", "1.0", "Clients below this version are asked to update."],
  ["latest_version", "1.5", "Shown in the in-app update prompt."],
  ["feature_ai_assistant", true, "Enables the Scritto AI assistant."],
  ["telemetry_enabled", true, "Remote kill-switch for anonymous usage events."],
  ["announcement", { enabled: false, message: "" }, "Optional in-app announcement card."],
];

const dayString = (ms) => new Date(ms).toISOString().slice(0, 10);

function startOfUtcDay(ms) {
  return Math.floor(ms / DAY) * DAY;
}

/** Calendar days (UTC) ending today, oldest first, with 0 for days that have no rows. */
function fillDays(rows, days, now) {
  const byDay = new Map(rows.map((r) => [r.day, Number(r.value)]));
  const today = startOfUtcDay(now);
  return Array.from({ length: days }, (_, i) => {
    const date = dayString(today - (days - 1 - i) * DAY);
    return { date, value: byDay.get(date) ?? 0 };
  });
}

function change(current, previous) {
  if (!previous) return null;
  return Math.round(((current - previous) / previous) * 1000) / 10;
}

/**
 * All SQL lives here. `sql` is a tagged-template function that returns rows:
 * the Neon driver in production, PGlite (an in-process Postgres) in the tests.
 */
export function createDb(sql) {
  let ready;

  function ensureSchema() {
    ready ??= (async () => {
      await sql`CREATE TABLE IF NOT EXISTS devices (
        installation_id uuid PRIMARY KEY,
        platform text NOT NULL DEFAULT 'android',
        manufacturer text, model text, os_version text,
        sdk_int integer, app_version text, app_build integer, locale text, push_token text,
        created_at timestamptz NOT NULL DEFAULT now(),
        last_seen_at timestamptz NOT NULL DEFAULT now())`;
      await sql`CREATE TABLE IF NOT EXISTS events (
        id bigserial PRIMARY KEY,
        installation_id uuid NOT NULL,
        name text NOT NULL,
        occurred_at timestamptz NOT NULL,
        properties jsonb,
        received_at timestamptz NOT NULL DEFAULT now())`;
      await sql`CREATE UNIQUE INDEX IF NOT EXISTS events_dedupe ON events (installation_id, name, occurred_at)`;
      await sql`CREATE INDEX IF NOT EXISTS events_time_idx ON events (occurred_at)`;
      await sql`CREATE INDEX IF NOT EXISTS events_name_idx ON events (name, occurred_at)`;
      await sql`CREATE INDEX IF NOT EXISTS events_received_idx ON events (installation_id, received_at)`;
      await sql`CREATE TABLE IF NOT EXISTS notifications (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        title text NOT NULL, body text NOT NULL, audience text NOT NULL,
        device_ids jsonb NOT NULL DEFAULT '[]',
        status text NOT NULL DEFAULT 'queued',
        recipient_count integer, success_count integer, failure_count integer,
        created_by text,
        created_at timestamptz NOT NULL DEFAULT now(),
        sent_at timestamptz)`;
      await sql`CREATE TABLE IF NOT EXISTS config (
        key text PRIMARY KEY, value jsonb NOT NULL, description text,
        updated_at timestamptz NOT NULL DEFAULT now())`;
      await sql`CREATE TABLE IF NOT EXISTS login_attempts (
        id bigserial PRIMARY KEY, ip text NOT NULL, at timestamptz NOT NULL DEFAULT now())`;
      await sql`CREATE INDEX IF NOT EXISTS login_attempts_idx ON login_attempts (ip, at)`;

      for (const [key, value, description] of DEFAULT_CONFIG) {
        await sql`INSERT INTO config (key, value, description)
          VALUES (${key}, ${JSON.stringify(value)}::jsonb, ${description})
          ON CONFLICT (key) DO NOTHING`;
      }
    })().catch((error) => {
      ready = undefined; // try again on the next request
      throw error;
    });
    return ready;
  }

  return {
    ensureSchema,

    /* ------------------------------------------------------------ ingest */

    async upsertDevice(d) {
      await ensureSchema();
      await sql`INSERT INTO devices (installation_id, platform, manufacturer, model, os_version, sdk_int,
          app_version, app_build, locale, push_token)
        VALUES (${d.installationId}, ${d.platform}, ${d.manufacturer}, ${d.model}, ${d.osVersion}, ${d.sdkInt},
          ${d.appVersion}, ${d.appBuild}, ${d.locale}, ${d.pushToken})
        ON CONFLICT (installation_id) DO UPDATE SET
          platform = EXCLUDED.platform,
          manufacturer = COALESCE(EXCLUDED.manufacturer, devices.manufacturer),
          model = COALESCE(EXCLUDED.model, devices.model),
          os_version = COALESCE(EXCLUDED.os_version, devices.os_version),
          sdk_int = COALESCE(EXCLUDED.sdk_int, devices.sdk_int),
          app_version = COALESCE(EXCLUDED.app_version, devices.app_version),
          app_build = COALESCE(EXCLUDED.app_build, devices.app_build),
          locale = COALESCE(EXCLUDED.locale, devices.locale),
          push_token = COALESCE(EXCLUDED.push_token, devices.push_token),
          last_seen_at = now()`;
    },

    /** Events received from this installation in the last [seconds]; used for a simple per-device rate limit. */
    async recentEventCount(installationId, seconds) {
      await ensureSchema();
      const rows = await sql`SELECT count(*)::int AS n FROM events
        WHERE installation_id = ${installationId}::uuid
          AND received_at > now() - make_interval(secs => ${seconds}::int)`;
      return rows[0].n;
    },

    /** Stores events (ignoring ones already stored) and returns how many were new. */
    async insertEvents(installationId, appVersion, events) {
      await ensureSchema();

      // The device row may not exist yet (events can arrive before registration succeeded).
      await sql`INSERT INTO devices (installation_id, app_version) VALUES (${installationId}, ${appVersion})
        ON CONFLICT (installation_id) DO UPDATE SET
          app_version = COALESCE(EXCLUDED.app_version, devices.app_version), last_seen_at = now()`;

      if (events.length === 0) return 0;

      const rows = await sql`WITH added AS (
          INSERT INTO events (installation_id, name, occurred_at, properties)
          SELECT ${installationId}::uuid, x.name, to_timestamp(x.at / 1000.0), x.properties
          FROM jsonb_to_recordset(${JSON.stringify(events)}::jsonb) AS x(name text, at bigint, properties jsonb)
          ON CONFLICT (installation_id, name, occurred_at) DO NOTHING
          RETURNING 1)
        SELECT count(*)::int AS inserted FROM added`;
      return rows[0].inserted;
    },

    /* --------------------------------------------------------- analytics */

    async overview(days, now = Date.now()) {
      await ensureSchema();
      const cutoff = new Date(now - days * DAY).toISOString();
      const previous = new Date(now - 2 * days * DAY).toISOString();

      const [r] = await sql`SELECT
          (SELECT count(*) FROM devices)::int AS total_now,
          (SELECT count(*) FROM devices WHERE created_at < ${cutoff}::timestamptz)::int AS total_then,
          (SELECT count(DISTINCT installation_id) FROM events WHERE occurred_at >= ${cutoff}::timestamptz)::int AS active_now,
          (SELECT count(DISTINCT installation_id) FROM events
             WHERE occurred_at >= ${previous}::timestamptz AND occurred_at < ${cutoff}::timestamptz)::int AS active_then,
          (SELECT count(*) FROM devices WHERE created_at >= ${cutoff}::timestamptz)::int AS new_now,
          (SELECT count(*) FROM devices
             WHERE created_at >= ${previous}::timestamptz AND created_at < ${cutoff}::timestamptz)::int AS new_then,
          (SELECT count(*) FROM events WHERE occurred_at >= ${cutoff}::timestamptz)::int AS events_now,
          (SELECT count(*) FROM events
             WHERE occurred_at >= ${previous}::timestamptz AND occurred_at < ${cutoff}::timestamptz)::int AS events_then`;

      const metric = (now_, then) => ({ value: now_, change_pct: change(now_, then) });

      return {
        period_days: days,
        total_installs: metric(r.total_now, r.total_then),
        active_installs: metric(r.active_now, r.active_then),
        new_installs: metric(r.new_now, r.new_then),
        events: metric(r.events_now, r.events_then),
      };
    },

    async installAnalytics(days, now = Date.now()) {
      await ensureSchema();
      const start = new Date(startOfUtcDay(now) - (days - 1) * DAY).toISOString();

      const active = await sql`SELECT to_char(occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day,
          count(DISTINCT installation_id)::int AS value
        FROM events WHERE occurred_at >= ${start}::timestamptz GROUP BY 1`;
      const fresh = await sql`SELECT to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day,
          count(*)::int AS value
        FROM devices WHERE created_at >= ${start}::timestamptz GROUP BY 1`;

      return { period_days: days, daily_active: fillDays(active, days, now), new_installs: fillDays(fresh, days, now) };
    },

    async eventAnalytics(days, now = Date.now()) {
      await ensureSchema();
      const start = new Date(startOfUtcDay(now) - (days - 1) * DAY).toISOString();

      const daily = await sql`SELECT to_char(occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day, count(*)::int AS value
        FROM events WHERE occurred_at >= ${start}::timestamptz GROUP BY 1`;
      const top = await sql`SELECT name AS label, count(*)::int AS count FROM events
        WHERE occurred_at >= ${start}::timestamptz GROUP BY name ORDER BY 2 DESC, 1 LIMIT 10`;
      const recent = await sql`SELECT e.id::text AS id, e.name, e.installation_id::text AS installation_id,
          d.model AS device_model, d.app_version,
          to_char(e.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS occurred_at,
          e.properties
        FROM events e LEFT JOIN devices d ON d.installation_id = e.installation_id
        WHERE e.occurred_at >= ${start}::timestamptz
        ORDER BY e.occurred_at DESC, e.id DESC LIMIT 20`;

      return { period_days: days, daily: fillDays(daily, days, now), top_events: top, recent };
    },

    async deviceAnalytics() {
      await ensureSchema();
      const osVersions = await sql`SELECT COALESCE(os_version, 'Unknown') AS label, count(*)::int AS count
        FROM devices GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 10`;
      const models = await sql`SELECT COALESCE(model, 'Unknown') AS label, count(*)::int AS count
        FROM devices GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 8`;
      return { os_versions: osVersions, models };
    },

    async versionAnalytics() {
      await ensureSchema();
      const versions = await sql`SELECT COALESCE(app_version, 'Unknown') AS label, count(*)::int AS count
        FROM devices GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 20`;
      const [latest] = await sql`SELECT value #>> '{}' AS latest FROM config WHERE key = 'latest_version'`;
      return { latest_version: latest?.latest ?? null, versions };
    },

    /* ----------------------------------------------------------- devices */

    async listDevices({ page, pageSize, search, appVersion }) {
      await ensureSchema();
      const offset = (page - 1) * pageSize;

      const items = await sql`SELECT d.installation_id::text AS id, d.platform, d.manufacturer, d.model, d.os_version,
          d.app_version, d.locale,
          (SELECT count(*) FROM events e WHERE e.installation_id = d.installation_id)::int AS event_count,
          to_char(d.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at,
          to_char(d.last_seen_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS last_seen_at
        FROM devices d
        WHERE (${appVersion}::text IS NULL OR d.app_version = ${appVersion}::text)
          AND (${search}::text IS NULL
            OR starts_with(d.installation_id::text, ${search}::text)
            OR position(${search}::text in lower(COALESCE(d.manufacturer, '') || ' ' || COALESCE(d.model, ''))) > 0
            OR position(${search}::text in lower(COALESCE(d.os_version, ''))) > 0)
        ORDER BY d.last_seen_at DESC, d.installation_id
        LIMIT ${pageSize}::int OFFSET ${offset}::int`;

      const [{ total }] = await sql`SELECT count(*)::int AS total FROM devices d
        WHERE (${appVersion}::text IS NULL OR d.app_version = ${appVersion}::text)
          AND (${search}::text IS NULL
            OR starts_with(d.installation_id::text, ${search}::text)
            OR position(${search}::text in lower(COALESCE(d.manufacturer, '') || ' ' || COALESCE(d.model, ''))) > 0
            OR position(${search}::text in lower(COALESCE(d.os_version, ''))) > 0)`;

      return { items, total, page, page_size: pageSize };
    },

    async getDevice(id) {
      await ensureSchema();
      const [device] = await sql`SELECT d.installation_id::text AS id, d.platform, d.manufacturer, d.model, d.os_version,
          d.app_version, d.locale,
          (SELECT count(*) FROM events e WHERE e.installation_id = d.installation_id)::int AS event_count,
          to_char(d.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at,
          to_char(d.last_seen_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS last_seen_at
        FROM devices d WHERE d.installation_id = ${id}::uuid`;
      if (!device) return null;

      const recent = await sql`SELECT e.id::text AS id, e.name, e.installation_id::text AS installation_id,
          ${device.model}::text AS device_model, ${device.app_version}::text AS app_version,
          to_char(e.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS occurred_at, e.properties
        FROM events e WHERE e.installation_id = ${id}::uuid ORDER BY e.occurred_at DESC, e.id DESC LIMIT 20`;
      const top = await sql`SELECT name AS label, count(*)::int AS count FROM events
        WHERE installation_id = ${id}::uuid GROUP BY name ORDER BY 2 DESC, 1 LIMIT 10`;

      return { ...device, recent_events: recent, top_events: top };
    },

    /* ----------------------------------------------------- notifications */

    async countPushDevices() {
      await ensureSchema();
      const [{ n }] = await sql`SELECT count(*)::int AS n FROM devices WHERE push_token IS NOT NULL`;
      return n;
    },

    async pushTokens(ids) {
      await ensureSchema();
      return sql`SELECT installation_id::text AS id, push_token AS token FROM devices
        WHERE push_token IS NOT NULL
          AND installation_id = ANY(ARRAY(SELECT jsonb_array_elements_text(${JSON.stringify(ids)}::jsonb))::uuid[])`;
    },

    async clearPushToken(token) {
      await ensureSchema();
      await sql`UPDATE devices SET push_token = NULL WHERE push_token = ${token}`;
    },

    async createNotification({ title, body, audience, deviceIds, createdBy }) {
      await ensureSchema();
      const [row] = await sql`INSERT INTO notifications (title, body, audience, device_ids, status, created_by)
        VALUES (${title}, ${body}, ${audience}, ${JSON.stringify(deviceIds)}::jsonb, 'sending', ${createdBy})
        RETURNING id::text AS id`;
      return row.id;
    },

    async finishNotification(id, { status, recipients, successes, failures }) {
      await ensureSchema();
      await sql`UPDATE notifications SET status = ${status}, recipient_count = ${recipients}::int,
          success_count = ${successes}::int, failure_count = ${failures}::int, sent_at = now()
        WHERE id = ${id}::uuid`;
    },

    async getNotification(id) {
      await ensureSchema();
      const [row] = await sql`SELECT id::text AS id, title, body, audience, status, recipient_count, success_count,
          failure_count, created_by,
          to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at,
          to_char(sent_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS sent_at
        FROM notifications WHERE id = ${id}::uuid`;
      return row ?? null;
    },

    async listNotifications({ page, pageSize }) {
      await ensureSchema();
      const offset = (page - 1) * pageSize;
      const items = await sql`SELECT id::text AS id, title, body, audience, status, recipient_count, success_count,
          failure_count, created_by,
          to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at,
          to_char(sent_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS sent_at
        FROM notifications ORDER BY created_at DESC, id LIMIT ${pageSize}::int OFFSET ${offset}::int`;
      const [{ total }] = await sql`SELECT count(*)::int AS total FROM notifications`;
      return { items, total, page, page_size: pageSize };
    },

    /* ------------------------------------------------------------ config */

    async getConfig() {
      await ensureSchema();
      const entries = await sql`SELECT key, value, description,
          to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS updated_at
        FROM config ORDER BY key`;
      const updatedAt = entries.map((e) => e.updated_at).sort().at(-1) ?? null;
      return { entries, updated_at: updatedAt };
    },

    /** Returns the keys that don't exist (nothing is changed then), or an empty list after updating. */
    async updateConfig(values) {
      await ensureSchema();
      const json = JSON.stringify(values);

      const unknown = await sql`SELECT k AS key FROM jsonb_object_keys(${json}::jsonb) AS k
        WHERE k NOT IN (SELECT key FROM config)`;
      if (unknown.length) return unknown.map((r) => r.key);

      await sql`UPDATE config c SET value = x.value, updated_at = now()
        FROM jsonb_each(${json}::jsonb) AS x WHERE c.key = x.key`;
      return [];
    },

    /* ----------------------------------------------------- login throttle */

    async failedLoginCount(ip, minutes) {
      await ensureSchema();
      const [{ n }] = await sql`SELECT count(*)::int AS n FROM login_attempts
        WHERE ip = ${ip} AND at > now() - make_interval(mins => ${minutes}::int)`;
      return n;
    },

    async recordFailedLogin(ip) {
      await ensureSchema();
      await sql`INSERT INTO login_attempts (ip) VALUES (${ip})`;
    },

    /* ------------------------------------------------------------ cleanup */

    /** Deletes events older than [days] and stale login attempts; returns how many events went. */
    async prune(days) {
      await ensureSchema();
      await sql`DELETE FROM login_attempts WHERE at < now() - interval '1 day'`;
      const rows = await sql`WITH gone AS (
          DELETE FROM events WHERE occurred_at < now() - make_interval(days => ${days}::int) RETURNING 1)
        SELECT count(*)::int AS deleted FROM gone`;
      return rows[0].deleted;
    },
  };
}

let shared;
let override;

/** The app-wide database. Reads DATABASE_URL (or POSTGRES_URL, which Vercel's Postgres integration sets). */
export function getDb() {
  if (override) return override;
  if (shared) return shared;

  const url = process.env.DATABASE_URL || process.env.POSTGRES_URL;
  if (!url) throw new Error("DATABASE_URL is not set");

  shared = createDb(neon(url));
  return shared;
}

/** Tests only: use a different database. Pass null to go back to the real one. */
export function setDbForTests(db) {
  override = db;
}
