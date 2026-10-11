import { adminConfig, requireAdmin, signToken, verifyPassword } from "../auth.js";
import { HttpError, clientIp, jsonBody, safeEqual } from "../http.js";

const MAX_FAILED_LOGINS = 10;
const WINDOW_MINUTES = 15;

// POST /auth/login  { email, password }  ->  { access_token, token_type, expires_in }
export async function login(ctx) {
  const { email, passwordHash, jwtSecret, ttlSeconds } = adminConfig(ctx.env);
  if (!email || !passwordHash || !jwtSecret) throw new HttpError(503, "admin login is not configured");

  const ip = clientIp(ctx.req);
  if ((await ctx.db.failedLoginCount(ip, WINDOW_MINUTES)) >= MAX_FAILED_LOGINS) throw new HttpError(429, "too many attempts");

  const body = jsonBody(ctx.req) ?? {};
  const givenEmail = String(body.email ?? "").trim().toLowerCase();
  const password = String(body.password ?? "");

  // Always check the password (against a dummy hash for a wrong email) so timing doesn't reveal which was wrong.
  const emailOk = safeEqual(givenEmail, email);
  const passwordOk = await verifyPassword(password, emailOk ? passwordHash : "");

  if (!emailOk || !passwordOk || !password) {
    await ctx.db.recordFailedLogin(ip);
    throw new HttpError(401, "invalid credentials");
  }

  const token = signToken({ sub: "admin", email, role: "admin" }, jwtSecret, ttlSeconds, ctx.now());
  return { access_token: token, token_type: "bearer", expires_in: ttlSeconds };
}

// GET /auth/me
export function me(ctx) {
  const claims = requireAdmin(ctx.req, ctx.env, ctx.now());
  return { id: claims.sub, email: claims.email, display_name: "Scritto Admin", role: "admin", is_admin: true };
}

// POST /auth/logout  (tokens are stateless and short-lived; the site forgets its copy)
export function logout() {
  return undefined;
}
