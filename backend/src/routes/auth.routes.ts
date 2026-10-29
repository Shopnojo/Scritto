import { Router } from "express";
import { createClient, type User } from "@supabase/supabase-js";
import { requireAdmin, requireAuth } from "../middleware/auth.js";

export const authRouter = Router();

authRouter.get("/me", requireAuth, async (_req, res) => {
  const user = res.locals.authUser as User;
  const supabase = res.locals.authSupabase as ReturnType<typeof createClient>;
  const metadata = user.user_metadata ?? {};

  try {
    const result = await supabase
      .from("admin_profiles")
      .select("role, display_name, is_active")
      .eq("user_id", user.id)
      .maybeSingle();

    // A signed-in Google account without an admin profile is authenticated but
    // not authorised. Returning the identity lets the frontend show that state.
    const profile = (result.error ? null : result.data) as {
      role: string | null;
      display_name: string | null;
      is_active: boolean | null;
    } | null;
    res.status(200).json({
      id: user.id,
      email: user.email ?? "",
      display_name: profile?.display_name ??
        (typeof metadata.full_name === "string" ? metadata.full_name :
          typeof metadata.name === "string" ? metadata.name : null),
      avatar_url: typeof metadata.avatar_url === "string" ? metadata.avatar_url :
        typeof metadata.picture === "string" ? metadata.picture : null,
      role: profile?.is_active ? profile.role : null,
      is_admin: Boolean(profile?.is_active && ["owner", "admin", "analyst"].includes(profile.role))
    });
  } catch {
    res.status(503).json({
      error: {
        code: "ADMIN_AUTHORIZATION_UNAVAILABLE",
        message: "Could not verify administrator access."
      }
    });
  }
});

export const adminRouter = Router();

adminRouter.get("/me", requireAuth, requireAdmin, (_req, res) => {
  res.status(200).json({
    admin: res.locals.adminProfile
  });
});
