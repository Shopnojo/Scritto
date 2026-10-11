import { HttpError, jsonBody, safeEqual } from "../http.js";
import { parseEventBatch, parseRegistration } from "../validate.js";

const MAX_EVENTS_PER_MINUTE = 600;

/**
 * The app has no login, so these two endpoints are public. If SCRITTO_APP_KEY is set, the app must send it as
 * `X-Scritto-App-Key`. That only keeps casual noise out (an APK can't truly hide a key).
 */
function checkAppKey({ req, env }) {
  const expected = env.SCRITTO_APP_KEY;
  if (!expected) return;

  const sent = req.headers?.["x-scritto-app-key"] ?? "";
  if (!safeEqual(Array.isArray(sent) ? sent[0] : sent, expected)) throw new HttpError(401, "unauthorized");
}

// POST /devices/register
export async function registerDevice(ctx) {
  checkAppKey(ctx);

  const parsed = parseRegistration(jsonBody(ctx.req));
  // 4xx makes the app drop the request instead of retrying it forever.
  if (parsed.error) throw new HttpError(422, parsed.error);

  await ctx.db.upsertDevice(parsed.device);
}

// POST /events
export async function ingestEvents(ctx) {
  checkAppKey(ctx);

  const parsed = parseEventBatch(jsonBody(ctx.req), ctx.now());
  if (parsed.error) throw new HttpError(422, parsed.error);

  // 429 makes the app retry later, which is right for a device that is simply sending too fast.
  if ((await ctx.db.recentEventCount(parsed.installationId, 60)) + parsed.events.length > MAX_EVENTS_PER_MINUTE) {
    throw new HttpError(429, "slow down");
  }

  await ctx.db.insertEvents(parsed.installationId, parsed.appVersion, parsed.events);
}
