// Run the whole backend on your PC with no database and no Vercel account:  npm run dev:local
//
// Same router as production (/api/v1/*), but the data lives in memory (PGlite, an in-process Postgres) and
// disappears when you stop it. It creates a demo admin so you can sign in to the admin website right away:
//
//   email    admin@scritto.local
//   password dev-password-123
//
// Point the website at it with  NEXT_PUBLIC_API_URL=http://localhost:3001/api/v1  (and run the site on :3000).
import { createServer } from "node:http";
import { PGlite } from "@electric-sql/pglite";
import { hashPassword } from "./lib/auth.js";
import { createDb, setDbForTests } from "./lib/db.js";
import { handle } from "./lib/router.js";

const PORT = Number(process.env.PORT || 3001);

process.env.ADMIN_EMAIL ||= "admin@scritto.local";
process.env.ADMIN_PASSWORD_HASH ||= hashPassword("dev-password-123");
process.env.JWT_SECRET ||= "dev-only-secret-dev-only-secret-dev-only";
process.env.ADMIN_ORIGIN ||= "http://localhost:3000,http://127.0.0.1:3000";

const pg = new PGlite();
setDbForTests(
  createDb(async (strings, ...values) => {
    let text = strings[0];
    for (let i = 0; i < values.length; i++) text += `$${i + 1}${strings[i + 1]}`;
    return (await pg.query(text, values)).rows;
  })
);

createServer(async (req, res) => {
  let raw = "";
  for await (const chunk of req) raw += chunk;

  // Vercel parses JSON bodies before calling a function; do the same here.
  req.body = raw && (req.headers["content-type"] ?? "").includes("json") ? safeParse(raw) : raw || undefined;

  await handle(req, res);
}).listen(PORT, () => {
  console.log(`Scritto backend (local, in-memory) on http://localhost:${PORT}/api/v1`);
  console.log(`Admin sign-in: ${process.env.ADMIN_EMAIL} / dev-password-123 (unless you set ADMIN_* yourself)`);
});

function safeParse(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
