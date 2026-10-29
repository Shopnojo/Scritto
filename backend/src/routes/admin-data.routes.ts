import { randomUUID } from "node:crypto";
import { Router } from "express";
import type { User } from "@supabase/supabase-js";
import { requireAdmin, requireAuth } from "../middleware/auth.js";
import { getSupabaseAdminClient } from "../lib/supabase-admin.js";

export const adminDataRouter = Router();

const allowedDays = new Set([7, 30, 90]);
const EVENT_NAMES = [
  "app_opened", "note_created", "task_created", "task_completed",
  "event_scheduled", "document_opened", "ai_message_sent",
  "voice_assistant_used", "file_imported"
] as const;

function daysFrom(value: unknown): number {
  const parsed = Number(value ?? 30);
  return allowedDays.has(parsed) ? parsed : 30;
}

function pageFrom(value: unknown): number {
  const parsed = Number(value ?? 1);
  return Number.isInteger(parsed) && parsed > 0 ? Math.min(parsed, 1_000_000) : 1;
}

function pageSizeFrom(value: unknown): number {
  const parsed = Number(value ?? 20);
  return Number.isInteger(parsed) && parsed > 0 ? Math.min(parsed, 100) : 20;
}

function sendUnavailable(res: import("express").Response) {
  res.status(503).json({ error: { code: "DATABASE_UNAVAILABLE", message: "The requested data is temporarily unavailable." } });
}

function adminClient(res: import("express").Response) {
  const client = getSupabaseAdminClient();
  if (!client) {
    res.status(503).json({ error: { code: "ADMIN_DATA_NOT_CONFIGURED", message: "Server-side database access is not configured." } });
    return null;
  }
  return client;
}

function dateKey(date: Date) {
  return date.toISOString().slice(0, 10);
}

function daysAgo(days: number, offset = 0) {
  const date = new Date();
  date.setUTCHours(0, 0, 0, 0);
  date.setUTCDate(date.getUTCDate() - days + 1 - offset);
  return date;
}

