import { getDb } from "./db.js";
import { fcmFromEnv } from "./fcm.js";
import { HttpError, applyCors, send } from "./http.js";
import { Reply } from "./reply.js";
import * as admin from "./handlers/admin.js";
import * as auth from "./handlers/auth.js";
import * as config from "./handlers/config.js";
import * as ingest from "./handlers/ingest.js";

/** [method, path pattern, handler]. Capture groups become `ctx.params`. */
const routes = [
  ["GET", /^\/health$/, async (ctx) => (await ctx.db.ensureSchema(), { ok: true })],

  // Admin sign-in
  ["POST", /^\/auth\/login$/, auth.login],
  ["GET", /^\/auth\/me$/, auth.me],
  ["POST", /^\/auth\/logout$/, auth.logout],

  // Called by the Android app (no login)
  ["POST", /^\/devices\/register$/, ingest.registerDevice],
  ["POST", /^\/events$/, ingest.ingestEvents],
  ["GET", /^\/config$/, config.getConfig],

  // Called by the admin website (admin token required)
  ["PUT", /^\/config$/, config.updateConfig],
  ["GET", /^\/admin\/analytics\/overview$/, admin.overview],
  ["GET", /^\/admin\/analytics\/installs$/, admin.installs],
  ["GET", /^\/admin\/analytics\/events$/, admin.events],
  ["GET", /^\/admin\/analytics\/devices$/, admin.devicesAnalytics],
  ["GET", /^\/admin\/analytics\/versions$/, admin.versions],
  ["GET", /^\/admin\/devices$/, admin.listDevices],
  ["GET", /^\/admin\/devices\/([^/]+)$/, admin.getDevice],
  ["POST", /^\/admin\/notifications\/send$/, admin.sendNotification],
  ["GET", /^\/admin\/notifications$/, admin.listNotifications],
  ["GET", /^\/admin\/notifications\/([^/]+)$/, admin.getNotification],
];

const PREFIX = "/api/v1";

/**
 * Answers one request. Everything under /api/v1 goes through here, so the whole API is a single
 * serverless function. `deps` lets tests swap the database, clock and push sender.
 */
export async function handle(req, res, deps = {}) {
  if (applyCors(req, res, deps.env?.ADMIN_ORIGIN ?? process.env.ADMIN_ORIGIN ?? "")) return;

  const url = new URL(req.url, "http://localhost");
  const path = (url.pathname.startsWith(PREFIX) ? url.pathname.slice(PREFIX.length) : url.pathname).replace(/\/+$/, "") || "/";

  const candidates = routes.filter(([, pattern]) => pattern.test(path));
  const match = candidates.find(([method]) => method === req.method);

  if (!match) {
    if (candidates.length) {
      res.setHeader("Allow", candidates.map(([method]) => method).join(", "));
      return send(res, 405, { error: "method not allowed" });
    }
    return send(res, 404, { error: "not found" });
  }

  try {
    const [, pattern, handler] = match;
    const env = deps.env ?? process.env;

    const ctx = {
      req,
      env,
      params: pattern.exec(path).slice(1).map(decodeURIComponent),
      query: req.query ?? Object.fromEntries(url.searchParams),
      db: deps.db ?? getDb(),
      fcm: deps.fcm ?? fcmFromEnv(env),
      now: deps.now ?? Date.now,
    };

    const result = await handler(ctx);

    if (result instanceof Reply) send(res, result.status, result.body);
    else if (result === undefined) send(res, 204);
    else send(res, 200, result);
  } catch (error) {
    if (error instanceof HttpError) return send(res, error.status, { error: error.message });
    console.error("request failed", error);
    send(res, 500, { error: "server error" });
  }
}
