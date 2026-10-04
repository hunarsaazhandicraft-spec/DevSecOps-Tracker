Paste everything below this line into Antigravity CLI as one task:
---------------------------------------------------------------
Project: C:\Users\Admin\Documents\DevSecOps Tracker

ROUND 41: V3 "Pipeline" look + new Today screen + diagram blocks in notes.
Source edited by Claude, NOT compiled. No commit, no push.
Changes:
- ui\theme\Theme.kt: new dark palette (mint primary, blue secondary, amber
  tertiary, coral error, darker surfaces). Same fonts (Bricolage, Figtree).
- ui\PipelineLogic.kt (new, pure): Stat enum, pipelineStats(), sessionNodes(),
  nextDayNumber().
- ui\PipelineKit.kt (new): StatGlyph (passed/running/queued/pending/failing/
  locked), MonoLabel, EvidenceLadder, GlowCard, TerminalCard, TermLine.
- ui\TodayPipeline.kt (new): PipelineCard (3 stages: Warm-up, Gap re-test, Naya
  session), MiniTile, SessionStrip, TutorLogCard (terminal-style replacement of
  the Tutor sync card on Today).
- ui\HomeScreen.kt: HomeContent rewritten: header "Aaj", attendance card,
  pipeline card, backlog rescue, 3 tiles (gaps / cards due / streak), session
  strip, tutor.log card, collapsed "Kya karu abhi?" (opens the old Continue
  card), Notes card, Log study / Focus / Plan buttons. Removed: plan card,
  stat figures, 7-day dot strip.
- ui\MarkdownView.kt: new MdBlock.Diagram and MdBlock.Callout; fenced blocks
  with kind stack | nest | steps | seq | callout become diagrams/callouts
  (other fences stay code). pairCallouts(): attacker+defender side by side.
- ui\MarkdownDiagrams.kt (new): DiagramView, CalloutView.
- Supabase topic_notes: notes "OSI model layers" and "TCP three-way handshake"
  were rewritten with diagrams (data only, no schema change).
- Tests: PipelineLogicTest (5 new), NotesTest (+2). Expect 46 total.
Device: tablet PFI22N1G1323D2463. Run `adb devices` first; if "offline" run
`adb kill-server` then `adb start-server` and check once more; if still not
"device", stop and report. If the device is locked, do not unlock it, report.

*** THE APP TESTING PART IS READ-ONLY ***
NEVER tap "Sign out", Start day, Take break, Resume, End day, Mark leave,
Cancel leave, Save & end, any attendance history row, any Revise rating
button, "Mark cleared", Save/Submit/Mark done, timer Start/Save, reminder
switch, "Run warm-up" / "Open gaps" / "Open next topic" buttons INSIDE the
pipeline card are allowed to be TAPPED ONLY IF they just navigate (see 2c),
and on a note NEVER tap Save / Mark reviewed / Back to draft or type in
"Mere shabdon mein". The "Copy DAY" button may be tapped (clipboard only).
Do NOT clear app data, do NOT uninstall, do NOT toggle any device setting.
Do not print local.properties, do not type any password anywhere.

Step 1. Build, tests, install:
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\gradlew.bat assembleRelease
  .\gradlew.bat testDebugUnitTest
  adb -s PFI22N1G1323D2463 install -r app\build\outputs\apk\release\app-release.apk
  adb -s PFI22N1G1323D2463 shell am start -n com.devsecops.tracker/.MainActivity
Likely compile issues: Icons.Filled.Refresh / Lock / Check / Close imports in
PipelineKit.kt; `size` inside Canvas lambdas (DrawScope.size) vs parameters;
Dp * Float / Dp * Int in MarkdownDiagrams.kt (SeqBody: `w * ((i+0.5f)/n)`,
`rowH * msgs.size`); Triple destructuring in CalloutView; an unused import is
only a warning; `inline` is an existing function name in MarkdownView.kt (now
`internal`). Row/Column with IntrinsicSize.Min containing a child with
Modifier.weight(1f) vertical line (StageRow, StepsBody): if it crashes at
runtime ("intrinsic measurements"), replace the weight(1f) line by
fillMaxHeight() and report. You MAY fix compile errors with the smallest
change that keeps the intent. List EVERY fix (file, line, before -> after).
If install fails with a signature/downgrade error, do NOT uninstall; stop. If
a test FAILS, do not change production code to pass it; report name, expected,
actual, totals (expect 46).

Step 2. Screens (screenshots, report the texts asked):
 a. App opens without a login form: t_r41_open.png.
 b. Today top: t_r41_today.png. Report, in order, every card from the top
    (header text, attendance card text, the pipeline card: "dayN" label, the 3
    stage titles with their pill word (passed/running/queued) and the
    subtitle of each, and which stage shows a filled green button and its
    label). Then scroll: t_r41_today2.png, t_r41_today3.png. Report: the 3
    tiles (label + value), the session strip (module code, the node labels in
    order, which node is green "agla"), the tutor.log card (the "> " lines, the
    live/offline word, the "Copy DAY n" button text). Say whether Continue (the
    green button of the running stage) is visible WITHOUT scrolling.
 c. Tap the green button of the running stage: report where it went, Back.
    Tap the "Copy DAY" button: report that its label changed to "Copied".
    Tap "tutor writes (n)" row: report that it expands; tap again.
 d. Tap "Kya karu abhi?": the old Continue card with 30/60/90 chips opens;
    t_r41_smart.png; collapse it. Tap the Notes card: Notes list opens; Back.
 e. Notes: open "TCP three-way handshake": screenshots t_r41_tcp1.png (top),
    t_r41_tcp2.png ... scroll the whole note. Report for each block that a
    DIAGRAM or CALLOUT appears and looks right: (1) the sequence diagram
    (two actor names Client and Server, 3 arrows labelled SYN, SYN-ACK, ACK,
    arrow directions right, left, right; labels not clipped), (2) the numbered
    steps list (3 steps), (3) the flags table, (4) attacker and defender
    callouts side by side, (5) misconception callout, (6) recap callout.
 f. Open "OSI model layers": t_r41_osi1.png, t_r41_osi2.png ... Report: (1)
    the nested-boxes diagram (4 levels, innermost green box says HTTP data),
    (2) the 7-row stack diagram (names, example text, unit pill), (3) the TCP/IP
    4-row stack, (4) the HTTPS steps (5 numbered), (5) the attack/defence table,
    (6) attacker/defender side by side, (7) misconception and recap callouts.
    Report any clipped, overlapping or unreadable element.
 g. Open one OTHER note (e.g. "Linux CLI basics"): confirm it still renders as
    before (headings, code, table) with no crash.
 h. Progress tab, Revise tab, Mastery tab, Library: one screenshot each
    (t_r41_progress.png etc.). Report only whether the new colours cause
    unreadable text or invisible buttons anywhere (name the screen).
 i. Any crash, red error, endless spinner, clipped text: screenshot + exact
    text.

Step 3. Report: build result and every fix; test totals; answers to every
"Report ..."; anything visually broken.
