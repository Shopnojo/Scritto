import { createClient, type SupabaseClient } from "@supabase/supabase-js";

export type SupabaseConfiguration = {
  url: string;
  publishableKey: string;
};

export function readSupabaseConfiguration(
  env: NodeJS.ProcessEnv = process.env
): SupabaseConfiguration | null {
  const url = env.SUPABASE_URL?.trim();
  const publishableKey = env.SUPABASE_PUBLISHABLE_KEY?.trim();

  if (!url || !publishableKey) return null;

  try {
    const parsed = new URL(url);
    if (parsed.protocol !== "https:" && parsed.hostname !== "localhost" && parsed.hostname !== "127.0.0.1") {
      return null;
    }
  } catch {
    return null;
  }

  return { url: url.replace(/\/$/, ""), publishableKey };
}

let cachedClient: SupabaseClient | undefined;

export function getSupabaseClient(): SupabaseClient | null {
  const config = readSupabaseConfiguration();
  if (!config) return null;

  if (!cachedClient) {
    cachedClient = createClient(config.url, config.publishableKey, {
      auth: {
        autoRefreshToken: false,
        persistSession: false,
        detectSessionInUrl: false
      }
    });
  }

  return cachedClient;
}
