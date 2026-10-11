import { getDb } from "../../lib/db.js";
import { bearerToken, safeEqual, send } from "../../lib/http.js";
import { intParam } from "../../lib/validate.js";

// Daily clean-up (see "crons" in vercel.json): deletes events older than RETENTION_DAYS (default 365).
// Vercel Cron calls this with "Authorization: Bearer <CRON_SECRET>" when CRON_SECRET is set.
export default async function handler(req, res) {
  const secret = process.env.CRON_SECRET;
  if (!secret) return send(res, 503, { error: "CRON_SECRET is not set" });
  if (!safeEqual(bearerToken(req), secret)) return send(res, 401, { error: "unauthorized" });

  const days = intParam(process.env.RETENTION_DAYS, 365, 7, 3650);

  try {
    send(res, 200, { retention_days: days, deleted: await getDb().prune(days) });
  } catch (error) {
    console.error("prune failed", error);
    send(res, 500, { error: "server error" });
  }
}
