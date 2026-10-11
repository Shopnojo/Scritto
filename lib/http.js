import { createHash, timingSafeEqual } from "node:crypto";

/** Throw from a handler to answer with a status code. The body is generic on purpose. */
export class HttpError extends Error {
  constructor(status, message = "error") {
    super(message);
    this.status = status;
  }
}

export function send(res, code, body) {
  res.statusCode = code;
  if (body === undefined) return res.end();
  res.setHeader("Content-Type", "application/json");
  res.setHeader("Cache-Control", "no-store");
  res.end(JSON.stringify(body));
}

/** Vercel parses JSON bodies for us; this also copes with a raw string. */
export function jsonBody(req) {
  const body = req.body;
  if (typeof body === "string") {
    try {
      return JSON.parse(body);
    } catch {
      return null;
    }
  }
  return body ?? null;
}

function sha(value) {
  return createHash("sha256").update(String(value)).digest();
}

/** Constant-time comparison, so response timing can't be used to guess a secret. */
export function safeEqual(a, b) {
  return timingSafeEqual(sha(a), sha(b));
}

export function bearerToken(req) {
  const header = req.headers?.authorization ?? "";
  return header.startsWith("Bearer ") ? header.slice(7).trim() : "";
}

/** Best guess of the caller's IP behind Vercel's proxy. */
export function clientIp(req) {
  const forwarded = req.headers?.["x-forwarded-for"];
  const first = (Array.isArray(forwarded) ? forwarded[0] : forwarded)?.split(",")[0]?.trim();
  return first || req.socket?.remoteAddress || "unknown";
}

/**
 * CORS for the admin website, which calls with `credentials: "include"`. That mode forbids `*`, so
 * origins must be listed exactly in ADMIN_ORIGIN (comma separated). Returns true when the request was a
 * preflight that has been fully answered.
 */
export function applyCors(req, res, allowed = process.env.ADMIN_ORIGIN ?? "") {
  const origins = allowed.split(",").map((o) => o.trim().replace(/\/+$/, "")).filter(Boolean);
  const origin = req.headers?.origin;

  if (origin && origins.includes(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Access-Control-Allow-Credentials", "true");
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
    res.setHeader("Access-Control-Max-Age", "86400");
  }

  if (req.method === "OPTIONS") {
    send(res, 204);
    return true;
  }
  return false;
}
