import { createSign } from "node:crypto";

/**
 * Firebase Cloud Messaging (HTTP v1) sender, used by the admin "Notifications" page.
 *
 * Needs a service account: FIREBASE_SERVICE_ACCOUNT_JSON holds its JSON key (raw or base64).
 * Without it the sender reports `configured: false` and notifications are recorded as failed.
 */
export function createFcm({ serviceAccount, fetchImpl = fetch, now = Date.now } = {}) {
  let cached; // { token, expiresAt }

  const configured = Boolean(serviceAccount?.client_email && serviceAccount?.private_key && serviceAccount?.project_id);

  async function accessToken() {
    if (cached && cached.expiresAt > now() + 60_000) return cached.token;

    const iat = Math.floor(now() / 1000);
    const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
    const claims = Buffer.from(
      JSON.stringify({
        iss: serviceAccount.client_email,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat,
        exp: iat + 3600,
      })
    ).toString("base64url");
    const signature = createSign("RSA-SHA256").update(`${header}.${claims}`).sign(serviceAccount.private_key, "base64url");

    const response = await fetchImpl("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion: `${header}.${claims}.${signature}`,
      }),
    });
    if (!response.ok) throw new Error(`token request failed: ${response.status}`);

    const json = await response.json();
    cached = { token: json.access_token, expiresAt: now() + (json.expires_in ?? 3600) * 1000 };
    return cached.token;
  }

  /** Sends one message. Returns { ok, unregistered } and never throws. */
  async function send(target, { title, body }) {
    if (!configured) return { ok: false, unregistered: false };

    try {
      const response = await fetchImpl(`https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`, {
        method: "POST",
        headers: { Authorization: `Bearer ${await accessToken()}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          message: {
            ...target,
            notification: { title, body },
            android: { notification: { channel_id: "scritto_announcements" } },
          },
        }),
      });
      return { ok: response.ok, unregistered: response.status === 404 };
    } catch {
      return { ok: false, unregistered: false };
    }
  }

  return {
    configured,
    sendToTopic: (topic, message) => send({ topic }, message),
    sendToToken: (token, message) => send({ token }, message),
  };
}

/** Reads FIREBASE_SERVICE_ACCOUNT_JSON (raw JSON or base64 of it). */
export function fcmFromEnv(env = process.env) {
  const raw = env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim();
  if (!raw) return createFcm({});

  try {
    const text = raw.startsWith("{") ? raw : Buffer.from(raw, "base64").toString("utf8");
    return createFcm({ serviceAccount: JSON.parse(text) });
  } catch {
    return createFcm({});
  }
}
