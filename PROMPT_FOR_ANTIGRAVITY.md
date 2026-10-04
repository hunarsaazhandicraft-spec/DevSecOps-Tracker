Paste everything below this line into Antigravity CLI as one task:
---------------------------------------------------------------
Project: C:\Users\Admin\Documents\DevSecOps Tracker

ROUND 36: audit fixes (source edited by Claude, NOT compiled). No commit, no push.
Changes:
- data\Attendance.kt, ui\AttendanceScreens.kt: endDay refuses when the day was
  never started and returns EndResult(hours, loggedToTutor); clearLeave only
  touches the streak row when a leave row existed; history() reads only the last
  35 days and lets a streak fetch error propagate; exportAttendance/exportBreaks.
- data\Fetch.kt: invalidate() also clears Learner's cache.
- data\Curriculum.kt: path() no longer hides fetch errors; new isPassed() matches
  the module code exactly ("M0.1 ..." or "M0.1: ...", not "M0.10").
- Self-set mastery removed: ui\RecordMasteryScreen.kt DELETED, route
  RECORD_MASTERY and the "+" button on the Learn screen removed.
- Dead code DELETED: data\NbaEngine.kt, ui\ReadinessView.kt,
  ui\KnowledgeGraphCanvas.kt.
- ui\HomeScreen.kt: MemoryRingCard removed from Today (it stays on Progress).
- ui\ProgressScreen.kt: two new cards "Learn: mastery by module" and "Weak spots".
- ui\PlannerScreen.kt: the "Take a rest day" button removed (leave is in Attendance).
- data\Models.kt + Repository.kt: backup now includes attendance and breaks.
- Extras.kt/GateScreen.kt: notionResult renamed gateResultText (no logic change).
- NEW test file app\src\test\java\com\devsecops\tracker\data\AttendanceTest.kt
  (9 tests: worked-minutes math and Curriculum.isPassed).
Device: tablet PFI22N1G1323D2463. Run `adb devices` first; if "offline" run
`adb kill-server` then `adb start-server` and check once more; if still not
"device", stop and report. If the device is locked, do not unlock it, report.

*** THE APP TESTING PART IS READ-ONLY ***
The account has a day in progress. NEVER tap: Start day, Take break, Resume,
End day, Mark leave, Cancel leave, Save & end, any history row, any Revise
rating button, "Mark cleared", Save/Submit/Mark done, timer Start/Save,
reminder switch. Only open screens, scroll, and leave dialogs with Back. Do not
change any device setting. Do not print local.properties.

Step 1. Build, tests, install:
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\gradlew.bat assembleRelease
  .\gradlew.bat testDebugUnitTest
  adb -s PFI22N1G1323D2463 install -r app\build\outputs\apk\release\app-release.apk
  adb -s PFI22N1G1323D2463 shell am start -n com.devsecops.tracker/.MainActivity
Likely compile issues: leftover references to deleted files or to
onRecordEvidence/RECORD_MASTERY, unused-import warnings (ignore warnings),
ProgressItem constructor in the new test. You MAY fix compile errors with the
smallest change that keeps behaviour. List EVERY fix (file, line, before ->
after). Do not restore deleted features. If install fails with a
signature/downgrade error, do NOT uninstall; stop. If a test FAILS, do not
change the production code to pass it; report name, expected, actual, totals
(expect 24 old + 9 new = 33).

Step 2. Screens (screenshots, report the texts asked):
 a. Today: t_r36_today.png. Report the order of cards (top to bottom) and
    confirm there is NO memory-ring card on Today.
 b. Tabs: t_r36_tabs.png. Report the 5 tab labels.
 c. Progress: t_r36_progress.png, scrolled t_r36_progress2.png. Report whether
    cards "Learn: mastery by module" and "Weak spots" exist; tap "Learn: mastery
    by module" and report the title and that there is NO "+" button in its header
    (t_r36_learn.png), then Back.
 d. Tap "Weak spots" card: t_r36_weak.png. Report it opens with a Back arrow.
 e. Attendance screen: t_r36_att.png, scrolled t_r36_att2.png. Report the Today
    card and the first 8 history rows with labels (30 Sep and 1-3 Oct are rest
    days in the data: say whether they read Leave or Absent) and the header counts.
 f. Planner (Today > Plan): t_r36_planner.png. Report that the "Take a rest day"
    button is gone and what text replaces it.
 g. Path tab: t_r36_path.png. Report the header stats line and the NOW card.
 h. Any crash, red error, endless spinner, error text: screenshot + exact text.

Step 3. Report: build result and every fix; test totals; answers to every
"Report ..."; anything visually broken.
