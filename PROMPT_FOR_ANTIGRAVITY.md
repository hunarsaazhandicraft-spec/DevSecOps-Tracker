Paste everything below this line into Antigravity CLI as one task:
---------------------------------------------------------------
Project: C:\Users\Admin\Documents\DevSecOps Tracker

ROUND 38: read-only Gates and Labs screens (data from Supabase tables gates,
gate_checks, labs). Source edited by Claude, NOT compiled. No commit, no push.
Changes:
- data\Curriculum.kt: new models GateRow, GateCheckRow, LabRow, GateStatus;
  Curriculum.gates(), gateChecks(), labs(), moduleGateStatus(progress, code).
- ui\GatesLabsScreens.kt (new): GatesLabsScreen(onBack) with tabs Gates / Labs.
  Gates grouped by kind (module, phase, project, critical_skill, capstone),
  expandable cards showing the checks; module gates show a status pill.
  Labs: "Golden rule" card + expandable lab cards.
- ui\Navigation.kt: Routes.GATES + composable.
- ui\ProgressScreen.kt: new card "Gates and labs" (opens the screen).
- app\src\test\...\AttendanceTest.kt: new test gateStatus_passedNotStartedAndPending.
Device: tablet PFI22N1G1323D2463. Run `adb devices` first; if "offline" run
`adb kill-server` then `adb start-server` and check once more; if still not
"device", stop and report. If the device is locked, do not unlock it, report.

*** THE APP TESTING PART IS READ-ONLY ***
The account is logged in and may have a day in progress. NEVER tap "Sign out",
Start day, Take break, Resume, End day, Mark leave, Cancel leave, Save & end,
any attendance history row, any Revise rating button, "Mark cleared",
Save/Submit/Mark done, timer Start/Save, reminder switch. Do NOT clear app data
and do NOT uninstall. Only open screens, scroll, expand/collapse cards, tap
tabs, and use Back. Do not change any device setting. Do not print
local.properties and do not type any password anywhere.

Step 1. Build, tests, install:
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\gradlew.bat assembleRelease
  .\gradlew.bat testDebugUnitTest
  adb -s PFI22N1G1323D2463 install -r app\build\outputs\apk\release\app-release.apk
  adb -s PFI22N1G1323D2463 shell am start -n com.devsecops.tracker/.MainActivity
Likely compile issues: missing imports in GatesLabsScreens.kt (Sp, AppCard,
Pill, SegTabs, GroupHeader, ErrorBlock, SkeletonList, OnResume, AppTitleBar
live in the ui package; check their exact signatures), @Serializable models
in Curriculum.kt, Supabase column names (use the data classes as written),
GateStatus naming clash. You MAY fix compile errors with the smallest change
that keeps the intent. List EVERY fix (file, line, before -> after). If
install fails with a signature/downgrade error, do NOT uninstall; stop. If a
test FAILS, do not change production code to pass it; report name, expected,
actual, totals (expect 34).

Step 2. Screens (screenshots, report the texts asked):
 a. App opens without a login form (session survives install -r): t_r38_open.png.
 b. Progress tab: scroll to the card "Gates and labs": t_r38_progress.png.
    Report its subtitle text.
 c. Tap it. Gates tab: t_r38_gates.png. Report the group headers in order and
    how many cards each has (expect module 15ish, phase 8, project 2,
    critical skill, capstone 1; report the real numbers).
 d. Module gates: report the status pill text of M0.1, M0.2, M0.3 (and any
    other module gate that is not "Not started"). Expected: whatever Supabase
    progress says; flag anything that looks wrong.
 e. Expand one module gate card: t_r38_gate_open.png. Report the check lines
    (check_no, text), whether "hands-on" and "min Lx" pills show.
 f. Labs tab: t_r38_labs.png. Report the count of lab cards (expect 22), the
    "Golden rule" card text, and expand one lab: t_r38_lab_open.png (report
    environment/scope/safety lines).
 g. Back returns to Progress. Rotate nothing. Any crash, red error, endless
    spinner, empty tab, clipped text: screenshot + exact text.

Step 3. Report: build result and every fix; test totals; answers to every
"Report ..."; anything visually broken.
