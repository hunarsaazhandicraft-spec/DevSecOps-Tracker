Paste everything below this line into Antigravity CLI as one task:
---------------------------------------------------------------
Project: C:\Users\Admin\Documents\DevSecOps Tracker

ROUND 35: Attendance feature (Start/End day, break, confidence, present/leave).
Two new files (data\Attendance.kt, ui\AttendanceScreens.kt), two edited
(ui\Navigation.kt route "attendance", ui\HomeScreen.kt AttendanceCard item).
Device: tablet PFI22N1G1323D2463. Run `adb devices` first; if "offline" run
`adb kill-server` then `adb start-server` and check once more; if still not
"device", stop and report. It must be awake and unlocked; if locked, do not
unlock it, report.

*** THE APP TESTING PART IS READ-ONLY ***
This feature WRITES to the real account, so NEVER tap: Start / Start day,
Take break, Resume, End day, Mark today as leave, Cancel leave, Mark leave,
Save & end, any day row in the history list, nor any rating button on Revise,
"Mark cleared", "Take a rest day", Save/Submit/Mark done, timer Start/Save,
reminder switch. Only open screens, scroll, and leave dialogs with Back.
Do not change any device setting. Do not print local.properties.

Step 1. Build, tests, install:
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\gradlew.bat assembleRelease
  .\gradlew.bat testDebugUnitTest
  adb -s PFI22N1G1323D2463 install -r app\build\outputs\apk\release\app-release.apk
  adb -s PFI22N1G1323D2463 shell am start -n com.devsecops.tracker/.MainActivity
The new files are uncompiled. You MAY fix compile errors with the smallest
change that keeps behaviour (imports, parameter names, API differences, e.g.
supabase-kt update/delete/filter syntax, Math.round, kotlinx-datetime plus/minus).
List EVERY fix (file, line, before -> after). Do not remove features. If install
fails with a signature/downgrade error, do NOT uninstall; stop. If a unit test
FAILS, do not change the test or production code to pass it; report name,
expected, actual, and totals.

Step 2. Screens (screenshots, report texts asked):
 a. Today: t_r35_today.png. Report whether an "Attendance" card appears above
    the "Tutor sync" card, its status line (expect "Day not started" or
    "Studying ..."), and whether a "Start" button is shown (do NOT tap).
 b. Tap the Attendance card body (not the Start button) to open the screen:
    t_r35_att.png. Report the header, the "Today" card contents and buttons,
    and the "Last 35 days" header counts (present / leave / absent).
 c. Scroll: t_r35_att2.png. Report the first 5 history rows (date, label,
    hours/confidence text) and whether a row says "Tap to mark as leave".
 d. Press Back; confirm you return to Today with no crash.
 e. Any crash, red error screen, endless spinner or "Could not load attendance":
    screenshot and report the exact text.

Step 3. Report: build result and every fix; test totals; answers to every
"Report ..."; anything visually broken (overlap, cut-off text).
