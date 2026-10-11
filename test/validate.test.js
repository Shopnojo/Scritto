import assert from "node:assert/strict";
import { test } from "node:test";
import {
  cleanProperties,
  daysParam,
  intParam,
  parseConfigUpdate,
  parseEventBatch,
  parseNotification,
  parseRegistration,
} from "../lib/validate.js";
import { ID, ID2 } from "./helpers.js";

const NOW = Date.parse("2026-10-10T12:00:00Z");
const at = (offsetMs) => new Date(NOW + offsetMs).toISOString();

test("registration keeps the fields the app sends and trims junk", () => {
  const { device, error } = parseRegistration({
    installation_id: ID.toUpperCase(),
    platform: "android",
    manufacturer: "Google",
    model: "Pixel 9",
    os_version: "Android 15",
    sdk_int: 35,
    app_version: "1.5",
    app_build: 5,
    locale: "en-IN",
    push_token: null,
  });

  assert.equal(error, undefined);
  assert.equal(device.installationId, ID);
  assert.equal(device.model, "Pixel 9");
  assert.equal(device.sdkInt, 35);
  assert.equal(device.pushToken, null);
});

test("registration rejects a bad id and tolerates missing extras", () => {
  assert.ok(parseRegistration({ installation_id: "nope" }).error);
  assert.ok(parseRegistration(null).error);
  const bare = parseRegistration({ installation_id: ID, sdk_int: 5000, model: "  " }).device;
  assert.equal(bare.platform, "android");
  assert.equal(bare.sdkInt, null);
  assert.equal(bare.model, null);
});

test("event batches keep valid events and count the rest as dropped", () => {
  const parsed = parseEventBatch(
    {
      installation_id: ID,
      app_version: "1.5",
      events: [
        { name: "note_created", occurred_at: at(-1000) },
        { name: "document_opened", occurred_at: at(-500), properties: { kind: "pdf", junk: { a: 1 }, "Bad Key": "x" } },
        { name: "Bad Name", occurred_at: at(0) },
        { name: "note_created", occurred_at: "yesterday" },
        { name: "task_created", occurred_at: at(-100 * 24 * 3600 * 1000) }, // older than 90 days
        { name: "task_created", occurred_at: at(3 * 24 * 3600 * 1000) }, // far future
        null,
      ],
    },
    NOW
  );

  assert.deepEqual(parsed.events.map((e) => e.name), ["note_created", "document_opened"]);
  assert.deepEqual(parsed.events[1].properties, { kind: "pdf" });
  assert.equal(parsed.dropped, 5);
  assert.equal(parsed.appVersion, "1.5");
});

test("event batches enforce structure and size", () => {
  assert.ok(parseEventBatch({ installation_id: ID, events: "x" }, NOW).error);
  assert.ok(parseEventBatch({ events: [] }, NOW).error);
  assert.ok(parseEventBatch({ installation_id: ID, events: Array(101).fill({ name: "a", occurred_at: at(0) }) }, NOW).error);
  assert.equal(parseEventBatch({ installation_id: ID, events: [] }, NOW).events.length, 0);
});

test("properties are limited to a few short, plain values", () => {
  assert.equal(cleanProperties(null), null);
  assert.equal(cleanProperties([1, 2]), null);
  assert.equal(cleanProperties({ nested: { a: 1 } }), null);
  assert.deepEqual(cleanProperties({ voice: true, n: 3, kind: "x".repeat(200) }), { voice: true, n: 3, kind: "x".repeat(64) });
  assert.equal(Object.keys(cleanProperties(Object.fromEntries(Array.from({ length: 30 }, (_, i) => [`k${i}`, i])))).length, 10);
});

test("notification requests are validated", () => {
  assert.ok(parseNotification({ title: "", body: "x", audience: "all" }).error);
  assert.ok(parseNotification({ title: "t", body: "b", audience: "everyone" }).error);
  assert.ok(parseNotification({ title: "t", body: "b", audience: "devices", device_ids: [] }).error);
  assert.ok(parseNotification({ title: "t", body: "b", audience: "devices", device_ids: ["nope"] }).error);

  assert.deepEqual(parseNotification({ title: " Hi ", body: "b", audience: "all" }).notification, {
    title: "Hi", body: "b", audience: "all", deviceIds: [],
  });
  assert.deepEqual(
    parseNotification({ title: "t", body: "b", audience: "devices", device_ids: [ID, ID.toUpperCase(), ID2] }).notification.deviceIds,
    [ID, ID2]
  );
});

test("config updates are validated", () => {
  assert.ok(parseConfigUpdate({}).error);
  assert.ok(parseConfigUpdate({ values: [] }).error);
  assert.ok(parseConfigUpdate({ values: { "Bad Key": 1 } }).error);
  assert.ok(parseConfigUpdate({ values: { big: "x".repeat(20_000) } }).error);
  assert.deepEqual(parseConfigUpdate({ values: { maintenance_mode: true } }).values, { maintenance_mode: true });
});

test("query helpers clamp", () => {
  assert.equal(intParam("999", 30, 1, 365), 365);
  assert.equal(intParam("abc", 30, 1, 365), 30);
  assert.equal(daysParam("7"), 7);
  assert.equal(daysParam("90"), 90);
  assert.equal(daysParam("45"), 30);
  assert.equal(daysParam(undefined), 30);
});
