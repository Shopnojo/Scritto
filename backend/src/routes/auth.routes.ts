import { Router } from "express";
import type { User } from "@supabase/supabase-js";
import { requireAdmin, requireAuth } from "../middleware/auth.js";

export const authRouter = Router();

authRouter.get("/me", requireAuth, (req, res) => {
  const user = res.locals.authUser as User;
  const metadata = user.user_metadata ?? {};

  res.status(200).json({
    user: {
      id: user.id,
      email: user.email ?? null,
      display_name: typeof metadata.full_name === "string"
        ? metadata.full_name
        : typeof metadata.name === "string"
          ? metadata.name
          : null,
      avatar_url: typeof metadata.avatar_url === "string"
        ? metadata.avatar_url
        : typeof metadata.picture === "string"
          ? metadata.picture
          : null
    }
  });
});

export const adminRouter = Router();

adminRouter.get("/me", requireAuth, requireAdmin, (_req, res) => {
  res.status(200).json({
    admin: res.locals.adminProfile
  });
});
