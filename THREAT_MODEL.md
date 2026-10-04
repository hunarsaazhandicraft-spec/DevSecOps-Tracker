# Threat model - DevSecOps Tracker (personal Android app)

Scope: one Android app (Kotlin/Compose) talking to one Supabase project (Postgres + Auth + PostgREST). Single user. Personal use only, never published. Supabase is the single source of truth for progress; the tutor chat writes to it live (Notion is an archive).

## Assets
- Learning records: mastery evidence, weak spots, reviews, notes, glossary, projects.
- The one account credential used for silent sign-in.
- The Supabase project itself (data + auth).

## Trust boundaries
Device (app + on-device session) -> HTTPS -> Supabase API -> Postgres (RLS).

## STRIDE
| Threat | Example | Mitigation | Residual |
|---|---|---|---|
| Spoofing | Someone calls the API pretending to be me | Supabase Auth JWT on every request; RLS `(select auth.uid()) = user_id` on every table | Password is typed once on the device; it is no longer compiled into the APK (changed 2026-10-04). Session is kept by supabase-kt in app-private storage |
| Tampering | Edit or delete mastery evidence | `mastery_records`: app role holds only SELECT + INSERT grants (UPDATE/DELETE revoked 2026-09-29) and a BEFORE UPDATE/DELETE trigger blocks edits, including FK actions; RLS has no update/delete policy | Owner/service role can bypass only by setting `app.allow_mastery_edit = 'on'` in a transaction |
| Repudiation | "I never recorded that" | Evidence rows are timestamped and immutable; reviews carry timestamps | No external audit log |
| Information disclosure | Leaked key or password in Git | Credentials moved out of source to git-ignored `local.properties`; gitleaks + Trivy secret scan in CI; `allowBackup=false` | Anon key is public by design (RLS protects data); password sits in the APK |
| Denial of service | Supabase pause or outage | App shows load errors with retry; data lives in Supabase | No offline cache yet |
| Elevation of privilege | Using the anon key to read other rows | RLS owner-only on all tables; `mastery_records` insert-only | Leaked-password protection needs the Pro plan (off); public signups are disabled (2026-09-29); `anon` role has no table privileges |

## Known gaps (tracked)
1. (Fixed 2026-10-04) The password is no longer baked into the APK; a login form asks for it once. Still open: encrypt the stored session (Android Keystore) and add passkey/OTP.
2. GitHub Actions are pinned to version tags, not commit SHAs.
3. No certificate pinning (Supabase TLS is standard).
4. (Done 2026-10-04) Account password rotated after the old value was found in git history and old source copies; repo history rewritten to one clean commit and the repo made private.

## Security checks in CI
gitleaks (secrets), Trivy (dependencies, secrets, misconfig, HIGH/CRITICAL fails the build), CycloneDX SBOM artifact, Android lint, detekt report. See `.github/workflows/security.yml`.


## Update 2026-09-29 (post-audit reality check)
Fixed: sign-up fallback removed from AuthGate; public signups disabled in Supabase Auth; `anon` privileges revoked; `authenticated` lost TRUNCATE/TRIGGER/REFERENCES; curriculum tables are read-only for the app; mastery_records is immutable (grants + trigger); backups now include every table and only the newest 3 files are kept.
Still open (honest list):
1. (Fixed 2026-10-04) The password is no longer in BuildConfig or the APK. Old builds still contain the previous password, which is now invalid.
2. Release build is signed with the debug key and not minified. Do NOT switch keys casually: a new signing key forces an uninstall (data loss on device).
3. GitHub Actions are pinned by tag, not commit SHA.
4. The project is not a git repo locally yet; run gitleaks on the tree before the first commit.
5. Session token and widget cache are plain SharedPreferences (readable on a rooted device).
6. Supabase is the single source of truth. Notion is an archive and is no longer synced.
