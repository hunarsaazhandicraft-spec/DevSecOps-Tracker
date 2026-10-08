-- ROUND 61: app-owned table for note-based daily revision (the app writes it, the tutor only reads it).
-- Run once in the Supabase SQL Editor. No existing table is touched.
create table if not exists public.note_revisions (
  id bigint generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  note_id bigint not null references public.topic_notes(id) on delete cascade,
  rung int not null,                       -- 1, 3, 7, 14, 30 (days after the note was written)
  done_at timestamptz not null default now(),
  score int,                               -- MCQ + order questions answered right
  total int,                               -- questions asked
  recall_hits int,                         -- recap points the learner ticked after the brain dump
  recall_total int,
  brain_dump text,                         -- the learner's own words (tutor reads this at DAY, never writes it)
  created_at timestamptz not null default now()
);
create index if not exists note_revisions_user_note on public.note_revisions (user_id, note_id);
alter table public.note_revisions enable row level security;
drop policy if exists "owner full access" on public.note_revisions;
create policy "owner full access" on public.note_revisions
  for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
