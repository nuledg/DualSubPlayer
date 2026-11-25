# DualSub Player

DualSub Player is an Android video player designed for language learning. It can show a main subtitle (S1) and a helper subtitle (S2), navigate playback by subtitle timestamps, and send individual subtitle words to a translation app.

## Main features

- Two independently configured subtitle slots: **Main (S1)** and **Helper (S2)**.
- External subtitle files and supported embedded subtitle tracks.
- Subtitle-aware rewind and forward navigation, with configurable rewind offset and fallback seeking.
- Per-video playback resume and per-video restoration of the selected S1/S2 subtitle sources.
- Independent subtitle appearance, position, and visibility settings.
- Tap-to-translate for individual words in S1 and S2, with separate enable/disable switches.
- Translation-app selection, an **Ask every time** option, and configurable resume after returning from the translator.
- Local subtitle matching and online subtitle search/download through SubDL and OpenSubtitles.
- Gesture zones, horizontal seek gestures, and brightness/volume swipes.
- Minimal playback seek rail with a right-time toggle for remaining time or full video duration.
- Optional top/bottom edge taps during playback or while paused that briefly show the right-side time without the seek rail.

See [FUNCTIONALITY.md](FUNCTIONALITY.md) for a fuller explanation and limitations. See [AUDIT.md](AUDIT.md) for implementation and verification notes. See [CHANGELOG.md](CHANGELOG.md) for the recent changes in this build.

## Build and run

1. Open this `DualSubPlayer` folder in Android Studio.
2. Let Android Studio sync the Gradle project.
3. Run the `app` configuration on a device or emulator with Android 8.0 (API 26) or newer.

The project includes the Gradle wrapper. To run local unit tests:

```bash
./gradlew testDebugUnitTest
```

The test command must be run in an environment that can download the configured Gradle distribution and Android/Google dependencies. The latest source changes documented in `AUDIT.md` have not been verified by a full Android Gradle build or on a physical device in the preparation environment.

## Project structure

- `app/src/main/java/com/dualsub/player/player/` — playback ViewModel, resume behavior, player/device controls.
- `app/src/main/java/com/dualsub/player/subtitle/` — subtitle parsing, cues, matching, selection, embedded subtitle readers, and seek logic.
- `app/src/main/java/com/dualsub/player/download/` — SubDL/OpenSubtitles providers, result ranking, filename parsing, and saving.
- `app/src/main/java/com/dualsub/player/ui/` — player screen, subtitle layer, settings, dialogs, and controls.
- `app/src/main/java/com/dualsub/player/data/` — Room database and app preferences.
- `app/src/test/` — unit tests and small subtitle/media fixtures.

## Translation settings

Open **Settings → Translation**:

| Setting | Default | Purpose |
|---|---:|---|
| Main subtitle (S1) word translation | On | Allows taps on individual words in the main subtitle. |
| Helper subtitle (S2) word translation | On | Allows taps on individual words in the helper subtitle. |
| Preferred translation app | Ask every time | Choose a compatible Android text-processing app, or return to the chooser. |
| Resume when returning | On | Resume from the same playback position after returning from the translator. |

The S1 and S2 word-translation switches are independent and saved separately. Turning one off does not affect the other.

## Same-folder subtitle discovery

Android may restrict access to a video's parent folder. To let the app list subtitle files next to a video, grant the app's **All files access** permission where available, or choose the video's folder once through the subtitle folder picker.

Supported external subtitle formats include SRT, WebVTT, ASS, and SSA. Matching uses the movie/show title and, for series, the exact season and episode marker. Release metadata such as resolution, source, codec, and release group helps rank matching files.

## Important subtitle limitations

- Word tapping works for text subtitles rendered by the app, including supported external files and supported embedded text tracks.
- Bitmap/image subtitle tracks (for example PGS/DVB) do not provide individual selectable words. OCR would be needed for word translation on those tracks.
- Restoring an external subtitle selected through Android's document picker depends on Android retaining the app's persisted URI permission and the file remaining available.
- A previously selected embedded track may not be restorable if the media file changes or the track is removed/re-encoded.
- Embedded subtitle extraction is format/container-dependent. See `AUDIT.md` for known coverage limits.

## Security note

Provider credentials are stored in app-private preferences. The app excludes its data from Android backup in the current manifest, but credentials are not encrypted by this app. Review this before distributing the app beyond personal use.
