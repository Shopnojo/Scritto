import { requireAdmin } from "../auth.js";
import { HttpError, jsonBody } from "../http.js";
import { Reply } from "../reply.js";
import { daysParam, intParam, isUuid, parseNotification, stringParam } from "../validate.js";

const admin = (ctx) => requireAdmin(ctx.req, ctx.env, ctx.now());

function paging(query) {
  return { page: intParam(query.page, 1, 1, 100_000), pageSize: intParam(query.page_size, 20, 1, 100) };
}

/* ---------------------------------------------------------------- analytics */

export async function overview(ctx) {
  admin(ctx);
  return ctx.db.overview(daysParam(ctx.query.days), ctx.now());
}

export async function installs(ctx) {
  admin(ctx);
  return ctx.db.installAnalytics(daysParam(ctx.query.days), ctx.now());
}

export async function events(ctx) {
  admin(ctx);
  return ctx.db.eventAnalytics(daysParam(ctx.query.days), ctx.now());
}

export async function devicesAnalytics(ctx) {
  admin(ctx);
  return ctx.db.deviceAnalytics();
}

export async function versions(ctx) {
  admin(ctx);
  return ctx.db.versionAnalytics();
}

/* ------------------------------------------------------------------ devices */

export async function listDevices(ctx) {
  admin(ctx);
  const search = stringParam(ctx.query.search)?.toLowerCase() ?? null;
  return ctx.db.listDevices({ ...paging(ctx.query), search, appVersion: stringParam(ctx.query.app_version, 32) });
}

export async function getDevice(ctx) {
  admin(ctx);
  const id = ctx.params[0];
  if (!isUuid(id)) throw new HttpError(404, "not found");

  const device = await ctx.db.getDevice(id.toLowerCase());
  if (!device) throw new HttpError(404, "not found");
  return device;
}

/* ------------------------------------------------------------ notifications */

export async function listNotifications(ctx) {
  admin(ctx);
  return ctx.db.listNotifications(paging(ctx.query));
}

export async function getNotification(ctx) {
  admin(ctx);
  const id = ctx.params[0];
  const notification = isUuid(id) ? await ctx.db.getNotification(id.toLowerCase()) : null;
  if (!notification) throw new HttpError(404, "not found");
  return notification;
}

const message = (n) => ({ title: n.title, body: n.body });

// POST /admin/notifications/send
export async function sendNotification(ctx) {
  const claims = admin(ctx);

  const parsed = parseNotification(jsonBody(ctx.req));
  if (parsed.error) throw new HttpError(422, parsed.error);
  const n = parsed.notification;

  const id = await ctx.db.createNotification({ ...n, createdBy: claims.email });

  let recipients = 0;
  let successes = 0;
  let failures = 0;

  if (!ctx.fcm.configured) {
    // Nothing can be delivered: record it honestly instead of leaving it "sending" forever.
    recipients = n.audience === "all" ? await ctx.db.countPushDevices() : n.deviceIds.length;
    failures = recipients;
  } else if (n.audience === "all") {
    recipients = await ctx.db.countPushDevices();
    const result = await ctx.fcm.sendToTopic("all", message(n));
    if (result.ok) successes = recipients;
    else failures = recipients;
  } else {
    const targets = await ctx.db.pushTokens(n.deviceIds);
    recipients = n.deviceIds.length;
    failures = recipients - targets.length; // no push token registered for those installations

    for (const target of targets) {
      const result = await ctx.fcm.sendToToken(target.token, message(n));
      if (result.ok) successes++;
      else failures++;
      if (result.unregistered) await ctx.db.clearPushToken(target.token);
    }
  }

  const status = successes === 0 ? "failed" : failures === 0 ? "sent" : "partial";
  await ctx.db.finishNotification(id, { status, recipients, successes, failures });

  return new Reply(201, await ctx.db.getNotification(id));
}
