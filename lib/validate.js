// Pure input checks. No I/O, so they are easy to test. Anything the app or admin sends is untrusted.

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const EVENT_NAME = /^[a-z0-9_]{1,64}$/;
const PROPERTY_KEY = /^[a-z0-9_]{1,32}$/;
const CONFIG_KEY = /^[a-z0-9_]{1,64}$/;
const DAY_MS = 24 * 60 * 60 * 1000;

export const MAX_EVENTS_PER_REQUEST = 100;
const MAX_EVENT_AGE_MS = 90 * DAY_MS;
const MAX_FUTURE_MS = DAY_MS; // phone clocks are not exact

export function isUuid(value) {
  return typeof value === "string" && UUID.test(value);
}

function text(value, max) {
  if (value === undefined || value === null) return null;
  const s = String(value).trim().slice(0, max);
  return s || null;
}

function integer(value, min, max) {
  const n = Number(value);
  return Number.isInteger(n) && n >= min && n <= max ? n : null;
}

/** Body of POST /devices/register. Returns { device } or { error }. */
export function parseRegistration(body) {
  if (!body || typeof body !== "object") return { error: "body must be a JSON object" };
  if (!isUuid(body.installation_id)) return { error: "installation_id must be a UUID" };

  return {
    device: {
      installationId: body.installation_id.toLowerCase(),
      platform: text(body.platform, 20) ?? "android",
      manufacturer: text(body.manufacturer, 64),
      model: text(body.model, 64),
      osVersion: text(body.os_version, 64),
      sdkInt: integer(body.sdk_int, 1, 99),
      appVersion: text(body.app_version, 32),
      appBuild: integer(body.app_build, 0, 10_000_000),
      locale: text(body.locale, 32),
      pushToken: text(body.push_token, 4096),
    },
  };
}

/** Keeps at most 10 small, non-personal properties: short string / number / boolean values only. */
export function cleanProperties(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;

  const out = {};
  for (const [key, v] of Object.entries(value).slice(0, 10)) {
    if (!PROPERTY_KEY.test(key)) continue;
    if (typeof v === "string") out[key] = v.slice(0, 64);
    else if (typeof v === "number" && Number.isFinite(v)) out[key] = v;
    else if (typeof v === "boolean") out[key] = v;
  }
  return Object.keys(out).length ? out : null;
}

/** Body of POST /events. Returns { installationId, appVersion, events, dropped } or { error }. */
export function parseEventBatch(body, now = Date.now()) {
  if (!body || typeof body !== "object") return { error: "body must be a JSON object" };
  if (!isUuid(body.installation_id)) return { error: "installation_id must be a UUID" };
  if (!Array.isArray(body.events)) return { error: "events must be an array" };
  if (body.events.length > MAX_EVENTS_PER_REQUEST) {
    return { error: `at most ${MAX_EVENTS_PER_REQUEST} events per request` };
  }

  const events = [];
  let dropped = 0;

  for (const item of body.events) {
    const name = item && item.name;
    const at = item && typeof item.occurred_at === "string" ? Date.parse(item.occurred_at) : NaN;

    const ok =
      typeof name === "string" &&
      EVENT_NAME.test(name) &&
      Number.isFinite(at) &&
      at >= now - MAX_EVENT_AGE_MS &&
      at <= now + MAX_FUTURE_MS;

    if (ok) events.push({ name, at: Math.trunc(at), properties: cleanProperties(item.properties) });
    else dropped++;
  }

  return {
    installationId: body.installation_id.toLowerCase(),
    appVersion: text(body.app_version, 32),
    events,
    dropped,
  };
}

/** Body of POST /admin/notifications/send. Returns { notification } or { error }. */
export function parseNotification(body) {
  if (!body || typeof body !== "object") return { error: "body must be a JSON object" };

  const title = text(body.title, 100);
  const message = text(body.body, 500);
  if (!title || !message) return { error: "title and body are required" };

  if (body.audience === "all") {
    return { notification: { title, body: message, audience: "all", deviceIds: [] } };
  }

  if (body.audience === "devices") {
    const ids = Array.isArray(body.device_ids) ? body.device_ids : [];
    if (ids.length === 0 || ids.length > 500 || !ids.every(isUuid)) {
      return { error: "device_ids must be 1-500 installation ids" };
    }
    return {
      notification: {
        title,
        body: message,
        audience: "devices",
        deviceIds: [...new Set(ids.map((id) => id.toLowerCase()))],
      },
    };
  }

  return { error: "audience must be all or devices" };
}

/** Body of PUT /config. Returns { values } or { error }. */
export function parseConfigUpdate(body) {
  const values = body && body.values;
  if (!values || typeof values !== "object" || Array.isArray(values)) return { error: "values must be an object" };

  const entries = Object.entries(values);
  if (entries.length === 0 || entries.length > 50) return { error: "send between 1 and 50 values" };

  for (const [key, value] of entries) {
    if (!CONFIG_KEY.test(key)) return { error: `invalid key ${key}` };
    if (JSON.stringify(value ?? null).length > 10_000) return { error: `value for ${key} is too large` };
  }
  return { values: Object.fromEntries(entries.map(([k, v]) => [k, v ?? null])) };
}

/** Query-string helpers. */
export function intParam(value, fallback, min, max) {
  const n = Number.parseInt(Array.isArray(value) ? value[0] : value, 10);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(max, Math.max(min, n));
}

/** The admin site offers 7, 30 or 90 days. */
export function daysParam(value) {
  const n = intParam(value, 30, 1, 365);
  return n === 7 || n === 90 ? n : 30;
}

export function stringParam(value, max = 100) {
  const v = Array.isArray(value) ? value[0] : value;
  return typeof v === "string" && v.trim() ? v.trim().slice(0, max) : null;
}
