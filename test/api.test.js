import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import { ID, ID2, createWorld } from "./helpers.js";

const w = await createWorld();

beforeEach(async () => {
  await w.pg.query("TRUNCATE events, devices, notifications RESTART IDENTITY");
  await w.pg.query("UPDATE config SET updated_at = now()");
  w.sent.length = 0;
  w.fcm.configured = true;
  w.fcm.failTokens.clear();
});

const iso = (msAgo = 0) => new Date(Date.now() - msAgo).toISOString();
const HOUR = 3600_000;
const DAY = 24 * HOUR;

const register = (id, extra = {}) =>
  w.call("POST", "/devices/register", {
    body: {
      installation_id: id,
      platform: "android",
      manufacturer: "Google",
      model: "Pixel 9",
      os_version: "Android 15",
      sdk_int: 35,
      app_version: "1.5",
      app_build: 5,
      locale: "en-IN",
      ...extra,
    },
  });

const track = (id, events, extra = {}) =>
  w.call("POST", "/events", { body: { installation_id: id, app_version: "1.5", events, ...extra } });

/* --------------------------------------------------------- called by the app */

test("registering a device stores it; registering again updates it and keeps what wasn't resent", async () => {
  assert.equal((await register(ID)).status, 204);
  assert.equal((await register(ID, { app_version: "1.6", manufacturer: undefined, model: undefined })).status, 204);

  const { rows } = await w.pg.query("SELECT manufacturer, model, app_version FROM devices");
  assert.deepEqual(rows, [{ manufacturer: "Google", model: "Pixel 9", app_version: "1.6" }]);
});

test("events are stored with their properties, and a retried batch is not counted twice", async () => {
  const events = [
    { name: "note_created", occurred_at: iso(5000) },
    { name: "document_opened", occurred_at: iso(4000), properties: { kind: "pdf" } },
  ];

  assert.equal((await track(ID, events)).status, 204);
  assert.equal((await track(ID, events)).status, 204); // the app retried

  const { rows } = await w.pg.query("SELECT name, properties FROM events ORDER BY name");
  assert.deepEqual(rows, [
    { name: "document_opened", properties: { kind: "pdf" } },
    { name: "note_created", properties: null },
  ]);
});

test("events for a device that never registered still create it", async () => {
  await track(ID, [{ name: "app_opened", occurred_at: iso() }]);
  assert.equal((await w.pg.query("SELECT count(*)::int AS n FROM devices")).rows[0].n, 1);
});

test("invalid uploads get a 4xx so the app drops them", async () => {
  assert.equal((await w.call("POST", "/devices/register", { body: { installation_id: "x" } })).status, 422);
  assert.equal((await w.call("POST", "/events", { body: { installation_id: ID, events: "no" } })).status, 422);
});

test("junk events inside a valid batch are dropped and the good ones kept", async () => {
  await track(ID, [
    { name: "ok_event", occurred_at: iso() },
    { name: "'; DROP TABLE events;--", occurred_at: iso() },
  ]);
  assert.deepEqual((await w.pg.query("SELECT name FROM events")).rows, [{ name: "ok_event" }]);
});

test("a device sending far too fast gets 429", async () => {
  const many = Array.from({ length: 100 }, (_, i) => ({ name: "app_opened", occurred_at: iso(i * 1000) }));
  for (let i = 0; i < 6; i++) await track(ID, many.map((e, j) => ({ ...e, occurred_at: iso(i * 200_000 + j * 1000 + 10_000) })));

  assert.equal((await track(ID, [{ name: "app_opened", occurred_at: iso(1) }])).status, 429);
});

test("when SCRITTO_APP_KEY is set the app must send it", async () => {
  const keyed = await createWorld({ SCRITTO_APP_KEY: "app-secret" });
  const body = { installation_id: ID, events: [] };

  assert.equal((await keyed.call("POST", "/events", { body })).status, 401);
  assert.equal((await keyed.call("POST", "/events", { body, headers: { "x-scritto-app-key": "wrong" } })).status, 401);
  assert.equal((await keyed.call("POST", "/events", { body, headers: { "x-scritto-app-key": "app-secret" } })).status, 204);
});

