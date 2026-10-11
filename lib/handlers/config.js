import { requireAdmin } from "../auth.js";
import { HttpError, jsonBody } from "../http.js";
import { parseConfigUpdate } from "../validate.js";

// GET /config  (public: the Android app reads it too)
export function getConfig(ctx) {
  return ctx.db.getConfig();
}

// PUT /config  { values: { key: value } }  (admin only)
export async function updateConfig(ctx) {
  requireAdmin(ctx.req, ctx.env, ctx.now());

  const parsed = parseConfigUpdate(jsonBody(ctx.req));
  if (parsed.error) throw new HttpError(422, parsed.error);

  const unknown = await ctx.db.updateConfig(parsed.values);
  if (unknown.length) throw new HttpError(422, `unknown keys: ${unknown.join(", ")}`);

  return ctx.db.getConfig();
}
