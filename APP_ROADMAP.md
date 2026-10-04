# DevSecOps Tracker - build roadmap (one theme per round)

Principles: personal use, never published, private GitHub only. Notion stays
the source of truth; the app mirrors it. One theme per round, each tested on
the S24 before the next. Database changes are done by Claude via Supabase.

- R22  UI polish [DONE, verified S24 + tablet]  (was: this round): compact title bars, clearer labels, overdue
       chips, friendly dates, full-width heatmap.  [code done]
- R23  Structure [DONE, verified on tablet]: 5 tabs (Today, Learn,
       Revise, Weak, Progress), phase headers + status chips on Learn.
       Still to do later: Chapter tabs (Days/Topics/Blockers/Cards),
       Log study form polish, Settings.
- R24  Revision upgrades [code done, awaiting tablet test]: confidence before
       reveal, wrong-answer tags, leech flag, interval preview on buttons.
       Later: cloze + command-typing cards, revised answer/done screens.
- R25  Pedagogy: Mastery Gate checklist, live coverage table, prerequisite
       map, mastery decay (stale topics return to the queue).
- R26  Labs and projects: Bandit tracker, command vault, evidence
       attachments, P01-P09 project tracker.
- R27  Automation: focus timer, "how much time today" planner, reminders,
       home-screen widget, streak forgiveness (rest day), calendar blocks.
- R28  Security hardening + CI (the app as a portfolio project): remove the
       hard-coded sign-in, gitleaks, dependency scan, detekt, SBOM, threat
       model in README.
- R29  Insights + Coach, global search, glossary screen, interview practice,
       skills radar.

Blocked on the learner's explicit yes: Notion -> Supabase auto-sync
scheduled task ("scheduled task banao, ha").
Mockup: https://claude.ai/artifact/H8yXZqicexwspr5qEpP9HN


UPDATE (combined build): R24-R29 code written together in one pass, awaiting one
combined tablet test (PROMPT_FOR_ANTIGRAVITY.md). Contents:
- R24 revise upgrades; R25 mastery gate/coverage/prerequisites/decay
- R26 bandit tracker, command vault, projects P01-P09, evidence link
- R27 focus timer, day planner + calendar block, rest day, home widget
- R28 credentials to local.properties, allowBackup=false, .gitignore, CI, THREAT_MODEL.md
- R29 insights+coach, search, glossary, interview practice, skills by phase
Still open afterwards: Notion->Supabase auto-sync (needs "scheduled task banao, ha"),
S24 final sign-off, SHA-pinning actions, password rotation, chapter tabs, Settings redesign.

R30 UI/UX redesign (code written, awaiting tablet test): bundled fonts (Bricolage Grotesque +
Figtree), new theme, UiKit, AppTitleBar with subtitle; Today = plan card; Learn = phase chips +
module rows; Chapter tabs (Days/Topics/Weak/Cards/Gate); Revise; Weak Open/Cleared;
Progress = Overview/Library/Insights (Library replaces the More tools grid).

## R33 (audit fixes, not yet built)
Audit findings addressed in source: paged+cached reads that throw (Fetch.kt), single due rule (DueRules.kt), streak from yesterday, IST session_date, full backup, auth without sign-up fallback, edge-to-edge, notification permission, rememberSaveable forms, In-Progress case bug, Gate shows Notion result, logo + dark theme, unit tests.
Still open: Notion->Supabase sync (needs "scheduled task banao, ha"), Supabase grants revoke + leaked-password protection + signups off (dashboard), real release keystore + minify, SHA-pinned Actions, git init + gitleaks, Learn Engine / packets / drills, must-cover checklist, lab tracker.