/* ----------------------------------------------------- called by the admin site */

async function seed() {
  await register(ID, { app_version: "1.5" });
  await register(ID2, { model: "Galaxy S24", manufacturer: "Samsung", os_version: "Android 14", app_version: "1.4" });
  await track(ID, [
    { name: "app_opened", occurred_at: iso(3 * HOUR) },
    { name: "note_created", occurred_at: iso(2 * HOUR) },
    { name: "note_created", occurred_at: iso(1 * HOUR) },
    { name: "document_opened", occurred_at: iso(30 * 60_000), properties: { kind: "pdf" } },
  ]);
  await track(ID2, [{ name: "app_opened", occurred_at: iso(10 * 60_000) }], { app_version: "1.4" });
}

test("every admin route refuses to answer without a token", async () => {
  const paths = [
    "/admin/analytics/overview", "/admin/analytics/installs", "/admin/analytics/events",
    "/admin/analytics/devices", "/admin/analytics/versions", "/admin/devices", `/admin/devices/${ID}`,
    "/admin/notifications", `/admin/notifications/${ID}`,
  ];
  for (const path of paths) assert.equal((await w.call("GET", path)).status, 401, path);
  assert.equal((await w.call("POST", "/admin/notifications/send", { body: {} })).status, 401);
  assert.equal((await w.call("PUT", "/config", { body: { values: { maintenance_mode: true } } })).status, 401);
});

test("overview counts installs, active installs, new installs and events", async () => {
  await seed();
  const { status, body } = await w.asAdmin("GET", "/admin/analytics/overview", { query: { days: "7" } });

  assert.equal(status, 200);
  assert.equal(body.period_days, 7);
  assert.equal(body.total_installs.value, 2);
  assert.equal(body.active_installs.value, 2);
  assert.equal(body.new_installs.value, 2);
  assert.equal(body.events.value, 5);
  assert.equal(body.events.change_pct, null); // nothing in the previous period to compare with
});

test("overview compares with the previous period", async () => {
  await register(ID);
  await track(ID, [
    { name: "app_opened", occurred_at: iso(1 * DAY) },
    { name: "note_created", occurred_at: iso(2 * DAY) },
    { name: "note_created", occurred_at: iso(10 * DAY) }, // previous 7-day window
  ]);

  const { body } = await w.asAdmin("GET", "/admin/analytics/overview", { query: { days: "7" } });
  assert.equal(body.events.value, 2);
  assert.equal(body.events.change_pct, 100); // 1 -> 2
});

test("installs analytics returns a full daily series with zeros", async () => {
  await seed();
  const { body } = await w.asAdmin("GET", "/admin/analytics/installs", { query: { days: "7" } });

  assert.equal(body.daily_active.length, 7);
  assert.equal(body.new_installs.length, 7);
  assert.match(body.daily_active[0].date, /^\d{4}-\d{2}-\d{2}$/);
  assert.ok(body.daily_active[0].date < body.daily_active[6].date);
  assert.equal(body.daily_active.reduce((s, p) => s + p.value, 0) >= 2, true);
  assert.equal(body.new_installs.reduce((s, p) => s + p.value, 0), 2);
});

test("event analytics returns daily totals, top events and recent activity with device info", async () => {
  await seed();
  const { body } = await w.asAdmin("GET", "/admin/analytics/events", { query: { days: "30" } });

  assert.equal(body.daily.length, 30);
  assert.deepEqual(body.top_events[0], { label: "app_opened", count: 2 }); // ties sort by name
  assert.equal(body.top_events.find((e) => e.label === "note_created").count, 2);

  const newest = body.recent[0];
  assert.equal(newest.name, "app_opened");
  assert.equal(newest.installation_id, ID2);
  assert.equal(newest.device_model, "Galaxy S24");
  assert.equal(newest.app_version, "1.4");
  assert.match(newest.occurred_at, /Z$/);
  assert.deepEqual(body.recent.find((e) => e.name === "document_opened").properties, { kind: "pdf" });
});

