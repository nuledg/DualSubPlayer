# DualSub Player — Functionality Description

This document describes the user-visible behavior represented by the current source tree. It is a feature guide, not a claim that every screen and playback path has passed device testing. Verification limits are listed in [AUDIT.md](AUDIT.md).

## 1. Two subtitle slots

The app has two subtitle slots:

- **S1 — Main subtitle:** the primary subtitle shown during normal playback.
- **S2 — Helper subtitle:** a second subtitle intended to help understand the main line. Its visibility follows the configured playback/rewind visibility rules.

Each slot can use its own language, source, style, position, and visibility options. S1 and S2 can be swapped. Supported text subtitle formats include SRT, WebVTT, ASS, and SSA.

### Subtitle sources

The subtitle picker can show external subtitle files found beside the video, files selected manually, and supported subtitle tracks embedded inside the video. Embedded track availability depends on the container and codec. The app's embedded readers cover selected MKV/WebM and MP4/MOV text subtitle cases; see the audit for unsupported formats and extraction limitations.

### Remembering a video's subtitle choices

The app stores S1 and S2 selections separately for each video. On later opens, it attempts to restore the same source and track rather than applying a new automatic selection. The saved state can represent:

- an external subtitle selected for S1 or S2;
- an embedded track selected for S1 or S2;
- a slot intentionally set to Off.

The first open can still use the configured language-based automatic selection. That selected result is then remembered for later opens. Manually selected external files can only be restored while Android's persisted document permission remains valid and the file is still accessible. If a source cannot be restored, the app should report that state instead of silently substituting a different subtitle.

## 2. Tap an individual subtitle word to translate

Words in the app-rendered subtitle text can be tapped to pass the selected word to a compatible Android text-processing app.

### Independent S1 and S2 switches

Open **Settings → Translation**. The two controls are separate:

| Control | What it enables |
|---|---|
| **Main subtitle (S1)** | Word taps in the main subtitle can open the translator. |
| **Helper subtitle (S2)** | Word taps in the helper subtitle can open the translator. |

Both are enabled by default. Each value is stored under its own preference key. Disabling S1 word tapping leaves S2 word tapping available, and disabling S2 leaves S1 available. When a slot is disabled, its subtitle remains visible as normal text; it simply has no word-translation click handler.

### What happens after a word tap

1. The app pauses playback before opening the translation handler.
2. It does not seek to a different timestamp; the player remains at the position where the word was tapped.
3. Android opens the preferred compatible app, or the app chooser when **Ask every time** is selected.
4. When the user returns, playback resumes from that position by default.
5. The **Resume when returning** setting can be disabled. When it is off, the movie stays paused after returning.

The preferred app can be changed at any time in the same Translation settings section. The preference is not tied to a specific subtitle slot.

Word tokenization is designed for Unicode text, including apostrophes, combining marks, numbers, and Persian/Arabic ZWNJ. Punctuation and whitespace are not treated as tappable words.

### Embedded subtitle support

External subtitle cues and supported embedded text subtitle cues use the app's text subtitle rendering layer, which is where the word-click callbacks are attached. Therefore word tapping can work for both source types and for either slot. Bitmap subtitle tracks rendered by ExoPlayer do not expose separate words to this layer; individual word tapping for those tracks would require OCR and is not included in this feature.

## 3. Playback and subtitle-aware navigation

The player includes subtitle-aware rewind/forward behavior. The current settings and implementation include:

- Rewind using subtitle cue timestamps when usable cues are available.
- A configurable rewind offset before a cue's start.
- A previous-subtitle action for the double-tap rewind gesture.
- A fixed-time fallback when no suitable subtitle timing is available.
- Main-subtitle timeline preference, with helper-subtitle timing used when the main track has no suitable cue or after the helper timeline takes over beyond the final main cue.
- An optional rewind-before-pause behavior that replays a relevant subtitle segment and pauses before its end.
- A saved playback position for each video, with periodic and lifecycle-triggered saves.

The exact interaction details and historical refinements are recorded in `AUDIT.md`.

## 4. Subtitle discovery and online downloads

### Local subtitle matching

The app searches for supported external subtitle files in the video's folder when Android storage access permits it. Matching considers the video title and exact episode marker for series. Release characteristics such as resolution, codec, source, and release group can improve ranking among candidates. Other episodes or unrelated titles should not be selected as matches.

### Online providers

The download area supports provider integrations for:

- **SubDL** — requires the user's SubDL API key.
- **OpenSubtitles** — requires the corresponding API credentials supported by the app.

The app ranks returned candidates using configured subtitle languages and release metadata, allows manual or automatic selection, handles ZIP subtitle downloads, and saves the chosen subtitle to the configured location. Live provider calls using the user's own credentials have not been verified in this environment.

## 5. Appearance, gestures, and controls

The settings UI includes gesture, playback, subtitle, style, translation, and download sections. Subtitle appearance can be adjusted independently for S1 and S2, including font, size, color, emphasis, outline/shadow, background, and screen position. Visibility can be configured for relevant playback states.

The player also supports configurable tap zones, long-press controls, horizontal seeking, and vertical brightness/volume gestures. Gesture and playback behavior can vary by the options selected in Settings.

## 6. Platform and storage considerations

- Minimum Android API level: 26 (Android 8.0).
- The project uses Kotlin, Jetpack Compose, Media3/ExoPlayer, and Room.
- Same-folder subtitle enumeration may require **All files access** or a folder selected through Android's Storage Access Framework.
- Persisted URI access can be lost if the user revokes permission or the external file becomes unavailable.
- Bitmap subtitles can be displayed in supported cases but do not support individual word taps without OCR.

## 7. Verification status

The source includes unit tests for subtitle parsing, matching, seeking, download parsing/ranking, and word tokenization. Some test results in the audit were obtained with local Kotlin test harnesses rather than the full Android Gradle test task. The latest independent translation switches have been checked in source, but the latest project has not been confirmed by a complete Android Gradle build or physical-device test in the preparation environment. See [AUDIT.md](AUDIT.md) before relying on unverified UI or lifecycle behavior.


## Minimal playback controls refinement

- The optional **Show time on edge tap** setting works while video is playing and while it is paused. A single tap in the top or bottom quarter of the screen, outside active controls, displays only the right-side time for five seconds. A new qualifying tap restarts the five-second window. The display is not dismissed just because playback resumes.
- The middle play/pause zone continues to use the configured tap-zone behavior. Vertical brightness/volume swipes are not treated as taps.
- The play/pause control is a white glyph without a filled circular background. The seek thumb is slightly larger than the previous version (4.5 dp idle radius; 5.5 dp while scrubbing).
