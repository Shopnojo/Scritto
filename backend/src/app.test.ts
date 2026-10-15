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

test("unknown routes return a consistent 404 response", async () => {
  const response = await fetch(`${baseUrl}/api/v1/does-not-exist`);
  assert.equal(response.status, 404);

  const body = await response.json() as {
    error: { code: string; message: string };
  };

  assert.equal(body.error.code, "NOT_FOUND");
});
