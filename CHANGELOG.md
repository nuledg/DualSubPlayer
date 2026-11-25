# Changelog

## Follow-up refinement — edge-time tap during playback and minimal controls polish

- Changed **Show time on edge tap** so a single tap in the top or bottom quarter works during normal playback as well as while paused.
- Removed the play/pause control's filled purple circular background; the control now displays only the white play or pause glyph.
- Increased the idle seek thumb radius from 3.5 dp to 4.5 dp and the active scrubbing radius from 5 dp to 5.5 dp.
- Kept the central play/pause tap zone, seek interactions, active buttons, and vertical brightness/volume swipes on their existing handlers.
- The time-only overlay still fades after 5 seconds, and every new qualifying edge tap restarts that timer. Resuming playback no longer dismisses it early.
- Updated the Settings explanation and README to describe both playback states.
- Source-level checks and ZIP integrity were performed. A full Android build remains unverified because Gradle 8.9 is not cached and the distribution host is unavailable from this environment.

## Follow-up fix — Compose gesture extension import

- Fixed the `detectTapGestures` unresolved-reference error in `ControlsOverlay.kt` by importing the extension and invoking it on the `PointerInputScope` receiver.
- Declared `androidx.compose.foundation:foundation` directly because the controls source uses Foundation APIs. The Compose BOM continues to manage its version.
- Source wiring was reviewed again. The full Gradle compile/test remains blocked in this environment because `services.gradle.org` does not resolve.

## Current update — minimal seek rail and paused edge-time display

- Replaced the Material seek widget with a thinner 2 dp rail and a compact progress thumb while keeping a 28 dp touch area for seeking.
- Made the right-side time label tappable. It switches between remaining playback time (for example, `-59:58`) and the full video duration (for example, `59:58`).
- Added **Settings → Gestures → When the video is paused → Show time on edge tap**. It is off by default.
- When enabled, a tap in the top or bottom quarter of the paused video, outside an active control, briefly displays only the right-side time. The time fades away after 5 seconds; another qualifying tap restarts the timer.
- Kept the central play/pause zone, the seek rail, action buttons, subtitle word taps, and swipe gestures separate from the edge-time behavior.
- Full Android Gradle build/device verification was not possible in the preparation environment because the Gradle 8.9 distribution could not be downloaded (network DNS unavailable).

## Current update — independent word-translation controls

- Added a separate **Main subtitle (S1)** word-translation switch.
- Added a separate **Helper subtitle (S2)** word-translation switch.
- Both switches default to enabled and persist independently in app preferences.
- Disabling one subtitle slot's word translation does not disable the other slot or hide the subtitle text.
- Kept the translator app preference and **Ask every time** option.
- Kept **Resume when returning** enabled by default, with a setting to leave playback paused instead.
- Kept the pause-at-current-position handoff: tapping a word pauses playback without seeking to a different timestamp.

## Previous update — translator handoff and subtitle restoration

- Added return behavior so playback resumes from the same position after returning from the translation app by default.
- Added a setting to disable automatic resume after returning from the translator.
- Added per-video storage for the selected S1 and S2 subtitle sources.
- Restores previously selected external subtitle files and embedded subtitle tracks when they remain available and accessible.
- Preserves an intentionally empty (Off) subtitle slot.

## Initial word-translation feature

- Added individual word hit targets to the app-rendered S1 and S2 subtitle text.
- Sends the selected word to a compatible Android text-processing activity, with a system chooser or share fallback as available.
- Added a preferred translation-app setting that can be changed later.
- Added Unicode-aware word tokenization for apostrophes, combining marks, numbers, and Persian/Arabic ZWNJ.
- Bitmap/image subtitle tracks remain outside individual word translation because they do not expose selectable text; OCR would be required.
