# DualSub Player: final audit (M7, second pass)

This audit replaces the first one. I read every source file myself, compiled all code that does not need
Compose, Media3 or Room against the real Android API, ran the tests, and checked the two provider APIs
against public documentation. Then I fixed what I found.

## What was actually verified

| Check | Result |
|-------|--------|
| Original 66 unit tests | Re-run: all pass. The first audit's number was correct. |
| Non-UI code (`subtitle/`, `download/` except the controller, `data/Prefs`, `player/MediaInfo`, `DeviceControls`, `EmbeddedSubtitleStore`) | Compiles with Kotlin 2.0.20 against the Android API 34 jar. |
| New tests added in this pass | 12 (total **92, all passing**). |
| SubDL and OpenSubtitles request and answer shapes | Match public documentation and open-source clients (field names, parameters, download host). Not tried with real keys. |
| Compose screens, ExoPlayer code, Room, `PlayerViewModel`, `SubtitleDownloadController` | **Read line by line, not compiled.** Maven and Google repositories are not reachable from my environment, so no Gradle build was possible. |

Levels used below: **Tested** = unit tests that I ran. **Compiled** = compiled against Android API 34, not run on a
device. **Reviewed** = read carefully, never compiled or run.

## Where the first audit was wrong or too generous

1. **Row 19 (encoding detection).** It said the general detector "was not exercised". I exercised it: for Persian text
   in Windows-1256 with *no* language hint (a file named `movie.srt`) the detector answers "Thai" and the text came out as
   garbage. Fixed with an Arabic-script check before the detector. Tested.
2. **Auto pick** could choose a "forced" English track (a few foreign-speech lines) as the main subtitle, because it
   came first in the file. Fixed: full subtitles win. Tested.
3. **Language from file name** read the name from the start, so `The.English.Patient.fa.srt` was English and a title
   with "Per" was Persian. "Add file" also read the video's own name. Fixed (reads from the end, skips the video name). Tested.
4. **S1 and S2 overlapped** at the default positions whenever both had two lines (S2 center 79%, S1 center 90%).
   Fixed: S2 moves up just enough to stay clear, only when you placed it above S1. Tested (math); drawing is reviewed only.
5. **API keys and the OpenSubtitles password were included in Android backups** (`allowBackup="true"`). Fixed:
   backup and device transfer exclude settings and database.
6. **No `gradlew`** although the README told you to run it. Added the wrapper (Gradle 8.9).
7. **MKV subtitle extraction** read 8 KB for every block header. Now 1 KB, about a third of the disk reads on a big file.
8. **Picture subtitle (PGS/DVB) track matching** counted every text track in the file. It now matches by format and
   position among tracks of that format. Still never run against a real PGS file.

Everything else in the first audit that I checked in code matched: rewind logic and the acceptance example, return
point, 0.5 s S2 lead, gestures, axis lock, controls overlay, resume key and cleanup, download ranking, zip handling,
save naming and fallback.

## Round 3 changes (your last list)

| Request | Status | Level | Notes |
|---------|--------|-------|-------|
| Show main/helper subtitle in any situation (playing, paused, after rewind tap, after forward tap) | Done | Tested + reviewed | Per subtitle, any mix, even "never". Rule tested. Forward mode lasts until the sentence reached ends. |
| Tabs in Settings | Done | Reviewed | Gestures, Playback, Subtitles, Style, Translation, Download. |
| Video follows the finger on a horizontal swipe | Done | Reviewed | Pauses while sliding, nearest-key-frame seeks every 70 ms, exact seek on release, resumes if it was playing. Smoothness on your phone is unknown. |
| Zones in any order and width, with preview | Done | Tested + reviewed | Order and widths tested. Drawing reviewed only. |
| Preview for settings | Done where possible | Reviewed | Zones, paused overlay, replay timeline, subtitle visibility, subtitle style. No preview for download keys or folders. |
| Tap froze the video until resume | Mitigated, not proven | Reviewed | I could not reproduce it. A tap now always continues playing from the new spot, and a watchdog re-seeks if the picture does not move 1.2 s later. |
| Rewind logic | Done | Tested | More than the set time into a sentence replays it; otherwise, or on a quick second tap (window in Settings), the previous one. |
| Any subtitle language | Done | Tested | 70 languages, searchable picker, any pair for main and helper. Old code pages (Russian, Turkish, Polish, Hebrew, Greek...) decode with the language hint. |
| Zones, axes and icons after pausing with the middle tap (switchable) | Done | Reviewed | Same drawing as the settings preview. |
| Modern, minimal design | Done | Reviewed | New violet-blue dark theme, rounded shapes, gradient control bars, icon buttons. |