function timeSeries(rows: Array<{ occurred_at?: string; received_at?: string }>, days: number, field: "occurred_at" | "received_at") {
  const counts = new Map<string, number>();
  for (const row of rows) {
    const value = row[field];
    if (value) {
      const key = dateKey(new Date(value));
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
  }
  const start = daysAgo(days);
  return Array.from({ length: days }, (_, i) => {
    const date = new Date(start);
    date.setUTCDate(start.getUTCDate() + i);
    const key = dateKey(date);
    return { date: key, value: counts.get(key) ?? 0 };
  });
}

function breakdown(values: Array<string | null | undefined>, limit = 100) {
  const counts = new Map<string, number>();
  for (const value of values) {
    const label = value?.trim() || "Unknown";
    counts.set(label, (counts.get(label) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label))
    .slice(0, limit);
}

function percentChange(current: number, previous: number): number | null {
  if (previous === 0) return current === 0 ? 0 : null;
  return Math.round(((current - previous) / previous) * 1000) / 10;
}

async function countInstalls(client: NonNullable<ReturnType<typeof getSupabaseAdminClient>>, from?: string, to?: string) {
  let query = client.from("installations").select("installation_id", { count: "exact", head: true });
  if (from) query = query.gte("created_at", from);
  if (to) query = query.lt("created_at", to);
  const result = await query;
  if (result.error) throw result.error;
  return result.count ?? 0;
}

async function loadEvents(client: NonNullable<ReturnType<typeof getSupabaseAdminClient>>, from: string, to?: string, limit = 10000) {
  let query = client.from("usage_events")
    .select("id, name, installation_id, user_id, occurred_at, received_at, app_version, properties, installations(model)")
    .gte("occurred_at", from)
    .order("occurred_at", { ascending: false })
    .limit(limit);
  if (to) query = query.lt("occurred_at", to);
  const result = await query;
  if (result.error) throw result.error;
  return (result.data ?? []) as Array<{
    id: number; name: string; installation_id: string; occurred_at: string; received_at: string;
    app_version: string | null; properties: Record<string, unknown>;
    installations: { model: string | null } | null;
  }>;
}

adminDataRouter.use(requireAuth, requireAdmin);

adminDataRouter.get("/devices", async (req, res) => {
  const client = adminClient(res);
  if (!client) return;
  const page = pageFrom(req.query.page);
  const pageSize = pageSizeFrom(req.query.page_size);
  const search = typeof req.query.search === "string" ? req.query.search.trim().slice(0, 120) : "";
  const appVersion = typeof req.query.app_version === "string" ? req.query.app_version.trim().slice(0, 80) : "";
  try {
    let query = client.from("installations")
      .select("installation_id, platform, manufacturer, model, os_version, app_version, locale, created_at, last_seen_at", { count: "exact" })
      .order("last_seen_at", { ascending: false })
      .range((page - 1) * pageSize, page * pageSize - 1);
    if (appVersion) query = query.eq("app_version", appVersion);
    if (search) {
      const safe = search.replace(/[,%()]/g, "");
      query = query.or(`installation_id.ilike.%${safe}%,manufacturer.ilike.%${safe}%,model.ilike.%${safe}%,os_version.ilike.%${safe}%`);
    }
    const result = await query;
    if (result.error) throw result.error;
    const rows = result.data ?? [];
    const ids = rows.map((row) => row.installation_id);
    const counts = new Map<string, number>();
    if (ids.length) {
      const events = await client.from("usage_events").select("installation_id").in("installation_id", ids).limit(10000);
      if (events.error) throw events.error;
      for (const event of events.data ?? []) counts.set(event.installation_id, (counts.get(event.installation_id) ?? 0) + 1);
    }
    res.json({
      items: rows.map((row) => ({
        id: row.installation_id, platform: row.platform, manufacturer: row.manufacturer,
        model: row.model, os_version: row.os_version, app_version: row.app_version,
        locale: row.locale, created_at: row.created_at, last_seen_at: row.last_seen_at,
        event_count: counts.get(row.installation_id) ?? 0
      })),
      total: result.count ?? 0, page, page_size: pageSize
    });
  } catch { sendUnavailable(res); }
});

adminDataRouter.get("/devices/:id", async (req, res) => {
  const client = adminClient(res);
  if (!client) return;
  try {
    const found = await client.from("installations")
      .select("installation_id, platform, manufacturer, model, os_version, app_version, locale, created_at, last_seen_at")
      .eq("installation_id", req.params.id).maybeSingle();
    if (found.error) throw found.error;
    if (!found.data) {
      res.status(404).json({ error: { code: "DEVICE_NOT_FOUND", message: "The requested installation was not found." } });
      return;
    }
    const eventResult = await client.from("usage_events")
      .select("id, name, installation_id, occurred_at, app_version, properties")
      .eq("installation_id", req.params.id)
      .order("occurred_at", { ascending: false }).limit(25);
    if (eventResult.error) throw eventResult.error;
    const events = (eventResult.data ?? []).map((event) => ({
      id: String(event.id), name: event.name, installation_id: event.installation_id,
      device_model: found.data.model, app_version: event.app_version,
      occurred_at: event.occurred_at, properties: event.properties
    }));
    res.json({
      id: found.data.installation_id, platform: found.data.platform,
      manufacturer: found.data.manufacturer, model: found.data.model,
      os_version: found.data.os_version, app_version: found.data.app_version,
      locale: found.data.locale, created_at: found.data.created_at,
      last_seen_at: found.data.last_seen_at, event_count: events.length,
      recent_events: events, top_events: breakdown(events.map((event) => event.name))
    });
  } catch { sendUnavailable(res); }
});

adminDataRouter.get("/analytics/overview", async (req, res) => {
  const client = adminClient(res);
  if (!client) return;
  const days = daysFrom(req.query.days);
  try {
    const now = new Date();
    const start = daysAgo(days);
    const previousStart = daysAgo(days, days);
    const [total, currentNew, previousNew, currentEvents, previousEvents] = await Promise.all([
      countInstalls(client),
      countInstalls(client, start.toISOString(), now.toISOString()),
      countInstalls(client, previousStart.toISOString(), start.toISOString()),
      loadEvents(client, start.toISOString(), now.toISOString(), 10000),
      loadEvents(client, previousStart.toISOString(), start.toISOString(), 10000)
    ]);
    const active = new Set(currentEvents.map((event) => event.installation_id)).size;
    const previousActive = new Set(previousEvents.map((event) => event.installation_id)).size;
    res.json({
      period_days: days,
      total_installs: { value: total, change_pct: null },
      active_installs: { value: active, change_pct: percentChange(active, previousActive) },
      new_installs: { value: currentNew, change_pct: percentChange(currentNew, previousNew) },
      events: { value: currentEvents.length, change_pct: percentChange(currentEvents.length, previousEvents.length) }
    });
  } catch { sendUnavailable(res); }
});

adminDataRouter.get("/analytics/installs", async (req, res) => {
  const client = adminClient(res);
  if (!client) return;
  const days = daysFrom(req.query.days);
  try {
    const now = new Date();
    const start = daysAgo(days);
    const previousStart = daysAgo(days, days);
    const [events, installations] = await Promise.all([
      loadEvents(client, start.toISOString(), now.toISOString(), 10000),
      client.from("installations").select("created_at").gte("created_at", start.toISOString()).lt("created_at", now.toISOString()).order("created_at").limit(10000)
    ]);
    if (installations.error) throw installations.error;
    const dailyActive = new Map<string, Set<string>>();
    for (const event of events) {
      const key = dateKey(new Date(event.occurred_at));
      if (!dailyActive.has(key)) dailyActive.set(key, new Set());
      dailyActive.get(key)!.add(event.installation_id);
    }
    const activeSeries = timeSeries(events, days, "occurred_at").map((point) => ({ ...point, value: dailyActive.get(point.date)?.size ?? 0 }));
    const newSeries = timeSeries((installations.data ?? []).map((row) => ({ received_at: row.created_at })), days, "received_at");
    res.json({ period_days: days, daily_active: activeSeries, new_installs: newSeries });
  } catch { sendUnavailable(res); }
});

adminDataRouter.get("/analytics/events", async (req, res) => {
  const client = adminClient(res);
  if (!client) return;
  const days = daysFrom(req.query.days);
  try {
    const start = daysAgo(days);
    const events = await loadEvents(client, start.toISOString(), new Date().toISOString(), 10000);
    res.json({
      period_days: days,
      daily: timeSeries(events, days, "occurred_at"),
      top_events: breakdown(events.map((event) => event.name), 10),
      recent: events.slice(0, 25).map((event) => ({
        id: String(event.id), name: event.name, installation_id: event.installation_id,
        device_model: event.installations?.model ?? null, app_version: event.app_version,
        occurred_at: event.occurred_at, properties: event.properties
      }))
    });
  } catch { sendUnavailable(res); }
});

adminDataRouter.get("/analytics/devices", async (_req, res) => {
  const client = adminClient(res);
  if (!client) return;
  try {
    const result = await client.from("installations").select("os_version, model").limit(10000);
    if (result.error) throw result.error;
    res.json({ os_versions: breakdown((result.data ?? []).map((row) => row.os_version)), models: breakdown((result.data ?? []).map((row) => row.model), 8) });
  } catch { sendUnavailable(res); }
});

adminDataRouter.get("/analytics/versions", async (_req, res) => {
  const client = adminClient(res);
  if (!client) return;
  try {
    const result = await client.from("installations").select("app_version").limit(10000);
    if (result.error) throw result.error;
    res.json({
      latest_version: process.env.LATEST_APP_VERSION?.trim() || null,
      versions: breakdown((result.data ?? []).map((row) => row.app_version))
    });
  } catch { sendUnavailable(res); }
});

export const configRouter = Router();

configRouter.get("/", async (_req, res) => {
  const client = getSupabaseAdminClient();
  if (!client) {
    res.status(503).json({ error: { code: "CONFIG_NOT_CONFIGURED", message: "Remote configuration is not available." } });
    return;
  }
  try {
    const result = await client.from("remote_config").select("key, value, description, updated_at").order("key");
    if (result.error) throw result.error;
    const entries = (result.data ?? []).map((row) => ({ key: row.key, value: row.value, description: row.description, updated_at: row.updated_at }));
    res.json({ entries, updated_at: entries.map((entry) => entry.updated_at ?? "").sort().at(-1) || null });
  } catch { sendUnavailable(res); }
});

configRouter.put("/", requireAuth, requireAdmin, async (req, res) => {
  const client = adminClient(res);
  if (!client) return;
  const body = req.body as { values?: unknown } | undefined;
  if (!body?.values || typeof body.values !== "object" || Array.isArray(body.values)) {
    res.status(400).json({ error: { code: "INVALID_CONFIG_PAYLOAD", message: "values must be a JSON object." } });
    return;
  }
  const values = body.values as Record<string, unknown>;
  const entries = Object.entries(values);
  if (!entries.length || entries.length > 100 || entries.some(([key, value]) =>
    !/^[a-z][a-z0-9_.-]{0,99}$/.test(key) || JSON.stringify(value)?.length > 10000
  )) {
    res.status(400).json({ error: { code: "INVALID_CONFIG_PAYLOAD", message: "Configuration keys or values are invalid." } });
    return;
  }
  const user = res.locals.authUser as User;
  try {
    const saved = await client.from("remote_config").upsert(
      entries.map(([key, value]) => ({ key, value, updated_by: user.id, updated_at: new Date().toISOString() })),
      { onConflict: "key" }
    );
    if (saved.error) throw saved.error;
    const refreshed = await client.from("remote_config").select("key, value, description, updated_at").order("key");
    if (refreshed.error) throw refreshed.error;
    const allEntries = refreshed.data ?? [];
    res.json({ entries: allEntries, updated_at: new Date().toISOString() });
  } catch { sendUnavailable(res); }
});

export const notificationsRouter = Router();
notificationsRouter.use(requireAuth, requireAdmin);

notificationsRouter.get("/", async (req, res) => {
  const client = adminClient(res);
  if (!client) return;
  const page = pageFrom(req.query.page);
  const pageSize = pageSizeFrom(req.query.page_size);
  try {
    const result = await client.from("admin_notifications")
      .select("id, title, body, audience, status, total_count, success_count, failure_count, created_by, created_at, sent_at", { count: "exact" })
      .order("created_at", { ascending: false }).range((page - 1) * pageSize, page * pageSize - 1);
    if (result.error) throw result.error;
    res.json({
      items: (result.data ?? []).map((row) => ({
        id: row.id, title: row.title, body: row.body,
        audience: (row.audience as { type?: string }).type === "devices" ? "devices" : "all",
        status: row.status === "partial" ? "partial" : row.status,
        recipient_count: row.total_count, success_count: row.success_count,
        failure_count: row.failure_count, created_by: row.created_by,
        created_at: row.created_at, sent_at: row.sent_at
      })),
      total: result.count ?? 0, page, page_size: pageSize
    });
  } catch { sendUnavailable(res); }
});

notificationsRouter.get("/:id", async (req, res) => {
  const client = adminClient(res);
  if (!client) return;
  try {
    const result = await client.from("admin_notifications")
      .select("id, title, body, audience, status, total_count, success_count, failure_count, created_by, created_at, sent_at")
      .eq("id", req.params.id).maybeSingle();
    if (result.error) throw result.error;
    if (!result.data) {
      res.status(404).json({ error: { code: "NOTIFICATION_NOT_FOUND", message: "The requested notification was not found." } });
      return;
    }
    const row = result.data;
    res.json({
      id: row.id, title: row.title, body: row.body,
      audience: (row.audience as { type?: string }).type === "devices" ? "devices" : "all",
      status: row.status, recipient_count: row.total_count,
      success_count: row.success_count, failure_count: row.failure_count,
      created_by: row.created_by, created_at: row.created_at, sent_at: row.sent_at
    });
  } catch { sendUnavailable(res); }
});

notificationsRouter.post("/send", async (req, res) => {
  const client = adminClient(res);
  if (!client) return;
  const body = req.body as { title?: unknown; body?: unknown; audience?: unknown; device_ids?: unknown } | undefined;
  const title = typeof body?.title === "string" ? body.title.trim() : "";
  const message = typeof body?.body === "string" ? body.body.trim() : "";
  const audience = body?.audience;
  const deviceIds = body?.device_ids;
  if (!title || title.length > 160 || !message || message.length > 2000 ||
      !["all", "devices"].includes(String(audience)) ||
      (audience === "devices" && (!Array.isArray(deviceIds) || !deviceIds.length || deviceIds.length > 1000 || deviceIds.some((id) => typeof id !== "string")))) {
    res.status(400).json({ error: { code: "INVALID_NOTIFICATION_PAYLOAD", message: "Provide a title, message, and valid audience." } });
    return;
  }
  const user = res.locals.authUser as User;
  try {
    const recipientsQuery = client.from("installations").select("installation_id, push_token");
    const recipients = audience === "all"
      ? await recipientsQuery
      : await recipientsQuery.in("installation_id", deviceIds as string[]);
    if (recipients.error) throw recipients.error;
    const targetRows = recipients.data ?? [];
    const id = randomUUID();
    const inserted = await client.from("admin_notifications").insert({
      id, title, body: message,
      audience: audience === "all" ? { type: "all" } : { type: "devices", device_ids: deviceIds },
      status: "queued", total_count: targetRows.length, success_count: 0,
      failure_count: 0, created_by: user.id
    });
    if (inserted.error) throw inserted.error;
    if (targetRows.length) {
      const deliveries = await client.from("notification_deliveries").insert(targetRows.map((target) => ({
        notification_id: id, installation_id: target.installation_id, status: "pending"
      })));
      if (deliveries.error) throw deliveries.error;
    }
    res.status(202).json({
      id, title, body: message, audience, status: "queued",
      recipient_count: targetRows.length, success_count: 0, failure_count: 0,
      created_by: user.id, created_at: new Date().toISOString(), sent_at: null
    });
  } catch { sendUnavailable(res); }
});
