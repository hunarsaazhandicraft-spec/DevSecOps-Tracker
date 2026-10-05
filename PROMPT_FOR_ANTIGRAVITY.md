Paste everything below this line into Antigravity CLI as one task:
---------------------------------------------------------------
Project: C:\Users\Admin\Documents\DevSecOps Tracker

ROUND 47+48 (one big round): responsive navigation (commit 5f48ae0) + session
bar + note reader (outline, find, two-pane) + app-wide card style. Source
committed locally by Claude. No commit, no push by you.
Changes:
- ui\Navigation.kt: width < 600dp bottom bar; >= 600dp left NavigationRail and
  no bottom bar; content max 720dp (600-840) / 960dp (> 840). SessionBar shown
  above the content on top-level tabs EXCEPT Today.
- ui\SessionBar.kt (new): "day-N", module code, "agla: x" read from Supabase.
  Tap opens Path.
- ui\PipelineLogic.kt: splitSections(md), sectionMatches(sections, q) (+2
  tests: expect 51 total).
- ui\NotesScreens.kt (NoteDetailScreen): the note is split at "## " headings;
  a chip row (outline) jumps to a section; a "Note mein dhundo" box with
  "k/n next" jumps between matching sections; above 840dp the "Mere shabdon
  mein" card is a second pane on the right; below it stays under the note.
  New private WordsCard (same behaviour as before).
- ui\UiKit.kt: AppCard now has 16dp corners and a 1dp outlineVariant border
  (all screens: Progress, Mastery, Settings, Planner, Gates, Insights, Notes).
- ui\WeakSpotsScreen.kt: mono line no longer starts with "· ". The "Mark
  cleared" button and its dialog are REMOVED (the app no longer writes
  weak_spots); an open card now has a "Copy re-test" button (clipboard only)
  and a hint line: clearing happens when the tutor passes the re-test.
- ui\UiKit.kt StatTile and ui\ProgressScreen.kt: tile labels (total time,
  sessions, streak, library tile units) are mono labels.
Device: tablet PFI22N1G1323D2463. Run `adb devices` first; if "offline" run
`adb kill-server` then `adb start-server`, check once more; if still not
"device", stop and report. If locked, do not unlock, report.

*** THE APP TESTING PART IS READ-ONLY ***
NEVER tap Sign out, Start day, Take break, Resume, End day, Mark leave,
Cancel leave, Save & end, any attendance history row, any Revise rating
button, "Mark cleared", Save/Submit/Mark done, timer Start/Save, reminder
switch. On a note NEVER tap Save / Mark reviewed / Back to draft or type in
"Mere shabdon mein". "Copy DAY" may be tapped. Do NOT clear app data, do NOT
uninstall, do NOT toggle any device setting. Do not print local.properties,
do not type any password anywhere.

Step 1. Build, tests, install:
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\gradlew.bat assembleRelease
  .\gradlew.bat testDebugUnitTest
  adb -s PFI22N1G1323D2463 install -r app\build\outputs\apk\release\app-release.apk
  adb -s PFI22N1G1323D2463 shell am start -n com.devsecops.tracker/.MainActivity
Report exact totals (expect 51 passed, 0 failed) and every fix (file, line,
before -> after). If install fails with a signature/downgrade error, do NOT
uninstall.

Step 2. Screens, screenshots, read-only (do NOT rotate the device, do NOT
run wm size/density or change any setting):
 a. Today at the current orientation: t_r48_today.png. Report: bottom bar or
    left rail; tab labels in order; centred, not clipped.
 b. Tap Path, Revise, Mastery, Progress tabs (navigation only), one screenshot
    each (t_r48_path.png ...). Report: the slim session bar under the status
    bar on each (text: "day-N", module code, "agla: x"), whether it is absent
    on Today, the selected tab highlight, and how Progress/Mastery cards look
    (rounded corners + thin border).
 c. Open the Notes list -> "TCP three-way handshake" note. Report: the chip
    row of headings, the find box. Tap the chip "Real artifact": the note
    scrolls to that section (screenshot t_r48_note_jump.png). Type "syn" ONLY in
    the find box (find box is allowed; do NOT type in "Mere shabdon mein"):
    report the "1/n next" button and tap it once; report the scroll. Clear it.
 d. Report whether the "Mere shabdon mein" card is under the note (narrow) or
    in a right pane (wide). NEVER tap Save / Mark reviewed / Back to draft.
 e. Open the weak spots screen: first card mono line must not start with "·";
    an open card shows a "Copy re-test" button (you MAY tap it once: label
    becomes "Copied") and NO "Mark cleared" button anywhere. Progress tab:
    report the mono labels under the tiles.
 NEVER tap Mark cleared, Clear, Add, rating buttons, Save/Submit.

Step 3. READ-ONLY info for later (do not edit any file): run these in
PowerShell and paste the output into the report:
  git ls-remote https://github.com/actions/checkout "refs/tags/v4*"
  git ls-remote https://github.com/actions/setup-java "refs/tags/v4*"
  git ls-remote https://github.com/actions/upload-artifact "refs/tags/v4*"
  git ls-remote https://github.com/gitleaks/gitleaks-action "refs/tags/v2*"
  git ls-remote https://github.com/aquasecurity/trivy-action "refs/tags/0.28.0*"

Step 4. Final report: totals, fixes (file, line, before -> after), observations,
git status (clean, zero commits).
