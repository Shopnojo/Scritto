import { handle } from "../../lib/router.js";

// One serverless function for the whole API: /api/v1/*  (the routes are listed in lib/router.js).
export default function handler(req, res) {
  return handle(req, res);
}
