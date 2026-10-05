-- Lab tracker: tutor-written, app is read-only (SELECT only). Run in Supabase SQL Editor.
create table if not exists public.lab_runs (
  id bigint generated always as identity primary key,
  user_id uuid not null default auth.uid(),
  lab_id text not null references public.labs(id),
  status text not null default 'in_progress'
    check (status in ('in_progress','done','blocked')),
  level text check (level in ('L1','L2','L3','L4','L5')),
  evidence text,
  evidence_url text,
  notes text,
  run_date date not null default current_date,
  created_at timestamptz not null default now()
);
create index if not exists lab_runs_user_lab_idx on public.lab_runs (user_id, lab_id, run_date desc);

alter table public.lab_runs enable row level security;
drop policy if exists lab_runs_select_own on public.lab_runs;
create policy lab_runs_select_own on public.lab_runs
  for select to authenticated using (user_id = auth.uid());

-- App is read-only: SELECT only, nothing for anon.
revoke all on public.lab_runs from anon, authenticated;
grant select on public.lab_runs to authenticated;
