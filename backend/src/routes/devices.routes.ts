import { randomUUID } from "node:crypto";
import { Router } from "express";
import { requireAuth } from "../middleware/auth.js";
import { getSupabaseAdminClient } from "../lib/supabase-admin.js";

export const devicesRouter = Router();

const stringLimits = {
  manufacturer: 120,
  model: 160,
  os_version: 80,
  app_version: 80,
  app_build: 80,
  locale: 35,
  push_token: 4096
} as const;

function optionalString(value: unknown, field: keyof typeof stringLimits): string | null | undefined {
  if (value === undefined || value === null) return value;
  if (typeof value !== "string") return undefined;
  const normalized = value.trim();
  if (normalized.length > stringLimits[field]) return undefined;
  return normalized || null;
}

devicesRouter.post("/register", requireAuth, async (req, res) => {
  const user = res.locals.authUser as { id: string } | undefined;
  if (!user) {
    res.status(401).json({ error: { code: "AUTH_REQUIRED", message: "Authentication is required." } });
    return;
  }

  if (!req.body || typeof req.body !== "object" || Array.isArray(req.body)) {
    res.status(400).json({ error: { code: "INVALID_DEVICE_PAYLOAD", message: "A JSON object is required." } });
    return;
  }

  const body = req.body as Record<string, unknown>;
  const allowedFields = new Set([
    "installation_id", "platform", "manufacturer", "model", "os_version",
    "sdk_int", "app_version", "app_build", "locale", "push_token"
  ]);
  if (Object.keys(body).some((key) => !allowedFields.has(key))) {
    res.status(400).json({ error: { code: "INVALID_DEVICE_PAYLOAD", message: "The payload contains unsupported fields." } });
    return;
  }

  const installationId = body.installation_id === undefined ? randomUUID() : body.installation_id;
  if (typeof installationId !== "string" ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(installationId)) {
    res.status(400).json({ error: { code: "INVALID_INSTALLATION_ID", message: "installation_id must be a UUID." } });
    return;
  }

  const platform = body.platform === undefined ? "android" : body.platform;
  if (typeof platform !== "string" || !["android", "ios", "web", "unknown"].includes(platform)) {
    res.status(400).json({ error: { code: "INVALID_PLATFORM", message: "platform must be android, ios, web, or unknown." } });
    return;
  }

  if (body.sdk_int !== undefined && body.sdk_int !== null &&
      (!Number.isInteger(body.sdk_int) || (body.sdk_int as number) < 0)) {
    res.status(400).json({ error: { code: "INVALID_SDK_INT", message: "sdk_int must be a non-negative integer." } });
    return;
  }

  const metadata: Record<string, string | null> = {};
  for (const field of Object.keys(stringLimits) as (keyof typeof stringLimits)[]) {
    const value = optionalString(body[field], field);
    if (value === undefined) {
      res.status(400).json({ error: { code: "INVALID_DEVICE_FIELD", message: `${field} must be a string no longer than ${stringLimits[field]} characters.` } });
      return;
    }
    if (value !== undefined) metadata[field] = value;
  }

  const admin = getSupabaseAdminClient();
  if (!admin) {
    res.status(503).json({ error: { code: "DEVICE_REGISTRATION_NOT_CONFIGURED", message: "Device registration is not configured on this server." } });
    return;
  }

  try {
    const { data: existing, error: lookupError } = await admin
      .from("installations")
      .select("installation_id, user_id")
      .eq("installation_id", installationId)
      .maybeSingle();

    if (lookupError) {
      res.status(503).json({ error: { code: "DEVICE_REGISTRATION_UNAVAILABLE", message: "Could not check the installation record." } });
      return;
    }
    if (existing && existing.user_id !== user.id) {
      res.status(409).json({ error: { code: "INSTALLATION_ID_IN_USE", message: "This installation ID is already registered to another account." } });
      return;
    }

    const now = new Date().toISOString();
    const record = {
      installation_id: installationId,
      user_id: user.id,
      platform,
      ...metadata,
      sdk_int: body.sdk_int ?? null,
      last_seen_at: now,
      updated_at: now
    };

    const saved = existing
      ? await admin.from("installations").update(record).eq("installation_id", installationId).eq("user_id", user.id)
          .select("installation_id, platform, manufacturer, model, os_version, sdk_int, app_version, app_build, locale, created_at, last_seen_at, updated_at").single()
      : await admin.from("installations").insert(record)
          .select("installation_id, platform, manufacturer, model, os_version, sdk_int, app_version, app_build, locale, created_at, last_seen_at, updated_at").single();

    if (saved.error || !saved.data) {
      res.status(503).json({ error: { code: "DEVICE_REGISTRATION_UNAVAILABLE", message: "Could not save the installation record." } });
      return;
    }

    res.status(existing ? 200 : 201).json({ installation: saved.data });
  } catch {
    res.status(503).json({ error: { code: "DEVICE_REGISTRATION_UNAVAILABLE", message: "Could not save the installation record." } });
  }
});
