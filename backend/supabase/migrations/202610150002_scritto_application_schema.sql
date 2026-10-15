-- Scritto application data foundation.
-- Client apps must never access these tables directly. The API uses a server-side
-- Supabase client and performs authentication/authorization before accessing data.
-- Do not store note/document contents, filenames, or AI conversation content here.

create table if not exists public.admin_profiles (
  user_id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  role text not null default 'admin'
    check (role in ('owner', 'admin', 'analyst')),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint admin_profiles_display_name_length
    check (display_name is null or char_length(display_name) <= 120)
);

create table if not exists public.installations (
  installation_id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users (id) on delete set null,
  platform text not null default 'android'
    check (platform in ('android', 'ios', 'web', 'unknown')),
  manufacturer text,
  model text,
  os_version text,
  sdk_int integer check (sdk_int is null or sdk_int >= 0),
  app_version text,
  app_build text,
  locale text,
  push_token text,
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint installations_manufacturer_length
    check (manufacturer is null or char_length(manufacturer) <= 120),
  constraint installations_model_length
    check (model is null or char_length(model) <= 160),
  constraint installations_os_version_length
    check (os_version is null or char_length(os_version) <= 80),
  constraint installations_app_version_length
    check (app_version is null or char_length(app_version) <= 80),
  constraint installations_app_build_length
    check (app_build is null or char_length(app_build) <= 80),
  constraint installations_locale_length
    check (locale is null or char_length(locale) <= 35),
  constraint installations_push_token_length
    check (push_token is null or char_length(push_token) <= 4096)
);

create index if not exists installations_user_id_idx
  on public.installations (user_id);
create index if not exists installations_last_seen_at_idx
  on public.installations (last_seen_at desc);
create unique index if not exists installations_push_token_unique_idx
  on public.installations (push_token)
  where push_token is not null;

create table if not exists public.usage_events (
  id bigint generated always as identity primary key,
  installation_id uuid not null
    references public.installations (installation_id) on delete cascade,
  user_id uuid references auth.users (id) on delete set null,
  name text not null check (name in (
    'app_opened',
    'note_created',
    'task_created',
    'task_completed',
    'event_scheduled',
    'document_opened',
    'ai_message_sent',
    'voice_assistant_used',
    'file_imported'
  )),
  occurred_at timestamptz not null,
  received_at timestamptz not null default now(),
  app_version text,
  properties jsonb not null default '{}'::jsonb
    check (jsonb_typeof(properties) = 'object'),
  constraint usage_events_properties_size
    check (octet_length(properties::text) <= 4096)
);

create index if not exists usage_events_received_at_idx
  on public.usage_events (received_at desc);
create index if not exists usage_events_name_received_at_idx
  on public.usage_events (name, received_at desc);
create index if not exists usage_events_installation_received_at_idx
  on public.usage_events (installation_id, received_at desc);
create index if not exists usage_events_user_received_at_idx
  on public.usage_events (user_id, received_at desc)
  where user_id is not null;

create table if not exists public.remote_config (
  key text primary key
    check (key ~ '^[a-z][a-z0-9_.-]{0,99}$'),
  value jsonb not null default '{}'::jsonb,
  description text,
  updated_by uuid references auth.users (id) on delete set null,
  updated_at timestamptz not null default now(),
  constraint remote_config_description_length
    check (description is null or char_length(description) <= 500)
);

create table if not exists public.admin_notifications (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 160),
  body text not null check (char_length(body) between 1 and 2000),
  audience jsonb not null default '{"type":"all"}'::jsonb
    check (jsonb_typeof(audience) = 'object'),
  status text not null default 'draft'
    check (status in ('draft', 'queued', 'sending', 'sent', 'failed', 'cancelled')),
  total_count integer not null default 0 check (total_count >= 0),
  success_count integer not null default 0 check (success_count >= 0),
  failure_count integer not null default 0 check (failure_count >= 0),
  created_by uuid references auth.users (id) on delete set null,
  created_at timestamptz not null default now(),
  sent_at timestamptz,
  constraint admin_notifications_counts_consistent
    check (success_count + failure_count <= total_count)
);

create index if not exists admin_notifications_created_at_idx
  on public.admin_notifications (created_at desc);
create index if not exists admin_notifications_status_created_at_idx
  on public.admin_notifications (status, created_at desc);

create table if not exists public.notification_deliveries (
  id bigint generated always as identity primary key,
  notification_id uuid not null
    references public.admin_notifications (id) on delete cascade,
  installation_id uuid not null
    references public.installations (installation_id) on delete cascade,
  status text not null default 'pending'
    check (status in ('pending', 'sent', 'delivered', 'failed', 'skipped')),
  provider_message_id text,
  error_code text,
  created_at timestamptz not null default now(),
  sent_at timestamptz,
  constraint notification_deliveries_provider_id_length
    check (provider_message_id is null or char_length(provider_message_id) <= 512),
  constraint notification_deliveries_error_code_length
    check (error_code is null or char_length(error_code) <= 120),
  unique (notification_id, installation_id)
);

create index if not exists notification_deliveries_notification_status_idx
  on public.notification_deliveries (notification_id, status);
create index if not exists notification_deliveries_installation_idx
  on public.notification_deliveries (installation_id, created_at desc);

-- RLS is enabled everywhere. The public/mobile clients receive no table grants.
-- Admin profile SELECT is limited to the signed-in user's own row; role and
-- active-state enforcement for administrative operations must also happen in API.
alter table public.admin_profiles enable row level security;
alter table public.installations enable row level security;
alter table public.usage_events enable row level security;
alter table public.remote_config enable row level security;
alter table public.admin_notifications enable row level security;
alter table public.notification_deliveries enable row level security;

drop policy if exists "Admins can read own profile" on public.admin_profiles;
create policy "Admins can read own profile"
  on public.admin_profiles
  for select
  to authenticated
  using (user_id = (select auth.uid()));

revoke all on table public.admin_profiles from anon, authenticated;
grant select on table public.admin_profiles to authenticated;

revoke all on table public.installations from anon, authenticated;
revoke all on table public.usage_events from anon, authenticated;
revoke all on table public.remote_config from anon, authenticated;
revoke all on table public.admin_notifications from anon, authenticated;
revoke all on table public.notification_deliveries from anon, authenticated;

grant all on table public.admin_profiles to service_role;
grant all on table public.installations to service_role;
grant all on table public.usage_events to service_role;
grant all on table public.remote_config to service_role;
grant all on table public.admin_notifications to service_role;
grant all on table public.notification_deliveries to service_role;

grant usage, select on all sequences in schema public to service_role;

-- Restrict client execution of future SECURITY DEFINER functions by default.