test("device and version breakdowns, with the latest version from config", async () => {
  await seed();

  const devices = (await w.asAdmin("GET", "/admin/analytics/devices")).body;
  assert.deepEqual(devices.os_versions.map((o) => o.label).sort(), ["Android 14", "Android 15"]);
  assert.deepEqual(devices.models.map((o) => o.label).sort(), ["Galaxy S24", "Pixel 9"]);

  const versions = (await w.asAdmin("GET", "/admin/analytics/versions")).body;
  assert.equal(versions.latest_version, "1.5");
  assert.deepEqual(versions.versions.map((v) => v.label).sort(), ["1.4", "1.5"]);
});

test("device list pages, searches and filters", async () => {
  await seed();

  const all = (await w.asAdmin("GET", "/admin/devices")).body;
  assert.equal(all.total, 2);
  assert.equal(all.page, 1);
  assert.equal(all.page_size, 20);
  assert.equal(all.items[0].id, ID2); // seen most recently
  assert.equal(all.items.find((d) => d.id === ID).event_count, 4);
  assert.match(all.items[0].created_at, /Z$/);

  const paged = (await w.asAdmin("GET", "/admin/devices", { query: { page: "2", page_size: "1" } })).body;
  assert.equal(paged.items.length, 1);
  assert.equal(paged.items[0].id, ID);
  assert.equal(paged.total, 2);

  const bySearch = async (search) => (await w.asAdmin("GET", "/admin/devices", { query: { search } })).body.items.map((d) => d.id);
  assert.deepEqual(await bySearch("galaxy"), [ID2]);
  assert.deepEqual(await bySearch("samsung galaxy"), [ID2]);
  assert.deepEqual(await bySearch("35591f4a"), [ID]);
  assert.deepEqual(await bySearch("android 15"), [ID]);
  assert.deepEqual(await bySearch("%"), []); // not a wildcard

  const byVersion = (await w.asAdmin("GET", "/admin/devices", { query: { app_version: "1.4" } })).body.items;
  assert.deepEqual(byVersion.map((d) => d.id), [ID2]);
});

test("device detail has recent and top events; unknown or malformed ids are 404", async () => {
  await seed();

  const { status, body } = await w.asAdmin("GET", `/admin/devices/${ID}`);
  assert.equal(status, 200);
  assert.equal(body.id, ID);
  assert.equal(body.recent_events.length, 4);
  assert.equal(body.recent_events[0].name, "document_opened");
  assert.deepEqual(body.top_events[0], { label: "note_created", count: 2 });

  assert.equal((await w.asAdmin("GET", "/admin/devices/11111111-1111-4111-8111-111111111111")).status, 404);
  assert.equal((await w.asAdmin("GET", "/admin/devices/not-a-uuid")).status, 404);
});

test("sending to everyone goes to the 'all' topic and is recorded", async () => {
  await register(ID, { push_token: "tok-1" });
  await register(ID2, { push_token: "tok-2" });

  const { status, body } = await w.asAdmin("POST", "/admin/notifications/send", {
    body: { title: "Scritto 1.6", body: "Update now", audience: "all" },
  });

  assert.equal(status, 201);
  assert.equal(body.status, "sent");
  assert.equal(body.recipient_count, 2);
  assert.equal(body.success_count, 2);
  assert.equal(body.created_by, "admin@scritto.test");
  assert.ok(body.sent_at);
  assert.deepEqual(w.sent, [{ topic: "all", title: "Scritto 1.6", body: "Update now" }]);

  const list = (await w.asAdmin("GET", "/admin/notifications")).body;
  assert.equal(list.total, 1);
  assert.equal((await w.asAdmin("GET", `/admin/notifications/${body.id}`)).body.title, "Scritto 1.6");
});

