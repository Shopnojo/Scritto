import { createClient, type User } from "@supabase/supabase-js";
import type { RequestHandler } from "express";
import { readSupabaseConfiguration } from "../lib/supabase.js";

export type AdminRole = "owner" | "admin" | "analyst";

export type AdminProfile = {
  user_id: string;
  role: AdminRole;
  is_active: boolean;
  display_name: string | null;
};

export const requireAuth: RequestHandler = async (_req, res, next) => {
  const config = readSupabaseConfiguration();
  if (!config) {
    res.status(503).json({
      error: {
        code: "AUTH_NOT_CONFIGURED",
        message: "Authentication is not configured on this server."
      }
    });
    return;
  }

  const authorization = res.req.get("authorization");
  const match = authorization?.match(/^Bearer\s+([^\s]+)$/i);
  if (!match) {
    res.status(401).json({
      error: {
        code: "AUTH_REQUIRED",
        message: "A valid Bearer access token is required."
      }
    });
    return;
  }

  const token = match[1];
  const supabase = createClient(config.url, config.publishableKey, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: {
      autoRefreshToken: false,
      persistSession: false,
      detectSessionInUrl: false
    }
  });

  try {
    const { data, error } = await supabase.auth.getUser(token);
    if (error || !data.user) {
      res.status(401).json({
        error: {
          code: "INVALID_ACCESS_TOKEN",
          message: "The access token is invalid or expired."
        }
      });
      return;
    }

    res.locals.authUser = data.user satisfies User;
    res.locals.authSupabase = supabase;
    next();
  } catch {
    res.status(502).json({
      error: {
        code: "AUTH_PROVIDER_UNAVAILABLE",
        message: "Could not verify the access token with the authentication provider."
      }
    });
  }
};

export const requireAdmin: RequestHandler = async (_req, res, next) => {
  const user = res.locals.authUser as User | undefined;
  const supabase = res.locals.authSupabase as ReturnType<typeof createClient> | undefined;

  if (!user || !supabase) {
    res.status(401).json({
      error: { code: "AUTH_REQUIRED", message: "Authentication is required." }
    });
    return;
  }

  try {
    const { data, error } = await supabase
      .from("admin_profiles")
      .select("user_id, role, is_active, display_name")
      .eq("user_id", user.id)
      .maybeSingle();

    if (error) {
      res.status(503).json({
        error: {
          code: "ADMIN_AUTHORIZATION_UNAVAILABLE",
          message: "Could not verify administrator access. Confirm the application schema migration has been applied."
        }
      });
      return;
    }

    const profile = data as AdminProfile | null;
    if (!profile || !profile.is_active || !["owner", "admin", "analyst"].includes(profile.role)) {
      res.status(403).json({
        error: {
          code: "ADMIN_ACCESS_DENIED",
          message: "This account is not authorized to access the admin API."
        }
      });
      return;
    }

    res.locals.adminProfile = profile;
    next();
  } catch {
    res.status(503).json({
      error: {
        code: "ADMIN_AUTHORIZATION_UNAVAILABLE",
        message: "Could not verify administrator access."
      }
    });
  }
};
