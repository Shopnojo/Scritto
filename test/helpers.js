import { PGlite } from "@electric-sql/pglite";
import { hashPassword } from "../lib/auth.js";
import { createDb } from "../lib/db.js";
import { handle } from "../lib/router.js";

/** Turns PGlite into the tagged-template `sql` function the app code expects (same shape as Neon's). */
export function taggedSql(pg) {
  return async (strings, ...values) => {
    let text = strings[0];
    for (let i = 0; i < values.length; i++) text += `$${i + 1}${strings[i + 1]}`;
    const result = await pg.query(text, values);
    return result.rows;
  };
}

export const ADMIN_EMAIL = "admin@scritto.test";
export const ADMIN_PASSWORD = "correct horse battery";

export const ID = "35591f4a-9f99-4107-ad53-c9455aa38505";
export const ID2 = "11111111-2222-4333-8444-555555555555";

/** A fresh in-memory Postgres plus the settings the API reads from the environment. */
export async function createWorld(extraEnv = {}) {
  const pg = new PGlite();
  const db = createDb(taggedSql(pg));
  await db.ensureSchema();

  const sent = [];
  const fcm = {
    configured: true,
    failTokens: new Set(),
    sendToTopic: async (topic, message) => (sent.push({ topic, ...message }), { ok: true, unregistered: false }),
    sendToToken: async (token, message) => {
      sent.push({ token, ...message });
      return fcm.failTokens.has(token) ? { ok: false, unregistered: fcm.failTokens.has(token) } : { ok: true, unregistered: false };
    },
  };

  const env = {
    ADMIN_EMAIL,
    ADMIN_PASSWORD_HASH: hashPassword(ADMIN_PASSWORD),
    JWT_SECRET: "x".repeat(48),
    ADMIN_ORIGIN: "https://admin.example.com",
    ...extraEnv,
  };

  const deps = { db, env, fcm, now: () => Date.now() };

  /** Calls the API like Vercel would and returns { status, headers, body }. */
  async function call(method, path, { headers = {}, body, query } = {}) {
    const out = { status: 200, headers: {}, body: undefined };

    const res = {
      set statusCode(code) {
        out.status = code;
      },
      get statusCode() {
        return out.status;
      },
      setHeader(name, value) {
        out.headers[name.toLowerCase()] = value;
      },
      end(text) {
        out.body = text ? JSON.parse(text) : undefined;
      },
    };

    const qs = query ? `?${new URLSearchParams(query)}` : "";
    await handle({ method, url: `/api/v1${path}${qs}`, headers, body }, res, deps);
    return out;
  }

  async function adminToken() {
    const { body } = await call("POST", "/auth/login", { body: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } });
    return body.access_token;
  }

  /** Same as `call` but signed in as the admin. */
  async function asAdmin(method, path, options = {}) {
    const token = await adminToken();
    return call(method, path, { ...options, headers: { authorization: `Bearer ${token}`, ...options.headers } });
  }

  return { pg, db, env, fcm, sent, call, asAdmin, adminToken };
}