test("sending to chosen devices reports partial delivery and drops dead push tokens", async () => {
  await register(ID, { push_token: "good" });
  await register(ID2, { push_token: "dead" });
  w.fcm.failTokens.add("dead");
  const unknown = "99999999-9999-4999-8999-999999999999";

  const { body } = await w.asAdmin("POST", "/admin/notifications/send", {
    body: { title: "Hi", body: "There", audience: "devices", device_ids: [ID, ID2, unknown] },
  });

  assert.equal(body.status, "partial");
  assert.equal(body.recipient_count, 3);
  assert.equal(body.success_count, 1);
  assert.equal(body.failure_count, 2);
  assert.deepEqual(w.sent.map((m) => m.token).sort(), ["dead", "good"]);
  assert.equal((await w.pg.query("SELECT push_token FROM devices WHERE installation_id = $1", [ID2])).rows[0].push_token, null);
});

test("without Firebase set up a notification is recorded as failed, not left hanging", async () => {
  w.fcm.configured = false;
  await register(ID, { push_token: "tok" });

  const { status, body } = await w.asAdmin("POST", "/admin/notifications/send", { body: { title: "Hi", body: "There", audience: "all" } });

  assert.equal(status, 201);
  assert.equal(body.status, "failed");
  assert.equal(w.sent.length, 0);
});

test("bad notification requests are 422 and unknown notifications 404", async () => {
  assert.equal((await w.asAdmin("POST", "/admin/notifications/send", { body: { title: "", body: "x", audience: "all" } })).status, 422);
  assert.equal((await w.asAdmin("POST", "/admin/notifications/send", { body: { title: "t", body: "b", audience: "devices" } })).status, 422);
  assert.equal((await w.asAdmin("GET", `/admin/notifications/${ID}`)).status, 404);
});

/* ----------------------------------------------------------------- config */

test("config is public to read, seeded with the keys the site and app expect", async () => {
  const { status, body } = await w.call("GET", "/config");
  const keys = body.entries.map((e) => e.key);

  assert.equal(status, 200);
  for (const key of ["maintenance_mode", "min_supported_version", "latest_version", "feature_ai_assistant", "telemetry_enabled", "announcement"]) {
    assert.ok(keys.includes(key), key);
  }
  assert.equal(body.entries.find((e) => e.key === "maintenance_mode").value, false);
  assert.ok(body.updated_at);
});

test("only an admin can change config, and only existing keys", async () => {
  const update = { values: { maintenance_mode: true, announcement: { enabled: true, message: "Hi" } } };

  const result = await w.asAdmin("PUT", "/config", { body: update });
  assert.equal(result.status, 200);
  assert.equal(result.body.entries.find((e) => e.key === "maintenance_mode").value, true);
  assert.deepEqual(result.body.entries.find((e) => e.key === "announcement").value, { enabled: true, message: "Hi" });

  // The public read sees it too.
  assert.equal((await w.call("GET", "/config")).body.entries.find((e) => e.key === "maintenance_mode").value, true);

  const before = JSON.stringify((await w.call("GET", "/config")).body.entries);
  assert.equal((await w.asAdmin("PUT", "/config", { body: { values: { maintenance_mode: false, not_a_key: 1 } } })).status, 422);
  assert.equal(JSON.stringify((await w.call("GET", "/config")).body.entries), before); // nothing half-applied
  assert.equal((await w.asAdmin("PUT", "/config", { body: { values: "x" } })).status, 422);
});

test("health checks the database", async () => {
  assert.deepEqual((await w.call("GET", "/health")).body, { ok: true });
});

test("pruning removes only events past the retention period", async () => {
  await register(ID);
  await track(ID, [{ name: "recent", occurred_at: iso(DAY) }]);
  await w.pg.query(`INSERT INTO events (installation_id, name, occurred_at) VALUES ($1, 'ancient', now() - interval '400 days')`, [ID]);

  assert.equal(await w.db.prune(365), 1);
  assert.deepEqual((await w.pg.query("SELECT name FROM events")).rows, [{ name: "recent" }]);
});
