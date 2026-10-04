Paste everything below this line into Antigravity CLI as one task:
---------------------------------------------------------------
Project: C:\Users\Admin\Documents\DevSecOps Tracker

ROUND 37: real login form + Sign out; the password is no longer compiled into
the APK. Source edited by Claude, NOT compiled. No commit, no push.
Changes:
- ui\AuthGate.kt rewritten: Authenticated -> app; NotAuthenticated -> a login
  form (email prefilled from BuildConfig.APP_EMAIL, password typed by the user);
  Initializing/RefreshFailure -> spinner with Retry after 8 s. No auto sign-in.
- app\build.gradle.kts: the APP_PASSWORD buildConfigField was removed.
- ui\SettingsScreen.kt: new "Account" card with a "Sign out" button + confirm
  dialog (calls Supa.client.auth.signOut()).
- THREAT_MODEL.md text updated (no code).
Device: tablet PFI22N1G1323D2463. Run `adb devices` first; if "offline" run
`adb kill-server` then `adb start-server` and check once more; if still not
"device", stop and report. If the device is locked, do not unlock it, report.

*** THE APP TESTING PART IS READ-ONLY ***
The account is logged in on the tablet and has a day in progress. NEVER tap
"Sign out" (nor its confirm dialog), Start day, Take break, Resume, End day,
Mark leave, Cancel leave, Save & end, any history row, any Revise rating
button, "Mark cleared", Save/Submit/Mark done, timer Start/Save, reminder
switch. Do NOT clear app data and do NOT uninstall. Only open screens, scroll,
and leave dialogs with Back. Do not change any device setting. Do not print
local.properties and do not type any password anywhere.

Step 1. Build, tests, install:
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\gradlew.bat assembleRelease
  .\gradlew.bat testDebugUnitTest
  adb -s PFI22N1G1323D2463 install -r app\build\outputs\apk\release\app-release.apk
  adb -s PFI22N1G1323D2463 shell am start -n com.devsecops.tracker/.MainActivity
Likely compile issues: SessionStatus.Initializing/RefreshFailure names in
supabase-kt 3.0.3, auth.refreshCurrentSession(), keyboardOptions imports,
widthIn. You MAY fix compile errors with the smallest change that keeps the
intent (login form when NotAuthenticated, no password in BuildConfig). List
EVERY fix (file, line, before -> after). If install fails with a
signature/downgrade error, do NOT uninstall; stop. If a test FAILS, do not
change production code to pass it; report name, expected, actual, totals
(expect 33).
Also confirm that the generated BuildConfig.java under
app\build\generated\source\buildConfig\release\ has NO APP_PASSWORD field
(print only the field NAMES, never values).

Step 2. Screens (screenshots, report the texts asked):
 a. After the install the app must open WITHOUT a login form (the saved session
    survives `install -r`): t_r37_open.png. Report whether it lands on Today or
    on a login form or a spinner. This is the key check.
 b. Today: t_r37_today.png. Report the first card.
 c. Settings (gear icon on Today): t_r37_settings.png, scrolled
    t_r37_settings2.png. Report whether an "Account" card with a "Sign out"
    button exists below Backup (do NOT tap it).
 d. Any crash, red error, endless spinner, error text: screenshot + exact text.

Step 3. Report: build result and every fix; the BuildConfig field names; test
totals; answers to every "Report ..."; anything visually broken.
