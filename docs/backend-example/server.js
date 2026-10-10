// Minimal receiver for Scritto's anonymous usage statistics. No dependencies: `node server.js`.
// Stores one JSON line per device registration / event batch in ./data/*.ndjson.
// For production put it behind HTTPS (Cloudflare, Caddy, nginx, Render, Railway, Fly.io...) and swap the
// files for a real database.
const http = require("node:http");
const fs = require("node:fs");

const PORT = process.env.PORT || 8080;

function append(file, text) {
  fs.mkdirSync("data", { recursive: true });
  fs.appendFileSync(`data/${file}`, text);
}

const NAME = /^[a-z][a-z0-9_]{0,39}$/;
const UUID = /^[0-9a-f-]{36}$/i;

function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > 100_000) { reject(new Error("too large")); req.destroy(); }
    });
    req.on("end", () => { try { resolve(JSON.parse(body)); } catch (e) { reject(e); } });
  });
}

http.createServer(async (req, res) => {
  const send = (code, obj = {}) => { res.writeHead(code, { "Content-Type": "application/json" }); res.end(JSON.stringify(obj)); };
  if (req.method !== "POST") return send(405);

  let data;
  try { data = await readJson(req); } catch { return send(400, { error: "bad json" }); }
  if (!UUID.test(data.installation_id || "")) return send(422, { error: "bad installation_id" });

  if (req.url === "/v1/devices") {
    const row = { id: data.installation_id, app_version: String(data.app_version || ""), android_sdk: Number(data.android_sdk) || 0, at: Date.now() };
    append("devices.ndjson", JSON.stringify(row) + "\n");
    return send(204);
  }

  if (req.url === "/v1/events") {
    const events = Array.isArray(data.events) ? data.events.slice(0, 100) : null;
    if (!events) return send(422, { error: "events must be an array" });
    const rows = events
      .filter((e) => NAME.test(e.name || "") && Number.isFinite(e.at))
      .map((e) => JSON.stringify({ id: data.installation_id, name: e.name, at: e.at }));
    if (rows.length) append("events.ndjson", rows.join("\n") + "\n");
    return send(204);
  }

  send(404);
}).listen(PORT, () => console.log(`Scritto stats server on :${PORT}`));
