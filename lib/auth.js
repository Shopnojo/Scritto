import { createHmac, randomBytes, scrypt as scryptCb, scryptSync, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";
import { HttpError, bearerToken, safeEqual } from "./http.js";

const scrypt = promisify(scryptCb);
const KEY_LENGTH = 64;

/* ------------------------------------------------------------ passwords */

/** "scrypt:<saltHex>:<hashHex>". Create one with `npm run hash-password -- "your password"`. */
export function hashPassword(password) {
  const salt = randomBytes(16);
  const hash = scryptSync(String(password), salt, KEY_LENGTH);
  return `scrypt:${salt.toString("hex")}:${hash.toString("hex")}`;
}

// Checked when the email is wrong, so a wrong email and a wrong password take equally long.
const DUMMY_HASH = hashPassword("not-a-real-password");

export async function verifyPassword(password, stored) {
  const parts = String(stored || DUMMY_HASH).split(":");
  const valid = parts.length === 3 && parts[0] === "scrypt";
  const [, saltHex, hashHex] = valid ? parts : DUMMY_HASH.split(":");

  const expected = Buffer.from(hashHex, "hex");
  const actual = await scrypt(String(password), Buffer.from(saltHex, "hex"), expected.length);

  return valid && timingSafeEqual(actual, expected);
}

/* ------------------------------------------------------------------ JWT */

const b64 = (value) => Buffer.from(value).toString("base64url");

function sign(data, secret) {
  return createHmac("sha256", secret).update(data).digest("base64url");
}

export function signToken(claims, secret, ttlSeconds, now = Date.now()) {
  const iat = Math.floor(now / 1000);
  const header = b64(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64(JSON.stringify({ ...claims, iat, exp: iat + ttlSeconds }));
  return `${header}.${payload}.${sign(`${header}.${payload}`, secret)}`;
}

/** Returns the claims, or null for anything wrong: bad format, bad signature, wrong algorithm, expired. */
export function verifyToken(token, secret, now = Date.now()) {
  if (!secret || typeof token !== "string") return null;

  const parts = token.split(".");
  if (parts.length !== 3) return null;

  try {
    const header = JSON.parse(Buffer.from(parts[0], "base64url").toString());
    if (header.alg !== "HS256") return null; // never trust the token to pick its own algorithm

    if (!safeEqual(parts[2], sign(`${parts[0]}.${parts[1]}`, secret))) return null;

    const claims = JSON.parse(Buffer.from(parts[1], "base64url").toString());
    if (typeof claims.exp !== "number" || claims.exp <= Math.floor(now / 1000)) return null;

    return claims;
  } catch {
    return null;
  }
}

/* --------------------------------------------------------------- guards */

export function adminConfig(env = process.env) {
  return {
    email: String(env.ADMIN_EMAIL ?? "").trim().toLowerCase(),
    passwordHash: env.ADMIN_PASSWORD_HASH ?? "",
    jwtSecret: env.JWT_SECRET ?? "",
    ttlSeconds: Math.round(Number(env.JWT_TTL_HOURS || 12) * 3600),
  };
}

/** The signed-in admin, or an HttpError: 401 without a valid token, 403 for a token that isn't an admin's. */
export function requireAdmin(req, env = process.env, now = Date.now()) {
  const { email, jwtSecret } = adminConfig(env);
  if (!jwtSecret) throw new HttpError(503, "server is missing JWT_SECRET");

  const claims = verifyToken(bearerToken(req), jwtSecret, now);
  if (!claims) throw new HttpError(401, "unauthorized");
  if (claims.role !== "admin" || !email || claims.email !== email) throw new HttpError(403, "forbidden");

  return claims;
}
