Paste everything below this line into Antigravity CLI as one task:
---------------------------------------------------------------
Project: C:\Users\Admin\Documents\DevSecOps Tracker

ROUND 39: Topic Notes (tutor-written markdown notes from Supabase table
topic_notes, with the learner's own-words box). Source edited by Claude, NOT
compiled. No commit, no push.
New/changed:
- data\Notes.kt (new): NoteMeta, NoteFull, Notes.list/get/forModule/searchIds/
  saveMyWords, disk cache in filesDir/notes_cache, shareText.
- ui\MarkdownView.kt (new): own small markdown renderer; pure parseMarkdown().
- ui\NotesScreens.kt (new): NotesScreen (search, filter All/Draft/Reviewed/Weak,
  grouped Phase > Module, "Abhi note nahi" rows, Share module) and
  NoteDetailScreen (markdown, weak-spot card, "Mere shabdon mein" box,
  Save / Mark reviewed / Back to draft, revision-card questions, Share icon).
- ui\Navigation.kt: Routes.NOTES ("notes"), Routes.NOTE ("note/{id}").
- ui\ProgressScreen.kt: new card "Notes" above "Gates and labs".
- app\src\test\...\data\NotesTest.kt (new, 4 tests).
Supabase now holds 15 notes (M0.1: 8, M0.2: 6, M0.3: 1).
Device: tablet PFI22N1G1323D2463. Run `adb devices` first; if "offline" run
`adb kill-server` then `adb start-server` and check once more; if still not
"device", stop and report. If the device is locked, do not unlock it, report.

*** THE APP TESTING PART IS READ-ONLY ***
NEVER tap "Sign out", Start day, Take break, Resume, End day, Mark leave,
Cancel leave, Save & end, any attendance history row, any Revise rating
button, "Mark cleared", Save/Submit/Mark done, timer Start/Save, reminder
switch, and on a note NEVER tap "Save", "Mark reviewed", "Back to draft", and
never type into the "Mere shabdon mein" box. Tapping the Share icon only to
see the chooser is fine, then press Back (do NOT send anything). Do NOT clear
app data, do NOT uninstall, do NOT toggle airplane mode or any device setting.
Do not print local.properties and do not type any password anywhere.

Step 1. Build, tests, install:
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\gradlew.bat assembleRelease
  .\gradlew.bat testDebugUnitTest
  adb -s PFI22N1G1323D2463 install -r app\build\outputs\apk\release\app-release.apk
  adb -s PFI22N1G1323D2463 shell am start -n com.devsecops.tracker/.MainActivity
Likely compile issues: Postgrest filter `or { ilike(...) }` inside
Notes.searchIds (if `or` is not available in supabase-kt 3.0.3, replace with a
single ilike on title and keep the rest), Columns import, `Module` name clash
with java.lang.Module in NotesScreens.kt (import is
com.devsecops.tracker.data.Module), `@Composable inline()` returning
AnnotatedString in MarkdownView.kt, Icons.Default.Share import, `Json`
encodeToString import. You MAY fix compile errors with the smallest change
that keeps the intent. List EVERY fix (file, line, before -> after). If
install fails with a signature/downgrade error, do NOT uninstall; stop. If a
test FAILS, do not change production code to pass it; report name, expected,
actual, totals (expect 38).

Step 2. Screens (screenshots, report the texts asked):
 a. App opens without a login form: t_r39_open.png.
 b. Progress tab -> card "Notes" (above "Gates and labs"): t_r39_progress.png.
 c. Tap it. t_r39_notes.png. Report: title bar subtitle (expect "15 notes"),
    the phase headers, the module headers with their note counts, whether a
    muted "Abhi note nahi: ..." line shows under M0.1 / M0.2 / M0.3 and which
    topic names it lists, and modules with no notes shown as one muted line.
 d. Type "handshake" in the search box (typing in the SEARCH box is allowed):
    t_r39_search.png. Report which note(s) remain. Clear the box. Then type
    "SSRF" and report which notes remain (expect IP addressing note via
    content search). Clear it.
 e. Filter tabs: tap Draft, Reviewed, Weak. Report the count of notes under
    each (expect all 15 Draft, 0 Reviewed; Weak = notes whose topic has an
    open weak spot, report the real list). Back to All.
 f. Open "TCP three-way handshake": t_r39_note.png and scroll through it
    (t_r39_note2.png, t_r39_note3.png). Report: do headings render larger than
    body, are code blocks monospace and not wrapped (horizontally scrollable),
    do the ASCII diagrams stay aligned, do tables show columns, do **bold**
    and `inline code` render without the raw asterisks/backticks. Any raw
    markdown characters visible? Quote them.
 g. Open "OSI model layers" and "IP addressing & CIDR" and report the same
    about their tables and diagrams.
 h. On a note: is there a weak-spot card or badge (for topics with an open
    weak spot)? Report the topic and the text. Is the "Mere shabdon mein" card
    present with Save and "Mark reviewed" (Mark reviewed must look DISABLED
    while the box is empty)? Do NOT tap them. Are the revision-card questions
    listed at the bottom? Report how many for this note.
 i. Tap the Share icon in the title bar: confirm the system share chooser
    opens, press Back. Back out to the list, Back to Progress.
 j. Any crash, red error, endless spinner, clipped text: screenshot + exact text.

Step 3. Report: build result and every fix; test totals; answers to every
"Report ..."; anything visually broken. State clearly that offline reading was
NOT tested.
