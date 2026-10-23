import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { readSupabaseConfiguration } from "./supabase.js";

export function getSupabaseAdminClient(): SupabaseClient | null {
  const config = readSupabaseConfiguration();
  const secretKey = process.env.SUPABASE_SECRET_KEY?.trim();
  if (!config || !secretKey) return null;

  return createClient(config.url, secretKey, {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
      detectSessionInUrl: false
    }
  });
}
