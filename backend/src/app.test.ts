import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import type { AddressInfo } from "node:net";
import { app } from "./app.js";

let server: ReturnType<typeof app.listen>;
let baseUrl: string;

before(async () => {
  server = app.listen(0);
  await new Promise<void>((resolve) => server.once("listening", resolve));
  const address = server.address() as AddressInfo;
  baseUrl = `http://127.0.0.1:${address.port}`;
});

after(async () => {
  await new Promise<void>((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
});

test("health endpoint returns an OK response", async () => {
  const response = await fetch(`${baseUrl}/api/v1/health`);
  assert.equal(response.status, 200);

  const body = await response.json() as {
    status: string;
    service: string;
    timestamp: string;
  };

  assert.equal(body.status, "ok");
  assert.equal(body.service, "scritto-api");
  assert.ok(Number.isFinite(Date.parse(body.timestamp)));
});

test("Supabase health endpoint reports missing configuration safely", async () => {
  const response = await fetch(`${baseUrl}/api/v1/health/supabase`);
  if (response.status === 503) {
    const body = await response.json() as {
      status: string;
      error: { code: string };
    };
    assert.equal(body.status, "unconfigured");
    assert.equal(body.error.code, "SUPABASE_NOT_CONFIGURED");
    assert.equal(JSON.stringify(body).includes("SUPABASE_PUBLISHABLE_KEY"), false);
  } else {
    // When environment variables are supplied, the route performs a live check.
    assert.ok(response.status === 200 || response.status === 502);
  }
});

test("current-user route never accepts a missing bearer token", async () => {
  const response = await fetch(`${baseUrl}/api/v1/auth/me`);
  assert.ok(response.status === 401 || response.status === 503);

  const body = await response.json() as {
    error: { code: string };
  };
  assert.ok(
    body.error.code === "AUTH_REQUIRED" ||
    body.error.code === "AUTH_NOT_CONFIGURED"
  );
});

test("admin identity route never accepts an unauthenticated request", async () => {
  const response = await fetch(`${baseUrl}/api/v1/admin/me`);
  assert.ok(response.status === 401 || response.status === 503);

  const body = await response.json() as {
    error: { code: string };
  };
  assert.ok(
    body.error.code === "AUTH_REQUIRED" ||
    body.error.code === "AUTH_NOT_CONFIGURED"
  );
});

test("unknown routes return a consistent 404 response", async () => {
  const response = await fetch(`${baseUrl}/api/v1/does-not-exist`);
  assert.equal(response.status, 404);

  const body = await response.json() as {
    error: { code: string; message: string };
  };

  assert.equal(body.error.code, "NOT_FOUND");
});
