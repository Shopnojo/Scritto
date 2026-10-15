import { Router } from "express";
import { readSupabaseConfiguration } from "../lib/supabase.js";

export const databaseHealthRouter = Router();

databaseHealthRouter.get("/", async (_req, res) => {
  const config = readSupabaseConfiguration();

  if (!config) {
    res.status(503).json({
      status: "unconfigured",
      error: {
        code: "SUPABASE_NOT_CONFIGURED",
        message: "Supabase environment configuration is missing or invalid."
      }
    });
    return;
  }

  try {
    const response = await fetch(`${config.url}/rest/v1/rpc/scritto_db_health`, {
      method: "POST",
      headers: {
        apikey: config.publishableKey,
        "Content-Type": "application/json"
      },
      body: "{}",
      signal: AbortSignal.timeout(5000)
    });

    if (!response.ok) {
      res.status(502).json({
        status: "unavailable",
        error: {
          code: "DATABASE_HEALTH_CHECK_FAILED",
          message: "The database health RPC failed. Confirm the Scritto database migration has been applied."
        }
      });
      return;
    }

    const result: unknown = await response.json();
    if (
      typeof result !== "object" ||
      result === null ||
      !("status" in result) ||
      result.status !== "ok"
    ) {
      res.status(502).json({
        status: "unavailable",
        error: {
          code: "DATABASE_HEALTH_CHECK_INVALID_RESPONSE",
          message: "The database health RPC returned an unexpected response."
        }
      });
      return;
    }

    res.status(200).json({
      status: "ok",
      service: "scritto-database",
      reachable: true
    });
  } catch {
    res.status(502).json({
      status: "unavailable",
      error: {
        code: "DATABASE_UNREACHABLE",
        message: "Could not reach the Supabase database API."
      }
    });
  }
});