## Requirements

| # | Requirement | Status | Level | Notes |
|---|-------------|--------|-------|-------|
| 1 | S1 shown the whole time | Done | Reviewed | Text drawn by the app, pictures by ExoPlayer. |
| 2 | S2 above S1, yellow, only in rewind mode, position in settings | Done | Tested + reviewed | Rewind logic and placement math tested. |
| 3 | Rewind tap: cue containing t or last before; over threshold goes to cue start, else previous cue | Done | Tested | Default threshold is now 500 ms. |
| 4 | First tap saves returnPoint, later taps keep it | Done | Tested | |
| 5 | S2 hides at position >= returnPoint | Done | Tested | Checked 10 times per second. |
| 6 | Exact seeking | Done | Reviewed | `SeekParameters.EXACT` set when the player is built. |
| 7 | S2 chosen by time overlap | Done | Tested | |
| 8 | Forward tap: start of next S1 cue | Done | Tested | |
| 9 | Manual seek leaves rewind mode | Done | Tested + reviewed | |
| 10 | Tap shows no controls or icons | Done | Reviewed | |
| 11 | Zone split configurable | Done | Tested + reviewed | Three zones in any order and width (default rewind 35, play/pause 30, forward 35). |
| 12 | Long press: play/pause + controls, hide after 4 s | Done | Reviewed | |
| 13 | Vertical swipe: volume (now the RIGHT half, as you asked), indicator | Done | Compiled + reviewed | |
| 14 | Vertical swipe: brightness (now the LEFT half), restored on exit, indicator | Done | Compiled + reviewed | |
| 15 | Horizontal swipe: relative seek, live preview, commit on release | Done | Reviewed | |
| 16 | Axis lock, tap only without movement, long press > swipe > tap | Done | Reviewed | |
| 17 | Auto-load same-name subtitle files | Done with a setting | Compiled + tested (path math) | Needs "All files access" (new switch in Settings) or the one-time folder choice. Android gives apps no other way to list a video's folder. |
| 18 | SRT, VTT, ASS/SSA | Done | Tested | |
| 19 | Encoding detection, Windows-1256 to UTF-8 | Done | Tested | Fixed in this pass (see above). |
| 20 | RTL | Done | Reviewed | Text direction follows the content. |
| 21 | List embedded tracks | Partial | Tested | MKV, WebM, MP4, MOV. Not AVI, TS, streams. |
| 22 | Any track can be S1 or S2, both kinds at once | Partial | Reviewed | Picture tracks can only be S1. |
| 23 | Full cue list extracted in background, cached | Done | Tested + compiled | |
| 24 | SRT, ASS, WebVTT, mov_text support sentence rewind | Done | Tested | |
| 25 | Picture formats: shown, message, fixed back-jump | Partial | Reviewed | PGS and DVB drawn by ExoPlayer. VobSub cannot be shown. |
| 26 | Swap button | Done | Reviewed | |
| 27 | Provider interface | Done | Tested | |
| 28 | SubDL first, then OpenSubtitles.com | Partial | Tested | Parsing tested; request format matches documentation; no live call with real keys. |
| 29 | Official APIs only | Done | n/a | |
| 30 | Persian and English | Done | Tested | Now any language, see round 3. |
| 31 | Parse title, year, season/episode, resolution, codec, source, group | Done | Tested | |
| 32 | Ranking rules | Done | Tested | Strict: a result with no season or episode (in its data or its name) is dropped for series. |
| 33 | Ranked list, auto pick, choose S1 or S2 | Done | Tested + reviewed | Auto pick uses your language settings. |
| 34 | Zip files | Done | Tested | |
| 35 | Save as base.lang.srt | Done | Compiled | Always UTF-8 SRT. |
| 36 | Download location with fallback and message | Done | Compiled | Writing next to the video needs All files access or write access to the chosen folder. |
| 37 | "Open with" for video/* on content, file, http, https | Done | Reviewed | Second filter added for links and files without a video type (names ending in mkv, mp4, m4v, avi, webm, mov, ts). |
| 38 | Save position every 5 s and on pause or exit | Done | Reviewed | |
| 39 | Key by name + size + duration | Done | Reviewed | |
| 40 | Delete entries not opened for 30 days (configurable) | Done | Reviewed | Runs when the player starts. |
| 41 | Clear entry in the last 20 seconds | Done | Reviewed | |
| 42 | Settings: zone split | Done | Reviewed | |
| 43 | Settings: style for S1 and S2 | Done | Tested + reviewed | Colors come from a palette; outline is black. |
| 44 | Settings: drag subtitles on a live preview, saved as percent | Done | Reviewed | |
| 45 | Settings: download location, API keys, thresholds, retention | Done | Reviewed | |
| 46 | Audit of existing code | N/A | n/a | No code was attached at the start. |
| 47 | Milestones M1 to M7 | Done | n/a | |
| 48 | Complete files | Done | n/a | |
| 49 | Unit tests | Done | Tested | 92 tests for logic. None for screens or the player. |
| 50 | Kotlin, Compose, Media3, Room, minSdk 26 | Done | n/a | |

## Known gaps (honest list)

1. **No Gradle build has ever run on this project.** The Compose, Media3 and Room files are unverified by a compiler.
   Expect a few small compile errors on the first sync. The ones I would look at first: `TextStyle.copy(drawStyle = ...)`
   in `SubtitleLayer.kt`, and the `Layout` and `layoutId` code I added there.
2. Nothing has run on a device. Gestures, seeking, PGS drawing and RTL look are all unseen.
3. SubDL and OpenSubtitles were not called with real keys.
4. API keys and the OpenSubtitles password are plain text in app-private storage (no longer backed up).
5. Embedded subtitles do not work for streams, AVI or TS.
6. "All files access" is a sensitive permission: fine for a personal app, not accepted by Google Play without a review.
7. No UI tests or on-device tests.

## First run checklist

1. Open the folder in Android Studio, sync, fix any compile errors (report them to me and I will fix them).
2. Run `./gradlew testDebugUnitTest` and use the test count reported by the current checkout; earlier test counts in this audit refer to earlier milestones.
3. On a phone: play a video, long press (controls), Settings, turn on All files access, reopen the video.
4. Test the acceptance example by tapping the left side of the screen twice during a sentence.


## Round 4 changes (filename matching and download results)

| Area | Status | Verification | Notes |
|------|--------|--------------|-------|
| Filename separators | Done | Tested | Title separators are normalized independently of punctuation style. Codec parsing recognizes `x265`, `x.265`, `x-265`, `x_265`, `H.265`, `H-265`, and related forms. Resolution and season/episode markers accept separator variations. The original basename is still sent unchanged to SubDL as `file_name`. |
| SubDL result list viewport | Updated | Reviewed, not Android-compiled | The dialog now has a bounded screen-height layout and a weighted `LazyColumn` result viewport. The list has a visible result count, loading/empty states, and resets to the first result after a new search. |
| SubDL unpacked files | Done | Tested with official documentation-shaped fixture | Individual entries in `unpack_files` become separate selectable results; the movie/show year is taken from `results[0]` when a subtitle item omits it. |
| ZIP language choice | Done | Tested | When a ZIP includes more than one language, the selected S1/S2 candidate language is used to choose the matching file. Generic untagged files remain a fallback. |
| Download-focused checks | Passed | Local Kotlin test harness | 27 filename/ranking test methods and 18 SubDL/OpenSubtitles/ZIP test methods passed (45 focused test methods in total). These use local JSON/JUnit test stubs, not a full Android Gradle run. |
| Full Android Gradle test/build | Blocked | Not run | `./gradlew :app:testDebugUnitTest` could not download Gradle 8.9 because `services.gradle.org` did not resolve. Android SDK is not installed in this environment. Compose dialog changes therefore remain reviewed, not compiler-verified or device-tested. |

The SubDL request/response fields were checked against the current official API documentation: `film_name`, `file_name`, `subs_per_page` (maximum 30), `unpack=1`, and per-file `unpack_files[].url`. Live requests with the user's private API key were not made in this pass.


## Round 5 changes (local subtitle title matching)

| Area | Change | Verification | Notes |
|---|---|---|---|
| Series subtitle matching | Accepts subtitle filenames containing the full series title followed by the exact same `SxxEyy` or `NxNN` marker, even when a prefix appears before the title. | Pure Kotlin acceptance checks passed; new unit cases added. | Other season/episode pairs and extra title words before the episode marker remain rejected. |
| Movie subtitle matching | Accepts prefixed subtitle names when the complete movie title is present and the remaining suffix consists of language/release metadata. | Pure Kotlin acceptance checks passed; new unit cases added. | Sequel numbers and mismatching movie years remain rejected. |
| Same-folder lookup | Maps a video's path into the previously selected SAF folder tree, including MediaStore-picked videos when their provider exposes a real path. | Path-mapping checks passed. | Android providers do not always expose a real path; All files access or selecting the video's folder remains necessary. |
| Full Android Gradle build | Not verified. | Blocked. | Gradle 8.9 could not be downloaded because `services.gradle.org` did not resolve in this environment. No Android device test was run. |


## Round 6 changes (tap a subtitle word to translate)

| Area | Change | Verification | Notes |
|---|---|---|---|
| Subtitle word tapping | Words in both S1 and S2 are individually tappable. Taps on punctuation or whitespace do nothing. | Pure Kotlin tokenizer compiled and manually exercised. | Uses Unicode letters, combining marks, numbers, apostrophes, and Persian/Arabic ZWNJ. |
| Translator handoff | Sends the selected word through Android `ACTION_PROCESS_TEXT`; the system chooser lists compatible apps when no preferred app is selected. | Source reviewed; not device-tested. | The preferred package can be set or changed in Settings → Translation. If no process-text handler exists, the player falls back to the Android share chooser. |
| Gesture interaction | The player gesture surface is behind the subtitle layer, so tapping a word does not trigger rewind/play/forward. | Source reviewed; not Compose-compiled. | Other screen areas continue to use the existing gesture layer. |
| Focused tests | Added three tokenizer unit tests for contractions/punctuation, Persian ZWNJ, and numbers. | Test source added; Gradle test run blocked. | Gradle 8.9 download failed because `services.gradle.org` could not resolve. |
| Full Android build/device test | Not verified. | Blocked. | No Android SDK/device test was available in this environment. |

## Subtitle word translation

- Individual words in the app-rendered S1 and S2 subtitle text can be tapped. This is shared by external subtitle files and embedded text subtitle tracks because both are rendered through the same subtitle layer.
- A word tap immediately calls ExoPlayer pause before opening Android's text-processing chooser or the selected app. The current playback position is not changed. The Round 7 default is to resume automatically when the user returns; this can be disabled in Settings → Translation.
- Settings includes a Translation section. The user can choose a compatible text-processing app or select “Ask every time”; the selected package is stored in app preferences and can be changed later.
- Bitmap subtitle tracks (such as PGS/DVB rendered by ExoPlayer) do not expose individual words to this tap handler; OCR would be required to support them.

## Round 7 changes (translation return and per-video subtitle restoration)

| Area | Change | Verification | Notes |
|---|---|---|---|
| Resume after word translation | Enabled by default. After the selected translator or Android chooser returns, the player resumes without seeking, from the exact position where the word was tapped. | Source reviewed; lifecycle not device-tested. | A switch in Settings → Translation can disable automatic resume. If disabled, the player stays paused. |
| Translator preference | The existing Translation settings menu still allows a specific compatible app or “Ask every time”; the new resume switch is stored separately. | Source reviewed; not device-tested. | The user can change the app at any time without reinstalling. |
| Per-video subtitle memory | Stores S1 and S2 choices separately, keyed by video filename and file size. The database upgrade adds a new table and migrates version 1 data without deleting resume history. | Migration and DAO source reviewed; not run against an installed database. | Existing video resume entries are preserved. |
| External subtitle restoration | Saves the selected subtitle's URI, name and language. This includes files chosen manually outside the video's folder, provided Android's persisted URI permission remains valid. | Source reviewed; not device-tested. | If Android revokes access or the file is moved/deleted, the player reports the missing subtitle rather than silently substituting another one. |
| Embedded subtitle restoration | Saves the container, track ID, codec, language, title and index; resolves the same track on the next open. | Three codec unit tests added; Gradle test suite not run. | Covers text and picture tracks that the existing embedded reader can enumerate. |
| Slot behavior | A deliberately cleared S1 or S2 slot stays empty after reopening. Swapping subtitles also updates the saved configuration. | Source reviewed. | First open still uses automatic language-based selection, then remembers that selection for subsequent opens. |
| Full Android build/device test | Not verified. | Not run. | Gradle/Android SDK availability remains the limiting factor in this environment. |

## Round 8 changes (independent S1/S2 word-translation switches)

| Area | Change | Verification | Notes |
|---|---|---|---|
| Main subtitle translation | Added a dedicated `mainSubtitleWordTranslationEnabled` preference and Settings switch. | Source reviewed. | Defaults to `true`; stored under its own preference key. |
| Helper subtitle translation | Added a dedicated `helperSubtitleWordTranslationEnabled` preference and Settings switch. | Source reviewed. | Defaults to `true`; stored under a separate preference key. |
| Independent rendering behavior | `PlayerScreen` passes the S1 and S2 click callbacks separately to `SubtitleLayer`; each callback is `null` when its own setting is disabled. | Source reviewed. | Turning off one slot leaves the other slot's word tapping enabled. Subtitle text remains visible. |
| Settings persistence | Each switch updates its corresponding preference immediately. | Source reviewed. | Settings take effect without reopening the video. |
| Existing translator behavior | Kept pause-at-current-position, preferred app / Ask every time, and resume-on-return default. | Source reviewed. | `resumeAfterTranslation` remains a separate setting and defaults to `true`. |
| Full Android Gradle build/device test | Not verified. | Not run. | The environment used to prepare this archive has not completed a full Gradle build or physical-device test. |

### Current translation-setting defaults

- Main subtitle (S1) word translation: **On**.
- Helper subtitle (S2) word translation: **On**.
- Preferred translator: **Ask every time** until the user selects a compatible app.
- Resume when returning from translator: **On**.

The switches are implemented as separate preference values and passed independently to the subtitle renderer. Both external and supported embedded **text** subtitle cues use that renderer. Bitmap/image subtitle tracks still do not have individual word hit targets; OCR is not part of this update.

### Verification for this documentation build

The archive was inspected to confirm that the two independent preferences, settings callbacks, and separate S1/S2 click-handler conditions are present in the source. The ZIP archive was integrity-tested after adding this documentation. This source inspection is not a substitute for a successful Android Gradle build or device test.


## Current UI update — seek rail and edge-time display

| Request | Status | Level | Notes |
|---------|--------|-------|-------|
| Make the seek slider more minimal | Implemented | Reviewed, not built | Custom 2 dp track, small thumb, and larger touch target for seeking. |
| Tap right-side time to switch display | Implemented | Reviewed, not built | Toggles remaining time (`-m:ss`) and full duration (`m:ss`). |
| Optional top/bottom edge tap while paused | Implemented | Reviewed, not built | Preference defaults off; top/bottom 25% of the screen; only taps outside active controls reach the edge-time action. |
| Show only the right-side time and fade after 5 seconds | Implemented | Reviewed, not built | A new qualifying tap restarts the timer; fade animation begins after the 5-second display period. |
| Preserve the central play/pause action | Implemented | Reviewed, not built | The edge test is limited to the outer vertical quarters. Center-section tap behavior remains on the existing zone logic. |

**Verification limitation:** I attempted `bash gradlew testDebugUnitTest --no-daemon`. The wrapper attempted to download Gradle 8.9 but failed because `services.gradle.org` could not be resolved in the preparation environment. Therefore, these latest Compose changes have not been compiled or exercised on an Android device. Run the Gradle test task and install the app from Android Studio before relying on the update.


## Follow-up correction — `detectTapGestures` unresolved reference

| Area | Correction | Verification | Notes |
|---|---|---|---|
| Compose gesture call | Changed the package-qualified extension call to an imported `detectTapGestures` extension invoked on the `PointerInputScope`. | Source checked. | This resolves the Kotlin call-shape mistake reported at `ControlsOverlay.kt:52:76`. |
| Compose dependency | Added an explicit `androidx.compose.foundation:foundation` dependency. | Gradle declaration checked. | The existing Compose BOM controls the version. |
| Edge-time feature wiring | Rechecked the preference default, Settings update, paused-only edge check, time overlay, and 5-second timer. | Source reviewed. | The full interaction was not device-tested. |
| Full Gradle test/build | Not verified. | Blocked. | `./gradlew testDebugUnitTest --no-daemon` failed while downloading Gradle 8.9 with `UnknownHostException: services.gradle.org`. |

The extension import/call correction is included in the updated source archive. The inability to download Gradle means no claim is made that the entire Android project has been compiler-verified.


## Round 9 — edge-time tap during playback and minimal controls polish

| Area | Change | Verification | Notes |
|---|---|---|---|
| Edge-time gesture | The top/bottom quarter tap now displays the time while playing and while paused. | Source inspection passed. | Only a tap triggers it; vertical swipes remain handled by the swipe recognizer. |
| Overlay lifetime | The right-side time remains for five seconds even if playback continues; a new edge tap restarts the timeout. | Timer and state wiring reviewed. | No immediate dismissal on play-state change. |
| Overlay pass-through | Empty areas of the top/bottom control bars pass the enabled edge-time tap through to the gesture layer. | Modifier wiring reviewed. | Active controls keep their handlers. |
| Play/pause appearance | Removed the purple filled circle; retained a white play/pause icon. | Source inspection passed. | 56 dp touch target retained. |
| Seek thumb | Increased radius to 4.5 dp idle and 5.5 dp while scrubbing. | Source inspection passed. | Track remains 2 dp with 28 dp touch height. |
| Full Android build/device test | Not verified. | Blocked. | Gradle 8.9 distribution is not cached; host availability prevents full compilation here. |
