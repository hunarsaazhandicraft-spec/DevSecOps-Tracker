-- Phase F: mastery_records becomes SELECT-only for the app.
-- The tutor writes it through the service side (unaffected). The immutability
-- trigger stays. Anyone holding the app's login can no longer forge mastery.
drop policy if exists "owner insert" on public.mastery_records;
revoke insert on public.mastery_records from authenticated;
revoke all on public.mastery_records from anon;
