Paste everything below this line into Antigravity CLI as one task:
---------------------------------------------------------------
Project: C:\Users\Admin\Documents\DevSecOps Tracker

ROUND 40: Resources screen, Notes shortcut on Today, mastery_records is now
SELECT-only for the app. Source edited by Claude, NOT compiled. No commit, no
push.
Changes:
- data\Resources.kt (new): ResourceRow, Resources.all(), Resources.openableUrl().
- ui\ResourcesScreen.kt (new): search, All/Primary/Official/Extra tabs, grouped
  by slot (Courses, Books, Videos, Docs and standards, Tools and labs); tap a
  card that has a link to open it in the browser.
- ui\Navigation.kt: Routes.RESOURCES. ui\ProgressScreen.kt: card "Resources".
- ui\HomeScreen.kt: new "Notes" card on Today below the Tutor card.
- data\Repository.kt: the unused recordMastery() was removed (the DB no longer
  allows the app to insert into mastery_records).
- app\src\test\...\NotesTest.kt: new class ResourcesTest (1 test).
Device: tablet PFI22N1G1323D2463. Run `adb devices` first; if "offline" run
`adb kill-server` then `adb start-server` and check once more; if still not
"device", stop and report. If the device is locked, do not unlock it, report.

*** THE APP TESTING PART IS READ-ONLY ***
NEVER tap "Sign out", Start day, Take break, Resume, End day, Mark leave,
Cancel leave, Save & end, any attendance history row, any Revise rating
button, "Mark cleared", Save/Submit/Mark done, timer Start/Save, reminder
switch, and on a note NEVER tap Save / Mark reviewed / Back to draft or type
in "Mere shabdon mein". Do NOT clear app data, do NOT uninstall, do NOT
toggle any device setting. Do not print local.properties, do not type any
password anywhere.

Step 1. Build, tests, install:
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\gradlew.bat assembleRelease
  .\gradlew.bat testDebugUnitTest
  adb -s PFI22N1G1323D2463 install -r app\build\outputs\apk\release\app-release.apk
  adb -s PFI22N1G1323D2463 shell am start -n com.devsecops.tracker/.MainActivity
Likely compile issues: unused import MasteryRecordInsert in Repository.kt is
only a warning; `Resources` name vs android.content.res.Resources in
ResourcesScreen.kt (we import com.devsecops.tracker.data.Resources); Intent/
Uri imports. You MAY fix compile errors with the smallest change that keeps
the intent. List EVERY fix (file, line, before -> after). If install fails
with a signature/downgrade error, do NOT uninstall; stop. If a test FAILS, do
not change production code to pass it; report name, expected, actual, totals
(expect 39).

Step 2. Screens (screenshots, report the texts asked):
 a. App opens without a login form: t_r40_open.png.
 b. Today: scroll to the new "Notes" card (below the tutor card):
    t_r40_today.png. Report its text and tap it; confirm it opens the Notes
    list (subtitle "15 notes"); Back.
 c. Progress -> card "Resources": t_r40_progress.png. Tap it:
    t_r40_resources.png. Report subtitle (expect "276 curated links
    (read-only)"), the group headers in order with their counts, and the first
    3 titles of the first group.
 d. Tabs: Primary, Official, Extra. Report the total of cards in each (sum the
    group counts; expect Primary 76, Official 79, Extra 121 = 276). Back to All.
 e. Search "burp": report how many cards remain and 3 titles. Clear it.
 f. Pick a card showing the pill "no link": tapping it must do nothing (report
    that). Pick a card with pill "link": DO NOT tap it (no browser opening).
 g. Check that M0.1 mastery data still loads: Progress -> "Learn: mastery by
    module": t_r40_mastery.png; report the first module row and that no error
    banner is shown (the table is now SELECT-only).
 h. Any crash, red error, endless spinner, clipped text: screenshot + exact
    text.

Step 3. Report: build result and every fix; test totals; answers to every
"Report ..."; anything visually broken.
