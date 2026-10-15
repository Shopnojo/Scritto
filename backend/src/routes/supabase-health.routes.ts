import { Router } from "express";
import { readSupabaseConfiguration } from "../lib/supabase.js";

export const supabaseHealthRouter = Router();

supabaseHealthRouter.get("/", async (_req, res) => {
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
    const response = await fetch(`${config.url}/auth/v1/health`, {
      method: "GET",
      headers: { apikey: config.publishableKey },
      signal: AbortSignal.timeout(5000)
    });

    if (!response.ok) {
      res.status(502).json({
        status: "unavailable",
        error: {
          code: "SUPABASE_UPSTREAM_ERROR",
          message: "Supabase health check returned an unsuccessful response."
        }
      });
      return;
    }

    res.status(200).json({
      status: "ok",
      service: "supabase",
      reachable: true
    });
  } catch {
    res.status(502).json({
      status: "unavailable",
      error: {
        code: "SUPABASE_UNREACHABLE",
        message: "Could not reach the Supabase health endpoint."
      }
    });
  }
});
