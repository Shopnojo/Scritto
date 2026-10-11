import assert from "node:assert/strict";
import { before, test } from "node:test";
import { signToken, verifyToken } from "../lib/auth.js";
import { ADMIN_EMAIL, ADMIN_PASSWORD, createWorld } from "./helpers.js";

let w;
before(async () => {
  w = await createWorld();
});

const login = (body, headers) => w.call("POST", "/auth/login", { body, headers });

test("login returns a bearer token the site can use", async () => {
  const { status, body } = await login({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD });

  assert.equal(status, 200);
  assert.equal(body.token_type, "bearer");
  assert.equal(body.expires_in, 12 * 3600);

  const me = await w.call("GET", "/auth/me", { headers: { authorization: `Bearer ${body.access_token}` } });
  assert.equal(me.status, 200);
  assert.deepEqual(me.body, { id: "admin", email: ADMIN_EMAIL, display_name: "Scritto Admin", role: "admin", is_admin: true });
});

test("email is not case sensitive", async () => {
  assert.equal((await login({ email: ADMIN_EMAIL.toUpperCase(), password: ADMIN_PASSWORD })).status, 200);
});

test("wrong email, wrong password and empty bodies are all 401", async () => {
  assert.equal((await login({ email: ADMIN_EMAIL, password: "nope nope nope" }, { "x-forwarded-for": "9.9.9.1" })).status, 401);
  assert.equal((await login({ email: "other@x.test", password: ADMIN_PASSWORD }, { "x-forwarded-for": "9.9.9.2" })).status, 401);
  assert.equal((await login({}, { "x-forwarded-for": "9.9.9.3" })).status, 401);
  assert.equal((await login(undefined, { "x-forwarded-for": "9.9.9.4" })).status, 401);
});

test("too many failures from one address are throttled, even for the right password", async () => {
  const headers = { "x-forwarded-for": "7.7.7.7" };
  for (let i = 0; i < 10; i++) assert.equal((await login({ email: ADMIN_EMAIL, password: "bad bad bad" }, headers)).status, 401);

  assert.equal((await login({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }, headers)).status, 429);
  // Another address is unaffected.
  assert.equal((await login({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }, { "x-forwarded-for": "8.8.8.8" })).status, 200);
});

test("login says it is not configured instead of letting anyone in", async () => {
  const bare = await createWorld({ ADMIN_PASSWORD_HASH: "" });
  assert.equal((await bare.call("POST", "/auth/login", { body: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } })).status, 503);
});

test("protected routes need a valid, unexpired, correctly signed admin token", async () => {
  const secret = w.env.JWT_SECRET;
  const good = signToken({ sub: "admin", email: ADMIN_EMAIL, role: "admin" }, secret, 60);
  const expired = signToken({ sub: "admin", email: ADMIN_EMAIL, role: "admin" }, secret, 60, Date.now() - 3600_000);
  const forged = signToken({ sub: "admin", email: ADMIN_EMAIL, role: "admin" }, "y".repeat(48), 60);
  const notAdmin = signToken({ sub: "u1", email: "user@x.test", role: "user" }, secret, 60);
  const otherEmail = signToken({ sub: "admin", email: "other@x.test", role: "admin" }, secret, 60);
  const noneAlg = `${Buffer.from('{"alg":"none"}').toString("base64url")}.${Buffer.from(
    JSON.stringify({ sub: "admin", email: ADMIN_EMAIL, role: "admin", exp: 9999999999 })
  ).toString("base64url")}.`;

  const get = (token) => w.call("GET", "/admin/analytics/versions", { headers: token ? { authorization: `Bearer ${token}` } : {} });

  assert.equal((await get(good)).status, 200);
  assert.equal((await get(null)).status, 401);
  assert.equal((await get("garbage")).status, 401);
  assert.equal((await get(expired)).status, 401);
  assert.equal((await get(forged)).status, 401);
  assert.equal((await get(noneAlg)).status, 401);
  assert.equal((await get(notAdmin)).status, 403);
  assert.equal((await get(otherEmail)).status, 403);
});

test("tokens that are tampered with do not verify", () => {
  const token = signToken({ role: "admin", email: "a@b.c" }, "s".repeat(40), 60);
  const [h, p, s] = token.split(".");
  const swapped = Buffer.from(JSON.stringify({ role: "admin", email: "evil@b.c", exp: 9999999999 })).toString("base64url");

  assert.ok(verifyToken(token, "s".repeat(40)));
  assert.equal(verifyToken(`${h}.${swapped}.${s}`, "s".repeat(40)), null);
  assert.equal(verifyToken(token, "different-secret-different-secret-xx"), null);
  assert.equal(verifyToken(`${h}.${p}`, "s".repeat(40)), null);
});

test("logout succeeds without a body", async () => {
  assert.equal((await w.call("POST", "/auth/logout")).status, 204);
});

test("CORS answers preflight and only allows the listed origin, with credentials", async () => {
  const ok = await w.call("OPTIONS", "/admin/devices", { headers: { origin: "https://admin.example.com" } });
  assert.equal(ok.status, 204);
  assert.equal(ok.headers["access-control-allow-origin"], "https://admin.example.com");
  assert.equal(ok.headers["access-control-allow-credentials"], "true");
  assert.match(ok.headers["access-control-allow-headers"], /Authorization/);

  const other = await w.call("GET", "/config", { headers: { origin: "https://evil.example.com" } });
  assert.equal(other.headers["access-control-allow-origin"], undefined);
});

test("unknown paths are 404 and wrong methods are 405", async () => {
  assert.equal((await w.call("GET", "/nope")).status, 404);
  const wrong = await w.call("GET", "/events");
  assert.equal(wrong.status, 405);
  assert.equal(wrong.headers.allow, "POST");
});
